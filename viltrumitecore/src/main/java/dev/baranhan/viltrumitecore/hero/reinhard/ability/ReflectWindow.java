package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.ActionTimeline;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Reflect Projectile (spec §6.2, Knight slot 6): the press opens a backhand
 * window of {@link ReinhardRules#REFLECT_WINDOW} ticks (cooldown from the press).
 * While it is open, Arrow Protection answers REFLECT ({@code ArrowWard}, beats
 * catch and deflect): {@code HeroProjectiles} sends the projectile back to its
 * shooter's eyes at the same speed, now owned by Reinhard. Each reflection plays
 * the swat with a white streak on the projectile; the window keeps its own end.
 * Same mechanics as {@link CatchWindow}.
 */
public final class ReflectWindow implements AbilityHandler {
   /** Pure: the press. */
   public static void open(ReinhardState state) {
      state.reflectWindow = ReinhardRules.REFLECT_WINDOW;
      state.cooldowns[ReinhardAbility.REFLECT.ordinal()] = ReinhardRules.REFLECT_COOLDOWN;
      state.action.start(ReinhardAbility.REFLECT, ReinhardRules.REFLECT_WINDOW, -1);
   }

   public static boolean isOpen(ReinhardState state) {
      return state.reflectWindow > 0;
   }

   /** Pure: one server tick of the window. */
   public static void tickWindow(ReinhardState state) {
      if (state.reflectWindow > 0) {
         state.reflectWindow--;
      }
   }

   /** Pure: mode switch, control, cleanup. */
   public static void close(ReinhardState state) {
      state.reflectWindow = 0;
      if (state.action.is(ReinhardAbility.REFLECT)) {
         state.action.cancel();
      }
   }

   /** Pure: a projectile was swatted back; the swat plays at least {@link ReinhardRules#REFLECT_SNAP_TICKS}. */
   public static void snap(ReinhardState state) {
      state.action.start(ReinhardAbility.REFLECT, Math.max(state.reflectWindow, ReinhardRules.REFLECT_SNAP_TICKS), -1, ActionTimeline.PHASE_SNAP);
   }

   /** Called by Reinhard's projectile response right before {@code HeroProjectiles} sends the projectile back. */
   public static void onReflect(ServerPlayer player, ReinhardState state, Projectile projectile) {
      snap(state);
      Vec3 hand = CatchWindow.hand(player);
      HeroFx.flash(player, hand, .65F);
      Vec3 returning = projectile.getOwner() instanceof net.minecraft.world.entity.LivingEntity shooter
         ? shooter.getEyePosition().subtract(hand).normalize() : projectile.getDeltaMovement().normalize().scale(-1);
      HeroFx.blade(player,hand,hand.add(returning.scale(2)),null);
      HeroFx.airTrail(player, projectile);
      ReinhardSounds.playAt(player, hand, ReinhardSounds.REFLECT.get(), 1.0F, 0.7F);
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
