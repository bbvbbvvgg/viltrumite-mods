package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.control.WorldPause;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusNet;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * «Королевство одного» (spec §5.8), the ultimate: a 7.5 s judgement of one target within 32 blocks.
 * ACCUSE (0–23) the target is frozen in place and accused; SCOOP (24–49) the dust and air around it
 * are scooped and stopped into a ring of grains; SENTENCE (50–105) the five grain waves strike the
 * held target; FLICK (106–125) the flick of a finger at tick 112 launches it and craters the ground;
 * RESOLVE (126–149) the calm return. The scene ends safely on any loss (target dead, gone, in
 * another level or beyond 48 blocks; caster death, logout, dimension or hero change) and pauses
 * while DIO's stopped time holds Regulus.
 */
public final class KingdomOfOne {
   static final UUID LOCK_ID = UUID.fromString("4e5a1c0b-7d3e-4a7c-9b1f-2c6d8e0f1a03");
   static final int FREEZE_SAFETY = 400;
   static final int ABORT_EARLY_COOLDOWN = 600;

   private KingdomOfOne() {
   }

   /** One running judgement (transient). */
   static final class Scene {
      final UUID targetId;
      final int targetEntityId;
      final UUID effectId = UUID.randomUUID();
      final Vec3 anchor;
      boolean frozen;
      boolean held;
      int waves;

      Scene(LivingEntity target, Vec3 anchor) {
         this.targetId = target.getUUID();
         this.targetEntityId = target.getId();
         this.anchor = anchor;
      }
   }

   static void press(ServerPlayer player, RegulusState state) {
      if (state.ultCooldown > 0 || state.ult != null || state.busy()) {
         RegulusHero.refuse(player, state);
         int seconds = refusalSeconds(state);
         if (seconds > 0) {
            // PR #87 fix 15 carried over: a silent refusal of the recharging ult read as "stuck" in playtests.
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.viltrumitecore.regulus.ult_refused.cooldown", seconds), true);
         }
         return;
      }

      LivingEntity target = pick(player, state);
      ServerLevel level = (ServerLevel) player.level();
      if (target == null) {
         RegulusHero.refuse(player, state);
         return;
      }

      Scene scene = new Scene(target, target.position());
      ControlManager control = ControlManager.get(level);
      if (!control.tryAcquire(target, player.getUUID(), scene.effectId, ControlKind.FREEZE, level.getGameTime() + FREEZE_SAFETY)) {
         RegulusHero.refuse(player, state);
         return;
      }

      scene.frozen = true;

      state.ult = scene;
      state.startMove(RegulusMove.ULT);
      state.ultCooldown = RegulusRules.ULT_COOLDOWN;
      lock(player, true);
      Vec3 dir = target.position().subtract(player.position());
      RegulusNet.fx(level, RegulusFxS2CPacket.ULT_START, player, target.getId(), target.position(), dir, 1.0F, 0);
      RegulusSounds.play(level, player.position().add(0.0, 1.5, 0.0), RegulusSounds.ULT_ACCUSE, 1.2F, 1.0F);
      HeroSpeech.ability(player, RegulusAbilities.KINGDOM_OF_ONE);
   }

   /**
    * Pure: whole seconds of recharge to name when the ult is refused, or 0 when the refusal is not about the
    * cooldown (a scene still running, or another move holding the body). The cooldown only runs while no scene
    * holds it, so an aborted or finished judgement always recharges and the ult opens again at 0.
    */
   static int refusalSeconds(RegulusState state) {
      if (state.ult != null || state.ultCooldown <= 0) {
         return 0;
      }

      return (state.ultCooldown + 19) / 20;
   }

