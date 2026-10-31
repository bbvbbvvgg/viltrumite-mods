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
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A city pedestrian: walks the prepared sidewalk network, idles, chats, and reacts to
 * danger (Living City 2.0: notices, flinches, runs, hides, falls, recovers; see
 * {@link CitizenBrain}). Never saved (the population manager refills the streets around
 * players), never an automatic hero target ({@link Bystander}), drops nothing. Its looks
 * and its personality both come from one synced seed ({@link CitizenLook},
 * {@link CitizenTraits}).
 */
public class CitizenEntity extends PathfinderMob implements Bystander {
   /** Walk speed modifier on the 0.25 base speed; fleeing runs {@link #FLEE_FACTOR} times faster. */
   public static final double WALK_SPEED = 0.55;
   public static final double FLEE_FACTOR = 1.75;
   /** Recovering people walk slower and look around more. */
   public static final double RECOVER_FACTOR = 0.8;
   private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.BYTE);

   /** Spawned by /city populate: exempt from density trimming. */
   public boolean manual;
   /** Ticks to stay idle before the next stroll. */
   int idleTicks;
   /** Set by damage: counts as standing inside a danger for a few seconds. */
   int hurtTicks;
   int offGridTicks;
   int stuckStrikes;
   int stateTicks;
   int calmTicks;
   int recoverTicks;
   int fallTicks;
   int fallCooldown;
   int legs;
   /** The danger this citizen reacts to (look at it, run away from it). */
   double threatX;
   double threatY;
   double threatZ;
   float threatRadius;
   private boolean threat;
   /** Where the last danger was: strolling avoids it for a while (cautious return). */
   double memX;
   double memZ;
   long memUntil = Long.MIN_VALUE;
   /** Destination of the current flight (crowd spreading counts these). */
   @Nullable
   BlockPos fleeTarget;
   @Nullable
   CitizenEntity talkPartner;
   /** A companion this citizen walks with (spawned as a small group), or null. */
   @Nullable
   CitizenEntity leader;
   @Nullable
   private CitizenTraits traits;
   @Nullable
   private CitizenLook look;
   private double lastX;
   private double lastZ;
   private int stillTicks;
   /** Client-only animation memory (state weights, gesture); opaque to the common side. */
   @Nullable
   public Object clientAnim;

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
         .add(Attributes.FOLLOW_RANGE, 48.0);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(SKIN, 0);
      this.entityData.define(STATE, (byte)CitizenState.IDLE.ordinal());
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new CitizenGoals.Panic(this));
      this.goalSelector.addGoal(2, new CitizenGoals.Alert(this));
      this.goalSelector.addGoal(3, new CitizenGoals.ReturnToGrid(this));
      this.goalSelector.addGoal(4, new CitizenGoals.Talk(this));
      this.goalSelector.addGoal(5, new CitizenGoals.Stroll(this));
      this.goalSelector.addGoal(6, new CitizenGoals.Gaze(this, 8.0F));
      this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
   }

   @Override
   protected PathNavigation createNavigation(Level level) {
      return new CitizenNavigation(this, level);
   }

   /** The appearance and personality seed (any int; synced). */
   public int skin() {
      return this.entityData.get(SKIN);
   }

   public void setSkin(int seed) {
      this.entityData.set(SKIN, seed);
      this.traits = null;
   }

   public CitizenTraits traits() {
      CitizenTraits t = this.traits;
      if (t == null) {
         t = CitizenTraits.of(this.skin());
         this.traits = t;
      }

      return t;
   }

   /** The decoded appearance (cached per seed). */
   public CitizenLook look() {
      CitizenLook l = this.look;
      int seed = this.skin();
      if (l == null || l.seed() != seed) {
         l = CitizenLook.of(seed);
         this.look = l;
      }

      return l;
   }

   /** Slim (3 px) arms: from the look. */
   public boolean slim() {
      return this.look().slim();
   }

   public CitizenState citizenState() {
      return CitizenState.of(this.entityData.get(STATE));
   }

   void setCitizenState(CitizenState s) {
      if (this.citizenState() != s) {
         this.entityData.set(STATE, (byte)s.ordinal());
         this.stateTicks = 0;
         if (s != CitizenState.FLEEING) {
            this.fleeTarget = null;
         }

         if (s != CitizenState.TALKING) {
            this.talkPartner = null;
         }
      }
   }

   public boolean hasThreat() {
      return this.threat;
   }

   void setThreat(double x, double y, double z, float radius) {
      this.threat = true;
      this.threatX = x;
      this.threatY = y;
      this.threatZ = z;
      this.threatRadius = radius;
   }

   void clearThreat() {
      this.threat = false;
   }

   /** Walks with this companion (spawned together as a small group). */
   public void leaderFollow(CitizenEntity lead) {
      if (lead != this && lead.leader != this) {
         this.leader = lead;
      }
   }

   void remember(double x, double z, long now) {
      this.memX = x;
      this.memZ = z;
      this.memUntil = now + CitizenBrain.MEMORY;
   }

   /** Walking speed modifier of this person (traits pace, slower while recovering). */
   double walkSpeed() {
      double v = WALK_SPEED * this.traits().pace();
      return this.citizenState() == CitizenState.RECOVERING ? v * RECOVER_FACTOR : v;
   }

   double runSpeed() {
      return WALK_SPEED * FLEE_FACTOR * (0.92 + this.traits().pace() * 0.12);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         return;
      }

      if (this.hurtTicks > 0) {
         this.hurtTicks--;
      }

      if (this.recoverTicks > 0) {
         this.recoverTicks--;
      }

      CityManager city = CityManager.of(this.level());
      if (city != null) {
         CitizenBrain.tick(this, city);
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
      if (!this.level().isClientSide && hurt) {
         CityManager city = CityManager.of(this.level());
         if (city != null) {
            // run away from whoever or whatever did it; a hurt person is also a sight for others
            net.minecraft.world.phys.Vec3 from = source.getSourcePosition();
            if (from == null && source.getEntity() != null) {
               from = source.getEntity().position();
            }

            if (from == null) {
               from = this.position();
            }

            city.danger(from.x, from.y, from.z, 12.0F, 200);
            if (this.isAlive()) {
               this.hurtTicks = 100;
               this.setThreat(from.x, from.y, from.z, 12.0F);
               CitizenState s = this.citizenState();
               if (s != CitizenState.FALLEN && s != CitizenState.GETTING_UP && s != CitizenState.FLEEING) {
                  this.legs = 0;
                  this.setCitizenState(CitizenState.FLEEING);
               }
            }
         }
      }

      return hurt;
   }

   @Override
   public void die(DamageSource source) {
      super.die(source);
      CityManager city = CityManager.of(this.level());
      if (city != null && !this.level().isClientSide) {
         // somebody died here: everybody around notices, the closest run
         city.dangerMap().add(this.getX(), this.getY(), this.getZ(), 10.0F, 36.0F, city.now(), 240);
      }
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
