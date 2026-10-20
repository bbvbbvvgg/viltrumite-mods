package dev.baranhan.viltrumitecore.hero;

import dev.baranhan.viltrumitecore.mixin.AbstractArrowAccessor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;

/**
 * Projectile seam: a projectile about to hit a hero player asks
 * {@link HeroDefinition#projectileResponse}; anything but PASS skips the hit
 * ({@code SKIP_ENTITY}: arrows keep flying, other projectiles skip onHit) and
 * the response is applied here. Server only.
 *
 * <p>Arrows, throwables and fireballs read their velocity after the hit check,
 * so the new velocity flies this very tick. A few classes (llama spit) move with
 * the velocity read before it; every response is therefore re-checked at the
 * end of the level tick ({@link #tickPending}) and re-applied when the
 * projectile's own tick overwrote it.
 */
public final class HeroProjectiles {
   /** How far (blocks) a turned projectile is pushed out of the hero's box at most. */
   private static final double EXIT_STEP = 0.25;
   private static final int EXIT_STEPS = 16;
   private static final double BOX_MARGIN = 0.3;
   private static final List<Pending> PENDING = new ArrayList<>();

   private HeroProjectiles() {
   }

   public static void onImpact(ProjectileImpactEvent event) {
      if (intercept(event.getProjectile(), event.getRayTraceResult()))
         event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
   }

   /** Custom projectile entities must call the same seam before their own onHit. */
   public static boolean intercept(Projectile projectile, net.minecraft.world.phys.HitResult result) {
      if (projectile.level().isClientSide() || !(result instanceof EntityHitResult hit)
         || !(hit.getEntity() instanceof ServerPlayer self) || projectile.getOwner() == self || !self.isAlive()) {
         return false;
      }

      ProjectileResponse response = HeroRegistry.get(self).projectileResponse(self, projectile);
      if (response == ProjectileResponse.PASS) {
         return false;
      }
      switch (response) {
         case DEFLECT -> deflect(self, projectile);
         case CATCH -> caught(self, projectile);
         case REFLECT -> reflect(self, projectile);
         default -> {
         }
      }
      return true;
   }

   private static void atContact(ServerPlayer self,Projectile projectile) {
      Vec3 contact=HeroRegistry.get(self).defenseContact(self);if(contact!=null && contact.distanceToSqr(projectile.position())<.36)projectile.setPos(contact);
   }
   private static void deflect(ServerPlayer self, Projectile projectile) {
      atContact(self,projectile);
      Vec3 v = projectile.getDeltaMovement();
      Vec3 toBody = self.getBoundingBox().getCenter().subtract(projectile.position());
      double yaw = ProjectileMath.deflectYaw(v, toBody, self.getRandom().nextBoolean(), ProjectileMath.deflectDegrees(self.getRandom().nextDouble()));
      Vec3 axis = ProjectileMath.sideAxis(v);
      Vec3 next = ProjectileMath.rotateAndScale(v, axis, yaw, ProjectileMath.DEFLECT_SCALE);
      if (projectile instanceof AbstractHurtingProjectile hurting) {
         // Fireballs accelerate by their power every tick: turn it alike, or they curve back onto the old line.
         Vec3 power = ProjectileMath.rotateAndScale(new Vec3(hurting.xPower, hurting.yPower, hurting.zPower), axis, yaw, ProjectileMath.DEFLECT_SCALE);
         hurting.xPower = power.x;
         hurting.yPower = power.y;
         hurting.zPower = power.z;
      }

      launch(self, projectile, next);
   }

   private static void reflect(ServerPlayer self, Projectile projectile) {
      atContact(self,projectile);
      Entity owner = projectile.getOwner();
      Vec3 ownerEye = owner != null && owner.isAlive() && owner.level() == projectile.level() ? owner.getEyePosition() : null;
      Vec3 next = ProjectileMath.reflected(projectile.getDeltaMovement(), projectile.position(), ownerEye);
      if (projectile instanceof AbstractHurtingProjectile hurting) {
         Vec3 power = ProjectileMath.alongVelocity(new Vec3(hurting.xPower, hurting.yPower, hurting.zPower), next);
         hurting.xPower = power.x;
         hurting.yPower = power.y;
         hurting.zPower = power.z;
      }

      projectile.setOwner(self);
      if (projectile instanceof dev.baranhan.viltrumitecore.entity.Homing homing) homing.retarget(owner);
      launch(self, projectile, next);
   }

   /** Arrow → its pickup item (unless CREATIVE_ONLY), trident → the trident, anything else is removed. */
   private static void caught(ServerPlayer self, Projectile projectile) {
      if (HeroRegistry.get(self).captureProjectile(self, projectile)) return;
      ItemStack item = null;
      if (projectile instanceof AbstractArrow arrow) {
         // A trident is an AbstractArrow: its pickup item is the trident stack itself.
         item = ProjectileMath.caughtItem(arrow.pickup, () -> ((AbstractArrowAccessor) arrow).viltrumitecore$getPickupItem().copy());
      }

      projectile.discard();
      if (item != null && !item.isEmpty() && !self.getInventory().add(item)) {
         self.drop(item, false);
      }

      self.level().playSound(null, self.getX(), self.getY() + 1.0, self.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.4F);
   }

   private static void launch(ServerPlayer self, Projectile projectile, Vec3 velocity) {
      projectile.setDeltaMovement(velocity);
      projectile.hasImpulse = true;
      pushOut(self, projectile, velocity);
      PENDING.add(new Pending(self, projectile, velocity));
   }

   /** Moves the projectile along its new direction until it no longer overlaps the hero (so it cannot hit him again at once). */
   private static void pushOut(ServerPlayer self, Projectile projectile, Vec3 velocity) {
      if (velocity.lengthSqr() < 1.0E-8) {
         return;
      }

      AABB body = self.getBoundingBox().inflate(BOX_MARGIN);
      Vec3 step = velocity.normalize().scale(EXIT_STEP);
      Vec3 pos = projectile.position();
      for (int i = 0; i < EXIT_STEPS && body.intersects(projectile.getBoundingBox().move(pos.subtract(projectile.position()))); i++) {
         pos = pos.add(step);
      }

      if (!pos.equals(projectile.position())) {
         projectile.setPos(pos);
      }
   }

   /** End of a level tick: a projectile whose own tick replaced the new direction gets it again. */
   public static void tickPending(ServerLevel level) {
      Iterator<Pending> it = PENDING.iterator();
      while (it.hasNext()) {
         Pending pending = it.next();
         if (pending.projectile.isRemoved()) {
            it.remove();
            continue;
         }

         if (pending.projectile.level() != level) {
            continue;
         }

         it.remove();

         Vec3 now = pending.projectile.getDeltaMovement();
         if (ProjectileMath.angleDeg(now, pending.velocity) > 30.0) {
            pending.projectile.setDeltaMovement(pending.velocity);
            pending.projectile.hasImpulse = true;
            if (pending.hero.level() == level) {
               pushOut(pending.hero, pending.projectile, pending.velocity);
            }
         }
      }
   }

   private record Pending(ServerPlayer hero, Projectile projectile, Vec3 velocity) {
   }
}