   /** Runs on the scene ticks (state.moveTick of the ULT move; it does not advance while Regulus is anchored). */
   static void tickMove(ServerPlayer player, RegulusState state) {
      Scene scene = state.ult;
      if (scene == null || state.move != RegulusMove.ULT) {
         return;
      }

      ServerLevel level = (ServerLevel) player.level();
      int t = state.moveTick;
      LivingEntity target = level.getEntity(scene.targetId) instanceof LivingEntity living ? living : null;
      if (t < RegulusRules.ULT_FLICK_CONTACT && (target == null || !target.isAlive() || target.level() != level
         || target.distanceToSqr(player) > RegulusRules.ULT_LEASH * RegulusRules.ULT_LEASH)) {
         abort(player, state, t < RegulusRules.ULT_SENTENCE);
         return;
      }

      if (t == RegulusRules.ULT_SCOOP) {
         RegulusSounds.play(level, scene.anchor.add(0.0, 1.0, 0.0), RegulusSounds.ULT_SCOOP, 1.2F, 1.0F);
         RegulusNet.fx(level, RegulusFxS2CPacket.ULT_SCOOP, player, scene.targetEntityId, scene.anchor, Vec3.ZERO, (float) RegulusRules.ULT_DOME_RADIUS,
            state.seed);
      }

      if (t == RegulusRules.ULT_SENTENCE) {
         if (scene.frozen) {
            ControlManager.get(level).release(scene.targetId, scene.effectId, ReleaseReason.NORMAL_END);
            scene.frozen = false;
         }

         scene.held = true;
         HeroSpeech.trigger(player, HeroLineTrigger.event(RegulusLines.EVENT_ULT_SENTENCE));
      }

      if (scene.held && target != null) {
         hold(player, target, scene.anchor);
      }

      if (RegulusRules.ultWaveTick(t) && target != null) {
         int wave = scene.waves++;
         target.invulnerableTime = 0;
         RegulusCombat.hit(player, target, RegulusRules.ULT_WAVE_DAMAGE);
         Vec3 at = target.getBoundingBox().getCenter();
         RegulusSounds.play(level, at, RegulusSounds.ULT_WAVE, 1.0F, 0.9F + 0.06F * wave);
         RegulusNet.fx(level, RegulusFxS2CPacket.ULT_WAVE, player, scene.targetEntityId, at, Vec3.ZERO, 1.0F, wave);
      }

      if (t == RegulusRules.ULT_FLICK) {
         HeroSpeech.trigger(player, HeroLineTrigger.event(RegulusLines.EVENT_ULT_FLICK));
      }

      if (t == RegulusRules.ULT_FLICK_CONTACT && target != null) {
         scene.held = false;
         Vec3 at = target.getBoundingBox().getCenter();
         Vec3 away = target.position().subtract(player.position());
         target.invulnerableTime = 0;
         RegulusCombat.hit(player, target, RegulusRules.ULT_FLICK_DAMAGE);
         RegulusCombat.push(target, away, 2.6, 0.7);
         crater(player, level, BlockPos.containing(scene.anchor));
         RegulusSounds.play(level, at, RegulusSounds.ULT_FLICK, 1.4F, 1.0F);
         RegulusNet.fx(level, RegulusFxS2CPacket.ULT_FLICK, player, scene.targetEntityId, at, away.normalize(), 1.0F, state.seed);
      }

      if (t == RegulusRules.ULT_RESOLVE) {
         RegulusSounds.play(level, player.position().add(0.0, 1.4, 0.0), RegulusSounds.ULT_RESOLVE, 1.0F, 1.0F);
      }
   }

   /** Move end at tick 150: the scene resolves. */
   static void onEnd(ServerPlayer player, RegulusState state, RegulusMove move) {
      if (move == RegulusMove.ULT && state.ult != null) {
         finish(player, state);
      }
   }

   /** Ends the scene early: release, unlock, tell the clients. Early (before the sentence) = shorter cooldown. */
   static void abort(ServerPlayer player, RegulusState state, boolean early) {
      if (state.ult == null) {
         return;
      }

      if (early) {
         state.ultCooldown = Math.min(state.ultCooldown, ABORT_EARLY_COOLDOWN);
      }

      if (state.move == RegulusMove.ULT) {
         state.clearMove();
      }

      finish(player, state);
   }

   /** Idempotent tear-down shared by the normal end, aborts and every cleanup path. */
   static void finish(ServerPlayer player, RegulusState state) {
      Scene scene = state.ult;
      state.ult = null;
      lock(player, false);
      if (scene == null) {
         return;
      }

      if (scene.frozen && player.level() instanceof ServerLevel level) {
         ControlManager.get(level).release(scene.targetId, scene.effectId, ReleaseReason.INTERRUPTED);
      }

      scene.frozen = false;
      scene.held = false;
      if (player.level() instanceof ServerLevel level) {
         RegulusNet.fx(level, RegulusFxS2CPacket.ULT_END, player, scene.targetEntityId, player.position());
      }
   }

