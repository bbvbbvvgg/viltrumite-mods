package dev.baranhan.viltrumitecore.hero.homelander;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Eye lasers (spec §5.1): hold to fire. 4 t charge, then 3 damage every 4 t to
 * the first entity on a 64-block ray, sets it on fire for 4 s. Blocks never
 * change; scorch marks are client decals.
 */
public final class EyeLasers {
   private EyeLasers() {
   }

   /** Does this beam age deal damage (age 0 = key press). */
   public static boolean damageTick(int age) {
      int beamAge = age - HomelanderRules.LASER_CHARGE_TICKS;
      return beamAge >= 0 && beamAge % HomelanderRules.LASER_DAMAGE_INTERVAL == 0;
   }

   static void press(ServerPlayer player, HomelanderState state, boolean allowed) {
      if (!allowed) {
         HomelanderFeedback.refuse(player);
         return;
      }

      if (state.laserAge >= 0) {
         return;
      }

      state.laserHeld = true;
      state.laserAge = 0;
      dev.baranhan.viltrumitecore.hero.speech.HeroSpeech.ability(player, HomelanderAbilities.LASERS);
      player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
         ViltrumiteCore.HOMELANDER_LASER_CHARGE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
   }

   static void release(ServerPlayer player, HomelanderState state) {
      if (state.laserAge < 0) {
         return;
      }

      boolean wasOn = state.laserBeamOn();
      state.laserHeld = false;
      state.laserAge = -1;
      if (wasOn) {
         player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
            ViltrumiteCore.HOMELANDER_LASER_RELEASE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
      }
   }

   /** Server tick while held. Call before the heat tick. */
   static void tick(ServerPlayer player, HomelanderState state) {
      if (state.laserAge < 0) {
         return;
      }

      if (!state.laserHeld || !state.heat.sourcesAllowed()) {
         release(player, state);
         return;
      }

      LivingEntity target = firstLivingOnRay(player);
      boolean guarded=state.laserBeamOn() && target instanceof ServerPlayer defender && dev.baranhan.viltrumitecore.hero.HeroRegistry.get(defender)
         .blocksContinuousRay(defender,player.getEyePosition(),ray(player,HomelanderRules.LASER_RANGE).getLocation());
      if (damageTick(state.laserAge) && !guarded) {
         if (target != null) {
            int previousInvulnerable = target.invulnerableTime;
            target.invulnerableTime = 0;
            if (target.hurt(player.damageSources().playerAttack(player), HomelanderRules.LASER_DAMAGE)) {
               target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), HomelanderRules.LASER_FIRE_TICKS));
            } else {
               target.invulnerableTime = previousInvulnerable;
            }
         }
      }

      state.laserAge++;
   }

   /** The first living entity on the eye ray before a solid block, or null. */
   @Nullable
   static LivingEntity firstLivingOnRay(ServerPlayer player) {
      HitResult hit = ray(player, HomelanderRules.LASER_RANGE);
      if (!(hit instanceof EntityHitResult entityHit)) {
         return null;
      }

      Entity entity = entityHit.getEntity();
      // Multipart mobs (ender dragon): the beam hits a part, damage goes to the living parent.
      if (entity instanceof net.minecraftforge.entity.PartEntity<?> part) {
         entity = part.getParent();
      }

      return entity instanceof LivingEntity living ? living : null;
   }

   /**
    * Nearest entity whose hitbox the segment crosses, with the real contact point.
    * Vanilla's level-based ProjectileUtil.getEntityHitResult returns a hit located
    * at the entity's feet (EntityHitResult(entity) = entity.position()), so beam,
    * sparks and the defence contact all landed at the target's feet instead of
    * where the eyes aim (playtest: "laser magnets to the feet").
    */
   @Nullable
   static EntityHitResult firstEntityOnSegment(Entity shooter, Vec3 from, Vec3 to, AABB box) {
      Entity best = null;
      Vec3 bestPoint = null;
      double bestDistance = Double.MAX_VALUE;
      for (Entity entity : shooter.level().getEntities(shooter, box,
         e -> e.isPickable() && !e.isSpectator() && e.isAlive() && !(e instanceof ArmorStand))) {
         Vec3 point = contactPoint(entity.getBoundingBox().inflate(entity.getPickRadius()), from, to);
         if (point == null) {
            continue;
         }

         double distance = from.distanceToSqr(point);
         if (distance < bestDistance) {
            best = entity;
            bestPoint = point;
            bestDistance = distance;
         }
      }

      return best == null ? null : new EntityHitResult(best, bestPoint);
   }

   /** Pure: where the segment enters the box (the start itself when it begins inside), or null. */
   @Nullable
   static Vec3 contactPoint(AABB bounds, Vec3 from, Vec3 to) {
      if (bounds.contains(from)) {
         return from;
      }

      return bounds.clip(from, to).orElse(null);
   }

   /** Shared ray: blocks (collider shape) then entities up to the block hit. Used on both sides. */
   public static HitResult ray(Entity shooter, double range) {
      Vec3 eye = shooter.getEyePosition();
      Vec3 end = eye.add(shooter.getLookAngle().scale(range));
      BlockHitResult blockHit = shooter.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
      Vec3 limit = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
      AABB box = shooter.getBoundingBox().expandTowards(limit.subtract(eye)).inflate(1.0);
      // No aim assist: the beam hits only an entity whose exact hitbox the eye ray crosses.
      EntityHitResult entityHit = firstEntityOnSegment(shooter, eye, limit, box);
      return entityHit != null ? entityHit : blockHit;
   }
}
