package dev.baranhan.viltrumitecore.entity.chitauri;

import dev.baranhan.viltrumitecore.balance.BalanceTable;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriDestruction;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriInvasion;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriSounds;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriTerrain;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriNet;
import javax.annotation.Nullable;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Chitauri air interceptor: a soldier strapped into a winged jet harness. It hunts flying heroes first (lead pursuit
 * from behind, twin blaster volleys), strafes ground targets in diving runs otherwise, rolls aside when a hero closes
 * in or hits it, and between fights perches on roofs and takes off again. Steering has a capped turn rate and
 * acceleration (banked turns, no instant reversal); the synced flight state drives the clips (take-off, cruise, boost,
 * brake, hover, roll, perch, shot-down fall). Never saved; never flies into unloaded chunks.
 */
public class ChitauriInterceptor extends Mob implements ChitauriMob, net.minecraft.world.entity.monster.Enemy {
   private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(ChitauriInterceptor.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(ChitauriInterceptor.class, EntityDataSerializers.FLOAT);
   public static final byte CRUISE = 0, HOVER = 1, BOOST = 2, BRAKE = 3, ROLLING = 4, PERCH = 5, TAKEOFF = 6, FALLING = 7;
   public static final byte EVENT_SHOOT = 70;
   public static final double CRUISE_SPEED = 0.55, CHASE_SPEED = 0.95, MAX_ACCEL = 0.07;
   public static final float MAX_TURN = 8.0F;
   public static final int DEATH_TICKS = 60;

   @Nullable
   private LivingEntity target;
   @Nullable
   private Vec3 waypoint;
   @Nullable
   private Vec3 perchAt;
   @Nullable
   private Vec3 orderPoint;
   private int stateTicks;
   private int idleTicks;
   private int fireCooldown;
   private int rollCooldown;
   private Vec3 rollDir = Vec3.ZERO;
   private float roll;
   private float spin;
   private double azimuth;
   private int stage;

   public int clientShootStart = -1000;
   public float clientRoll;
   public float clientRollO;
   public float clientSpeed;
   public float clientSpeedO;
   public float clientAccel;
   public int clientStateStart;
   public byte clientState = -1;

   public ChitauriInterceptor(EntityType<? extends ChitauriInterceptor> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
      this.xpReward = 0;
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, BalanceTable.CHITAURI_INTERCEPTOR_HEALTH)
         .add(Attributes.ARMOR, 2.0)
         .add(Attributes.FOLLOW_RANGE, 64.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
         .add(Attributes.FLYING_SPEED, 0.9);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(STATE, CRUISE);
      this.entityData.define(ROLL, 0.0F);
   }

   public byte state() {
      return this.entityData.get(STATE);
   }

   private void setState(byte s) {
      if (this.state() != s) {
         this.entityData.set(STATE, s);
         this.stateTicks = 0;
      }
   }

   public float syncedRoll() {
      return this.entityData.get(ROLL);
   }

   public void setOrderPoint(@Nullable Vec3 p) {
      this.orderPoint = p;
   }

   /** Launch out of the portal (or after a spawn command on the ground: a take-off). */
   public void launch(Vec3 dir) {
      this.setDeltaMovement(dir.normalize().scale(0.6));
      this.setYRot((float)(Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG));
      this.yBodyRot = this.getYRot();
      this.setState(CRUISE);
   }

   public void takeOff() {
      this.setState(TAKEOFF);
   }

   // ------------------------------------------------------------------ movement

   @Override
   public void travel(Vec3 input) {
      if (this.isControlledByLocalInstance()) {
         Vec3 before = this.getDeltaMovement();
         this.move(MoverType.SELF, before);
         if (!this.level().isClientSide && (this.horizontalCollision || this.verticalCollision)) {
            if (this.state() == FALLING) {
               this.crash();
               return;
            }
            if (this.state() != PERCH) {
               this.setDeltaMovement(before.scale(-0.25).add(0, 0.2, 0));
               this.waypoint = null;
            }
         }
      }
      this.calculateEntityAnimation(false);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         this.clientRollO = this.clientRoll;
         this.clientRoll += (this.syncedRoll() - this.clientRoll) * 0.3F;
         float sp = (float)new Vec3(this.getX() - this.xo, this.getY() - this.yo, this.getZ() - this.zo).length();
         this.clientSpeedO = this.clientSpeed;
         this.clientSpeed += (sp - this.clientSpeed) * 0.3F;
         this.clientAccel += ((this.clientSpeed - this.clientSpeedO) * 20.0F - this.clientAccel) * 0.2F;
         if (this.state() != this.clientState) {
            this.clientState = this.state();
            this.clientStateStart = this.tickCount;
         }
      }
   }

