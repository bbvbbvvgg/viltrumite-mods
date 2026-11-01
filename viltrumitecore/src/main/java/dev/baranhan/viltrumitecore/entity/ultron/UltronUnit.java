package dev.baranhan.viltrumitecore.entity.ultron;

import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriTerrain;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronMove;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronRole;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronSounds;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronUnitKind;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronUprising;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronNet;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Shared body of Ultron's machines. The director hands out orders (role, post, goal, follow, job); the unit
 * picks its targets from what it really sees (chunk-safe line of sight), runs timed moves ({@link UltronMove})
 * whose contact ticks deal the damage, play the sound and send the effect on the frame where the client clip
 * makes contact, reports what it sees and what hits it (the network's only intel), and dies with its own
 * destruction clip. No friendly fire, no loot, never saved, never despawns by itself.
 */
public abstract class UltronUnit extends Monster implements UltronMob {
   private static final EntityDataAccessor<Byte> ROLE = SynchedEntityData.defineId(UltronUnit.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(UltronUnit.class, EntityDataSerializers.BYTE);
   /** (sequence << 8) | move ordinal: a new sequence restarts the same move on the client. */
   private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(UltronUnit.class, EntityDataSerializers.INT);
   /** Entity id the head and weapons track (-1: none). */
   private static final EntityDataAccessor<Integer> AIM = SynchedEntityData.defineId(UltronUnit.class, EntityDataSerializers.INT);

   public static final int FLAG_DEFLECTOR = 1;
   public static final int FLAG_DAMAGED = 2;
   public static final int FLAG_CRITICAL = 4;
   public static final int FLAG_COMBAT = 8;
   public static final int FLAG_SHIELD = 16;
   public static final int FLAG_BEAM = 32;
   public static final int FLAG_GROUNDED = 64;

   /** A hit worth this share of max health staggers the unit (heavy-hit reaction). */
   public static final float HEAVY_SHARE = 0.25F;
   /** Frontal deflector: ranged hits from within this half-angle (degrees) lose {@link #DEFLECT_BLOCK}. */
   public static final float DEFLECT_ARC = 70.0F;
   public static final float DEFLECT_BLOCK = 0.7F;

   // ---- orders from the director (server) ----
   public int netId = -1;
   @Nullable
   public Vec3 post;
   @Nullable
   public Vec3 goal;
   @Nullable
   public UUID follow;
   public int job = -1;
   @Nullable
   public UUID hunt;
   /** Until this tick (its own tickCount) an alert / mark order holds against the role coordinator. */
   public int alertUntil;

   // ---- move state (server) ----
   protected UltronMove move = UltronMove.NONE;
   protected int moveTick;
   @Nullable
   protected LivingEntity moveTarget;
   private int actionSeq;
   protected int attackCooldown;
   protected int targetScan;
   protected int sightReport;
   protected int lostSight;
   protected int navCooldown;
   @Nullable
   private Vec3 navDest;
   private Vec3 lastCheckPos = Vec3.ZERO;
   private int stuckChecks;
   protected int airTicks;
   protected int heavyCooldown;
   /** Flyers: velocity the steering wants this tick (applied in travel). */
   protected Vec3 steer = Vec3.ZERO;

   // ---- client presentation ----
   public UltronMove clientMove = UltronMove.NONE;
   public int clientMoveStart = -1000;
   public float clientSpeed;
   public float clientSpeedO;
   public float clientAccel;
   public float clientAccelO;
   public float clientGait;
   public float clientGaitO;
   public float clientTurn;
   public float clientTurnO;
   public float clientCombat;
   public float clientCombatO;
   public int clientLandStart = -1000;
   public int clientAirTicks;
   private float clientLastYaw;
   /** Smoothed look (degrees, relative to the body): the head and weapons track the aim entity. */
   public float clientLookYaw;
   public float clientLookYawO;
   public float clientLookPitch;
   public float clientLookPitchO;
   /**
    * World positions of the rendered anchor bones (hands / muzzles, eyes, tools: per kind, set by the
    * renderer every frame) so beams, charges and flashes sit on the animated part; {@link #clientAnchorTick}
    * is the tick they were rendered.
    */
   public final Vec3[] clientAnchors = new Vec3[4];
   public int clientAnchorTick = -1000;
   /** A weak part already came off (critical damage, client side). */
   public boolean clientPartLost;
   /** The body broke apart into its pieces (client side): the renderer stops drawing it. */
   public boolean clientShattered;
   /** Client: smoothed 0..1 weight of the kind's held stance (shield, welding, deflector guard, flight). */
   public float clientHold;
   public float clientHoldO;

   protected UltronUnit(EntityType<? extends UltronUnit> type, Level level) {
      super(type, level);
      this.xpReward = 0;
      this.setMaxUpStep(1.0F);
      this.setPersistenceRequired();
   }

   // ------------------------------------------------------------------ kind hooks

   public abstract UltronUnitKind kind();

   /** Ticks of the destruction clip before the body breaks apart. */
   public abstract int deathTicks();

   /** How far it notices a target it can see. */
   protected abstract double senseRange();

   /** One combat tick with a live target: move into position and start moves. */
   protected abstract void fight(LivingEntity target, double distance, boolean sees);

   /** A contact tick of a running move. */
   protected abstract void onContact(UltronMove m, int index, @Nullable LivingEntity target);

   protected abstract SoundEvent hurtSound();

   protected abstract SoundEvent deathSound();

   public boolean flier() {
      return this.kind().flies();
   }

   /** Moves that a heavy hit may cut short (the big wind-ups are committed). */
   protected boolean interruptible(UltronMove m) {
      return true;
   }

   protected void onMoveStart(UltronMove m, @Nullable LivingEntity target) {
   }

   protected void onMoveTick(UltronMove m, int tick, @Nullable LivingEntity target) {
   }

   protected void onMoveEnd(UltronMove m) {
   }

   /** Non-combat behaviour of the role (default: go to the goal, else keep near the post). */
   protected void idle() {
      if (this.follow != null && this.level() instanceof ServerLevel sl) {
         Entity leader = sl.getEntity(this.follow);
         if (leader != null && leader.isAlive()) {
            double d = this.distanceTo(leader);
            if (d > 6.0) {
               this.travelTo(leader.position(), this.cruiseSpeed() * (d > 16.0 ? 1.2 : 1.0));
            } else if (!this.flier()) {
               this.stopMoving();
            } else {
               this.steerTo(leader.position().add(0.0, this.hoverHeight() * 0.5, 0.0), this.cruiseSpeed() * 0.2, this.hoverHeight() * 0.5);
            }
            return;
         }
         this.follow = null;
      }
      if (this.goal != null && this.position().distanceToSqr(this.goal) < 9.0 && this.role() != UltronRole.REPAIR) {
         this.goal = null; // reached: back to the post
      }
      Vec3 dest = this.goal != null ? this.goal : this.post;
      if (dest == null) {
         this.stopMoving();
         return;
      }
      double d = this.position().distanceTo(dest);
      double keep = this.role() == UltronRole.GUARD ? 4.0 : this.role() == UltronRole.PATROL ? 10.0 : 3.0;
      if (d > keep) {
         this.travelTo(dest, this.cruiseSpeed());
      } else if (this.role() == UltronRole.PATROL && this.random.nextInt(60) == 0) {
         double a = this.random.nextDouble() * Math.PI * 2.0;
         this.travelTo(dest.add(Math.cos(a) * 8.0, 0.0, Math.sin(a) * 8.0), this.cruiseSpeed() * 0.7);
      } else if (!this.flier() && this.getNavigation().isDone()) {
         this.stopMoving();
      }
   }

   protected double cruiseSpeed() {
      return 1.0;
   }

   /** Moves during which the unit stands (its own steering, if any, runs in {@link #onMoveTick}). */
   protected boolean rooted(UltronMove m) {
      return m == UltronMove.FABRICATE || m == UltronMove.HEAVY_HIT || m == UltronMove.LAND;
   }

   /** What a rooted unit does with its movement (default: stop). */
   protected void holdStill() {
      this.stopMoving();
   }

   /** What the head and tools track out of combat (engineers: the node they weld). */
   @Nullable
   protected Entity idleAim() {
      return null;
   }

   /** A point in the unit's frame: {@code right}, {@code up}, {@code forward} blocks from its feet. */
   public Vec3 local(double right, double up, double forward) {
      float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
      double sin = Mth.sin(yaw);
      double cos = Mth.cos(yaw);
      return this.position().add(-sin * forward - cos * right, up, cos * forward - sin * right);
   }

   /** Horizontal unit vector the body faces. */
   public Vec3 facing() {
      float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
      return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
   }

   /** Valid targets around a point (no line-of-sight check; callers check what they need). */
   protected List<LivingEntity> around(Vec3 at, double radius) {
      return this.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius), e -> UltronMob.validTarget(e)
         && e.position().distanceToSqr(at) <= radius * radius);
   }

   /** Is the target in the air (flying hero, jetpack, elytra), not just jumping? */
   protected boolean airborne(LivingEntity target) {
      if (target.onGround() || target.isInWater()) {
         return false;
      }
      int ground = ChitauriTerrain.surface(this.level(), target.getX(), target.getZ());
      return ground != ChitauriTerrain.UNKNOWN && target.getY() - ground > 3.0 || target instanceof Player p && p.getAbilities().flying;
   }

   /** A radial blast from {@code at}: damage falls off to half at the rim, a push away and up; returns how many it hit. */
   protected int blast(Vec3 at, double radius, float damage, double push, double lift, boolean groundOnly) {
      int hits = 0;
      for (LivingEntity e : this.around(at, radius)) {
         if (groundOnly && e.getY() - at.y > 2.5) {
            continue;
         }
         if (!ChitauriTerrain.clear(this.level(), at.add(0.0, 0.6, 0.0), e.position().add(0.0, e.getBbHeight() * 0.5, 0.0))) {
            continue;
         }
         double d = e.position().distanceTo(at);
         float f = (float)(1.0 - 0.5 * Mth.clamp(d / radius, 0.0, 1.0));
         if (e.hurt(this.damageSources().mobAttack(this), damage * f * this.damageScale())) {
            hits++;
            if (push > 0.0 && dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsImpulse(e)) {
               Vec3 dir = new Vec3(e.getX() - at.x, 0.0, e.getZ() - at.z);
               dir = dir.lengthSqr() < 1.0E-4 ? this.facing() : dir.normalize();
               double resist = 1.0 - e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
               e.push(dir.x * push * f * resist, lift * f * resist, dir.z * push * f * resist);
               e.hurtMarked = true;
            }
         }
      }
      return hits;
   }

   /**
    * A straight beam from {@code from} along {@code dir} up to {@code range} (stopped by blocks, chunk-safe):
    * every valid target it crosses takes {@code damage}. Returns where it ended.
    */
   protected Vec3 beam(Vec3 from, Vec3 dir, double range, float damage, double width) {
      Vec3 d = dir.normalize();
      double len = ChitauriTerrain.clearDistance(this.level(), from, from.add(d.scale(range)));
      Vec3 end = from.add(d.scale(len));
      net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(from, end).inflate(width + 1.0);
      for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, box, UltronMob::validTarget)) {
         net.minecraft.world.phys.AABB bb = e.getBoundingBox().inflate(width);
         if (bb.contains(from) || bb.clip(from, end).isPresent()) {
            e.hurt(this.damageSources().indirectMagic(this, this), damage * this.damageScale());
         }
      }
      return end;
   }

   protected double hoverHeight() {
      return 6.0;
   }

   // ------------------------------------------------------------------ synced state

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(ROLE, (byte)UltronRole.PATROL.ordinal());
      this.entityData.define(FLAGS, (byte)0);
      this.entityData.define(ACTION, 0);
      this.entityData.define(AIM, -1);
   }

   @Override
   protected void registerGoals() {
      if (!this.flier()) {
         this.goalSelector.addGoal(0, new FloatGoal(this));
      }
   }

   public UltronRole role() {
      return UltronRole.byId(this.entityData.get(ROLE));
   }

   public void setRole(UltronRole role) {
      if (this.role() != role) {
         this.entityData.set(ROLE, (byte)role.ordinal());
      }
   }

   public boolean flag(int f) {
      return (this.entityData.get(FLAGS) & f) != 0;
   }

   public void setFlag(int f, boolean on) {
      byte flags = this.entityData.get(FLAGS);
      byte next = (byte)(on ? flags | f : flags & ~f);
      if (next != flags) {
         this.entityData.set(FLAGS, next);
      }
   }

   public int aimId() {
      return this.entityData.get(AIM);
   }

   protected void setAim(@Nullable Entity e) {
      int id = e == null ? -1 : e.getId();
      if (this.entityData.get(AIM) != id) {
         this.entityData.set(AIM, id);
      }
   }

   public UltronMove currentMove() {
      return this.move;
   }

   public int moveTick() {
      return this.moveTick;
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (ACTION.equals(key) && this.level().isClientSide) {
         int v = this.entityData.get(ACTION);
         this.clientMove = UltronMove.byId(v & 0xFF);
         this.clientMoveStart = this.tickCount;
         if (this.clientMove != UltronMove.NONE) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.viltrumitecore.client.ultron.UltronClientHooks.moveStarted(this));
         }
      }
   }

   /** Client: seconds into the current move's clip, or -1 when it is over. */
   public float clientMoveSeconds(float partial) {
      if (this.clientMove == UltronMove.NONE) {
         return -1.0F;
      }
      float t = this.tickCount - this.clientMoveStart + partial;
      return t >= 0.0F && t < this.clientMove.length ? t / 20.0F : -1.0F;
   }

   // ------------------------------------------------------------------ moves

   /** Starts a move now (false while another one runs). */
   public boolean startMove(UltronMove m, @Nullable LivingEntity target) {
      if (this.move != UltronMove.NONE || !this.isAlive()) {
         return false;
      }
      this.forceMove(m, target);
      return true;
   }

   /** Starts a move over whatever runs (reactions, director orders). */
   public void forceMove(UltronMove m, @Nullable LivingEntity target) {
      if (this.move != UltronMove.NONE && this.move != m) {
         this.onMoveEnd(this.move);
      }
      this.move = m;
      this.moveTick = 0;
      this.moveTarget = target;
      this.actionSeq = this.actionSeq + 1 & 0x7FFFFF;
      this.entityData.set(ACTION, this.actionSeq << 8 | m.ordinal());
      this.onMoveStart(m, target);
   }

   private void tickMove() {
      if (this.move == UltronMove.NONE) {
         return;
      }
      this.moveTick++;
      UltronMove m = this.move;
      int idx = m.contactIndex(this.moveTick);
      if (idx >= 0) {
         this.onContact(m, idx, this.moveTarget);
      }
      if (this.move != m) {
         return;
      }
      this.onMoveTick(m, this.moveTick, this.moveTarget);
      if (this.move == m && this.moveTick >= m.length) {
         this.move = UltronMove.NONE;
         this.moveTarget = null;
         this.onMoveEnd(m);
      }
   }

   // ------------------------------------------------------------------ tick

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         this.clientTick();
         return;
      }
      if (this.getY() < this.level().getMinBuildHeight() - 16) {
         this.discard();
         return;
      }
      if (!this.flier()) {
         if (!this.onGround()) {
            this.airTicks++;
         } else {
            this.airTicks = 0;
         }
      }
   }

   private void clientTick() {
      double dx = this.getX() - this.xo;
      double dz = this.getZ() - this.zo;
      float moved = this.isAlive() ? (float)Math.min(1.2, Math.sqrt(dx * dx + dz * dz)) : 0.0F;
      this.clientSpeedO = this.clientSpeed;
      float before = this.clientSpeed;
      this.clientSpeed += (moved - this.clientSpeed) * 0.3F;
      this.clientAccelO = this.clientAccel;
      this.clientAccel += ((this.clientSpeed - before) * 12.0F - this.clientAccel) * 0.25F;
      this.clientGaitO = this.clientGait;
      this.clientGait += moved / this.stride(this.clientSpeed);
      if (this.clientGait > 1000.0F) {
         this.clientGait -= 1000.0F;
         this.clientGaitO -= 1000.0F;
      }
      float yawRate = Mth.wrapDegrees(this.yBodyRot - this.clientLastYaw);
      this.clientLastYaw = this.yBodyRot;
      this.clientTurnO = this.clientTurn;
      this.clientTurn += (Mth.clamp(yawRate, -20.0F, 20.0F) - this.clientTurn) * 0.3F;
      this.clientCombatO = this.clientCombat;
      this.clientCombat += ((this.flag(FLAG_COMBAT) ? 1.0F : 0.0F) - this.clientCombat) * 0.15F;
      this.clientHoldO = this.clientHold;
      this.clientHold += (this.clientHoldTarget() - this.clientHold) * 0.25F;
      this.clientLookYawO = this.clientLookYaw;
      this.clientLookPitchO = this.clientLookPitch;
      float[] look = this.lookTarget();
      this.clientLookYaw += (look[0] - this.clientLookYaw) * 0.35F;
      this.clientLookPitch += (look[1] - this.clientLookPitch) * 0.35F;
      if (!this.flier()) {
         if (!this.onGround() && this.getDeltaMovement().y < -0.1) {
            this.clientAirTicks++;
         } else {
            if (this.clientAirTicks > 6 && this.onGround()) {
               this.clientLandStart = this.tickCount;
            }
            this.clientAirTicks = 0;
         }
      }
   }

   /** Client: what {@link #clientHold} heads for (the kind's held stance; none by default). */
   protected float clientHoldTarget() {
      return 0.0F;
   }

   /** Look toward the aim entity (else the synced head rotation), relative to the body, clamped. */
   private float[] lookTarget() {
      float yaw;
      float pitch;
      Entity aim = this.isAlive() && this.aimId() >= 0 ? this.level().getEntity(this.aimId()) : null;
      if (aim != null && aim.distanceToSqr(this) < 64.0 * 64.0) {
         Vec3 eye = this.getEyePosition();
         Vec3 to = aim.position().add(0.0, aim.getBbHeight() * 0.7, 0.0).subtract(eye);
         double flat = Math.sqrt(to.x * to.x + to.z * to.z);
         yaw = Mth.wrapDegrees((float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F - this.yBodyRot);
         pitch = (float)(-(Mth.atan2(to.y, flat) * Mth.RAD_TO_DEG));
      } else {
         yaw = Mth.wrapDegrees(this.yHeadRot - this.yBodyRot);
         pitch = this.getXRot();
      }
      return new float[]{Mth.clamp(yaw, -70.0F, 70.0F), Mth.clamp(pitch, -50.0F, 50.0F)};
   }

   /** Blocks per gait cycle at a speed (blocks/tick): longer strides when running. */
   public float stride(float speed) {
      return 1.6F + 2.2F * Mth.clamp(speed / 0.3F, 0.0F, 1.0F);
   }

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      if (!this.isAlive() || UltronMob.heldByHero(this)) {
         return;
      }
      if (this.attackCooldown > 0) {
         this.attackCooldown--;
      }
      if (this.heavyCooldown > 0) {
         this.heavyCooldown--;
      }
      if (this.navCooldown > 0) {
         this.navCooldown--;
      }
      this.tickMove();
      if (this.rooted(this.move)) {
         // assembling on the pad, staggered, landing (Prime: his cinematics and channels): no steering, no new moves
         this.holdStill();
         return;
      }
      LivingEntity target = this.getTarget();
      if (target != null && (!this.keepTarget(target) || this.leashed(target))) {
         this.setTarget(null);
         target = null;
      }
      if (--this.targetScan <= 0) {
         this.targetScan = 8 + this.random.nextInt(5);
         LivingEntity found = this.findTarget();
         if (found != null && found != target && (target == null || this.distanceToSqr(found) < this.distanceToSqr(target) * 0.5)) {
            this.setTarget(found);
            target = found;
         }
      }
      boolean combat = target != null;
      this.setFlag(FLAG_COMBAT, combat);
      if (combat) {
         double dist = this.distanceTo(target);
         boolean sees = this.sees(target);
         this.lostSight = sees ? 0 : this.lostSight + 1;
         if (sees && target instanceof Player player && --this.sightReport <= 0) {
            this.sightReport = 20;
            UltronUprising.sighted(this, player);
         }
         this.setAim(target);
         this.fight(target, dist, sees);
      } else {
         this.setAim(this.idleAim());
         if (this.move == UltronMove.NONE) {
            this.idle();
         }
      }
      if (this.tickCount % 20 == 0) {
         this.antiStuck();
         this.updateDamageFlags();
      }
   }

   /** Guards and escorts do not chase far from what they hold. */
   private boolean leashed(LivingEntity target) {
      double leash = switch (this.role()) {
         case GUARD -> 26.0;
         case ESCORT -> 30.0;
         default -> -1.0;
      };
      if (leash < 0.0) {
         return false;
      }
      Vec3 anchor = this.role() == UltronRole.ESCORT && this.follow != null && this.level() instanceof ServerLevel sl && sl.getEntity(this.follow) != null
         ? sl.getEntity(this.follow).position() : this.post;
      return anchor != null && target.position().distanceToSqr(anchor) > leash * leash && target != this.getLastHurtByMob();
   }

   private boolean keepTarget(LivingEntity target) {
      if (!UltronMob.validTarget(target) || target.level() != this.level()) {
         return false;
      }
      if (!UltronUprising.hostile(this.level()) && target != this.getLastHurtByMob()) {
         return false;
      }
      if (UltronUprising.beyondLeash(this.level(), target.getX(), target.getZ())) {
         return false; // past the event's leash ring nobody is chased (the director then calls the machine home)
      }
      return this.distanceToSqr(target) < this.senseRange() * this.senseRange() * 2.25 && this.lostSight < 120;
   }

   /** Nearest valid target it can see (players first); outside the hostile phases only whoever hurt it. */
   @Nullable
   protected LivingEntity findTarget() {
      if (!UltronUprising.hostile(this.level())) {
         LivingEntity attacker = this.getLastHurtByMob();
         return attacker != null && UltronMob.validTarget(attacker) && this.distanceToSqr(attacker) < 32.0 * 32.0
            && !UltronUprising.beyondLeash(this.level(), attacker.getX(), attacker.getZ()) ? attacker : null;
      }
      double r = this.senseRange();
      List<LivingEntity> list = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(r, r * 0.6, r), UltronMob::validTarget);
      LivingEntity best = null;
      double bestScore = Double.MAX_VALUE;
      for (LivingEntity e : list) {
         double d = this.distanceToSqr(e);
         if (d > r * r || UltronUprising.beyondLeash(this.level(), e.getX(), e.getZ())) {
            continue;
         }
         boolean close = d < 36.0;
         if (!close && !this.sees(e)) {
            continue;
         }
         double score = d * (e instanceof Player ? 1.0 : 1.8);
         if (this.hunt != null && e.getUUID().equals(this.hunt)) {
            score *= 0.25;
         }
         if (score < bestScore) {
            bestScore = score;
            best = e;
         }
      }
      return best;
   }

   /** Chunk-safe line of sight between the eyes (never loads a chunk). */
   public boolean sees(Entity e) {
      return ChitauriTerrain.clear(this.level(), this.getEyePosition(), e.getEyePosition());
   }

   private void updateDamageFlags() {
      float f = this.getHealth() / this.getMaxHealth();
      this.setFlag(FLAG_DAMAGED, f < 0.5F);
      this.setFlag(FLAG_CRITICAL, f < 0.25F);
   }

   // ------------------------------------------------------------------ movement

   /** Ground: path there (repath at most every half second); flyers: steer there at hover height. */
   protected void travelTo(Vec3 dest, double speed) {
      if (this.flier()) {
         this.steerTo(dest, speed * 0.32, this.hoverHeight());
         return;
      }
      Vec3 to = dest.subtract(this.position());
      double far = Math.sqrt(to.x * to.x + to.z * to.z);
      if (far > 40.0) {
         // long march: path in legs (the pathfinder's range), the next leg on the way
         Vec3 step = new Vec3(to.x / far * 32.0, 0.0, to.z / far * 32.0);
         int ground = ChitauriTerrain.surface(this.level(), this.getX() + step.x, this.getZ() + step.z);
         dest = new Vec3(this.getX() + step.x, ground == ChitauriTerrain.UNKNOWN ? this.getY() : ground, this.getZ() + step.z);
      }
      if (this.navCooldown <= 0 || this.navDest == null || this.navDest.distanceToSqr(dest) > 4.0 || this.getNavigation().isDone()) {
         this.navCooldown = 10;
         this.navDest = dest;
         this.getNavigation().moveTo(dest.x, dest.y, dest.z, speed);
      }
   }

   protected void stopMoving() {
      if (this.flier()) {
         this.steer = this.steer.scale(0.85);
      } else {
         this.getNavigation().stop();
         this.navDest = null;
      }
   }

   /** Flyer steering: toward {@code dest}, never below {@code hover} over the ground, climbing over obstacles ahead. */
   protected void steerTo(Vec3 dest, double speed, double hover) {
      Vec3 pos = this.position();
      Vec3 to = dest.subtract(pos);
      double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
      int ground = ChitauriTerrain.surface(this.level(), pos.x, pos.z);
      double floor = ground == ChitauriTerrain.UNKNOWN ? pos.y : ground + hover;
      double wantY = Math.max(dest.y, floor);
      Vec3 dir = horiz > 1.0E-3 ? new Vec3(to.x / horiz, 0.0, to.z / horiz) : Vec3.ZERO;
      double slow = Mth.clamp(horiz / 6.0, 0.0, 1.0);
      Vec3 want = dir.scale(speed * slow).add(0.0, Mth.clamp((wantY - pos.y) * 0.12, -speed, speed), 0.0);
      // obstacle ahead: climb
      if (horiz > 1.0) {
         Vec3 ahead = pos.add(0.0, this.getBbHeight() * 0.5, 0.0);
         Vec3 probe = ahead.add(dir.scale(4.0 + speed * 8.0));
         if (!ChitauriTerrain.clear(this.level(), ahead, probe)) {
            want = new Vec3(want.x * 0.3, Math.max(want.y, speed * 0.8), want.z * 0.3);
         }
      }
      this.steer = want;
   }

   @Override
   public void travel(Vec3 input) {
      if (!this.flier() || !this.isAlive()) {
         super.travel(input);
         return;
      }
      if (this.level().isClientSide) {
         return;
      }
      Vec3 v = this.getDeltaMovement();
      Vec3 next = v.add(this.steer.subtract(v).scale(0.18));
      this.setDeltaMovement(next);
      this.move(MoverType.SELF, next);
   }

   /** Turns the body toward a point at most {@code maxStep} degrees this tick (flyers face their target or travel). */
   protected void faceTowards(Vec3 at, float maxStep) {
      double dx = at.x - this.getX();
      double dz = at.z - this.getZ();
      if (dx * dx + dz * dz < 1.0E-4) {
         return;
      }
      float want = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
      float yaw = Mth.approachDegrees(this.getYRot(), want, maxStep);
      this.setYRot(yaw);
      this.yBodyRot = yaw;
      this.yHeadRot = yaw;
   }

   /** Repath after 3 s without progress, hop after 5 s, a safe nudge after 8 s. */
   private void antiStuck() {
      if (this.flier()) {
         return;
      }
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
         this.navDest = null;
      } else if (this.stuckChecks == 5 && this.onGround()) {
         float a = this.random.nextFloat() * Mth.TWO_PI;
         this.setDeltaMovement(Mth.cos(a) * 0.3, 0.5, Mth.sin(a) * 0.3);
         this.hasImpulse = true;
      } else if (this.stuckChecks >= 8) {
         this.stuckChecks = 0;
         this.navDest = null;
      }
   }

   // ------------------------------------------------------------------ combat helpers

   /** Group and core-tether scaling of this unit's damage (1 outside an event). */
   protected float damageScale() {
      return UltronUprising.unitDamageScale(this.level());
   }

   /** A melee strike in front (arc 80°): damage, then a push away (and up). */
   protected boolean strike(@Nullable LivingEntity target, float damage, double reach, double push, double lift) {
      if (target == null || !target.isAlive()) {
         return false;
      }
      double d = this.distanceTo(target) - target.getBbWidth() * 0.5 - this.getBbWidth() * 0.5;
      if (d > reach) {
         return false;
      }
      Vec3 to = target.position().subtract(this.position());
      float yawTo = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
      if (Math.abs(Mth.wrapDegrees(yawTo - this.yBodyRot)) > 80.0F && d > 0.6) {
         return false;
      }
      boolean hit = target.hurt(this.damageSources().mobAttack(this), damage * this.damageScale());
      if (hit && push > 0.0 && dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsImpulse(target)) {
         Vec3 dir = new Vec3(to.x, 0.0, to.z).normalize();
         double resist = 1.0 - target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
         target.push(dir.x * push * resist, lift * resist, dir.z * push * resist);
         target.hurtMarked = true;
      }
      return hit;
   }

   /** Where a target will be when a shot of {@code speed} blocks/tick arrives (simple lead). */
   protected Vec3 lead(LivingEntity target, Vec3 from, double speed, double amount) {
      Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
      double flight = from.distanceTo(aim) / Math.max(0.5, speed);
      return aim.add(target.getDeltaMovement().multiply(1.0, 0.3, 1.0).scale(flight * amount));
   }

   protected void fx(int kind, Vec3 at, Vec3 d, float power, int extra) {
      if (this.level() instanceof ServerLevel level) {
         UltronNet.fx(level, new UltronFxS2CPacket(kind, at, d, power, extra), at, 96.0);
      }
   }

   protected void sound(net.minecraftforge.registries.RegistryObject<SoundEvent> s, float volume, float pitch) {
      if (this.level() instanceof ServerLevel level) {
         UltronSounds.play(level, this.position(), s, volume, pitch * (0.94F + this.random.nextFloat() * 0.12F));
      }
   }

   // ------------------------------------------------------------------ damage

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.level().isClientSide) {
         return false;
      }
      Entity attacker = source.getEntity();
      Entity direct = source.getDirectEntity();
      if (UltronMob.side(attacker) || UltronMob.side(direct)) {
         return false; // no friendly fire
      }
      if (source.is(DamageTypes.IN_WALL) && this.flier() || source.is(DamageTypes.DROWN)) {
         return false;
      }
      boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
      float dealt = amount;
      boolean ranged = source.is(DamageTypeTags.IS_PROJECTILE) || attacker != null && attacker.distanceTo(this) > 5.0F;
      if (!bypass && ranged && (this.flag(FLAG_DEFLECTOR) || this.flag(FLAG_SHIELD)) && this.frontal(source)) {
         dealt *= this.flag(FLAG_SHIELD) ? 0.15F : 1.0F - DEFLECT_BLOCK;
         Vec3 at = source.getSourcePosition() != null ? this.position().add(source.getSourcePosition().subtract(this.position()).normalize().scale(0.8))
            .add(0.0, this.getBbHeight() * 0.55, 0.0) : this.getEyePosition();
         this.fx(UltronFxS2CPacket.DEFLECT, at, Vec3.ZERO, 1.0F, this.getId());
         this.sound(UltronSounds.DEFLECT, 0.9F, 1.0F);
      }
      if (!bypass) {
         dealt *= UltronUprising.bulwarkFactor(this);
      }
      boolean hit = super.hurt(source, dealt);
      if (hit && !bypass) {
         UltronUprising.unitHurt(this, source, dealt, ranged);
         if (dealt >= this.getMaxHealth() * HEAVY_SHARE && this.heavyCooldown <= 0 && this.isAlive()
            && (this.move == UltronMove.NONE || this.interruptible(this.move))) {
            this.heavyCooldown = 60;
            this.forceMove(UltronMove.HEAVY_HIT, null);
         }
      }
      return hit;
   }

   /** Plain living-entity damage (Ultron Prime runs his own caps, then this). */
   protected final boolean baseHurt(DamageSource source, float amount) {
      return super.hurt(source, amount);
   }

   @Override
   public boolean canChangeDimensions() {
      return false;
   }

   /** The hit came from the unit's front half-cone (where the deflector points). */
   protected boolean frontal(DamageSource source) {
      Vec3 from = source.getSourcePosition();
      if (from == null) {
         return false;
      }
      Vec3 to = from.subtract(this.position());
      float yawTo = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
      return Math.abs(Mth.wrapDegrees(yawTo - this.yBodyRot)) <= DEFLECT_ARC;
   }

   @Override
   public void die(DamageSource source) {
      super.die(source);
      if (!this.level().isClientSide) {
         this.move = UltronMove.NONE;
         this.getNavigation().stop();
         if (this.flier()) {
            this.setNoGravity(false);
         }
         UltronUprising.unitKilled(this, source);
      }
   }

   @Override
   protected void tickDeath() {
      this.deathTime++;
      if (this.flier() && !this.onGround()) {
         this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.04, 0.0));
      }
      if (this.deathTime >= this.deathTicks() && !this.level().isClientSide && !this.isRemoved()) {
         Vec3 at = this.position().add(0.0, this.getBbHeight() * 0.4, 0.0);
         this.fx(UltronFxS2CPacket.UNIT_BREAKUP, at, Vec3.ZERO, this.getBbWidth() * this.getBbHeight(), this.kind().ordinal());
         this.sound(UltronSounds.DEBRIS, 0.8F + this.getBbHeight() * 0.2F, 1.1F - this.getBbHeight() * 0.1F);
         this.remove(Entity.RemovalReason.KILLED);
      }
   }

   /** Removed by the director without a death (event over, shutdown cascade): the destruction plays, no report. */
   public void shutdown() {
      if (this.isAlive()) {
         this.move = UltronMove.NONE;
         this.setHealth(0.0F);
         this.dead = true;
         this.deathTime = 0;
         this.level().broadcastEntityEvent(this, (byte)3);
         if (this.flier()) {
            this.setNoGravity(false);
         }
      }
   }

   // ------------------------------------------------------------------ own sounds, no loot, no save

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return this.hurtSound();
   }

   @Override
   protected SoundEvent getDeathSound() {
      return this.deathSound();
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState state) {
   }

   @Override
   public LivingEntity.Fallsounds getFallSounds() {
      return new LivingEntity.Fallsounds(UltronSounds.DEBRIS.get(), UltronSounds.DEBRIS.get());
   }

   @Override
   protected void playBlockFallSound() {
   }

   @Override
   protected SoundEvent getSwimSound() {
      return UltronSounds.SPLASH.get();
   }

   @Override
   protected SoundEvent getSwimSplashSound() {
      return UltronSounds.SPLASH.get();
   }

   @Override
   protected SoundEvent getSwimHighSpeedSplashSound() {
      return UltronSounds.SPLASH.get();
   }

   @Override
   public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
      return !this.flier() && super.causeFallDamage(Math.max(0.0F, distance - 6.0F), multiplier * 0.5F, source);
   }

   @Override
   public boolean canBreatheUnderwater() {
      return true;
   }

   @Override
   public boolean isAlliedTo(Entity other) {
      return UltronMob.side(other) || super.isAlliedTo(other);
   }

   @Override
   public boolean canAttack(LivingEntity target) {
      return UltronMob.validTarget(target) && super.canAttack(target);
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
   public void checkDespawn() {
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
   }
}
