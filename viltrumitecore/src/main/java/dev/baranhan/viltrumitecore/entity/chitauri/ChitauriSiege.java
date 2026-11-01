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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Chitauri siege lancer: a slow hovering artillery sled with a long plasma lance. It keeps 20-32 blocks from its target,
 * anchors (deploy), charges for two seconds with a visible aim beam and a danger mark at the point of impact, then fires
 * one heavy shell. A hero hiding behind a wall gets the wall shelled first ({@link ChitauriBolt#POWER_SIEGE} breaks cover
 * blocks). Counterplay: the glowing core is exposed while it charges (hits there count 1.5x and a hard hit breaks the
 * charge), the shell can be dodged once the aim locks, and every hit is capped. Never saved; never moves into unloaded
 * chunks.
 */
public class ChitauriSiege extends Mob implements ChitauriMob, net.minecraft.world.entity.monster.Enemy {
   private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(ChitauriSiege.class, EntityDataSerializers.BYTE);
   public static final byte MOVE = 0, DEPLOY = 1, CHARGE = 2, FIRE = 3, DYING = 4;
   public static final int DEPLOY_TICKS = 20, CHARGE_TICKS = 44, LOCK_TICKS = 12, FIRE_TICKS = 22, COOLDOWN = 90, DEATH_TICKS = 50;
   public static final double MIN_RANGE = 20.0, MAX_RANGE = 34.0, HOVER = 7.0;
   public static final byte EVENT_STAGGER = 74, EVENT_FIRE = 75;

   @Nullable
   private LivingEntity target;
   @Nullable
   private Vec3 orderPoint;
   @Nullable
   private Vec3 aim;
   private int stateTicks;
   private int cooldown = 60;
   private double bearing;
   private float spin;

   public int clientStateStart;
   public byte clientState = -1;
   public int clientStaggerStart = -1000;

   public ChitauriSiege(EntityType<? extends ChitauriSiege> type, net.minecraft.world.level.Level level) {
      super(type, level);
      this.setNoGravity(true);
      this.xpReward = 0;
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, BalanceTable.CHITAURI_SIEGE_HEALTH)
         .add(Attributes.ARMOR, 8.0)
         .add(Attributes.FOLLOW_RANGE, 64.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
         .add(Attributes.FLYING_SPEED, 0.3);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(STATE, MOVE);
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

   public void setOrderPoint(@Nullable Vec3 p) {
      this.orderPoint = p;
   }

   @Nullable
   public LivingEntity siegeTarget() {
      return this.target;
   }

   /** Out of the portal: a slow glide down towards the city. */
   public void launch(Vec3 dir) {
      this.setDeltaMovement(dir.normalize().scale(0.3));
      this.setYRot((float)(Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG));
      this.yBodyRot = this.getYRot();
      this.bearing = this.random.nextDouble() * Math.PI * 2.0;
   }

   /** Muzzle of the lance (front of the turret, which follows the head yaw / pitch). */
   public Vec3 muzzle() {
      float yaw = this.yHeadRot * Mth.DEG_TO_RAD;
      float pitch = this.getXRot() * Mth.DEG_TO_RAD;
      Vec3 fwd = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
      return this.position().add(0, 1.15, 0).add(fwd.scale(2.3));
   }

   // ------------------------------------------------------------------ movement

   @Override
   public void travel(Vec3 input) {
      if (this.isControlledByLocalInstance()) {
         Vec3 before = this.getDeltaMovement();
         this.move(MoverType.SELF, before);
         if (!this.level().isClientSide && this.state() == DYING && (this.horizontalCollision || this.verticalCollision)) {
            this.crash();
            return;
         }
      }
      this.calculateEntityAnimation(false);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide && this.state() != this.clientState) {
         this.clientState = this.state();
         this.clientStateStart = this.tickCount;
      }
   }

   @Override
   protected void customServerAiStep() {
      ServerLevel level = (ServerLevel)this.level();
      this.stateTicks++;
      if (this.cooldown > 0) {
         this.cooldown--;
      }
      byte st = this.state();
      if (st == DYING || ChitauriMob.heldByHero(this)) {
         return;
      }
      if (this.tickCount % 20 == 0 || this.target != null && !ChitauriMob.pickTarget(this.target)) {
         this.target = this.pickTarget(level);
      }
      Vec3 pos = this.position();
      LivingEntity t = this.target;
      if (st == MOVE) {
         Vec3 goal = this.standPoint(level, pos, t);
         Vec3 desired = goal == null ? Vec3.ZERO : ChariotSteering.seek(pos, goal, 0.22, 4.0);
         this.setDeltaMovement(ChariotSteering.steer(this.getDeltaMovement(), desired, 0.02, 0.24));
         if (t != null) {
            this.turret(t.getEyePosition(), 4.0F);
            double d = t.distanceTo(this);
            if (this.cooldown <= 0 && d >= MIN_RANGE * 0.7 && d <= MAX_RANGE + 6.0 && t.getY() > this.getY() - 40.0) {
               this.setState(DEPLOY);
               ChitauriSounds.play(level, pos, ChitauriSounds.SIEGE_HUM, 1.8F, 0.8F);
            }
         } else {
            float yaw = (float)(Mth.atan2(-this.getDeltaMovement().x, this.getDeltaMovement().z) * Mth.RAD_TO_DEG);
            if (this.getDeltaMovement().horizontalDistanceSqr() > 0.002) {
               this.yBodyRot = Mth.approachDegrees(this.yBodyRot, yaw, 3.0F);
               this.setYRot(this.yBodyRot);
               this.yHeadRot = Mth.approachDegrees(this.yHeadRot, yaw, 4.0F);
            }
         }
         return;
      }
      // anchored: hold position, settle the hover
      this.setDeltaMovement(this.getDeltaMovement().scale(0.7));
      if (t == null && st != FIRE) {
         this.setState(MOVE);
         return;
      }
      if (st == DEPLOY) {
         this.turret(t.getEyePosition(), 3.0F);
         if (this.stateTicks >= DEPLOY_TICKS) {
            this.setState(CHARGE);
            this.aim = this.aimPoint(level, t);
            ChitauriSounds.playWide(level, pos, ChitauriSounds.SIEGE_CHARGE, 3.0F, 1.0F, 96.0);
         }
      } else if (st == CHARGE) {
         if (this.stateTicks < CHARGE_TICKS - LOCK_TICKS) {
            this.aim = this.aimPoint(level, t); // tracks, then locks: the last 0.6 s is the window to get out
         }
         if (this.aim != null) {
            this.turret(this.aim, 6.0F);
            if (this.stateTicks % 4 == 0) {
               Vec3 m = this.muzzle();
               ChitauriNet.fx(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.SIEGE_CHARGE, m, this.aim.subtract(m),
                  this.stateTicks / (float)CHARGE_TICKS, this.getId()), m, 128);
            }
            if (this.stateTicks == CHARGE_TICKS - LOCK_TICKS) {
               ChitauriDestruction.danger(level, this.aim, 10.0F, 50);
            }
         }
         if (this.stateTicks >= CHARGE_TICKS) {
            this.fire(level);
         }
      } else if (st == FIRE) {
         if (this.stateTicks >= FIRE_TICKS) {
            this.cooldown = COOLDOWN + this.random.nextInt(40);
            this.bearing += (this.random.nextBoolean() ? 1 : -1) * (0.6 + this.random.nextDouble() * 0.8); // relocate between shots
            this.setState(MOVE);
         }
      }
   }

   private void fire(ServerLevel level) {
      Vec3 m = this.muzzle();
      Vec3 aimAt = this.aim != null ? this.aim : m.add(this.getLookAngle().scale(20.0));
      ChitauriBolt.shoot(level, this, m, aimAt.subtract(m), 1.9, ChitauriBolt.POWER_SIEGE, 0.6F);
      ChitauriSounds.playWide(level, m, ChitauriSounds.SIEGE_FIRE, 4.0F, 0.9F + this.random.nextFloat() * 0.1F, 128.0);
      level.broadcastEntityEvent(this, EVENT_FIRE);
      this.setState(FIRE);
      this.aim = null;
   }

   /** Where to hover: on a ring around the target at the preferred range, above the roofs; else over the order point. */
   @Nullable
   private Vec3 standPoint(ServerLevel level, Vec3 pos, @Nullable LivingEntity t) {
      Vec3 centre;
      double r;
      if (t != null) {
         centre = t.position();
         double d = Math.sqrt(t.distanceToSqr(pos.x, t.getY(), pos.z));
         double want = Mth.clamp(d, MIN_RANGE + 2.0, MAX_RANGE - 4.0);
         double cur = Math.atan2(pos.z - centre.z, pos.x - centre.x);
         this.bearing = cur + Mth.wrapDegrees((float)Math.toDegrees(this.bearing - cur)) * Mth.DEG_TO_RAD * 0.02;
         r = want;
      } else {
         Vec3 anchor = ChitauriInvasion.anchor(level);
         centre = this.orderPoint != null ? this.orderPoint : anchor != null ? anchor : pos;
         r = 12.0;
         this.bearing += 0.004;
      }
      double x = centre.x + Math.cos(this.bearing) * r;
      double z = centre.z + Math.sin(this.bearing) * r;
      if (!ChitauriTerrain.ticking(level, x, pos.y, z)) {
         return null; // never into unloaded chunks: wait where it is
      }
      int top = ChitauriTerrain.surfaceMax(level, x, z, 4);
      if (top == ChitauriTerrain.UNKNOWN) {
         return null;
      }
      return new Vec3(x, top + HOVER, z);
   }

   /** The impact point: the hero, or the block that hides the hero (cover gets shelled). */
   private Vec3 aimPoint(ServerLevel level, LivingEntity t) {
      Vec3 m = this.muzzle();
      Vec3 lead = t.getEyePosition().add(t.getDeltaMovement().scale(6.0)).add(0, -0.4, 0);
      BlockHitResult hit = level.clip(new ClipContext(m, lead, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
      if (hit.getType() == HitResult.Type.BLOCK) {
         return Vec3.atCenterOf(hit.getBlockPos());
      }
      return lead;
   }

   private void turret(Vec3 at, float maxStep) {
      Vec3 from = this.position().add(0, 1.15, 0);
      Vec3 d = at.subtract(from);
      float yaw = (float)(Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
      float pitch = (float)(-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
      this.yHeadRot = Mth.approachDegrees(this.yHeadRot, yaw, maxStep);
      this.setXRot(Mth.approach(this.getXRot(), Mth.clamp(pitch, -30.0F, 60.0F), maxStep * 0.6F));
      // the sled body follows slowly (the turret has a 70 degree arc)
      float off = Mth.wrapDegrees(this.yHeadRot - this.yBodyRot);
      if (Math.abs(off) > 35.0F) {
         this.yBodyRot = Mth.approachDegrees(this.yBodyRot, this.yHeadRot, 2.5F);
         this.setYRot(this.yBodyRot);
      }
   }

   @Nullable
   private LivingEntity pickTarget(ServerLevel level) {
      LivingEntity best = null;
      double bestScore = Double.MAX_VALUE;
      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(56.0, 40.0, 56.0), ChitauriMob::pickTarget)) {
         double d = Math.sqrt(e.distanceToSqr(this));
         double score = Math.abs(d - 27.0);
         if (e instanceof Player) {
            score -= 12.0; // heroes first
         }
         if (score < bestScore) {
            bestScore = score;
            best = e;
         }
      }
      return best;
   }

   // ------------------------------------------------------------------ damage, death

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (ChitauriMob.is(source.getEntity()) || ChitauriMob.is(source.getDirectEntity())) {
         return false;
      }
      if (this.state() == DYING) {
         return false;
      }
      float a = amount;
      if (this.state() == CHARGE) {
         a *= 1.5F; // the open core
      }
      a = Math.min(a, this.getMaxHealth() * BalanceTable.CHITAURI_SIEGE_HIT_CAP);
      a = Math.max(a, Math.min(amount, 1.0F));
      boolean hit = super.hurt(source, a);
      if (hit && !this.level().isClientSide && this.isAlive() && this.state() == CHARGE && a >= this.getMaxHealth() * 0.1F) {
         // a hard hit on the core breaks the charge
         this.level().broadcastEntityEvent(this, EVENT_STAGGER);
         this.aim = null;
         this.cooldown = 50;
         this.setState(MOVE);
      }
      return hit;
   }

   @Override
   public void die(DamageSource source) {
      boolean first = this.state() != DYING;
      super.die(source);
      if (first && !this.level().isClientSide) {
         this.entityData.set(STATE, DYING);
         this.setNoGravity(false);
         this.spin = (this.random.nextBoolean() ? 1 : -1) * (2.0F + this.random.nextFloat() * 2.0F);
         ServerLevel level = (ServerLevel)this.level();
         ChitauriSounds.play(level, this.position(), ChitauriSounds.INTERCEPTOR_DOWN, 2.6F, 0.6F);
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
      this.setDeltaMovement(v.x * 0.95, Math.max(-0.8, v.y - 0.035), v.z * 0.95);
      this.setYRot(this.getYRot() + this.spin);
      this.yBodyRot = this.getYRot();
      this.setXRot(Math.min(25.0F, this.getXRot() + 0.8F));
      if (this.onGround() || this.deathTime > DEATH_TICKS || this.isInWater()) {
         this.crash();
      }
   }

   private void crash() {
      if (this.isRemoved() || !(this.level() instanceof ServerLevel level)) {
         return;
      }
      Vec3 at = this.position().add(0, 0.6, 0);
      ChitauriNet.fxAlways(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.EXPLOSION, at, this.getDeltaMovement(), 0.8F, this.random.nextInt()), at, 160);
      ChitauriSounds.playWide(level, at, ChitauriSounds.EXPLOSION, 3.0F, 0.9F, 128.0);
      ChitauriDestruction.blastEntities(level, at, 3.5, 6.0F, this, this);
      ChitauriDestruction.crack(level, net.minecraft.core.BlockPos.containing(at), 2.0, Vec3.ZERO);
      if (this.isAlive()) {
         ChitauriInvasion.onKilled(this, this.damageSources().generic());
      }
      this.discard();
   }

   @Override
   public void handleEntityEvent(byte id) {
      if (id == EVENT_STAGGER) {
         this.clientStaggerStart = this.tickCount;
      } else if (id == EVENT_FIRE) {
         this.clientStateStart = this.tickCount;
      } else {
         super.handleEntityEvent(id);
      }
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
      return distance < 256.0 * 256.0;
   }

   @Override
   public AABB getBoundingBoxForCulling() {
      return this.getBoundingBox().inflate(3.0);
   }
}