   @Override
   protected void customServerAiStep() {
      ServerLevel level = (ServerLevel)this.level();
      this.stateTicks++;
      if (this.fireCooldown > 0) {
         this.fireCooldown--;
      }
      if (this.rollCooldown > 0) {
         this.rollCooldown--;
      }
      if (this.state() == FALLING || ChitauriMob.heldByHero(this)) {
         return;
      }
      Vec3 pos = this.position().add(0, 0.5, 0);
      Vec3 anchor = ChitauriInvasion.anchor(level);
      Vec3 home = this.orderPoint != null ? this.orderPoint : anchor != null ? anchor : pos;
      if (this.tickCount % 10 == 0) {
         this.target = this.pickTarget(level);
      }
      byte st = this.state();
      Vec3 goal;
      double speed = CRUISE_SPEED;
      double accel = MAX_ACCEL;
      if (st == PERCH) {
         this.setDeltaMovement(Vec3.ZERO);
         if (this.target != null || this.stateTicks > 220) {
            this.setState(TAKEOFF);
            ChitauriSounds.play(level, this.position(), ChitauriSounds.INTERCEPTOR_BOOST, 1.4F, 1.1F);
         }
         this.orient(Vec3.ZERO, this.target != null ? this.target.position().subtract(pos) : null);
         return;
      }
      if (st == TAKEOFF) {
         this.setDeltaMovement(this.getDeltaMovement().scale(0.8).add(0, 0.09, 0));
         this.orient(this.getDeltaMovement(), this.target != null ? this.target.position().subtract(pos) : null);
         if (this.stateTicks > 16) {
            this.setState(CRUISE);
         }
         return;
      }
      if (st == ROLLING) {
         goal = pos.add(this.rollDir.scale(10.0));
         speed = CHASE_SPEED;
         accel = 0.14;
         this.roll += 26.0F * Math.signum(this.spin == 0.0F ? 1.0F : this.spin);
         if (this.stateTicks > 13) {
            this.roll = Mth.wrapDegrees(this.roll);
            this.setState(CRUISE);
         }
      } else if (this.target != null) {
         LivingEntity t = this.target;
         Vec3 tp = t.position().add(0, t.getBbHeight() * 0.5, 0);
         double dist = tp.distanceTo(pos);
         boolean flier = !t.onGround() && heightAbove(level, t) > 3.0;
         if (flier) {
            // pursuit: aim for a point behind and above the hero, leading its motion
            Vec3 tv = t.getDeltaMovement();
            Vec3 back = tv.lengthSqr() > 0.01 ? tv.normalize().scale(-6.0) : pos.subtract(tp).normalize().scale(6.0);
            goal = tp.add(tv.scale(10.0)).add(back).add(0, 2.0, 0);
            speed = dist > 14.0 ? CHASE_SPEED : 0.7;
         } else {
            // strafing runs at a ground target: approach from an azimuth, dive, pull up
            if (this.stage == 0) {
               goal = ChariotSteering.approachPoint(tp, this.azimuth, 24.0, 11.0);
               speed = 0.8;
               if (pos.distanceToSqr(goal) < 36.0) {
                  this.stage = 1;
               }
            } else {
               goal = tp.add(0, 2.5, 0);
               speed = 0.85;
               if (dist < 8.0 || this.stateTicks > 200) {
                  this.stage = 0;
                  this.azimuth = ChariotSteering.nextAzimuth(this.azimuth, this.random.nextDouble());
               }
            }
         }
         Vec3 vel = this.getDeltaMovement();
         Vec3 to = tp.subtract(pos);
         if (this.fireCooldown <= 0 && dist < 30.0 && vel.lengthSqr() > 0.01 && vel.normalize().dot(to.normalize()) > 0.93
            && ChitauriTerrain.clear(level, pos, tp)) {
            this.volley(level, t);
         }
         if (dist < 5.0 && this.rollCooldown <= 0) {
            this.startRoll(pos.subtract(tp));
         }
      } else {
         // patrol over the ordered sector; now and then land on a roof and watch the streets
         this.idleTicks++;
         if (this.perchAt != null) {
            goal = this.perchAt.add(0, 0.2, 0);
            speed = Math.min(0.5, pos.distanceTo(goal) * 0.08 + 0.05);
            if (pos.distanceToSqr(goal) < 1.2) {
               this.setDeltaMovement(Vec3.ZERO);
               this.setPos(this.perchAt.x, this.perchAt.y, this.perchAt.z);
               this.perchAt = null;
               this.idleTicks = 0;
               this.setState(PERCH);
               return;
            }
         } else {
            if (this.waypoint == null || pos.distanceToSqr(this.waypoint) < 25.0 || this.stateTicks > 260) {
               this.waypoint = this.patrolPoint(level, home);
               this.stateTicks = 0;
            }
            goal = this.waypoint;
            if (this.idleTicks > 300 && this.random.nextInt(120) == 0) {
               this.perchAt = this.roofSpot(level, pos);
            }
         }
      }
      Vec3 desired = ChariotSteering.seek(pos, goal, speed, this.perchAt != null ? 6.0 : 0.0);
      desired = this.avoid(level, pos, desired, speed, home);
      Vec3 before = this.getDeltaMovement();
      Vec3 vel = ChariotSteering.steer(before, desired, accel, Math.max(0.05, speed * 1.08));
      this.setDeltaMovement(vel);
      double sp = vel.length();
      double prev = before.length();
      if (st != ROLLING) {
         this.setState(sp < 0.12 ? HOVER : sp > prev + 0.01 && sp > 0.6 ? BOOST : sp < prev - 0.015 ? BRAKE : CRUISE);
      }
      this.orient(vel, this.target != null ? this.target.position().subtract(pos) : null);
   }

