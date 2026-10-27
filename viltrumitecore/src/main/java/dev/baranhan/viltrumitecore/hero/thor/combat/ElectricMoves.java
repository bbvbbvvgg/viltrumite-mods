package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.HeroShockwave;
import dev.baranhan.viltrumitecore.hero.combat.InterruptReason;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.thor.ThorActions;
import dev.baranhan.viltrumitecore.hero.thor.ThorHands;
import dev.baranhan.viltrumitecore.hero.thor.ThorMode;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveContext;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveHandler;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Session B electric mode (#40, #49, #153): R toggles it; LMB is a directed
 * discharge from the hand that chains to neighbours (#73, #75); a tapped RMB
 * is a circular wave (#74); a held RMB grows into the storm around Thor
 * (#77, #114). Works without Mjölnir (#16, #116); a distant hammer never
 * conducts (#128). Damage and push only, no stun (#84); no mana, only the
 * storm cooldown (#82); the sky changes only on clients, never the weather (#137).
 */
public final class ElectricMoves {
   public static final String ELECTRIC_ON = "hud.viltrumitecore.thor.electric_on";
   public static final String ELECTRIC_OFF = "hud.viltrumitecore.thor.electric_off";

   /** ELECTRIC_MODE: the toggle itself (a short technical move). */
   public static final ThorMoveHandler MODE = new ThorMoveHandler() {
      @Override
      public void onStart(ThorMoveContext context) {
         ThorState state = context.state();
         if (state.timeline.queued() == context.move()) {
            return;
         }

         state.mode = state.mode == ThorMode.ELECTRIC ? ThorMode.NORMAL : ThorMode.ELECTRIC;
         ThorCombatState combat = ThorCombatState.of(state);
         combat.recognizer.clear();
         combat.rmbHeld = false;
         ServerPlayer player = context.player();
         if (player == null) {
            return;
         }

         ServerLevel level = player.serverLevel();
         boolean on = state.mode == ThorMode.ELECTRIC;
         ElectricFx.sound(level, player.position(), on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, 0.8F, on ? 1.6F : 1.2F);
         if (on) {
            ElectricFx.crackle(level, player.position().add(0.0, 1.0, 0.0), 30);
            ElectricFx.arc(level, ElectricFx.hand(player), ElectricFx.hand(player).add(0.0, 1.6, 0.0));
         }

         player.displayClientMessage(Component.translatable(on ? ELECTRIC_ON : ELECTRIC_OFF), true);
      }
   };

   /** ELECTRIC_COMBO: LMB discharge (each chained step one more). */
   public static final ThorMoveHandler DISCHARGE = new ThorMoveHandler() {
      @Override
      public void onStart(ThorMoveContext context) {
         if (context.player() != null && context.state().timeline.queued() != context.move()) {
            begin(context.player(), context.state());
         }
      }

      @Override
      public void onPhase(ThorMoveContext context, StagedTimeline.Event event) {
         ServerPlayer player = context.player();
         if (player == null) {
            return;
         }

         switch (event) {
            case CHAIN -> begin(player, context.state());
            case ENTER_MAIN -> {
               ThorCombatState combat = ThorCombatState.of(context.state());
               discharge(player, combat);
               ElectricCombos.push(player, context.state(), combat, ElectricAction.Kind.LMB);
            }
            case END -> context.state().combatPose = 0;
            default -> {
            }
         }
      }

      @Override
      public void onStop(ThorMoveContext context) {
         context.state().combatPose = 0;
      }

      private void begin(ServerPlayer player, ThorState state) {
         ThorCombatState combat = ThorCombatState.of(state);
         combat.electricStep++;
         // Poses 1 / 2: right / left hand in turn.
         state.combatPose = 1 + (combat.electricStep & 1);
         combat.strikeAttackId = combat.nextAttackId();
         MeleeMoves.captureStep(player, combat);
         ElectricFx.sound(player.serverLevel(), player.position(), SoundEvents.FIRECHARGE_USE, 0.3F, 1.8F);
      }
   };

   /** ELECTRIC_WAVE: RMB; still held when the intro ends → the storm. */
   public static final ThorMoveHandler WAVE = new ThorMoveHandler() {
      @Override
      public void onStart(ThorMoveContext context) {
         ThorCombatState combat = ThorCombatState.of(context.state());
         combat.rmbHeld = true;
         if (context.player() != null && context.state().timeline.queued() != context.move()) {
            begin(context.player(), context.state(), combat);
         }
      }

      @Override
      public void onPhase(ThorMoveContext context, StagedTimeline.Event event) {
         ServerPlayer player = context.player();
         if (player == null) {
            return;
         }

         ThorState state = context.state();
         ThorCombatState combat = ThorCombatState.of(state);
         switch (event) {
            case CHAIN -> begin(player, state, combat);
            case ENTER_MAIN -> {
               if (combat.rmbHeld && combat.ready(ThorCombatState.Cooldown.STORM) && startStorm(player, state, combat)) {
                  return;
               }

               wave(player, combat);
               ElectricCombos.push(player, state, combat, ElectricAction.Kind.RMB);
            }
            case END -> state.combatPose = 0;
            default -> {
            }
         }
      }

      @Override
      public void onRelease(ThorMoveContext context, int heldTicks) {
         ThorCombatState combat = ThorCombatState.of(context.state());
         combat.rmbHeld = false;
         if (context.player() != null && context.state().timeline.is(ThorMove.ELECTRIC_STORM)) {
            releaseStorm(context.player(), context.state(), combat);
         }
      }

      @Override
      public void onStop(ThorMoveContext context) {
         if (!context.state().timeline.is(ThorMove.ELECTRIC_STORM)) {
            context.state().combatPose = 0;
         }
      }

      private void begin(ServerPlayer player, ThorState state, ThorCombatState combat) {
         state.combatPose = 1;
         combat.strikeAttackId = combat.nextAttackId();
         MeleeMoves.captureStep(player, combat);
         ElectricFx.crackle(player.serverLevel(), player.position().add(0.0, 1.0, 0.0), 12);
      }
   };

   /** ELECTRIC_STORM: grows while RMB is held, bursts on release or at full size. */
   public static final ThorMoveHandler STORM = new ThorMoveHandler() {
      @Override
      public void onPhase(ThorMoveContext context, StagedTimeline.Event event) {
         if (context.player() != null && event == StagedTimeline.Event.ENTER_FINISH) {
            burst(context.player(), ThorCombatState.of(context.state()));
         } else if (event == StagedTimeline.Event.END) {
            context.state().combatPose = 0;
         }
      }

      @Override
      public void onRelease(ThorMoveContext context, int heldTicks) {
         if (context.player() != null) {
            releaseStorm(context.player(), context.state(), ThorCombatState.of(context.state()));
         }
      }

      @Override
      public void onStop(ThorMoveContext context) {
         ThorCombatState combat = ThorCombatState.of(context.state());
         if (combat.stormTicks >= 0) {
            combat.startCooldown(ThorCombatState.Cooldown.STORM, ThorCombatConfig.get().stormCooldownTicks);
            combat.stormTicks = -1;
         }

         context.state().combatPose = 0;
      }
   };

   private ElectricMoves() {
   }

   // --- LMB ---

   /** Directed discharge from the hand along the view, then chain jumps to neighbours. */
   static void discharge(ServerPlayer player, ThorCombatState combat) {
      ThorCombatConfig config = ThorCombatConfig.get();
      ServerLevel level = player.serverLevel();
      combat.actionPrimary.clear();
      combat.actionAll.clear();
      Vec3 from = ElectricFx.hand(player);
      LivingEntity first = CombatHits.alongRay(player, player.getEyePosition(), player.getLookAngle(), config.dischargeRange, 0.6);
      ElectricFx.sound(level, from, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7F, 1.5F + level.getRandom().nextFloat() * 0.3F);
      if (first == null) {
         Vec3 end = ElectricFx.aim(player, config.dischargeRange);
         ElectricFx.arc(level, from, end);
         ElectricFx.crackle(level, end, 6);
         return;
      }

      ElectricFx.arc(level, from, first.getBoundingBox().getCenter());
      if (CombatHits.hit(player, first, config.dischargeDamage, ThorMove.ELECTRIC_COMBO, combat.strikeAttackId)) {
         combat.actionPrimary.add(first.getUUID());
         combat.actionAll.add(first.getUUID());
         CombatHits.push(first, player.position(), 0.4, 0.0);
      }

      chain(player, combat, first, config.chainJumps, config.chainRadius, config.dischargeDamage * config.chainFalloff, config.chainFalloff,
         ThorMove.ELECTRIC_COMBO);
   }

   /** Jumps from {@code from} to the nearest not-yet-hit enemy, {@code jumps} times. */
   static void chain(ServerPlayer player, ThorCombatState combat, LivingEntity from, int jumps, double radius, float damage, float falloff, ThorMove move) {
      ServerLevel level = player.serverLevel();
      Set<UUID> hit = new HashSet<>(combat.actionAll);
      hit.add(from.getUUID());
      LivingEntity current = from;
      float amount = damage;
      for (int i = 0; i < jumps; i++) {
         LivingEntity next = null;
         double best = Double.MAX_VALUE;
         for (LivingEntity candidate : CombatHits.around(player, current.position(), radius, radius)) {
            double d = candidate.distanceToSqr(current);
            if (!hit.contains(candidate.getUUID()) && d < best && current.hasLineOfSight(candidate)) {
               next = candidate;
               best = d;
            }
         }

         if (next == null) {
            return;
         }

         ElectricFx.arc(level, current.getBoundingBox().getCenter(), next.getBoundingBox().getCenter());
         hit.add(next.getUUID());
         if (CombatHits.hit(player, next, amount, move, combat.strikeAttackId)) {
            combat.actionAll.add(next.getUUID());
            CombatHits.push(next, current.position(), 0.3, 0.0);
         }

         current = next;
         amount *= falloff;
      }
   }

   // --- RMB ---

   /** Circular wave around Thor. */
   static void wave(ServerPlayer player, ThorCombatState combat) {
      ThorCombatConfig config = ThorCombatConfig.get();
      ServerLevel level = player.serverLevel();
      combat.actionPrimary.clear();
      combat.actionAll.clear();
      Vec3 center = player.position();
      ring(player, combat, center, config.waveRadius, config.waveDamage, config.waveKnockback, 0.3, true);
      HeroFx.shockwave(player, center, 0.5F, HeroShockwave.groundUnder(level, player), (float)config.waveRadius);
      ElectricFx.sound(level, center, SoundEvents.TRIDENT_THUNDER, 0.5F, 1.6F);
   }

   /** Damage + push in a ring, with an expanding spark ring over a few ticks. */
   static void ring(ServerPlayer player, ThorCombatState combat, Vec3 center, double radius, float damage, double knockback, double lift, boolean primary) {
      ServerLevel level = player.serverLevel();
      for (LivingEntity target : CombatHits.around(player, center, radius, 3.0)) {
         if (!player.hasLineOfSight(target)) {
            continue;
         }

         ElectricFx.arc(level, center.add(0.0, 1.0, 0.0), target.getBoundingBox().getCenter());
         if (CombatHits.hit(player, target, damage, ThorMove.ELECTRIC_WAVE, combat.strikeAttackId)) {
            if (primary) {
               combat.actionPrimary.add(target.getUUID());
            }

            combat.actionAll.add(target.getUUID());
            CombatHits.push(target, center, knockback, lift);
         }
      }

      int steps = Math.max(1, (int)Math.ceil(radius));
      for (int i = 1; i <= steps; i++) {
         double r = radius * i / steps;
         combat.schedule(i - 1, () -> ElectricFx.ring(level, center, r));
      }
   }

   // --- held RMB: storm ---

   static boolean startStorm(ServerPlayer player, ThorState state, ThorCombatState combat) {
      if (ThorActions.start(state, ThorMove.ELECTRIC_STORM, ThorHands.free(player)) != ThorActions.Refusal.NONE) {
         return false;
      }

      ThorCombatConfig config = ThorCombatConfig.get();
      combat.stormTicks = 0;
      combat.stormRadius = config.stormStartRadius;
      combat.stormStage = 1;
      combat.strikeAttackId = combat.nextAttackId();
      state.combatPose = 1;
      ServerLevel level = player.serverLevel();
      ElectricFx.sound(level, player.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.4F);
      ElectricFx.sound(level, player.position(), SoundEvents.BEACON_POWER_SELECT, 0.8F, 0.7F);
      combat.actionPrimary.clear();
      combat.actionAll.clear();
      ElectricCombos.push(player, state, combat, ElectricAction.Kind.HOLD_RMB);
      return true;
   }

   /** Server tick of a running storm (MAIN phase): grow, pulse, bolts. */
   static void tickStorm(ServerPlayer player, ThorState state, ThorCombatState combat) {
      if (!state.timeline.is(ThorMove.ELECTRIC_STORM) || state.timeline.phase() != MovePhase.MAIN || combat.stormTicks < 0) {
         return;
      }

      ThorCombatConfig config = ThorCombatConfig.get();
      ServerLevel level = player.serverLevel();
      RandomSource random = level.getRandom();
      combat.stormTicks++;
      combat.stormRadius = StormCurve.radius(combat.stormTicks, ThorCombatTimings.STORM_MAX_TICKS, config.stormStartRadius, config.stormMaxRadius);
      Vec3 center = player.position();
      int stage = StormCurve.stage(combat.stormTicks, ThorCombatTimings.STORM_MAX_TICKS, config.stormStages);
      if (stage > combat.stormStage) {
         // A new charge stage: a clear cue (sound, ring, flash), the burst grows (#81).
         combat.stormStage = stage;
         ElectricFx.sound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5F + 0.2F * stage, 1.6F - 0.2F * stage);
         ElectricFx.ring(level, center, combat.stormRadius);
         ElectricFx.crackle(level, center.add(0.0, 1.0, 0.0), 20 * stage);
         HeroFx.flash(player, center.add(0.0, 1.0, 0.0), 0.6F + 0.3F * stage);
      }

      if (combat.stormTicks % 2 == 0) {
         ElectricFx.ring(level, center, combat.stormRadius);
         double angle = random.nextDouble() * Math.PI * 2.0;
         double r = random.nextDouble() * combat.stormRadius;
         ElectricFx.arc(level, center.add(0.0, 1.2, 0.0), center.add(Math.cos(angle) * r, 0.2 + random.nextDouble() * 2.0, Math.sin(angle) * r));
      }

      if (config.stormPulseTicks > 0 && combat.stormTicks % config.stormPulseTicks == 0) {
         for (LivingEntity target : CombatHits.around(player, center, combat.stormRadius, 4.0)) {
            if (!player.hasLineOfSight(target)) {
               continue;
            }

            ElectricFx.arc(level, center.add(0.0, 1.2, 0.0), target.getBoundingBox().getCenter());
            if (CombatHits.hit(player, target, config.stormPulseDamage, ThorMove.ELECTRIC_STORM, combat.strikeAttackId)) {
               CombatHits.push(target, center, 0.2, 0.0);
            }
         }
      }

      if (config.stormBoltTicks > 0 && combat.stormTicks % config.stormBoltTicks == 0) {
         List<LivingEntity> near = CombatHits.around(player, center, combat.stormRadius, 4.0);
         Vec3 at;
         if (!near.isEmpty()) {
            at = near.get(random.nextInt(near.size())).position();
         } else {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double r = config.stormStartRadius + random.nextDouble() * Math.max(0.0, combat.stormRadius - config.stormStartRadius);
            at = ElectricFx.ground(level, center.add(Math.cos(angle) * r, 3.0, Math.sin(angle) * r), 8);
         }

         ElectricFx.bolt(level, at);
      }

      if (combat.stormTicks % 20 == 0) {
         ElectricFx.sound(level, center, SoundEvents.BEACON_AMBIENT, 0.8F, 1.6F);
      }
   }

   /** RMB released: the storm bursts and ends. */
   static void releaseStorm(ServerPlayer player, ThorState state, ThorCombatState combat) {
      if (!state.timeline.is(ThorMove.ELECTRIC_STORM) || combat.stormTicks < 0) {
         return;
      }

      burst(player, combat);
      state.timeline.interrupt(InterruptReason.INPUT);
      state.combatPose = 0;
   }

   /** The closing burst at the storm's current size. */
   static void burst(ServerPlayer player, ThorCombatState combat) {
      if (combat.stormTicks < 0) {
         return;
      }

      ThorCombatConfig config = ThorCombatConfig.get();
      ServerLevel level = player.serverLevel();
      Vec3 center = player.position();
      double radius = combat.stormRadius;
      int stages = Math.max(1, config.stormStages);
      float damage = StormCurve.burstAtStage(config.stormBurstDamage, combat.stormStage, stages);
      for (LivingEntity target : CombatHits.around(player, center, radius, 4.0)) {
         if (!player.hasLineOfSight(target)) {
            continue;
         }

         ElectricFx.arc(level, center.add(0.0, 1.2, 0.0), target.getBoundingBox().getCenter());
         if (CombatHits.hit(player, target, damage, ThorMove.ELECTRIC_STORM, combat.strikeAttackId)) {
            CombatHits.push(target, center, 1.5, 0.4);
         }
      }

      RandomSource random = level.getRandom();
      for (int i = 0; i < 3; i++) {
         double angle = random.nextDouble() * Math.PI * 2.0;
         ElectricFx.bolt(level, ElectricFx.ground(level, center.add(Math.cos(angle) * radius * 0.6, 3.0, Math.sin(angle) * radius * 0.6), 8));
      }

      HeroFx.shockwave(player, center, 1.0F, HeroShockwave.groundUnder(level, player), (float)radius);
      HeroFx.flash(player, center.add(0.0, 1.0, 0.0), 1.2F);
      ElectricFx.sound(level, center, SoundEvents.GENERIC_EXPLODE, 1.0F, 1.2F);
      ElectricFx.sound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 1.0F);
      if (combat.stormStage >= stages && config.stormFullCraterRadius > 0 && config.meleeBreaksBlocks) {
         // Only the fully charged storm scars the ground (#18, #30); ordinary blocks only.
         ElectricFx.carve(level, ElectricFx.crater(BlockPos.containing(center).below(), config.stormFullCraterRadius, 0.5));
      }

      combat.startCooldown(ThorCombatState.Cooldown.STORM, config.stormCooldownTicks);
      combat.stormTicks = -1;
      combat.stormStage = 1;
   }

   @Nullable
   static LivingEntity firstOnRay(ServerPlayer player, Vec3 direction, double range) {
      return CombatHits.alongRay(player, player.getEyePosition(), direction, range, 0.6);
   }
}
