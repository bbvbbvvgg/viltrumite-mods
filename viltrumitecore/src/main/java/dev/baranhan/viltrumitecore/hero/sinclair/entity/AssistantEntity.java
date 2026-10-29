package dev.baranhan.viltrumitecore.hero.sinclair.entity;

import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairSounds;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Savannah, the spectral assistant (spec §5.2, §5.3). Hovers 0.3 above the floor at her spot, faces her
 * owner's aim point, mirrors his sparks and bolts, swaps places with him. Her animation state and its
 * start tick are synced data; every view plays the same clip from them. No loot, xp or vanilla sound.
 */
public class AssistantEntity extends PathfinderMob implements SinclairOwned.Owned {
   public static final int APPEAR = 0;
   public static final int IDLE = 1;
   public static final int ATTACK = 2;
   public static final int CAST = 3;
   public static final int SWAP_OUT = 4;
   public static final int SWAP_IN = 5;
   public static final int ACT = 6;
   public static final int HURT = 7;
   public static final int LEAVE = 8;
   public static final int DEATH = 9;
   public static final int BOW = 10;
   private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(AssistantEntity.class, EntityDataSerializers.OPTIONAL_UUID);
   private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(AssistantEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> STATE_START = SynchedEntityData.defineId(AssistantEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(AssistantEntity.class, EntityDataSerializers.INT);
   private int life;
   private int stateTimer;
   @Nullable
   private Vec3 anchor;

   public AssistantEntity(EntityType<? extends AssistantEntity> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
      this.xpReward = 0;
      this.moveControl = new MoveControl(this) {
         @Override
         public void tick() {
         }
      };
   }

   public static AttributeSupplier.Builder createAttributes() {
      return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, SinclairRules.ASSIST_HEALTH).add(Attributes.MOVEMENT_SPEED, 0.0)
         .add(Attributes.FOLLOW_RANGE, 0.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(OWNER, Optional.empty());
      this.entityData.define(STATE, APPEAR);
      this.entityData.define(STATE_START, 0);
      this.entityData.define(OWNER_ID, -1);
   }

   public void setup(ServerPlayer owner, Vec3 at) {
      this.entityData.set(OWNER, Optional.of(owner.getUUID()));
      this.entityData.set(OWNER_ID, owner.getId());
      this.anchor = at;
      this.moveTo(at.x, at.y, at.z, owner.getYRot() + 180.0F, 0.0F);
      this.setHealth(SinclairRules.ASSIST_HEALTH);
      this.setState(APPEAR, SinclairRules.ASSIST_APPEAR_TICKS);
   }

   @Override
   @Nullable
   public UUID ownerId() {
      return this.entityData.get(OWNER).orElse(null);
   }

   public int ownerEntityId() {
      return this.entityData.get(OWNER_ID);
   }

   public int state() {
      return this.entityData.get(STATE);
   }

   /** Tick count at the state's start (client clips run from tickCount - start). */
   public int stateStart() {
      return this.entityData.get(STATE_START);
   }

   public int life() {
      return this.life;
   }

   public boolean leaving() {
      int state = this.state();
      return state == LEAVE || state == DEATH;
   }

   public boolean onStage() {
      return this.isAlive() && !this.leaving() && this.state() != APPEAR;
   }

   public void setState(int state, int ticks) {
      this.entityData.set(STATE, state);
      this.entityData.set(STATE_START, this.tickCount);
      this.stateTimer = ticks;
   }

   /** Teleport for the swap; the floor check keeps the hover height. */
   public void relocate(Vec3 at) {
      this.anchor = at;
      this.teleportTo(at.x, at.y, at.z);
      this.setDeltaMovement(Vec3.ZERO);
   }

   public void leave() {
      if (!this.leaving() && this.isAlive()) {
         this.setState(LEAVE, SinclairRules.ASSIST_LEAVE_TICKS);
         SinclairSounds.play(this.level(), this.position().add(0.0, 1.0, 0.0), SinclairSounds.ASSISTANT_LEAVE, 0.9F, 1.0F);
      }
   }

   /** One gesture (attack, cast, act, bow) unless she is busy leaving or swapping. */
   public void gesture(int state, int ticks) {
      int now = this.state();
      if (!this.leaving() && now != SWAP_OUT && now != SWAP_IN && now != APPEAR) {
         this.setState(state, ticks);
      }
   }

   /** Muzzle point of her gloved hand. */
   public Vec3 handPoint() {
      Vec3 look = Vec3.directionFromRotation(0.0F, this.getYRot());
      Vec3 side = new Vec3(-look.z, 0.0, look.x);
      return this.position().add(0.0, 1.25, 0.0).add(look.scale(0.45)).add(side.scale(0.25));
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         return;
      }

      ServerPlayer owner = SinclairOwned.liveOwner(this, this.ownerId());
      this.life++;
      if (this.isDeadOrDying()) {
         return;
      }

      if (!this.leaving() && (owner == null || this.life >= SinclairRules.ASSIST_LIFE
         || owner.distanceToSqr(this) > SinclairRules.ASSIST_LEASH * SinclairRules.ASSIST_LEASH)) {
         this.leave();
      }

      if (this.stateTimer > 0 && --this.stateTimer == 0) {
         int state = this.state();
         if (state == LEAVE) {
            this.discard();
            return;
         }

         if (state != DEATH && state != IDLE) {
            this.setState(IDLE, 0);
         }
      }

      this.hover();
      if (owner != null && !this.leaving()) {
         Vec3 aim = aimPoint(owner);
         Vec3 to = aim.subtract(this.getEyePosition());
         float yaw = (float) (Math.toDegrees(Math.atan2(-to.x, to.z)));
         float pitch = (float) (-Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z))));
         this.setYRot(yaw);
         this.yBodyRot = yaw;
         this.yHeadRot = yaw;
         this.setXRot(pitch);
      }
   }

   private void hover() {
      Vec3 base = this.anchor != null ? this.anchor : this.position();
      BlockHitResult floor = this.level().clip(new ClipContext(base.add(0.0, 0.5, 0.0), base.add(0.0, -4.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
      double floorY = floor.getType() == HitResult.Type.MISS ? base.y : floor.getLocation().y;
      double wantY = floorY + SinclairRules.ASSIST_HOVER;
      double y = this.getY() + (wantY - this.getY()) * 0.35;
      this.setPos(base.x, y, base.z);
      this.setDeltaMovement(Vec3.ZERO);
   }

   /** The owner's crosshair point (48 blocks or the first block). */
   public static Vec3 aimPoint(ServerPlayer owner) {
      Vec3 eye = owner.getEyePosition();
      Vec3 end = eye.add(owner.getLookAngle().scale(SinclairRules.SPARK_RANGE));
      BlockHitResult hit = owner.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
      return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      Entity attacker = source.getEntity();
      if (attacker != null && SinclairOwned.ownedBy(attacker, this.ownerId())) {
         return false;
      }

      boolean hurt = super.hurt(source, amount);
      if (hurt && this.isAlive() && !this.leaving()) {
         this.gesture(HURT, 8);
      }

      return hurt;
   }

   @Override
   public void die(DamageSource source) {
      if (!this.level().isClientSide && this.state() != DEATH) {
         this.setState(DEATH, 20);
         SinclairSounds.play(this.level(), this.position().add(0.0, 1.0, 0.0), SinclairSounds.ASSISTANT_LEAVE, 1.0F, 0.75F);
      }

      super.die(source);
   }

   /** Vanilla death poof particles are replaced by her own ribbon scatter (client renderer). */
   @Override
   protected void tickDeath() {
      this.deathTime++;
      if (this.deathTime >= 20 && !this.level().isClientSide && !this.isRemoved()) {
         this.remove(RemovalReason.KILLED);
      }
   }

   @Override
   protected boolean shouldDropLoot() {
      return false;
   }

   @Override
   public boolean shouldDropExperience() {
      return false;
   }

   @Override
   @Nullable
   protected SoundEvent getAmbientSound() {
      return null;
   }

   @Override
   @Nullable
   protected SoundEvent getHurtSound(DamageSource source) {
      return null;
   }

   @Override
   @Nullable
   protected SoundEvent getDeathSound() {
      return null;
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState state) {
   }

   @Override
   public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
      return false;
   }

   @Override
   protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   protected void doPush(Entity entity) {
   }

   @Override
   public boolean removeWhenFarAway(double distance) {
      return false;
   }

   @Override
   public MobType getMobType() {
      return MobType.UNDEFINED;
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) {
      return false;
   }

   @Override
   public boolean isAlliedTo(Entity other) {
      UUID owner = this.ownerId();
      return owner != null && SinclairOwned.ownedBy(other, owner) || super.isAlliedTo(other);
   }

   /** A living helper never counts as a target of her owner's tricks. */
   public boolean ownedBy(LivingEntity entity) {
      return entity != null && entity.getUUID().equals(this.ownerId());
   }
}
