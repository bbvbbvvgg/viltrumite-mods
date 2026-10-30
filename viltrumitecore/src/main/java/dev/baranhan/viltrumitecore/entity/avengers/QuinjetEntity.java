package dev.baranhan.viltrumitecore.entity.avengers;

import dev.baranhan.viltrumitecore.hero.captain.assemble.AvengersTeam;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Quinjet fly-over (spec §5.7): a kinematic prop the scene moves along its path (no collision, never
 * saved, no rider logic). The ramp state and the thrust level are synced for the model.
 */
public class QuinjetEntity extends Entity {
   private static final EntityDataAccessor<Boolean> RAMP = SynchedEntityData.defineId(QuinjetEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Float> THRUST = SynchedEntityData.defineId(QuinjetEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(QuinjetEntity.class, EntityDataSerializers.INT);
   @Nullable private UUID ownerUuid;
   /** Server: life guard; the scene discards it on time, this catches a lost scene. */
   public int life = 260;
   public int clientRampStart = -1000;
   public boolean clientRamp;

   public QuinjetEntity(EntityType<? extends QuinjetEntity> type, Level level) {
      super(type, level);
      this.noPhysics = true;
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(RAMP, false);
      this.entityData.define(THRUST, 1.0F);
      this.entityData.define(OWNER, -1);
   }

   public void setOwner(ServerPlayer owner) {
      this.ownerUuid = owner.getUUID();
      this.entityData.set(OWNER, owner.getId());
   }

   public int ownerId() {
      return this.entityData.get(OWNER);
   }

   @Nullable
   public ServerPlayer owner() {
      if (this.ownerUuid == null || !(this.level() instanceof ServerLevel level)) {
         return null;
      }
      return level.getServer().getPlayerList().getPlayer(this.ownerUuid);
   }

   public boolean ramp() {
      return this.entityData.get(RAMP);
   }

   public void setRamp(boolean open) {
      if (this.ramp() != open) {
         this.entityData.set(RAMP, open);
      }
   }

   public float thrust() {
      return this.entityData.get(THRUST);
   }

   public void setThrust(float t) {
      this.entityData.set(THRUST, t);
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (RAMP.equals(key) && this.level().isClientSide && this.clientRamp != this.ramp()) {
         this.clientRamp = this.ramp();
         this.clientRampStart = this.tickCount;
      }
   }

   /** Server: kinematic step to the next path point, facing the travel direction. */
   public void moveTo(Vec3 at) {
      Vec3 d = at.subtract(this.position());
      this.setDeltaMovement(d);
      if (d.horizontalDistanceSqr() > 1.0E-3) {
         float yaw = (float)(Math.atan2(-d.x, d.z) * 180.0 / Math.PI);
         this.setYRot(yaw);
      }
      this.setXRot((float)(-Math.atan2(d.y, Math.max(0.05, d.horizontalDistance())) * 180.0 / Math.PI) * 0.5F);
      this.setPos(at);
   }

   @Override
   public void tick() {
      super.tick();
      if (!this.level().isClientSide && (--this.life <= 0 || this.tickCount % 20 == 0 && !AvengersTeam.member(this))) {
         this.discard(); // a lost scene
      } else if (!this.level().isClientSide && this.tickCount % 60 == 1) {
         // engines: one 3.8 s pass every three seconds keeps the roar continuous while it flies and hovers
         dev.baranhan.viltrumitecore.hero.captain.CaptainSounds.play(this, dev.baranhan.viltrumitecore.hero.captain.CaptainSounds.QUINJET, 3.0F, 1.0F);
      }
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
   public boolean shouldRenderAtSqrDistance(double distance) {
      return distance < 256 * 256;
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }
}
