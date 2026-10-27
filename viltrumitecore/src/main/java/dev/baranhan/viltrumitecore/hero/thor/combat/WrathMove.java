package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.HeroShockwave;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.thor.ThorFeedback;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveContext;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * WRATH (internal id; name not approved): the ultimate on its own key
 * (Z is a temporary foundation default), aimed with the ordinary crosshair
 * (#136), concentrating the power in one point (#102), unlike the storm
 * around Thor. Key, duration, scale, damage, cooldown, use without Mjölnir
 * and in flight / grabs are OPEN: every value is PLACEHOLDER and the hammer
 * rule is a config switch, off by default (not decided).
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
         context.state().combatPose = 1;
         if (player != null) {
            ServerLevel level = player.serverLevel();
            ElectricFx.sound(level, player.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 0.6F);
            ElectricFx.sound(level, player.position(), SoundEvents.TRIDENT_THUNDER, 0.8F, 0.6F);
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
         } else if (event == StagedTimeline.Event.END) {
            context.state().combatPose = 0;
         }
      }

      @Override
      public void onStop(ThorMoveContext context) {
         context.state().combatPose = 0;
      }
   };

   private WrathMove() {
   }

   /** Charge visuals during the intro (server tick). */
   static void tick(ServerPlayer player, ThorState state, ThorCombatState combat) {
      if (!state.timeline.is(ThorMove.WRATH) || state.timeline.phase() != MovePhase.INTRO || state.timeline.elapsed() % 3 != 0) {
         return;
      }

      ServerLevel level = player.serverLevel();
      RandomSource random = level.getRandom();
      Vec3 hand = ElectricFx.hand(player).add(0.0, 0.8, 0.0);
      Vec3 sky = player.position().add((random.nextDouble() - 0.5) * 8.0, 10.0 + random.nextDouble() * 4.0, (random.nextDouble() - 0.5) * 8.0);
      ElectricFx.arc(level, sky, hand);
      ElectricFx.crackle(level, hand, 8);
   }

   /** The concentrated strike where the crosshair points. */
   static void strike(ServerPlayer player, ThorCombatState combat) {
      ThorCombatConfig config = ThorCombatConfig.get();
      ServerLevel level = player.serverLevel();
      Vec3 aim = ElectricFx.ground(level, ElectricFx.aim(player, config.wrathRange), 16);
      combat.wrathAim = aim;
      ElectricFx.bolt(level, aim);
      for (int i = 0; i < 4; i++) {
         double angle = Math.PI / 2.0 * i + Math.PI / 4.0;
         ElectricFx.bolt(level, aim.add(Math.cos(angle) * 0.8, 0.0, Math.sin(angle) * 0.8));
      }

      ElectricFx.arc(level, ElectricFx.hand(player), aim.add(0.0, 0.5, 0.0));
      for (LivingEntity target : CombatHits.around(player, aim, config.wrathRadius, 4.0)) {
         double distance = target.position().distanceTo(aim);
         float damage = damageAt(distance, config.wrathCoreRadius, config.wrathRadius, config.wrathDamage);
         if (CombatHits.hit(player, target, damage, ThorMove.WRATH, combat.strikeAttackId)) {
            CombatHits.push(target, aim, 2.2, 0.8);
         }
      }

      HeroFx.slam(player, aim, HeroShockwave.groundUnder(level, player), (float)config.wrathRadius);
      HeroFx.flash(player, aim.add(0.0, 1.0, 0.0), 2.0F);
      ElectricFx.crackle(level, aim.add(0.0, 1.0, 0.0), 80);
      ElectricFx.sound(level, aim, SoundEvents.GENERIC_EXPLODE, 2.0F, 0.6F);
      ElectricFx.sound(level, aim, SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0F, 0.7F);
      ElectricFx.carve(level, ElectricFx.crater(BlockPos.containing(aim).below(), config.wrathCraterRadius, 0.7));
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
