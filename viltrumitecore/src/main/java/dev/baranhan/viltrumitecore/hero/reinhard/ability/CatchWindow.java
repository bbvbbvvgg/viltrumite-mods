package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.ActionTimeline;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

/**
 * Catch Projectile (spec §6.1, Restrained slot 3): the press opens the palm for
 * {@link ReinhardRules#CATCH_WINDOW} ticks (cooldown from the press). While it is
 * open, Arrow Protection answers CATCH instead of DEFLECT ({@code ArrowWard}):
 * {@code HeroProjectiles} turns an arrow / trident into its item and removes
 * anything else. Each catch plays the closing hand; the window keeps its own
 * end, so several projectiles may be caught.
 */
public final class CatchWindow implements AbilityHandler {
   /** Pure: the press. */
   public static void open(ReinhardState state) {
      state.catchWindow = ReinhardRules.CATCH_WINDOW;
      state.cooldowns[ReinhardAbility.CATCH.ordinal()] = ReinhardRules.CATCH_COOLDOWN;
      state.action.start(ReinhardAbility.CATCH, ReinhardRules.CATCH_WINDOW, -1);
   }

   public static boolean isOpen(ReinhardState state) {
      return state.catchWindow > 0;
   }

   /** Pure: one server tick of the window. */
   public static void tickWindow(ReinhardState state) {
      if (state.catchWindow > 0) {
         state.catchWindow--;
      }
   }

   /** Pure: mode switch, control, cleanup. */
   public static void close(ReinhardState state) {
      state.catchWindow = 0;
      if (state.action.is(ReinhardAbility.CATCH)) {
         state.action.cancel();
      }
   }

   /** Pure: a projectile was caught; the closing hand plays at least {@link ReinhardRules#CATCH_SNAP_TICKS}. */
   public static void snap(ReinhardState state) {
      state.action.start(ReinhardAbility.CATCH, Math.max(state.catchWindow, ReinhardRules.CATCH_SNAP_TICKS), -1, ActionTimeline.PHASE_SNAP);
   }

   /** Called by Reinhard's projectile response right before {@code HeroProjectiles} takes the projectile. */
   public static void onCatch(ServerPlayer player, ReinhardState state) {
      snap(state);
      Vec3 hand = hand(player);
      HeroFx.flash(player, hand);
      ReinhardSounds.playAt(player, hand, ReinhardSounds.CATCH.get(), 0.9F, 0.8F);
   }

   /** Roughly where the main hand is when held up in front of the face. */
   static Vec3 hand(ServerPlayer player) {
      double yaw = Math.toRadians(player.getYRot());
      double side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0 : -1.0;
      Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw)).scale(0.35 * side);
      return player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(right).add(0.0, -0.25, 0.0);
   }

   @Override
   public boolean tryStart(ServerPlayer player, ReinhardState state) {
      open(state);
      return true;
   }

   @Override
   public void tick(ServerPlayer player, ReinhardState state) {
      tickWindow(state);
   }

   @Override
   public void cancel(ServerPlayer player, ReinhardState state, CleanupReason reason) {
      close(state);
   }
}