   /** Releases a FREEZE this scene holds in any level (the caster may already stand elsewhere). */
   static void releaseEverywhere(ServerPlayer player, RegulusState state) {
      Scene scene = state.ult;
      if (scene != null && scene.frozen && player.getServer() != null) {
         for (ServerLevel level : player.getServer().getAllLevels()) {
            ControlManager.get(level).release(scene.targetId, scene.effectId, ReleaseReason.INTERRUPTED);
         }

         scene.frozen = false;
      }
   }

   private static void hold(ServerPlayer player, LivingEntity target, Vec3 anchor) {
      // DIO's paused world (or an isolated body) is never moved by the scene: it waits with it
      if (HeroDamage.isAnchored(target) || WorldPause.blocksMovement(player, target)) {
         return;
      }

      target.setDeltaMovement(Vec3.ZERO);
      target.fallDistance = 0.0F;
      if (target.position().distanceToSqr(anchor) > 0.0025) {
         if (target instanceof ServerPlayer victim) {
            victim.connection.teleport(anchor.x, anchor.y, anchor.z, victim.getYRot(), victim.getXRot());
         } else {
            target.teleportTo(anchor.x, anchor.y, anchor.z);
         }
      }

      target.hurtMarked = true;
   }

   /** Flick crater: a shallow bowl of radius 3 under the target, soft blocks only (world rules apply). */
   private static void crater(ServerPlayer player, ServerLevel level, BlockPos center) {
      int r = (int) Math.ceil(RegulusRules.ULT_CRATER_RADIUS);
      int budget = 40;
      for (int dy = -1; dy >= -2 && budget > 0; dy--) {
         for (int dx = -r; dx <= r && budget > 0; dx++) {
            for (int dz = -r; dz <= r && budget > 0; dz++) {
               double depth = dx * dx + dz * dz + (dy + 1) * (dy + 1) * 3.0;
               if (depth > RegulusRules.ULT_CRATER_RADIUS * RegulusRules.ULT_CRATER_RADIUS) {
                  continue;
               }

               if (RegulusCombat.breakBlock(player, level, center.offset(dx, dy, dz), RegulusRules.SEVER_BLOCK_HARDNESS)) {
                  budget--;
               }
            }
         }
      }
   }

   static void lock(ServerPlayer player, boolean on) {
      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed == null) {
         return;
      }

      boolean has = speed.getModifier(LOCK_ID) != null;
      if (on && !has) {
         speed.addTransientModifier(new AttributeModifier(LOCK_ID, "Regulus Kingdom of One", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
      } else if (!on && has) {
         speed.removeModifier(LOCK_ID);
      }
   }

   /** The looked-at hittable living within 32 blocks and in sight. */
   @Nullable
   static LivingEntity pick(ServerPlayer player, RegulusState state) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      Vec3 end = eye.add(look.scale(RegulusRules.ULT_RANGE));
      AABB sweep = player.getBoundingBox().expandTowards(look.scale(RegulusRules.ULT_RANGE)).inflate(1.5);
      LivingEntity best = null;
      double bestDist = Double.MAX_VALUE;
      for (Entity entity : player.level().getEntities(player, sweep)) {
         if (!RegulusCombat.hittable(player, entity, state)) {
            continue;
         }

         var clip = entity.getBoundingBox().inflate(0.6).clip(eye, end);
         if (clip.isEmpty()) {
            continue;
         }

         double d = eye.distanceToSqr(clip.get());
         if (d < bestDist && RegulusCombat.sees(player, eye, entity)) {
            best = (LivingEntity) entity;
            bestDist = d;
         }
      }

      return best;
   }

   /** Target entity id for the snapshot (resource2), -1 when no scene. */
   static int targetEntityId(RegulusState state) {
      return state.ult == null ? -1 : state.ult.targetEntityId;
   }
}
