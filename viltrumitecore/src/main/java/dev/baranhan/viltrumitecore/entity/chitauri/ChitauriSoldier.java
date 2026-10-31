package dev.baranhan.viltrumitecore.entity.chitauri;

import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.hero.SafeSpot;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriDestruction;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriInvasion;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriSounds;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriTerrain;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriNet;
import java.util.EnumSet;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Chitauri foot soldier. Ranged bursts after a visible charge, cover search by ray casts, bayonet
 * melee, squads share targets and spread on flanks, anti-stuck (repath, hop, safe spot). Rides
 * chariots as pilot, gunner or trooper (seat synced for the client). Never saved, no loot.
 */
public class ChitauriSoldier extends Monster implements ChitauriMob {
   private static final EntityDataAccessor<Byte> ROLE = SynchedEntityData.defineId(ChitauriSoldier.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> SEAT = SynchedEntityData.defineId(ChitauriSoldier.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(ChitauriSoldier.class, EntityDataSerializers.BYTE);

   public static final byte ROLE_TROOPER = 0, ROLE_PILOT = 1, ROLE_GUNNER = 2;
   public static final byte EVENT_SHOOT = 70, EVENT_MELEE = 71, EVENT_LAND = 72, EVENT_SEVER = 73;
   private static final int FLAG_AIM = 1, FLAG_CHARGE = 2, FLAG_SEVERED = 4;
   public static final int DEATH_TICKS = 70;

   public int squad = -1;
   public int squadIndex;
   private int severDelay = -1;
   private int stuckChecks;
   private Vec3 lastCheckPos = Vec3.ZERO;
   private int airTicks;

   // client presentation clocks
   public int clientShootStart = -1000;
   public int clientMeleeStart = -1000;
   public int clientLandStart = -1000;
   public int clientSeverStart = -1000;
   public int clientChargeStart = -1000;
   public int clientAirTicks;
   public float clientAim;
   public float clientAimO;
   public int clientShots;
   /** Client: smoothed ground speed (blocks/tick) and the gait phase (cycles) the walk/run clips follow. */
   public float clientMove;
   public float clientMoveO;
   public float clientGait;
   public float clientGaitO;
   /** Client: last rendered muzzle (world) and the tick it was taken; the shot flash starts there. */
   public net.minecraft.world.phys.Vec3 clientMuzzle;
   public int clientMuzzleTick = -1000;

   public ChitauriSoldier(EntityType<? extends ChitauriSoldier> type, Level level) {
      super(type, level);
      this.xpReward = 0;
      this.setMaxUpStep(1.0F);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 26.0)
         .add(Attributes.MOVEMENT_SPEED, 0.27)
         .add(Attributes.FOLLOW_RANGE, 40.0)
         .add(Attributes.ATTACK_DAMAGE, 6.0)
         .add(Attributes.ATTACK_KNOCKBACK, 0.6)
         .add(Attributes.ARMOR, 6.0)
         .add(Attributes.ARMOR_TOUGHNESS, 2.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(ROLE, ROLE_TROOPER);
      this.entityData.define(SEAT, (byte)-1);
      this.entityData.define(FLAGS, (byte)0);
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(2, new CombatGoal(this));
      this.goalSelector.addGoal(5, new RoamGoal(this));
      this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
      this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ChitauriSoldier.class).setAlertOthers());
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, ChitauriMob::validTarget));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, CitizenEntity.class, 10, true, false, ChitauriMob::validTarget));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, 10, true, false, ChitauriMob::validTarget));
      this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, IronGolem.class, 10, true, false, ChitauriMob::validTarget));
      this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.Mob.class, 10, true, false,
         e -> e instanceof net.minecraft.world.entity.Mob m && ChitauriMob.is(m.getTarget()) && ChitauriMob.validTarget(e)));
   }

   // ------------------------------------------------------------------ state

   public byte role() {
      return this.entityData.get(ROLE);
   }

   public void setRole(byte role) {
      this.entityData.set(ROLE, role);
   }

   public int seat() {
      return this.entityData.get(SEAT);
   }

   public void setSeat(int seat) {
      this.entityData.set(SEAT, (byte)seat);
   }

   private boolean flag(int f) {
      return (this.entityData.get(FLAGS) & f) != 0;
   }

   private void setFlag(int f, boolean on) {
      byte b = this.entityData.get(FLAGS);
      byte n = (byte)(on ? b | f : b & ~f);
      if (n != b) {
         this.entityData.set(FLAGS, n);
      }
   }

   public boolean aiming() {
      return this.flag(FLAG_AIM);
   }

   public boolean charging() {
      return this.flag(FLAG_CHARGE);
   }

   public boolean severed() {
      return this.flag(FLAG_SEVERED);
   }

   public boolean mounted() {
      return this.getVehicle() instanceof ChitauriChariot;
   }

   /** Pure: walk clip weight for a ground speed in blocks/tick (0 standing, 1 from 0.045). */
   public static float walkWeight(float speed) {
      return Mth.clamp((speed - 0.008F) / 0.037F, 0.0F, 1.0F);
   }

   /** Pure: run clip weight over the walk (soldiers sprint at 0.13+ blocks/tick). */
   public static float runWeight(float speed) {
      float t = Mth.clamp((speed - 0.095F) / 0.04F, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   /** Pure: blocks per full gait cycle (two steps) at a speed: the walk clip covers 1.7, the run 2.1. */
   public static float stride(float speed) {
      return Mth.lerp(runWeight(speed), 1.7F, 2.1F);
   }

   /** The hive link breaks (invasion closing): collapse after {@code delay} ticks. */
   public void sever(int delay) {
      if (this.severDelay < 0 && this.isAlive()) {
         this.severDelay = Math.max(1, delay);
      }
   }

   // ------------------------------------------------------------------ tick

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         this.clientAimO = this.clientAim;
         float want = this.aiming() || this.charging() ? 1.0F : 0.0F;
         this.clientAim += (want - this.clientAim) * 0.25F;
         if (!this.onGround() && !this.isPassenger() && this.getDeltaMovement().y < -0.08) {
            this.clientAirTicks++;
         } else {
            this.clientAirTicks = 0;
         }
         double dx = this.getX() - this.xo;
         double dz = this.getZ() - this.zo;
         float moved = this.isPassenger() || !this.isAlive() ? 0.0F : (float)Math.min(0.6, Math.sqrt(dx * dx + dz * dz));
         this.clientMoveO = this.clientMove;
         this.clientMove += (moved - this.clientMove) * 0.35F;
         this.clientGaitO = this.clientGait;
         this.clientGait += moved / stride(this.clientMove);
         if (this.clientGait > 1000.0F) {
            this.clientGait -= 1000.0F;
            this.clientGaitO -= 1000.0F;
         }
         return;
      }
      if (this.severDelay > 0 && --this.severDelay == 0) {
         this.setFlag(FLAG_SEVERED, true);
         this.level().broadcastEntityEvent(this, EVENT_SEVER);
         ChitauriSounds.play((ServerLevel)this.level(), this.position(), ChitauriSounds.SEVER, 0.6F, 1.1F + this.random.nextFloat() * 0.3F);
         this.kill();
         return;
      }
      if (this.getY() < this.level().getMinBuildHeight() - 16) {
         this.discard();
         return;
      }
      // landing after a drop (cushioned)
      if (!this.onGround() && !this.isPassenger()) {
         this.airTicks++;
      } else {
         if (this.airTicks > 6 && this.onGround()) {
            this.level().broadcastEntityEvent(this, EVENT_LAND);
            if (this.airTicks > 12) {
               ChitauriNet.fx((ServerLevel)this.level(), new ChitauriFxS2CPacket(ChitauriFxS2CPacket.DROP_LANDING, this.position(), Vec3.ZERO, 1.0F,
                  this.random.nextInt()), this.position(), 48);
            }
         }
         this.airTicks = 0;
      }
      if (this.tickCount % 20 == 0 && !this.isPassenger()) {
         this.antiStuck();
      }
   }

   /** Repath after 3 s without progress, hop after 5 s, safe spot after 8 s; far from everyone after 12 s: gone. */
   private void antiStuck() {
      Vec3 now = this.position();
      boolean trying = !this.getNavigation().isDone();
      if (trying && now.distanceToSqr(this.lastCheckPos) < 0.09) {
         this.stuckChecks++;
      } else {
         this.stuckChecks = Math.max(0, this.stuckChecks - 1);
      }
      this.lastCheckPos = now;
      if (this.stuckChecks == 3) {
         this.getNavigation().stop();
      } else if (this.stuckChecks == 5 && this.onGround()) {
         float a = this.random.nextFloat() * Mth.TWO_PI;
         this.setDeltaMovement(Mth.cos(a) * 0.3, 0.45, Mth.sin(a) * 0.3);
         this.hasImpulse = true;
      } else if (this.stuckChecks == 8) {
         Optional<Vec3> spot = SafeSpot.find(now, new SafeSpot.LevelProbe(this.level(), this), 5.0, 1.0);
         spot.ifPresent(v -> this.teleportTo(v.x, v.y, v.z));
      } else if (this.stuckChecks >= 12) {
         Player near = this.level().getNearestPlayer(this, 48.0);
         if (near == null) {
            this.discard();
         }
         this.stuckChecks = 0;
      }
   }

   // ------------------------------------------------------------------ combat

   /** Muzzle of the rifle (held at the right shoulder when aiming). */
   public Vec3 muzzle() {
      Vec3 look = this.getViewVector(1.0F);
      float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
      Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
      return this.getEyePosition().add(look.scale(1.05)).add(right.scale(0.28)).add(0, -0.22, 0);
   }

   void fireAt(LivingEntity target) {
      ServerLevel level = (ServerLevel)this.level();
      Vec3 from = this.muzzle();
      Vec3 aim = target.position().add(0, target.getBbHeight() * 0.55, 0);
      double dist = from.distanceTo(aim);
      double flight = dist / 2.4;
      aim = aim.add(target.getDeltaMovement().multiply(1.0, 0.3, 1.0).scale(flight * 0.8));
      float spread = (float)(1.6 + dist * 0.06) * (this.mounted() ? 2.0F : 1.0F);
      ChitauriBolt.shoot(level, this, from, aim.subtract(from), 2.4, 1.0F, spread);
      this.level().broadcastEntityEvent(this, EVENT_SHOOT);
      ChitauriSounds.play(level, from, ChitauriSounds.RIFLE_FIRE, 2.0F, 0.92F + this.random.nextFloat() * 0.16F);
      ChitauriDestruction.danger(level, this.position(), 14.0F, 80);
   }

   void melee(LivingEntity target) {
      this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
      this.level().broadcastEntityEvent(this, EVENT_MELEE);
      ChitauriSounds.play((ServerLevel)this.level(), this.position(), ChitauriSounds.SOLDIER_MELEE, 0.9F, 0.9F + this.random.nextFloat() * 0.2F);
      this.doHurtTarget(target);
   }

   @Override
   public void setTarget(@Nullable LivingEntity target) {
      LivingEntity old = this.getTarget();
      super.setTarget(target);
      if (target != null && target != old && this.squad >= 0 && !this.level().isClientSide) {
         // squad mates without a target take this one
         for (ChitauriSoldier mate : this.level().getEntitiesOfClass(ChitauriSoldier.class, this.getBoundingBox().inflate(32.0),
            m -> m != this && m.squad == this.squad && m.getTarget() == null && m.isAlive())) {
            mate.setTargetQuiet(target);
         }
      }
   }

   private void setTargetQuiet(LivingEntity target) {
      super.setTarget(target);
   }

   /** Best firing spot around {@code target} for this soldier's flank (null: none found). */
   @Nullable
   Vec3 pickSpot(LivingEntity target) {
      Level level = this.level();
      Vec3 tp = target.position();
      Vec3 me = this.position();
      double bearing = Math.atan2(me.z - tp.z, me.x - tp.x) + CoverScore.flankOffset(this.squadIndex) * 0.6;
      Vec3 targetEye = target.getEyePosition();
      Vec3 targetChest = tp.add(0, target.getBbHeight() * 0.5, 0);
      Vec3 best = null;
      double bestScore = -1.0E9;
      for (int i = 0; i < 9; i++) {
         double a = bearing + (this.random.nextDouble() - 0.5) * 1.6;
         double r = CoverScore.PREFERRED_MIN + this.random.nextDouble() * (CoverScore.PREFERRED_MAX - CoverScore.PREFERRED_MIN);
         double x = tp.x + Math.cos(a) * r;
         double z = tp.z + Math.sin(a) * r;
         int y = standY(level, x, z, Mth.floor(me.y));
         if (y == ChitauriTerrain.UNKNOWN) {
            continue;
         }
         Vec3 spot = new Vec3(Mth.floor(x) + 0.5, y, Mth.floor(z) + 0.5);
         boolean covered = !ChitauriTerrain.clear(level, spot.add(0, 0.7, 0), targetChest);
         boolean eye = ChitauriTerrain.clear(level, spot.add(0, 1.75, 0), targetEye);
         double off = Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(spot.z - tp.z, spot.x - tp.x) - bearing)) * Mth.DEG_TO_RAD;
         double score = CoverScore.score(covered, eye, spot.distanceTo(tp), spot.distanceTo(me), off);
         if (score > bestScore) {
            bestScore = score;
            best = spot;
         }
      }
      return best;
   }

   /** A standable Y (solid below, two free above) near {@code aroundY} at x/z, or UNKNOWN. */
   static int standY(Level level, double x, double z, int aroundY) {
      if (!ChitauriTerrain.loaded(level, x, z)) {
         return ChitauriTerrain.UNKNOWN;
      }
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      int bx = Mth.floor(x), bz = Mth.floor(z);
      for (int dy = 4; dy >= -6; dy--) {
         int y = aroundY + dy;
         pos.set(bx, y - 1, bz);
         BlockState below = level.getBlockState(pos);
         if (!below.blocksMotion() || !below.getFluidState().isEmpty()) {
            continue;
         }
         pos.set(bx, y, bz);
         if (level.getBlockState(pos).blocksMotion()) {
            continue;
         }
         pos.set(bx, y + 1, bz);
         if (level.getBlockState(pos).blocksMotion()) {
            continue;
         }
         return y;
      }
      return ChitauriTerrain.UNKNOWN;
   }

   // ------------------------------------------------------------------ events, sounds, damage

   @Override
   public void handleEntityEvent(byte id) {
      switch (id) {
         case EVENT_SHOOT -> {
            this.clientShootStart = this.tickCount;
            this.clientShots++;
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
               () -> () -> dev.baranhan.viltrumitecore.client.chitauri.ChitauriClientHooks.soldierShot(this));
         }
         case EVENT_MELEE -> this.clientMeleeStart = this.tickCount;
         case EVENT_LAND -> this.clientLandStart = this.tickCount;
         case EVENT_SEVER -> this.clientSeverStart = this.tickCount;
         default -> super.handleEntityEvent(id);
      }
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (FLAGS.equals(key) && this.level().isClientSide && this.charging() && this.tickCount - this.clientChargeStart > 10) {
         this.clientChargeStart = this.tickCount;
      }
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (ChitauriMob.is(source.getEntity()) || ChitauriMob.is(source.getDirectEntity())) {
         return false; // no friendly fire
      }
      return super.hurt(source, amount);
   }

   @Override
   public void die(DamageSource source) {
      super.die(source);
      if (!this.level().isClientSide) {
         ChitauriInvasion.onKilled(this, source);
      }
   }

   @Override
   protected void tickDeath() {
      this.deathTime++;
      if (this.deathTime >= DEATH_TICKS && !this.level().isClientSide && !this.isRemoved()) {
         this.remove(Entity.RemovalReason.KILLED);
      }
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return ChitauriSounds.SOLDIER_CHATTER.get();
   }

   @Override
   public int getAmbientSoundInterval() {
      return 160;
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return ChitauriSounds.SOLDIER_HURT.get();
   }

   @Override
   protected SoundEvent getDeathSound() {
      return ChitauriSounds.SOLDIER_DEATH.get();
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState state) {
      this.playSound(ChitauriSounds.SOLDIER_STEP.get(), 0.35F, 0.9F + this.random.nextFloat() * 0.2F);
   }

   @Override
   public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
      return super.causeFallDamage(Math.max(0.0F, distance - 5.0F), multiplier * 0.5F, source);
   }

   @Override
   public boolean isAlliedTo(Entity other) {
      return ChitauriMob.is(other) || super.isAlliedTo(other);
   }

   @Override
   public boolean shouldRiderSit() {
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
   protected boolean shouldDespawnInPeaceful() {
      return false;
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
      super.addAdditionalSaveData(tag);
   }

   // ------------------------------------------------------------------ goals

   /** Ranged bursts with a charge, cover / flank moves, melee up close; mounted: fire from the seat. */
   static final class CombatGoal extends Goal {
      private final ChitauriSoldier s;
      private int repick;
      private int charge;
      private int burst;
      private int gap;
      private int cooldown;
      private int meleeCooldown;
      private int lostSight;

      CombatGoal(ChitauriSoldier s) {
         this.s = s;
         this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
      }

      @Override
      public boolean canUse() {
         LivingEntity t = this.s.getTarget();
         return t != null && ChitauriMob.validTarget(t) && !this.s.severed() && !(this.s.mounted() && this.s.role() == ROLE_PILOT);
      }

      @Override
      public boolean canContinueToUse() {
         return this.canUse();
      }

      @Override
      public void start() {
         this.repick = 0;
         this.cooldown = 10 + this.s.random.nextInt(25);
         this.lostSight = 0;
      }

      @Override
      public void stop() {
         this.s.getNavigation().stop();
         this.s.setFlag(FLAG_AIM, false);
         this.s.setFlag(FLAG_CHARGE, false);
         this.charge = 0;
         this.burst = 0;
      }

      @Override
      public boolean requiresUpdateEveryTick() {
         return true;
      }

      @Override
      public void tick() {
         LivingEntity t = this.s.getTarget();
         if (t == null) {
            return;
         }
         double d = this.s.distanceTo(t);
         boolean see = this.s.getSensing().hasLineOfSight(t);
         this.s.getLookControl().setLookAt(t, 40.0F, 40.0F);
         this.lostSight = see ? 0 : this.lostSight + 1;
         boolean mounted = this.s.isPassenger();
         if (this.meleeCooldown > 0) {
            this.meleeCooldown--;
         }
         if (!mounted) {
            if (d < 2.4) {
               this.s.getNavigation().stop();
               if (this.meleeCooldown <= 0) {
                  this.s.melee(t);
                  this.meleeCooldown = 22;
               }
            } else if (d > 32.0 || this.lostSight > 60) {
               if (this.s.tickCount % 10 == 0) {
                  this.s.getNavigation().moveTo(t, 1.15);
               }
            } else if (--this.repick <= 0 || this.lostSight == 30) {
               Vec3 spot = this.s.pickSpot(t);
               this.repick = 70 + this.s.random.nextInt(50);
               if (spot != null) {
                  this.s.getNavigation().moveTo(spot.x, spot.y, spot.z, d > 18.0 ? 1.15 : 0.95);
               }
            }
         }
         boolean aim = see && d < (mounted ? 44.0 : 36.0) && d >= 2.4;
         this.s.setFlag(FLAG_AIM, aim);
         if (this.charge > 0) {
            if (--this.charge == 0) {
               this.burst = 2 + this.s.random.nextInt(3);
               this.gap = 0;
            }
         } else if (this.burst > 0) {
            if (--this.gap <= 0) {
               if (see) {
                  this.s.fireAt(t);
               }
               this.burst--;
               this.gap = 4;
               if (this.burst == 0) {
                  this.cooldown = 26 + this.s.random.nextInt(34);
                  this.s.setFlag(FLAG_CHARGE, false);
               }
            }
         } else if (aim && --this.cooldown <= 0) {
            this.charge = 12;
            this.s.setFlag(FLAG_CHARGE, true);
            ChitauriSounds.play((ServerLevel)this.s.level(), this.s.position(), ChitauriSounds.RIFLE_CHARGE, 0.7F, 0.95F + this.s.random.nextFloat() * 0.1F);
         }
      }
   }

   /** No target: patrol the streets, drifting back towards the invasion anchor when far. */
   static final class RoamGoal extends Goal {
      private final ChitauriSoldier s;
      private Vec3 goal;

      RoamGoal(ChitauriSoldier s) {
         this.s = s;
         this.setFlags(EnumSet.of(Goal.Flag.MOVE));
      }

      @Override
      public boolean canUse() {
         if (this.s.getTarget() != null || this.s.isPassenger() || this.s.severed() || this.s.random.nextInt(40) != 0) {
            return false;
         }
         Vec3 anchor = ChitauriInvasion.anchor(this.s.level());
         Vec3 pos;
         if (anchor != null && anchor.distanceToSqr(this.s.position()) > 60.0 * 60.0) {
            pos = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosTowards(this.s, 20, 6, anchor, Math.PI / 2.0);
         } else {
            pos = DefaultRandomPos.getPos(this.s, 18, 6);
         }
         this.goal = pos;
         return pos != null;
      }

      @Override
      public boolean canContinueToUse() {
         return this.s.getTarget() == null && !this.s.getNavigation().isDone() && !this.s.isPassenger();
      }

      @Override
      public void start() {
         this.s.getNavigation().moveTo(this.goal.x, this.goal.y, this.goal.z, 0.85);
      }
   }
}
