package dev.baranhan.viltrumitecore.entity.avengers;

import dev.baranhan.viltrumitecore.hero.captain.CaptainFx;
import dev.baranhan.viltrumitecore.hero.captain.CaptainSounds;
import dev.baranhan.viltrumitecore.hero.captain.CaptainTargets;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Projectiles of the team: Hawkeye's arrows (standard, explosive, shock), Iron Man's micro-missiles and
 * Thor's thrown Mjolnir (flies through the target and back to Thor's hand). Own collision, never saved,
 * explosions are FX + filtered area damage only (no block damage, no friendly fire).
 */
public class AvengerProjectileEntity extends Entity {
   public static final int ARROW = 0;
   public static final int ARROW_EXPLOSIVE = 1;
   public static final int ARROW_SHOCK = 2;
   public static final int MISSILE = 3;
   public static final int HAMMER = 4;
   private static final EntityDataAccessor<Byte> TYPE = SynchedEntityData.defineId(AvengerProjectileEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Integer> SHOOTER = SynchedEntityData.defineId(AvengerProjectileEntity.class, EntityDataSerializers.INT);

   private int targetId = -1;
   private float damage = 6.0F;
   private int age;
   private boolean returning;
   private final Set<Integer> hit = new HashSet<>();

   public AvengerProjectileEntity(EntityType<? extends AvengerProjectileEntity> type, Level level) {
      super(type, level);
      this.noPhysics = true;
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(TYPE, (byte)ARROW);
      this.entityData.define(SHOOTER, -1);
   }

   public void setup(AvengerEntity shooter, int type, Vec3 from, Vec3 velocity, @Nullable LivingEntity target, float damage) {
      this.entityData.set(TYPE, (byte)type);
      this.entityData.set(SHOOTER, shooter.getId());
      this.targetId = target == null ? -1 : target.getId();
      this.damage = damage;
      this.setPos(from);
      this.setDeltaMovement(velocity);
      this.faceVelocity();
   }

   public int type() {
      return this.entityData.get(TYPE);
   }

   public int shooterId() {
      return this.entityData.get(SHOOTER);
   }

   /** A projectile hit (paced by {@link TeamDamage}); the mob turns on the Avenger who shot it. */
   private static boolean hitBy(LivingEntity shooter, LivingEntity e, float damage) {
      if (!CaptainTargets.strike(shooter, e, TeamDamage.take(e, damage))) {
         return false;
      }
      if (shooter instanceof AvengerEntity a && a.brain != null) {
         a.brain.drawFire(e);
      }
      return true;
   }

   @Nullable
   private AvengerEntity shooter() {
      return this.level().getEntity(this.shooterId()) instanceof AvengerEntity a && a.isAlive() ? a : null;
   }

   private void faceVelocity() {
      Vec3 v = this.getDeltaMovement();
      if (v.lengthSqr() > 1.0E-5) {
         this.setYRot((float)(Math.atan2(v.x, v.z) * 180.0 / Math.PI));
         this.setXRot((float)(Math.atan2(v.y, v.horizontalDistance()) * 180.0 / Math.PI));
         this.yRotO = this.getYRot();
         this.xRotO = this.getXRot();
      }
   }

   @Override
   public void tick() {
      super.tick();
      Vec3 v = this.getDeltaMovement();
      if (this.level().isClientSide) {
         this.setPos(this.position().add(v));
         return;
      }
      AvengerEntity shooter = this.shooter();
      if (shooter == null || ++this.age > 120) {
         this.discard();
         return;
      }
      int type = this.type();
      Entity target = this.targetId >= 0 ? this.level().getEntity(this.targetId) : null;
      if (type == MISSILE && target instanceof LivingEntity t && t.isAlive()) {
         Vec3 aim = t.getBoundingBox().getCenter().subtract(this.position());
         v = v.normalize().lerp(aim.normalize(), this.age < 6 ? 0.08 : 0.3).normalize().scale(0.9);
      } else if (type == HAMMER) {
         if (this.returning) {
            Vec3 hand = shooter.position().add(0, shooter.getBbHeight() * 0.6, 0);
            Vec3 d = hand.subtract(this.position());
            if (d.length() < 1.3) {
               this.discard();
               return;
            }
            v = d.normalize().scale(Math.min(1.6, d.length()));
         } else if (target instanceof LivingEntity t && t.isAlive()) {
            Vec3 aim = t.getBoundingBox().getCenter().subtract(this.position());
            v = v.normalize().lerp(aim.normalize(), 0.5).normalize().scale(1.5);
         }
         if (this.age > 40) {
            this.returning = true;
         }
      } else if (type != MISSILE) {
         v = v.add(0, -0.025, 0); // arrows drop a little
      }
      Vec3 from = this.position();
      Vec3 to = from.add(v);
      if (!(type == HAMMER && this.returning)) {
         BlockHitResult block = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
         if (block.getType() != HitResult.Type.MISS) {
            if (type == HAMMER) {
               this.returning = true;
               this.setDeltaMovement(v.scale(-1));
               return;
            }
            this.impact(shooter, block.getLocation(), null);
            return;
         }
      }
      AABB sweep = this.getBoundingBox().expandTowards(v).inflate(0.3);
      for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, sweep, e -> shooter.mayHit(e) && !this.hit.contains(e.getId()))) {
         if (type == HAMMER) {
            this.hit.add(e.getId());
            if (hitBy(shooter, e, this.damage)) {
               CaptainTargets.push(e, v.normalize().scale(1.1).add(0, 0.35, 0));
               CaptainFx.around(this, CaptainFx.SHOCK, 0, e.getBoundingBox().getCenter(), this.position());
               CaptainSounds.playAt(this, e.position(), CaptainSounds.HAMMER_HIT, 1.0F, 1.0F);
            }
            this.returning = true;
            continue;
         }
         this.impact(shooter, e.getBoundingBox().getCenter(), e);
         return;
      }
      this.setPos(to);
      this.setDeltaMovement(v);
      this.faceVelocity();
   }

   private void impact(AvengerEntity shooter, Vec3 at, @Nullable LivingEntity direct) {
      int type = this.type();
      ServerLevel level = (ServerLevel)this.level();
      switch (type) {
         case ARROW_EXPLOSIVE, MISSILE -> {
            double radius = type == MISSILE ? 2.6 : 3.2;
            for (LivingEntity e : CaptainTargets.around(this, at, radius, shooter::mayHit)) {
               double d = Math.sqrt(e.getBoundingBox().distanceToSqr(at));
               if (hitBy(shooter, e, (float)(this.damage * (1.0 - 0.5 * Math.min(1.0, d / radius))))) {
                  Vec3 away = e.position().subtract(at);
                  Vec3 h = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
                  CaptainTargets.push(e, h.scale(0.6).add(0, 0.35, 0));
               }
            }
            CaptainFx.near(level, at, 64, CaptainFx.EXPLOSION, this.getId(), type == MISSILE ? 0 : 1, at, new Vec3(radius, 0, 0));
            CaptainSounds.playAt(this, at, CaptainSounds.EXPLOSION, type == MISSILE ? 1.0F : 1.3F, type == MISSILE ? 1.2F : 0.95F);
         }
         case ARROW_SHOCK -> {
            for (LivingEntity e : CaptainTargets.around(this, at, 2.2, shooter::mayHit)) {
               if (hitBy(shooter, e, this.damage)) {
                  CaptainTargets.stun(shooter, e, 40);
                  CaptainFx.around(this, CaptainFx.STUN, 40, e.getBoundingBox().getCenter(), Vec3.ZERO, e.getId());
               }
            }
            CaptainFx.near(level, at, 48, CaptainFx.SHOCK, this.getId(), 1, at, at.add(0, 1.5, 0));
            CaptainSounds.playAt(this, at, CaptainSounds.SHOCK, 1.0F, 1.0F);
         }
         default -> {
            if (direct != null && hitBy(shooter, direct, this.damage)) {
               CaptainTargets.push(direct, this.getDeltaMovement().normalize().scale(0.35).add(0, 0.1, 0));
            }
            CaptainFx.near(level, at, 32, CaptainFx.CONTACT, this.getId(), 5, at, this.position());
            CaptainSounds.playAt(this, at, CaptainSounds.ARROW_HIT, 0.8F, CaptainSounds.jitter(this, 1.0F));
         }
      }
      this.discard();
   }

   @Override
   public boolean isPickable() {
      return false;
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   public boolean isNoGravity() {
      return true;
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double distance) {
      return distance < 96 * 96;
   }
}
