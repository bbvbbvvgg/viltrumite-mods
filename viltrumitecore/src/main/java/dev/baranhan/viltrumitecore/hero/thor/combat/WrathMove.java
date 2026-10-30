package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.thor.ThorSounds;
import dev.baranhan.viltrumitecore.hero.thor.ThorCue;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.thor.ThorFeedback;
import dev.baranhan.viltrumitecore.hero.thor.ThorFx;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveContext;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveHandler;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Audit v3 (item 8), the staging: the ultimate is a short, protected build-up that already fights and a
 * climax in three beats instead of a long, naked wind-up and one hit. Build-up ({@code 2 s}): Mjölnir to
 * the sky, the storm gathers (ThorStormVfx), every {@link #CALL_EVERY} ticks a called bolt strikes an enemy
 * around the aim (light damage, pinned by a short slow) and the storm shrouds Thor (incoming damage
 * {@link #SHROUD_FACTOR}). Climax: the column onto the crosshair (the old strike, full damage), then
 * {@link #AFTERSHOCKS} aftershock bolts onto the toughest survivors of the blast; the caster's camera
 * narrows into the build-up and kicks out on the strike (client WrathCamera). The lines are ThorLines.
 *
 * <p>Before the audit: WRATH (internal id; name not approved): the ultimate on its own key
 * (Z is a temporary foundation default), aimed with the ordinary crosshair
 * (#136), concentrating the power in one point (#102), unlike the storm
 * around Thor. Key, duration, scale, damage, cooldown, use without Mjölnir
 * and in flight / grabs were OPEN. v3 settles the timing (40/12/28) and the
 * staging above; damage, radius and cooldown stay config numbers (ThorCombatConfig,
 * not author-approved) and the hammer rule is a config switch, off by default.
 */
public final class WrathMove {
   public static final String RECHARGING = "hud.viltrumitecore.thor.recharging";
   public static final String NEEDS_HAMMER = "hud.viltrumitecore.thor.needs_hammer";

   public static final ThorMoveHandler HANDLER = new ThorMoveHandler() {
      @Override
      public boolean admits(ThorMoveContext context) {
         ServerPlayer player = context.player();
         if (player == null) {
            return true;
         }

         ThorCombatState combat = ThorCombatState.of(context.state());
         Component name = Component.translatable(context.move().translationKey());
         if (!combat.ready(player, ThorCombatState.Cooldown.WRATH)) {
            int seconds = (combat.cooldownLeft(player, ThorCombatState.Cooldown.WRATH) + 19) / 20;
            ThorFeedback.refuse(player, Component.translatable(RECHARGING, name, seconds));
            return false;
         } else if (ThorCombatConfig.get().wrathRequiresHammer && !CombatHits.hammerInHand(player)) {
            ThorFeedback.refuse(player, Component.translatable(NEEDS_HAMMER, name));
            return false;
         }

         return true;
      }

      @Override
      public void onStart(ThorMoveContext context) {
         ServerPlayer player = context.player();
         ThorCombatState combat = ThorCombatState.of(context.state());
         combat.startCooldown(player, ThorCombatState.Cooldown.WRATH, ThorCombatConfig.get().wrathCooldownTicks);
         combat.strikeAttackId = combat.nextAttackId();
         combat.wrathAim = null;
         context.state().setCombatPose(1);
         if (player != null) {
            ServerLevel level = player.serverLevel();
            ThorSounds.play(player, ThorCue.WRATH_WINDUP);
            dev.baranhan.viltrumitecore.hero.speech.HeroSpeech.ability(player, dev.baranhan.viltrumitecore.hero.thor.ThorAbilities.WRATH);
         }
      }

      @Override
      public void onPhase(ThorMoveContext context, StagedTimeline.Event event) {
         ServerPlayer player = context.player();
         if (player == null) {
            return;
         }

         if (event == StagedTimeline.Event.ENTER_MAIN) {
            strike(player, ThorCombatState.of(context.state()));
         } else if (event == StagedTimeline.Event.ENTER_FINISH) {
            ThorSounds.play(player, ThorCue.WRATH_TAIL);
         } else if (event == StagedTimeline.Event.END) {
            context.state().setCombatPose(0);
         }
      }

      @Override
      public void onStop(ThorMoveContext context) {
         context.state().setCombatPose(0);
         if (context.player() != null) {
            // Cancelled in the windup: the riser stops with the move (after the contact it is already cut).
            ThorSounds.stop(context.player().serverLevel(), context.player().position(), ThorCue.WRATH_WINDUP);
         }
      }
   };

   /** Build-up: a called bolt every this many ticks (not in the last {@link #CALL_QUIET} before the strike). */
   public static final int CALL_EVERY = 8;
   public static final int CALL_QUIET = 4;
   public static final float CALL_DAMAGE = 3.0F;
   /** Called bolts look this far round the aim (times the blast radius). */
   public static final double CALL_REACH = 1.6;
   public static final int CALL_PIN = 15;
   /** Ticks after the contact the aftershock bolts fall, and their share of the full damage. */
   public static final int[] AFTERSHOCKS = {3, 6, 9};
   public static final float AFTERSHOCK_SHARE = 0.25F;
   /** Incoming damage while the storm shrouds Thor (build-up and contact). */
   public static final float SHROUD_FACTOR = 0.5F;

   /** Radius the thor:wrath_strike recipe is drawn for at scale 1 (scaled to the configured blast). */
   static final double WRATH_FX_RADIUS = 7.0;
   /** Contact hit-stop of the strike, ticks. */
   static final int WRATH_HIT_STOP = 5;

   private WrathMove() {
   }

   /**
    * Intro (server tick). The build-up visuals (storm cloud, sky arcs into
    * the hammer, rumble, ground crawlers) are drawn by each client from the
    * synced move state (FX id {@code thor:wrath_storm}, ThorStormVfx), so the
    * server sends nothing here.
    */
   static void tick(ServerPlayer player, ThorState state, ThorCombatState combat) {
      if (!state.timeline.is(ThorMove.WRATH)) {
         return;
      }

      ThorAttacks.Phases phases = ThorAttacks.envelope(ThorMove.WRATH).phases();
      int elapsed = state.timeline.elapsed();
      if (state.timeline.phase() == MovePhase.INTRO && calls(elapsed, phases.windup())) {
         call(player, combat);
      } else if (state.timeline.phase() == MovePhase.MAIN && combat.wrathAim != null) {
         int since = elapsed - phases.windup();
         for (int i = 0; i < AFTERSHOCKS.length; i++) {
            if (since == AFTERSHOCKS[i]) {
               aftershock(player, combat, i);
            }
         }
      }
   }

   /** Pure: does a called bolt fall on this build-up tick. */
   public static boolean calls(int elapsed, int windup) {
      return elapsed > 0 && elapsed % CALL_EVERY == 0 && elapsed < windup - CALL_QUIET;
   }

   /** Pure: the share of a hit that reaches a shrouded Thor ({@code 1} outside the build-up and the contact). */
   public static float shroud(@javax.annotation.Nullable ThorMove running, @javax.annotation.Nullable MovePhase phase) {
      return running == ThorMove.WRATH && (phase == MovePhase.INTRO || phase == MovePhase.MAIN) ? SHROUD_FACTOR : 1.0F;
   }

   /** A called bolt: an enemy round the aim (the nearest to it not struck by the last call), else the ground there. */
   private static void call(ServerPlayer player, ThorCombatState combat) {
      ThorCombatConfig config = ThorCombatConfig.get();
      ServerLevel level = player.serverLevel();
      Vec3 aim = ElectricFx.ground(level, ElectricFx.aim(player, config.wrathRange), 16);
      LivingEntity pick = null;
      for (LivingEntity target : CombatHits.around(player, aim, config.wrathRadius * CALL_REACH, 6.0)) {
         if (visibleFrom(level, target.position().add(0.0, 8.0, 0.0), target)
            && (pick == null || target.distanceToSqr(aim) < pick.distanceToSqr(aim)) && target.getId() != combat.wrathLastCall) {
            pick = target;
         }
      }

      RandomSource random = player.getRandom();
      Vec3 at = pick != null ? pick.position()
         : ElectricFx.ground(level, aim.add((random.nextDouble() - 0.5) * config.wrathRadius, 4.0, (random.nextDouble() - 0.5) * config.wrathRadius), 12);
      combat.wrathLastCall = pick == null ? -1 : pick.getId();
      bolt(level, at, 1.0F);
      if (pick != null && CombatHits.hitArea(player, pick, CALL_DAMAGE, ThorMove.WRATH, combat.nextAttackId())) {
         pick.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, CALL_PIN, 2, false, false,
            true));
      }
   }

   /** An aftershock: the toughest one still standing in the blast. */
   private static void aftershock(ServerPlayer player, ThorCombatState combat, int index) {
      ThorCombatConfig config = ThorCombatConfig.get();
      ServerLevel level = player.serverLevel();
      Vec3 aim = combat.wrathAim;
      LivingEntity pick = null;
      for (LivingEntity target : CombatHits.around(player, aim, config.wrathRadius * 1.2, 6.0)) {
         if (target.isAlive() && visibleFrom(level, aim.add(0.0, 1.0, 0.0), target) && (pick == null || target.getHealth() > pick.getHealth())) {
            pick = target;
         }
      }

      RandomSource random = player.getRandom();
      double angle = (index + random.nextDouble() * 0.5) * Math.PI * 2.0 / AFTERSHOCKS.length;
      Vec3 at = pick != null ? pick.position()
         : ElectricFx.ground(level, aim.add(Math.cos(angle) * config.wrathRadius * 0.6, 4.0, Math.sin(angle) * config.wrathRadius * 0.6), 12);
      bolt(level, at, ThorFx.HEAVY_BOLT_POWER);
      if (pick != null && CombatHits.hitArea(player, pick, config.wrathDamage * AFTERSHOCK_SHARE, ThorMove.WRATH, combat.nextAttackId())) {
         CombatHits.push(pick, aim, 0.8, 0.4);
      }
   }

   private static void bolt(ServerLevel level, Vec3 at, float power) {
      HeroFx.electricBolt(level, at, ElectricFx.BOLT_HEIGHT, power, ThorSounds.boltCrack());
      ThorSounds.bolt(level, at, power);
   }

   /** The concentrated strike where the crosshair points. */
   static void strike(ServerPlayer player, ThorCombatState combat) {
      ThorCombatConfig config = ThorCombatConfig.get();
      ServerLevel level = player.serverLevel();
      Vec3 aim = ElectricFx.ground(level, ElectricFx.aim(player, config.wrathRange), 16);
      combat.wrathAim = aim;
      IntArrayList struck = new IntArrayList();
      struck.add(player.getId());
      for (LivingEntity target : CombatHits.around(player, aim, config.wrathRadius, 4.0)) {
         if (!visibleFrom(level, aim.add(0.0, 1.0, 0.0), target)) {
            // B-m5: no hit through walls and floors (the wave and the storm check line of sight too).
            continue;
         }

         double distance = target.position().distanceTo(aim);
         float damage = damageAt(distance, config.wrathCoreRadius, config.wrathRadius, config.wrathDamage);
         if (CombatHits.hit(player, target, damage, ThorMove.WRATH, combat.strikeAttackId)) {
            CombatHits.push(target, aim, 2.2, 0.8);
            struck.add(target.getId());
         }
      }

      // One layered preset (R4): main + satellite bolts, sky flash, screen flash, slam, dust, debris,
      // ground crawlers, scorch and the long tail; hit-stop for Thor and everyone struck.
      HeroFx.preset(player, ThorFx.WRATH_STRIKE, aim, ElectricFx.hand(player), (float)(config.wrathRadius / WRATH_FX_RADIUS),
         level.getBlockState(BlockPos.containing(aim).below()), WRATH_HIT_STOP, struck.toIntArray());
      // R5: the windup loop stops on contact; the layered contact cue replaces the vanilla explode + thunder.
      ThorSounds.stop(level, player.position(), ThorCue.WRATH_WINDUP);
      ThorSounds.playAt(level, aim, ThorCue.WRATH_CONTACT);
      if (config.meleeBreaksBlocks) {
         // B-m5: the ultimate's crater honours the same block switch as every other crater.
         ElectricFx.carve(level, ElectricFx.crater(BlockPos.containing(aim).below(), config.wrathCraterRadius, 0.7));
      }
   }

   /** The strike point sees the target's body (eye or centre). */
   static boolean visibleFrom(ServerLevel level, Vec3 from, LivingEntity target) {
      for (Vec3 to : new Vec3[]{target.getEyePosition(), target.getBoundingBox().getCenter()}) {
         if (level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, target)).getType() == HitResult.Type.MISS) {
            return true;
         }
      }

      return false;
   }

   /** Pure: full damage in the core, falling to a fifth at the edge, nothing outside. */
   public static float damageAt(double distance, double core, double radius, float full) {
      if (distance <= core) {
         return full;
      } else if (distance > radius) {
         return 0.0F;
      }

      double t = (distance - core) / Math.max(1.0E-3, radius - core);
      return (float)(full * (1.0 - 0.8 * t));
   }
}
