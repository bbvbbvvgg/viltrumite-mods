package dev.baranhan.viltrumitecore.city.citizen;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.entity.Bystander;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A city pedestrian: walks the prepared sidewalk network, idles, and flees from danger
 * zones. Never saved (the population manager refills the streets around players), never
 * an automatic hero target ({@link Bystander}), drops nothing.
 */
public class CitizenEntity extends PathfinderMob implements Bystander {
   public static final int SKINS = CitizenSkinPainter.COUNT;
   /** Walk speed modifier on the 0.25 base speed; fleeing runs {@link #FLEE_FACTOR} times faster. */
   public static final double WALK_SPEED = 0.55;
   public static final double FLEE_FACTOR = 1.7;
   private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.BYTE);

   public enum State {
      WALKING,
      IDLE,
      FLEEING;

      static State of(int b) {
         return b >= 0 && b < values().length ? values()[b] : IDLE;
      }
   }

   /** Spawned by /city populate: exempt from density trimming. */
   public boolean manual;
   /** Ticks to stay idle before the next stroll. */
   int idleTicks;
   /** Set by damage: flee immediately on the next goal tick. */
   int panicTicks;
   int offGridTicks;
   int stuckStrikes;
   private double lastX;
   private double lastZ;
   private int stillTicks;

   public CitizenEntity(EntityType<? extends CitizenEntity> type, Level level) {
      super(type, level);
      this.setMaxUpStep(1.0F);
      this.setCanPickUpLoot(false);
      this.xpReward = 0;
   }

   public static AttributeSupplier.Builder createAttributes() {
      return PathfinderMob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 10.0)
         .add(Attributes.MOVEMENT_SPEED, 0.25)
         .add(Attributes.FOLLOW_RANGE, 40.0);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(SKIN, 0);
      this.entityData.define(STATE, (byte)State.IDLE.ordinal());
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new CitizenGoals.Flee(this));
      this.goalSelector.addGoal(2, new CitizenGoals.ReturnToGrid(this));
      this.goalSelector.addGoal(3, new CitizenGoals.Stroll(this));
      this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
      this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
   }

   @Override
   protected PathNavigation createNavigation(Level level) {
      return new CitizenNavigation(this, level);
   }

   public int skin() {
      return Math.floorMod(this.entityData.get(SKIN), SKINS);
   }

   public void setSkin(int skin) {
      this.entityData.set(SKIN, Math.floorMod(skin, SKINS));
   }

   /** Odd skins use the slim (3 px arm) model. */
   public boolean slim() {
      return (this.skin() & 1) == 1;
   }

   public State state() {
      return State.of(this.entityData.get(STATE));
   }

   void setState(State s) {
      if (this.state() != s) {
         this.entityData.set(STATE, (byte)s.ordinal());
      }
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         return;
      }

      if (this.panicTicks > 0) {
         this.panicTicks--;
      }

      // stuck detection: navigating but not moving for 3 s -> give up this path
      if (this.tickCount % 20 == 0) {
         double moved = Math.abs(this.getX() - this.lastX) + Math.abs(this.getZ() - this.lastZ);
         this.lastX = this.getX();
         this.lastZ = this.getZ();
         if (!this.getNavigation().isDone() && moved < 0.3) {
            this.stillTicks += 20;
            if (this.stillTicks >= 60) {
               this.getNavigation().stop();
               this.stillTicks = 0;
               this.stuckStrikes++;
            }
         } else {
            this.stillTicks = 0;
            if (moved > 2.0) {
               this.stuckStrikes = 0;
            }
         }

         if (this.stuckStrikes >= 5 || this.getY() < this.level().getMinBuildHeight()) {
            this.discard();
         }
      }
   }

   /** Feet on the pedestrian grid (or no city here: then anything goes). */
   public boolean onGrid() {
      CityManager city = CityManager.of(this.level());
      if (city == null) {
         return true;
      }

      BlockPos p = this.blockPosition();
      return city.grid().standable(p.getX(), p.getY(), p.getZ());
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      boolean hurt = super.hurt(source, amount);
      if (!this.level().isClientSide && this.isAlive()) {
         this.panicTicks = 100;
         CityManager city = CityManager.of(this.level());
         if (city != null) {
            city.danger(this.getX(), this.getY(), this.getZ(), 12.0F, 200);
         }
      }

      return hurt;
   }

   @Override
   public boolean removeWhenFarAway(double distanceSq) {
      return false; // the city population manager owns despawning
   }

   @Override
   public boolean requiresCustomPersistence() {
      return true;
   }

   @Override
   protected boolean shouldDropLoot() {
      return false;
   }

   @Override
   public boolean shouldDropExperience() {
      return false;
   }

   /** Never step down more than one block: bridge and terrace edges are not exits. */
   @Override
   public int getMaxFallDistance() {
      return 1;
   }

   @Override
   public boolean canBeLeashed(Player player) {
      return false;
   }

   @Override
   protected float getStandingEyeHeight(net.minecraft.world.entity.Pose pose, net.minecraft.world.entity.EntityDimensions dims) {
      return dims.height * 0.9F;
   }

   @Nullable
   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return SoundEvents.PLAYER_HURT;
   }

   @Nullable
   @Override
   protected SoundEvent getDeathSound() {
      return SoundEvents.PLAYER_DEATH;
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putInt("Skin", this.skin());
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.setSkin(tag.getInt("Skin"));
   }

   @Override
   public void onAddedToWorld() {
      super.onAddedToWorld();
      if (!this.level().isClientSide) {
         CityManager.track(this, true);
      }
   }

   @Override
   public void onRemovedFromWorld() {
      super.onRemovedFromWorld();
      if (!this.level().isClientSide) {
         CityManager.track(this, false);
      }
   }
}
