package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * One hit of a Reinhard ability (stage 3): {@code hurt} first (the target's
 * i-frames are skipped and restored on a miss), the knockback only when the hit
 * landed and the target accepts impulses ({@link HeroRegistry#allowsImpulse}).
 */
public final class Strikes {
   private Strikes() {
   }

   /** A player attack that ignores the target's i-frames; false = the hit did not land. */
   public static boolean hit(ServerPlayer player, LivingEntity target, float damage) {
      int saved = target.invulnerableTime;
      target.invulnerableTime = 0;
      if (!target.hurt(player.damageSources().playerAttack(player), damage)) {
         target.invulnerableTime = saved;
         return false;
      }

      return true;
   }

   /** Pure: the horizontal push of {@code strength} along {@code look}; zero when looking straight up/down. */
   public static Vec3 push(Vec3 look, double strength) {
      Vec3 flat = new Vec3(look.x, 0.0, look.z);
      return flat.lengthSqr() < 1.0E-8 ? Vec3.ZERO : flat.normalize().scale(strength);
   }

   /** Knockback after a landed hit (vanilla knockback: honours resistance and the Forge event). */
   public static void knockback(LivingEntity target, Vec3 look, double strength) {
      Vec3 push = push(look, strength);
      if (push == Vec3.ZERO || !HeroRegistry.allowsImpulse(target)) {
         return;
      }

      // LivingEntity.knockback pushes against (x, z): pass the opposite of the push.
      target.knockback(strength, -push.x, -push.z);
      target.hurtMarked = true;
   }
}
