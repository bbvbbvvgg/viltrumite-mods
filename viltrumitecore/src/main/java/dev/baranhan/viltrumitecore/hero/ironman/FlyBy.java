package dev.baranhan.viltrumitecore.hero.ironman;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fly-by punch (spec §7.4): LMB in flight hits the nearest body within
 * {@link IronManRules#FLYBY_RANGE} along the look or the velocity. Damage
 * grows with speed; knockback follows the flight direction; the player's own
 * velocity is never changed (no stop on impact). Damage goes through
 * {@code hurt}, i.e. HeroDamage routing and the damage layers.
 */
public final class FlyBy {
   public record Result(float damage, Vec3 knockback, Vec3 selfImpulse) {
   }

   private FlyBy() {
   }

   /** Pure: damage + knockback for a flight velocity (blocks/tick). */
   public static Result compute(Vec3 velocity) {
      double speed = velocity.length();
      float damage = Math.min(IronManRules.FLYBY_MAX, IronManRules.FLYBY_BASE + (float)speed * IronManRules.FLYBY_PER_SPEED);
      Vec3 dir = speed < 1.0E-4 ? Vec3.ZERO : velocity.scale(1.0 / speed);
      Vec3 knockback = new Vec3(dir.x, Math.max(0.1, dir.y), dir.z).normalize().scale(IronManRules.FLYBY_KNOCKBACK * (0.5 + Math.min(1.0, speed / 3.0)));
      return new Result(damage, knockback, Vec3.ZERO);
   }

   /** Server: punch the nearest target, true when something was hit. */
   public static boolean hit(ServerPlayer player) {
      Vec3 velocity = velocityOf(player);
      LivingEntity target = findTarget(player, velocity);
      if (target == null) {
         return false;
      }

      Result result = compute(velocity);
      if (target.hurt(player.damageSources().playerAttack(player), result.damage())) {
         push(target, result.knockback());
         IronManSounds.play(player, dev.baranhan.viltrumitecore.ViltrumiteCore.IRONMAN_FLYBY_HIT.get(), 1.0F, 0.9F + player.getRandom().nextFloat() * 0.2F);
      }

      player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
      return true;
   }

   /** Movement is client-side: take the larger of the synced velocity and the position delta. */
   static Vec3 velocityOf(ServerPlayer player) {
      Vec3 motion = player.getDeltaMovement();
      Vec3 delta = player.position().subtract(player.xo, player.yo, player.zo);
      return delta.lengthSqr() > motion.lengthSqr() ? delta : motion;
   }

   static void push(LivingEntity target, Vec3 impulse) {
      if (!dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsImpulse(target)) {
         return;
      }

      double resist = 1.0 - target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
      if (resist <= 0.0) {
         return;
      }

      target.push(impulse.x * resist, impulse.y * resist, impulse.z * resist);
      target.hurtMarked = true;
   }

   static boolean valid(ServerPlayer player, Entity entity) {
      return entity instanceof LivingEntity living && entity != player && living.isAlive() && !entity.isSpectator()
         && entity.isPickable() && !player.isAlliedTo(entity) && !player.isPassengerOfSameVehicle(entity);
   }

   @Nullable
   private static LivingEntity findTarget(ServerPlayer player, Vec3 velocity) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle().scale(IronManRules.FLYBY_RANGE);
      Vec3 along = velocity.lengthSqr() < 1.0E-6 ? look : velocity.normalize().scale(IronManRules.FLYBY_RANGE);
      AABB area = player.getBoundingBox().expandTowards(look).expandTowards(along).inflate(IronManRules.FLYBY_RADIUS + 1.0);
      LivingEntity best = null;
      double bestDist = Double.MAX_VALUE;
      // No fly-by hit through a wall.
      for (Entity entity : player.level().getEntities(player, area, e -> valid(player, e) && player.hasLineOfSight(e))) {
         AABB box = entity.getBoundingBox().inflate(IronManRules.FLYBY_RADIUS);
         double dist = Math.min(clipDist(box, eye, eye.add(look)), clipDist(box, eye, eye.add(along)));
         if (dist < bestDist) {
            bestDist = dist;
            best = (LivingEntity)entity;
         }
      }

      return best;
   }

   private static double clipDist(AABB box, Vec3 from, Vec3 to) {
      if (box.contains(from)) {
         return 0.0;
      }

      return box.clip(from, to).map(from::distanceToSqr).orElse(Double.MAX_VALUE);
   }
}
