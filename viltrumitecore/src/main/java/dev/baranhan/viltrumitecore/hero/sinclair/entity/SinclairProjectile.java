package dev.baranhan.viltrumitecore.hero.sinclair.entity;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * Shared flight of Sinclair's projectiles (spec §7: real {@link Projectile}s, so shared reflect/catch
 * mechanics see them). No gravity, a fixed lifetime, an owner check every tick, never saved.
 * {@code sinclairOwner} is the Sinclair player even when an assistant fired it.
 */
public abstract class SinclairProjectile extends Projectile implements SinclairOwned.Owned {
   /** Fired by the assistant (lavender tint, assistant damage). */
   private static final EntityDataAccessor<Boolean> FROM_ASSISTANT = SynchedEntityData.defineId(SinclairProjectile.class, EntityDataSerializers.BOOLEAN);
   @Nullable
   protected UUID sinclairOwner;
   protected int age;

   protected SinclairProjectile(EntityType<? extends SinclairProjectile> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(FROM_ASSISTANT, false);
   }

   public boolean fromAssistant() {
      return this.entityData.get(FROM_ASSISTANT);
   }

   public void setFromAssistant(boolean value) {
      this.entityData.set(FROM_ASSISTANT, value);
   }

   public void launch(ServerPlayer owner, Vec3 from, Vec3 velocity) {
      this.sinclairOwner = owner.getUUID();
      this.setOwner(owner);
      this.setPos(from);
      this.setDeltaMovement(velocity);
      ProjectileUtil.rotateTowardsMovement(this, 1.0F);
      this.xRotO = this.getXRot();
      this.yRotO = this.getYRot();
   }

   @Override
   @Nullable
   public UUID ownerId() {
      return this.sinclairOwner;
   }

   public int age() {
      return this.age;
   }

   protected abstract int maxAge();

   /** Called once when the lifetime runs out without a hit. */
   protected void onExpire() {
   }

   @Override
   public void tick() {
      super.tick();
      this.age++;
      if (!this.level().isClientSide) {
         ServerPlayer owner = SinclairOwned.liveOwner(this, this.sinclairOwner);
         if (owner == null) {
            this.discard();
            return;
         }

         this.steer(owner);
      }

      HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
      if (hit.getType() != HitResult.Type.MISS && !this.level().isClientSide && !ForgeEventFactory.onProjectileImpact(this, hit)) {
         this.onHit(hit);
         if (this.isRemoved()) {
            return;
         }
      }

      Vec3 motion = this.getDeltaMovement();
      this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
      ProjectileUtil.rotateTowardsMovement(this, 0.6F);
      if (!this.level().isClientSide && this.age >= this.maxAge()) {
         this.onExpire();
         this.discard();
      }
   }

   /** Server: change the course (bolt redirect, card homing). */
   protected void steer(ServerPlayer owner) {
   }

   @Override
   protected boolean canHitEntity(Entity target) {
      // A reflected projectile (shared HeroProjectiles REFLECT re-owns it) may hit Sinclair and his entities.
      Entity shooter = this.getOwner();
      UUID safe = shooter == null || shooter.getUUID().equals(this.sinclairOwner) ? this.sinclairOwner : shooter.getUUID();
      return super.canHitEntity(target) && !SinclairOwned.ownedBy(target, safe) && !(target instanceof HexMarkEntity)
         && !(target instanceof ActPropEntity) && !(target instanceof SinclairProjectile);
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   public boolean isPickable() {
      return false;
   }
}
