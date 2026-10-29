package dev.baranhan.viltrumitecore.hero.dio;

import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.dio.net.DioFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.dio.net.DioNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * DIO's knife. Straight, fast, nearly without gravity. Inside stopped time it
 * flies {@link DioRules#KNIFE_STOP_DISTANCE} and then hangs in the air (also
 * right before a frozen victim) until time moves again, then keeps its flight.
 * Hits go through vanilla hurt with a thrown source owned by DIO.
 */
public class DioKnifeEntity extends Projectile {
   private static final EntityDataAccessor<Boolean> STOPPED = SynchedEntityData.defineId(DioKnifeEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(DioKnifeEntity.class, EntityDataSerializers.BOOLEAN);
   private int life;
   private int stuckTicks;
   private double stoppedTravel;
   private Vec3 heldMotion = Vec3.ZERO;
   /** Client: spin phase for the renderer. */
   public float spin;

   public DioKnifeEntity(EntityType<? extends DioKnifeEntity> type, Level level) {
      super(type, level);
   }

   public static DioKnifeEntity throwFrom(ServerPlayer owner, Vec3 from, Vec3 direction) {
      DioKnifeEntity knife = new DioKnifeEntity(ViltrumiteEntities.DIO_KNIFE.get(), owner.level());
      knife.setOwner(owner);
      knife.setPos(from);
      Vec3 motion = direction.normalize().scale(DioRules.KNIFE_SPEED);
      knife.setDeltaMovement(motion);
      knife.alignTo(motion);
      owner.level().addFreshEntity(knife);
      return knife;
   }

   public boolean stopped() {
      return this.entityData.get(STOPPED);
   }

   public boolean stuck() {
      return this.entityData.get(STUCK);
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(STOPPED, false);
      this.entityData.define(STUCK, false);
   }

   private void alignTo(Vec3 motion) {
      double h = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
      this.setYRot((float)(Mth.atan2(motion.x, motion.z) * (180.0 / Math.PI)));
      this.setXRot((float)(Mth.atan2(motion.y, h) * (180.0 / Math.PI)));
      this.yRotO = this.getYRot();
      this.xRotO = this.getXRot();
   }

   @Override
   public void tick() {
      super.tick();
      if (this.stuck()) {
         this.setDeltaMovement(Vec3.ZERO);
         if (!this.level().isClientSide && ++this.stuckTicks > 40) {
            this.discard();
         }

         return;
      }

      if (this.level().isClientSide) {
         if (!this.stopped()) {
            Vec3 m = this.getDeltaMovement();
            this.setPos(this.getX() + m.x, this.getY() + m.y, this.getZ() + m.z);
            this.alignTo(m);
         }

         return;
      }

      TimeStopField field = DioTimeStop.covering((ServerLevel)this.level(), this.position());
      if (this.stopped()) {
         if (field == null) {
            // Time moves again: the knife keeps its flight.
            this.entityData.set(STOPPED, false);
            this.setDeltaMovement(this.heldMotion);
            this.hasImpulse = true;
         } else {
            this.setDeltaMovement(Vec3.ZERO);
            return;
         }
      }

      Vec3 motion = this.getDeltaMovement();
      if (field != null && this.stoppedTravel + motion.length() > DioRules.KNIFE_STOP_DISTANCE) {
         double rest = Math.max(0.0, DioRules.KNIFE_STOP_DISTANCE - this.stoppedTravel);
         this.hold(motion, motion.normalize().scale(rest));
         return;
      }

      HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
      if (hit.getType() == HitResult.Type.ENTITY && field != null && ((EntityHitResult)hit).getEntity() instanceof LivingEntity living
         && DioTimeStop.freezing(living) != null) {
         // Stopped time: the knife halts a hand's width before the frozen victim.
         Vec3 to = hit.getLocation().subtract(this.position());
         this.hold(motion, to.lengthSqr() > 0.09 ? to.subtract(to.normalize().scale(0.3)) : Vec3.ZERO);
         return;
      }

      if (hit.getType() != HitResult.Type.MISS) {
         this.onHit(hit);
         if (this.isRemoved() || this.stuck()) {
            return;
         }
      }

      if (field != null) {
         this.stoppedTravel += motion.length();
      }

      this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
      this.setDeltaMovement(motion.add(0, -0.004, 0));
      this.alignTo(motion);
      if (++this.life > DioRules.KNIFE_LIFE) {
         this.discard();
      }
   }

   private void hold(Vec3 motion, Vec3 step) {
      this.setPos(this.getX() + step.x, this.getY() + step.y, this.getZ() + step.z);
      this.heldMotion = motion;
      this.setDeltaMovement(Vec3.ZERO);
      this.entityData.set(STOPPED, true);
      this.hasImpulse = true;
      if (this.getOwner() instanceof ServerPlayer owner) {
         DioNet.fx(this, DioFxS2CPacket.KNIFE_STOP, this.position(), motion, 1.0F, 0);
         DioSounds.play((ServerLevel)this.level(), this.position(), DioSounds.KNIFE_STOP, 0.5F, 1.4F + this.random.nextFloat() * 0.3F);
      }
   }

   @Override
   protected boolean canHitEntity(Entity entity) {
      return super.canHitEntity(entity) && !(entity instanceof DioKnifeEntity) && entity != this.getOwner();
   }

   @Override
   protected void onHitEntity(EntityHitResult result) {
      Entity entity = result.getEntity();
      if (entity instanceof net.minecraftforge.entity.PartEntity<?> part) {
         entity = part.getParent();
      }

      Entity owner = this.getOwner();
      if (entity instanceof LivingEntity living) {
         living.invulnerableTime = 0;
         boolean hurt = living.hurt(this.damageSources().thrown(this, owner), DioRules.KNIFE_DAMAGE);
         if (hurt) {
            if (HeroRegistry.allowsImpulse(living)) {
               Vec3 push = this.getDeltaMovement().normalize().scale(0.15);
               living.push(push.x, 0.04, push.z);
               living.hurtMarked = true;
            }

            if (owner instanceof ServerPlayer dio) {
               DioHero.addBlood(dio, DioRules.BLOOD_KNIFE_HIT);
            }
         }
      }

      DioNet.fx(this, DioFxS2CPacket.KNIFE_HIT, result.getLocation(), this.getDeltaMovement(), 1.0F, 1);
      DioSounds.play((ServerLevel)this.level(), result.getLocation(), DioSounds.KNIFE_HIT, 0.8F, 0.9F + this.random.nextFloat() * 0.25F);
      this.discard();
   }

   @Override
   protected void onHitBlock(BlockHitResult result) {
      Vec3 at = result.getLocation();
      Vec3 back = this.getDeltaMovement().normalize().scale(0.12);
      this.setPos(at.x - back.x, at.y - back.y, at.z - back.z);
      this.setDeltaMovement(Vec3.ZERO);
      this.entityData.set(STUCK, true);
      this.hasImpulse = true;
      DioNet.fx(this, DioFxS2CPacket.KNIFE_HIT, at, back, 0.6F, 0);
      DioSounds.play((ServerLevel)this.level(), at, DioSounds.KNIFE_HIT, 0.6F, 1.3F + this.random.nextFloat() * 0.2F);
   }

   @Override
   public boolean isPickable() {
      return false;
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double distance) {
      return distance < 96.0 * 96.0;
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.discard();
   }
}
