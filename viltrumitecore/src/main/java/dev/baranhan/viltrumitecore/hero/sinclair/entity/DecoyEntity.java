package dev.baranhan.viltrumitecore.hero.sinclair.entity;

import dev.baranhan.viltrumitecore.hero.sinclair.Ovation;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairCombat;
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
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Misdirection decoy (spec §5.5): Sinclair's own model in the "running magician" pose, keeps the owner's
 * horizontal course at 0.26 blocks/tick for 50 ticks, steps up one block, stops at a wall, falls off
 * ledges. Any hit or the end of its time reveals it: a burst of cards and doves that dazzles enemies in 3.
 */
public class DecoyEntity extends PathfinderMob implements SinclairOwned.Owned {
   private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(DecoyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
   private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(DecoyEntity.class, EntityDataSerializers.INT);
   /** Tick count of the reveal, -1 while running (client plays the card/dove burst). */
   private static final EntityDataAccessor<Integer> REVEAL = SynchedEntityData.defineId(DecoyEntity.class, EntityDataSerializers.INT);
   /** Movement attribute giving ~0.26 blocks/tick on flat ground (vanilla: ~2.16 b/t per unit). */
   private static final double WALK_ATTRIBUTE = SinclairRules.DECOY_SPEED / 2.16;
   public static final int REVEAL_TICKS = 12;
   private float course;
   private int life;

   public DecoyEntity(EntityType<? extends DecoyEntity> type, Level level) {
      super(type, level);
      this.xpReward = 0;
      this.setMaxUpStep(1.0F);
      this.moveControl = new MoveControl(this) {
         @Override
         public void tick() {
         }
      };
   }

   public static AttributeSupplier.Builder createAttributes() {
      return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 1.0).add(Attributes.MOVEMENT_SPEED, WALK_ATTRIBUTE)
         .add(Attributes.FOLLOW_RANGE, 0.0);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(OWNER, Optional.empty());
      this.entityData.define(OWNER_ID, -1);
      this.entityData.define(REVEAL, -1);
   }

   public void setup(ServerPlayer owner, float course) {
      this.entityData.set(OWNER, Optional.of(owner.getUUID()));
      this.entityData.set(OWNER_ID, owner.getId());
      this.course = course;
      this.moveTo(owner.getX(), owner.getY(), owner.getZ(), course, 0.0F);
      this.yBodyRot = course;
      this.yHeadRot = owner.getYHeadRot();
      this.setDeltaMovement(owner.getDeltaMovement().multiply(1.0, 0.0, 1.0));
   }

   @Override
   @Nullable
   public UUID ownerId() {
      return this.entityData.get(OWNER).orElse(null);
   }

   public int ownerEntityId() {
      return this.entityData.get(OWNER_ID);
   }

   public int revealTick() {
      return this.entityData.get(REVEAL);
   }

   public boolean revealed() {
      return this.revealTick() >= 0;
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         return;
      }

      ServerPlayer owner = SinclairOwned.liveOwner(this, this.ownerId());
      if (owner == null) {
         this.discard();
         return;
      }

      if (this.revealed()) {
         this.setZza(0.0F);
         if (this.tickCount - this.revealTick() >= REVEAL_TICKS) {
            this.discard();
         }

         return;
      }

      this.life++;
      this.setYRot(this.course);
      this.yBodyRot = this.course;
      this.yHeadRot = this.course;
      if (this.horizontalCollision && this.onGround() && !this.canStepOn()) {
         this.setZza(0.0F);
      } else {
         this.setSpeed((float) WALK_ATTRIBUTE);
         this.setZza(1.0F);
      }

      if (this.life >= SinclairRules.DECOY_TICKS) {
         this.reveal(owner);
      }
   }

   private boolean canStepOn() {
      // horizontalCollision stays true when a 2-high wall blocks; a single step is climbed by maxUpStep.
      return false;
   }

   /** §5.5: cards and doves, dazzle enemies in 3, Ovation when an enemy stood next to it. */
   public void reveal(@Nullable ServerPlayer owner) {
      if (this.revealed() || this.level().isClientSide) {
         return;
      }

      this.entityData.set(REVEAL, this.tickCount);
      this.setInvulnerable(true);
      Vec3 at = this.position().add(0.0, 1.0, 0.0);
      SinclairSounds.play(this.level(), at, SinclairSounds.DECOY_REVEAL, 1.0F, 1.0F);
      if (owner != null) {
         boolean any = false;
         for (LivingEntity target : SinclairCombat.enemiesNear(owner, at, SinclairRules.DECOY_REVEAL_RADIUS)) {
            SinclairCombat.daze(target);
            any = true;
         }

         if (any) {
            Ovation.grant(owner);
         }
      }
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      Entity attacker = source.getEntity();
      if (this.revealed() || attacker != null && SinclairOwned.ownedBy(attacker, this.ownerId())) {
         return false;
      }

      if (!this.level().isClientSide) {
         this.reveal(SinclairOwned.liveOwner(this, this.ownerId()));
      }

      return false;
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
}
