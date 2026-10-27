package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.HeroShockwave;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.thor.ThorMode;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvent;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEventType;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * The six electric combos at runtime: every electric action (LMB discharge,
 * RMB wave, RMB storm, MMB strike, and A's throw / recall events) feeds the
 * recognizer; a recognised combo fires its own effect (#17, #27), or does
 * nothing when it needs Mjölnir and the hand is empty (#148, no automatic
 * recall). Success posts COMBO_END (move ELECTRIC_COMBO, success = true);
 * B's own COMBO_END listener then drops the automatic sky lightning (#129).
 * Effects and numbers PLACEHOLDER; sky target policy OPEN (#75, #130, #149).
 */
public final class ElectricCombos {
   private ElectricCombos() {
   }

   /** One electric action happened (its hits are in combat.actionPrimary / actionAll). */
   static void push(ServerPlayer player, ThorState state, ThorCombatState combat, ElectricAction.Kind kind) {
      push(player, state, combat, kind, combat.stepDirection, combat.stepAirborne, CombatHits.hammerInHand(player));
   }

   static void push(ServerPlayer player, ThorState state, ThorCombatState combat, ElectricAction.Kind kind, MoveDirection direction, boolean airborne,
      boolean hammerAtAction) {
      if (state.mode != ThorMode.ELECTRIC) {
         return;
      }

      ElectricAction action = new ElectricAction(kind, direction, airborne, hammerAtAction, combat.ticks, combat.actionPrimary, combat.actionAll);
      combat.actionPrimary.clear();
      combat.actionAll.clear();
      ElectricRecognizer.Result result = combat.recognizer.push(action, ThorCombatConfig.get().electricComboGapTicks, CombatHits.hammerInHand(player));
      if (result != null) {
         fire(player, combat, result);
      }
   }

   /** The MMB melee strike in the electric mode: its hits become an MMB_STRIKE action (no bonus damage, no new mechanic). */
   static void strikeAction(ServerPlayer player, ThorState state, ThorCombatState combat, List<LivingEntity> hits) {
      combat.actionPrimary.clear();
      combat.actionAll.clear();
      for (LivingEntity target : hits) {
         combat.actionPrimary.add(target.getUUID());
         combat.actionAll.add(target.getUUID());
      }

      push(player, state, combat, ElectricAction.Kind.MMB_STRIKE);
   }

   /** A's THROW / RECALL event of this Thor (the recognizer's MMB throw / recall actions, #147). */
   static void onHammerEvent(ThorEvent event) {
      ServerPlayer player = player(event.attackerId());
      ThorState state = player == null ? null : ThorState.of(player);
      if (state == null || state.mode != ThorMode.ELECTRIC) {
         return;
      }

      ThorCombatState combat = ThorCombatState.of(state);
      ElectricAction.Kind kind = event.type() == ThorEventType.THROW ? ElectricAction.Kind.MMB_THROW : ElectricAction.Kind.MMB_RECALL;
      MoveDirection direction = MoveDirection.classify(combat.velocity.x, combat.velocity.z, player.getYRot());
      combat.actionPrimary.clear();
      combat.actionAll.clear();
      // A throw starts from the hand: the hammer was in the hand for it.
      push(player, state, combat, kind, direction, !player.onGround(), kind == ElectricAction.Kind.MMB_THROW || CombatHits.hammerInHand(player));
   }

   @Nullable
   static ServerPlayer player(UUID id) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      return server == null ? null : server.getPlayerList().getPlayer(id);
   }

   private static void fire(ServerPlayer player, ThorCombatState combat, ElectricRecognizer.Result result) {
      int attackId = combat.nextAttackId();
      ServerLevel level = player.serverLevel();
      if (!result.fires()) {
         // Needs Mjölnir in the hand: nothing happens, nothing is recalled (#148).
         level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.2, player.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
         ElectricFx.sound(level, player.position(), SoundEvents.FIRE_EXTINGUISH, 0.3F, 1.6F);
         ThorEvents.post(CombatHits.event(ThorEventType.COMBO_END, player.getUUID(), null, ThorMove.ELECTRIC_COMBO, attackId, false, 0.0F, false));
         return;
      }

      combat.actionPrimary.clear();
      combat.actionAll.clear();
      combat.strikeAttackId = attackId;
      Vec3 point = effect(player, combat, result.combo());
      List<UUID> primary = new ArrayList<>();
      List<UUID> all = new ArrayList<>();
      for (ElectricAction action : result.actions()) {
         primary.addAll(action.primaryHits());
         all.addAll(action.allHits());
      }

      primary.addAll(combat.actionPrimary);
      all.addAll(combat.actionAll);
      combat.actionPrimary.clear();
      combat.actionAll.clear();
      SkyTargetPolicy policy = ThorCombatConfig.get().skyTargetPolicy;
      UUID target = policy.select(primary, all);
      combat.skyFallback.put(attackId, policy == SkyTargetPolicy.AIM_POINT ? ElectricFx.aim(player, 32.0) : point);
      ThorEvents.post(CombatHits.event(ThorEventType.COMBO_END, player.getUUID(), target, ThorMove.ELECTRIC_COMBO, attackId,
         CombatHits.hammerInHand(player), result.combo().ordinal(), true));
   }

   /** The combo's own effect; returns its centre (the sky lightning's fallback point). */
   static Vec3 effect(ServerPlayer player, ThorCombatState combat, ElectricCombo combo) {
      ThorCombatConfig config = ThorCombatConfig.get();
      float scale = config.electricComboDamageScale;
      ServerLevel level = player.serverLevel();
      Vec3 hand = ElectricFx.hand(player);
      Vec3 look = player.getLookAngle();
      ElectricFx.sound(level, player.position(), SoundEvents.TRIDENT_THUNDER, 0.9F, 1.0F);
      switch (combo) {
         case RICOCHET -> {
            LivingEntity first = ElectricMoves.firstOnRay(player, look, 20.0);
            if (first == null) {
               Vec3 end = ElectricFx.aim(player, 20.0);
               ElectricFx.arc(level, hand, end);
               return end;
            }

            ElectricFx.arc(level, hand, first.getBoundingBox().getCenter());
            if (CombatHits.hit(player, first, 10.0F * scale, ThorMove.ELECTRIC_COMBO, combat.strikeAttackId)) {
               combat.actionPrimary.add(first.getUUID());
               combat.actionAll.add(first.getUUID());
            }

            ElectricMoves.chain(player, combat, first, 6, 10.0, 10.0F * scale, 1.0F, ThorMove.ELECTRIC_COMBO);
            return first.position();
         }
         case THUNDER_RING -> {
            Vec3 center = player.position();
            ElectricMoves.ring(player, combat, center, 9.0, 11.0F * scale, 1.6, 0.5, true);
            combat.schedule(6, player, () -> {
               if (player.isAlive()) {
                  ElectricMoves.ring(player, combat, center, 12.0, 7.0F * scale, 1.0, 0.2, false);
               }
            });
            HeroFx.shockwave(player, center, 1.0F, HeroShockwave.groundUnder(level, player), 9.0F);
            for (int i = 0; i < 4; i++) {
               double angle = Math.PI / 2.0 * i;
               ElectricFx.bolt(level, ElectricFx.ground(level, center.add(Math.cos(angle) * 9.0, 3.0, Math.sin(angle) * 9.0), 8));
            }

            return center;
         }
         case CONDUIT_SMASH -> {
            Vec3 flat = CombatHits.flat(look);
            for (LivingEntity target : CombatHits.around(player, player.position().add(flat.scale(4.0)), 5.0, 3.0)) {
               if (!CombatHits.withinArc(flat, target.position().subtract(player.position()), 35.0) || !player.hasLineOfSight(target)) {
                  continue;
               }

               ElectricFx.arc(level, hand, target.getBoundingBox().getCenter());
               if (CombatHits.hit(player, target, 16.0F * scale, ThorMove.ELECTRIC_COMBO, combat.strikeAttackId)) {
                  combat.actionPrimary.add(target.getUUID());
                  combat.actionAll.add(target.getUUID());
                  CombatHits.push(target, player.position(), 1.4, 0.3);
               }
            }

            Vec3 point = ElectricFx.ground(level, player.position().add(flat.scale(4.0)).add(0.0, 2.0, 0.0), 6);
            ElectricFx.bolt(level, point);
            HeroFx.slam(player, point, HeroShockwave.groundUnder(level, player), 4.0F);
            return point;
         }
         case STORM_LANCE -> {
            Vec3 eye = player.getEyePosition();
            Vec3 end = ElectricFx.aim(player, 24.0);
            ElectricFx.arc(level, hand, end);
            double length = eye.distanceTo(end);
            List<LivingEntity> line = new ArrayList<>();
            for (LivingEntity target : CombatHits.around(player, eye.lerp(end, 0.5), length * 0.5 + 1.0, length * 0.5 + 2.0)) {
               Vec3 c = target.getBoundingBox().getCenter();
               double t = c.subtract(eye).dot(look);
               if (t > 0.0 && t <= length && c.distanceTo(eye.add(look.scale(t))) <= 1.2 + target.getBbWidth() * 0.5 && player.hasLineOfSight(target)) {
                  line.add(target);
               }
            }

            line.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
            for (LivingEntity target : line) {
               if (CombatHits.hit(player, target, 14.0F * scale, ThorMove.ELECTRIC_COMBO, combat.strikeAttackId)) {
                  combat.actionPrimary.add(target.getUUID());
                  combat.actionAll.add(target.getUUID());
                  CombatHits.push(target, player.position(), 0.8, 0.2);
               }
            }

            ElectricFx.bolt(level, end);
            return end;
         }
         case RECALL_FAN -> {
            Vec3 last = ElectricFx.aim(player, 12.0);
            for (int i = -2; i <= 2; i++) {
               Vec3 dir = look.yRot((float)Math.toRadians(15.0 * i));
               LivingEntity target = ElectricMoves.firstOnRay(player, dir, 12.0);
               Vec3 end = target != null ? target.getBoundingBox().getCenter() : player.getEyePosition().add(dir.scale(12.0));
               ElectricFx.arc(level, hand, end);
               if (target != null && CombatHits.hit(player, target, 9.0F * scale, ThorMove.ELECTRIC_COMBO, combat.strikeAttackId)) {
                  combat.actionPrimary.add(target.getUUID());
                  combat.actionAll.add(target.getUUID());
                  CombatHits.push(target, player.position(), 0.6, 0.1);
                  last = target.position();
               }
            }

            return last;
         }
         case JUDGMENT -> {
            Vec3 center = player.position();
            List<LivingEntity> targets = CombatHits.around(player, center, 6.0, 3.0);
            int bolts = 0;
            for (LivingEntity target : targets) {
               if (!player.hasLineOfSight(target)) {
                  continue;
               }

               if (bolts++ < 6) {
                  ElectricFx.bolt(level, target.position());
               }

               if (CombatHits.hit(player, target, 24.0F * scale, ThorMove.ELECTRIC_COMBO, combat.strikeAttackId)) {
                  combat.actionPrimary.add(target.getUUID());
                  combat.actionAll.add(target.getUUID());
                  CombatHits.push(target, center, 1.2, 1.1);
               }
            }

            Vec3 front = ElectricFx.ground(level, center.add(CombatHits.flat(look).scale(3.0)).add(0.0, 2.0, 0.0), 6);
            HeroFx.slam(player, front, HeroShockwave.groundUnder(level, player), 6.0F);
            HeroFx.flash(player, front.add(0.0, 1.0, 0.0), 1.6F);
            if (ThorCombatConfig.get().meleeBreaksBlocks) {
               ElectricFx.carve(level, ElectricFx.crater(BlockPos.containing(front).below(), 3, 0.6));
            }

            ElectricFx.sound(level, center, SoundEvents.GENERIC_EXPLODE, 1.2F, 0.8F);
            return front;
         }
      }

      return player.position();
   }

   // --- automatic sky lightning (#129, #104, #137) ---

   /** B's COMBO_END listener: a successful electric combo drops a directed bolt from the sky. */
   static void onComboEnd(ThorEvent event) {
      if (!event.success() || event.move() != ThorMove.ELECTRIC_COMBO) {
         return;
      }

      ServerPlayer player = player(event.attackerId());
      ThorState state = player == null ? null : ThorState.of(player);
      if (state == null) {
         return;
      }

      ThorCombatState combat = ThorCombatState.of(state);
      Vec3 fallback = combat.skyFallback.remove(event.attackId());
      UUID targetId = event.targetId();
      int attackId = event.attackId();
      combat.schedule(ThorCombatConfig.get().skyStrikeDelayTicks, player, () -> skyStrike(player, combat, targetId, fallback, attackId));
   }

   static void skyStrike(ServerPlayer player, ThorCombatState combat, @Nullable UUID targetId, @Nullable Vec3 fallback, int attackId) {
      if (!player.isAlive()) {
         return;
      }

      ThorCombatConfig config = ThorCombatConfig.get();
      ServerLevel level = player.serverLevel();
      Entity entity = targetId == null ? null : level.getEntity(targetId);
      LivingEntity target = entity instanceof LivingEntity living && living.isAlive() && living.distanceTo(player) <= config.skyMaxDistance ? living : null;
      Vec3 at = target != null ? target.position() : fallback;
      if (at == null) {
         return;
      }

      ElectricFx.bolt(level, at);
      HeroFx.flash(player, at.add(0.0, 1.0, 0.0), 1.4F);
      ElectricFx.crackle(level, at.add(0.0, 1.0, 0.0), 30);
      // Through ordinary blocks above the target; protected / unbreakable blocks are spared (#104).
      double top = target != null ? target.getBoundingBox().maxY : at.y + 1.0;
      ElectricFx.carve(level, ElectricFx.column(BlockPos.containing(at.x, top, at.z).below(), config.skyColumnHeight));
      if (target != null && CombatHits.hit(player, target, config.skyStrikeDamage, ThorMove.ELECTRIC_COMBO, attackId)) {
         CombatHits.push(target, at.add(0.01, 0.0, 0.01), 0.8, 0.4);
      }

      for (LivingEntity other : CombatHits.around(player, at, config.skyStrikeSplashRadius, 2.0)) {
         if (other != target && CombatHits.hit(player, other, config.skyStrikeDamage * 0.5F, ThorMove.ELECTRIC_COMBO, attackId)) {
            CombatHits.push(other, at, 0.8, 0.3);
         }
      }
   }
}