   private static double heightAbove(ServerLevel level, LivingEntity e) {
      int s = ChitauriTerrain.surface(level, e.getX(), e.getZ());
      return s == ChitauriTerrain.UNKNOWN ? 0.0 : e.getY() - s;
   }

   @Nullable
   private LivingEntity pickTarget(ServerLevel level) {
      LivingEntity best = null;
      double bestScore = Double.MAX_VALUE;
      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(64.0, 40.0, 64.0), ChitauriMob::pickTarget)) {
         double d = e.distanceToSqr(this);
         if (e instanceof Player p) {
            d *= !p.onGround() && heightAbove(level, p) > 3.0 ? 0.15 : 0.6; // flying heroes first
         } else if (d > 40.0 * 40.0) {
            continue;
         }
         if (d < bestScore) {
            bestScore = d;
            best = e;
         }
      }
      return best;
   }

   private void startRoll(Vec3 away) {
      Vec3 fwd = this.getDeltaMovement().lengthSqr() > 1.0E-4 ? this.getDeltaMovement().normalize() : new Vec3(0, 0, 1);
      Vec3 side = fwd.cross(new Vec3(0, 1, 0));
      if (side.lengthSqr() < 1.0E-4) {
         side = new Vec3(1, 0, 0);
      }
      side = side.normalize();
      if (side.dot(away) < 0.0) {
         side = side.scale(-1.0);
      }
      this.rollDir = side.add(fwd.scale(0.6)).add(0, 0.25, 0).normalize();
      this.spin = side.dot(fwd.cross(new Vec3(0, 1, 0))) > 0 ? 1.0F : -1.0F;
      this.rollCooldown = 70;
      this.setState(ROLLING);
      ChitauriSounds.play((ServerLevel)this.level(), this.position(), ChitauriSounds.INTERCEPTOR_BOOST, 1.4F, 1.25F);
   }

   private void volley(ServerLevel level, LivingEntity t) {
      Vec3 pos = this.position().add(0, 0.5, 0);
      Vec3 tp = t.position().add(0, t.getBbHeight() * 0.5, 0);
      double dist = pos.distanceTo(tp);
      Vec3 aim = tp.add(t.getDeltaMovement().scale(dist / 2.8 * 0.85));
      float yaw = this.getYRot() * Mth.DEG_TO_RAD;
      Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
      for (int side = -1; side <= 1; side += 2) {
         Vec3 from = pos.add(right.scale(0.45 * side)).add(this.getDeltaMovement().normalize().scale(0.8));
         ChitauriBolt.shoot(level, this, from, aim.subtract(from), 2.8, 0.75F, 1.8F);
      }
      this.fireCooldown = 16 + this.random.nextInt(8);
      this.level().broadcastEntityEvent(this, EVENT_SHOOT);
      ChitauriSounds.play(level, pos, ChitauriSounds.INTERCEPTOR_FIRE, 2.0F, 0.95F + this.random.nextFloat() * 0.1F);
   }

   private Vec3 patrolPoint(ServerLevel level, Vec3 centre) {
      for (int i = 0; i < 8; i++) {
         double a = this.random.nextDouble() * Math.PI * 2.0;
         double r = 10.0 + this.random.nextDouble() * 34.0;
         double x = centre.x + Math.cos(a) * r;
         double z = centre.z + Math.sin(a) * r;
         int s = ChitauriTerrain.surface(level, x, z);
         if (s == ChitauriTerrain.UNKNOWN || !ChitauriTerrain.ticking(level, x, s, z)) {
            continue;
         }
         return new Vec3(x, s + 12.0 + this.random.nextDouble() * 12.0, z);
      }
      return centre.add(0, 20, 0);
   }

   /** A free roof spot near {@code pos}: the highest surface around, two free blocks above. */
   @Nullable
   private Vec3 roofSpot(ServerLevel level, Vec3 pos) {
      Vec3 best = null;
      int bestY = Integer.MIN_VALUE;
      for (int i = 0; i < 10; i++) {
         double x = Mth.floor(pos.x + (this.random.nextDouble() - 0.5) * 40.0) + 0.5;
         double z = Mth.floor(pos.z + (this.random.nextDouble() - 0.5) * 40.0) + 0.5;
         int s = ChitauriTerrain.surface(level, x, z);
         if (s == ChitauriTerrain.UNKNOWN || !ChitauriTerrain.ticking(level, x, s, z)) {
            continue;
         }
         net.minecraft.core.BlockPos below = net.minecraft.core.BlockPos.containing(x, s - 1, z);
         if (!level.getBlockState(below).getFluidState().isEmpty()) {
            continue;
         }
         if (s > bestY) {
            bestY = s;
            best = new Vec3(x, s, z);
         }
      }
      return best;
   }

   private Vec3 avoid(ServerLevel level, Vec3 pos, Vec3 desired, double speed, Vec3 home) {
      Vec3 vel = this.getDeltaMovement();
      Vec3 fwd = vel.lengthSqr() > 0.0025 ? vel.normalize() : desired.lengthSqr() > 1.0E-6 ? desired.normalize() : new Vec3(0, 0, 1);
      double look = 4.0 + vel.length() * 14.0;
      Vec3 ahead = pos.add(fwd.scale(look));
      if (!ChitauriTerrain.ticking(level, ahead.x, ahead.y, ahead.z)) {
         return home.add(0, 18, 0).subtract(pos).normalize().scale(Math.max(0.3, speed));
      }
      if (this.perchAt == null && ChitauriTerrain.clearDistance(level, pos, ahead) < look - 0.5) {
         desired = new Vec3(desired.x * 0.4, Math.max(0.35, desired.y + 0.4), desired.z * 0.4);
      }
      if (this.perchAt == null) {
         Vec3 probe = pos.add(new Vec3(vel.x, 0, vel.z).scale(6.0));
         int ground = ChitauriTerrain.surfaceMax(level, probe.x, probe.z, 1);
         if (ground != ChitauriTerrain.UNKNOWN && pos.y < ground + 4.0) {
            desired = new Vec3(desired.x, Math.max(desired.y, Math.min(0.5, (ground + 4.0 - pos.y) * 0.16)), desired.z);
         }
      }
      if (pos.distanceToSqr(home) > 150.0 * 150.0) {
         desired = desired.add(home.subtract(pos).normalize().scale(0.25));
      }
      for (Entity e : level.getEntities(this, this.getBoundingBox().inflate(6.0), e -> e instanceof ChitauriInterceptor || e instanceof ChitauriChariot)) {
         desired = desired.add(ChariotSteering.separation(pos, e.getBoundingBox().getCenter(), 5.0, 0.3));
      }
      return desired;
   }

   /** Turn-rate capped yaw, pitch from the climb, bank from the turn (rolls add their own spin). */
   private void orient(Vec3 vel, @Nullable Vec3 face) {
      double h = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
      float yaw = this.getYRot();
      float delta = 0.0F;
      Vec3 dir = h > 0.06 ? vel : face;
      if (dir != null && dir.horizontalDistanceSqr() > 1.0E-4) {
         float want = (float)(Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG);
         delta = Mth.clamp(Mth.wrapDegrees(want - yaw), -MAX_TURN, MAX_TURN);
         yaw += delta;
      }
      this.setYRot(yaw);
      this.yBodyRot = yaw;
      this.yHeadRot = yaw;
      float pitch = h > 0.06 ? (float)Mth.clamp(-Mth.atan2(vel.y, Math.max(h, 0.05)) * Mth.RAD_TO_DEG, -40.0, 40.0) : 0.0F;
      this.setXRot(this.getXRot() + (pitch - this.getXRot()) * 0.2F);
      if (this.state() != ROLLING) {
         this.roll += (ChariotSteering.bank(delta) * 1.2F - this.roll) * 0.18F;
      }
      this.entityData.set(ROLL, this.roll);
   }

   // ------------------------------------------------------------------ damage, death

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (ChitauriMob.is(source.getEntity()) || ChitauriMob.is(source.getDirectEntity())) {
         return false;
      }
      if (this.state() == FALLING) {
         if (!this.level().isClientSide && amount >= 6.0F) {
            this.crash();
         }
         return false;
      }
      boolean hit = super.hurt(source, amount);
      if (hit && !this.level().isClientSide && this.isAlive() && source.getEntity() instanceof Player p && this.rollCooldown <= 0) {
         this.startRoll(this.position().subtract(p.position()));
      }
      return hit;
   }

   @Override
   public void die(DamageSource source) {
      boolean first = this.state() != FALLING;
      super.die(source);
      if (first && !this.level().isClientSide) {
         this.entityData.set(STATE, FALLING);
         this.setNoGravity(false);
         this.spin = (this.random.nextBoolean() ? 1 : -1) * (9.0F + this.random.nextFloat() * 8.0F);
         ServerLevel level = (ServerLevel)this.level();
         ChitauriSounds.play(level, this.position(), ChitauriSounds.INTERCEPTOR_DOWN, 2.0F, 1.0F);
         ChitauriInvasion.onKilled(this, source);
      }
   }

   @Override
   protected void tickDeath() {
      if (this.level().isClientSide) {
         return;
      }
      this.deathTime++;
      Vec3 v = this.getDeltaMovement();
      this.setDeltaMovement(v.x * 0.97, Math.max(-1.0, v.y - 0.05), v.z * 0.97);
      this.setYRot(this.getYRot() + this.spin);
      this.yBodyRot = this.getYRot();
      this.roll += this.spin * 1.6F;
      this.entityData.set(ROLL, Mth.wrapDegrees(this.roll));
      this.setXRot(Math.min(60.0F, this.getXRot() + 2.0F));
      if (this.onGround() || this.deathTime > DEATH_TICKS || this.isInWater()) {
         this.crash();
      }
   }

   private void crash() {
      if (this.isRemoved() || !(this.level() instanceof ServerLevel level)) {
         return;
      }
      Vec3 at = this.position().add(0, 0.4, 0);
      ChitauriNet.fxAlways(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.EXPLOSION, at, this.getDeltaMovement(), 0.45F, this.random.nextInt()), at, 128);
      ChitauriSounds.play(level, at, ChitauriSounds.EXPLOSION, 2.0F, 1.3F);
      ChitauriDestruction.blastEntities(level, at, 2.5, 4.0F, this, this);
      if (this.isAlive()) {
         ChitauriInvasion.onKilled(this, this.damageSources().generic());
      }
      this.discard();
   }

   @Override
   public void handleEntityEvent(byte id) {
      if (id == EVENT_SHOOT) {
         this.clientShootStart = this.tickCount;
      } else {
         super.handleEntityEvent(id);
      }
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
   protected SoundEvent getAmbientSound() {
      return null;
   }

   @Override
   public boolean isAlliedTo(Entity other) {
      return ChitauriMob.is(other) || super.isAlliedTo(other);
   }

   @Override
   public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
      return false;
   }

   @Override
   protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   protected void pushEntities() {
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
   public boolean removeWhenFarAway(double distance) {
      return false;
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double distance) {
      return distance < 160.0 * 160.0;
   }

   @Override
   public AABB getBoundingBoxForCulling() {
      return this.getBoundingBox().inflate(1.5);
   }
}
