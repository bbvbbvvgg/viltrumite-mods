package dev.baranhan.viltrumitecore.entity.godzilla;

import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.entity.ControlResistant;
import dev.baranhan.viltrumitecore.worldevent.godzilla.GodzillaEvent;
import dev.baranhan.viltrumitecore.worldevent.godzilla.GodzillaMove;
import dev.baranhan.viltrumitecore.worldevent.godzilla.GodzillaRules;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import org.joml.Vector3f;

/**
 * The kaiju of the Godzilla city catastrophe. A body and a motor, no AI goals: the event session
 * ({@code worldevent/godzilla}) decides where it walks, what it looks at and which move it starts; the body walks
 * on the street height through the buildings, turns its whole mass slowly, keeps the gait phase, the head look and
 * the lagging tail chain, and places its sixteen hit zones ({@link GodzillaPart}) every tick on both sides.
 * Synced: the move (with a sequence number and an argument), flags, head yaw / pitch, gait phase, beam end and
 * beam length. Never saved: a restart ends the event.
 */
public class GodzillaEntity extends Mob implements Enemy, ControlResistant {
   private static final EntityDataAccessor<Byte> MOVE = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> MOVE_ARG = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Integer> MOVE_SEQ = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Float> HEAD_YAW = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> HEAD_PITCH = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> GAIT = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Vector3f> BEAM_END = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.VECTOR3);
   private static final EntityDataAccessor<Integer> FIRE_LEN = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> BODY_YAW = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.FLOAT);

   public static final int FLAG_RAGE = 1;
   public static final int FLAG_RETREAT = 2;
   public static final int FLAG_FINALE = 4;
   public static final int FLAG_BEAM = 8;
   public static final int FLAG_WALKING = 16;

   private final GodzillaPart[] parts;
   // ---- motor (server)
   @Nullable
   private Vec3 walkTarget;
   @Nullable
   private Vec3 faceTarget;
   @Nullable
   private Vec3 lookTarget;
   private boolean beamAim;
   private double groundY = Double.NaN;
   private int groundRecheck;
   private int moveTick;
   private double gait;
   private float headYaw;
   private float headPitch;
   private float walkAmount;
   private float damageThisTick;
   private long damageTick = Long.MIN_VALUE;
   @Nullable
   private Vec3 lastHitAt;
   /** Extra forward speed of a lunge this tick (body slam), set by the session. */
   public float lunge;
   /** The retreat's end: the body sinks (into the bay, under the streets) instead of following the ground. */
   public boolean sinking;
   private boolean turnedLastTick;
   /** Slows the walk while the destruction queue is behind (pushing through a dense block). */
   public float drag = 1.0F;
   // ---- tail chain (both sides): world yaw of each segment
   private final float[] tailYaw = new float[GodzillaRules.TAIL_SEGMENTS];
   public final float[] tailYawO = new float[GodzillaRules.TAIL_SEGMENTS];
   private boolean tailReady;
   private AABB cullBox;
   // ---- client view state
   public int clientMoveStart;
   /** Client: the move before the current one, its argument and its start (the renderer cross-fades out of it). */
   public GodzillaMove clientPrevMove = GodzillaMove.NONE;
   public int clientPrevArg;
   public int clientPrevStart;
   private GodzillaMove clientMove = GodzillaMove.NONE;
   private int clientArg;
   /** Client: the level of detail the renderer drew last frame (hysteresis between the LOD distances). */
   public int clientLod = -1;
   public float clientHeadYaw;
   public float clientHeadYawO;
   public float clientHeadPitch;
   public float clientHeadPitchO;
   public float clientGait;
   public float clientGaitO;
   public float clientWalk;
   public float clientWalkO;
   public float clientYaw;
   public float clientYawO;
   private boolean clientYawReady;
   public Vec3 clientBeamEnd = Vec3.ZERO;
   public Vec3 clientBeamEndO = Vec3.ZERO;
   /** Client: the mouth and the head in world space, published by the renderer each frame (effects, beam origin). */
   @Nullable
   public Vec3 clientMouth;
   public Vec3 clientMouthDir = new Vec3(0.0, 0.0, 1.0);
   public int clientMouthTick = -100;

   public GodzillaEntity(EntityType<? extends GodzillaEntity> type, Level level) {
      super(type, level);
      this.parts = new GodzillaPart[GodzillaRules.PARTS];
      for (int i = 0; i < this.parts.length; i++) {
         this.parts[i] = new GodzillaPart(this, i);
      }
      this.setId(ENTITY_COUNTER.getAndAdd(this.parts.length + 1) + 1);
      this.setNoGravity(true);
      this.noPhysics = true;
      this.xpReward = 0;
      this.setPersistenceRequired();
      this.cullBox = this.getBoundingBox().inflate(GodzillaRules.HEIGHT);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, GodzillaRules.MAX_HEALTH)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
         .add(Attributes.FOLLOW_RANGE, 160.0)
         .add(Attributes.MOVEMENT_SPEED, 0.0)
         .add(Attributes.ATTACK_DAMAGE, GodzillaRules.CLAW_DAMAGE);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(MOVE, (byte)0);
      this.entityData.define(MOVE_ARG, (byte)0);
      this.entityData.define(MOVE_SEQ, 0);
      this.entityData.define(FLAGS, (byte)0);
      this.entityData.define(HEAD_YAW, 0.0F);
      this.entityData.define(HEAD_PITCH, 0.0F);
      this.entityData.define(GAIT, 0.0F);
      this.entityData.define(BEAM_END, new Vector3f());
      this.entityData.define(FIRE_LEN, GodzillaRules.FIRE_TICKS);
      this.entityData.define(BODY_YAW, 0.0F);
   }

   @Override
   public boolean allowsControl(ControlKind kind) {
      return false;
   }

   // ------------------------------------------------------------------ parts

   @Override
   public boolean isMultipartEntity() {
      return true;
   }

   @Override
   public PartEntity<?>[] getParts() {
      return this.parts;
   }

   public GodzillaPart[] parts() {
      return this.parts;
   }

   @Override
   public void setId(int id) {
      super.setId(id);
      if (this.parts != null) {
         for (int i = 0; i < this.parts.length; i++) {
            this.parts[i].setId(id + i + 1);
         }
      }
   }

   // ------------------------------------------------------------------ synced state

   public GodzillaMove move() {
      return GodzillaMove.byId(this.entityData.get(MOVE));
   }

   public int moveArg() {
      return this.entityData.get(MOVE_ARG);
   }

   /** Server: ticks since the move started. */
   public int moveTick() {
      return this.moveTick;
   }

   /** Ticks since the move started on this side (client: since the sync arrived) plus the partial tick. */
   public float moveTime(float partial) {
      return this.level().isClientSide ? this.tickCount - this.clientMoveStart + partial : this.moveTick + partial;
   }

   public boolean flag(int f) {
      return (this.entityData.get(FLAGS) & f) != 0;
   }

   public void setFlag(int f, boolean on) {
      byte b = this.entityData.get(FLAGS);
      byte n = (byte)(on ? b | f : b & ~f);
      if (n != b) {
         this.entityData.set(FLAGS, n);
      }
   }

   public boolean rage() {
      return this.flag(FLAG_RAGE);
   }

   public boolean retreating() {
      return this.flag(FLAG_RETREAT);
   }

   public int fireLength() {
      return this.entityData.get(FIRE_LEN);
   }

   public Vec3 beamEnd() {
      Vector3f v = this.entityData.get(BEAM_END);
      return new Vec3(v.x, v.y, v.z);
   }

   /** Server: the beam end this tick (synced) and whether the beam is on. */
   public void setBeam(@Nullable Vec3 end) {
      if (end != null) {
         this.entityData.set(BEAM_END, new Vector3f((float)end.x, (float)end.y, (float)end.z));
      }
      this.setFlag(FLAG_BEAM, end != null);
   }

   /** Server: starts a timed move (restarts the clip on every client). */
   public void startMove(GodzillaMove move, int arg) {
      this.moveTick = 0;
      this.entityData.set(MOVE, (byte)move.ordinal());
      this.entityData.set(MOVE_ARG, (byte)arg);
      this.entityData.set(MOVE_SEQ, this.entityData.get(MOVE_SEQ) + 1);
      if (move == GodzillaMove.FIRE) {
         this.entityData.set(FIRE_LEN, Math.max(1, arg));
      }
   }

   public void endMove() {
      if (this.move() != GodzillaMove.NONE) {
         this.startMove(GodzillaMove.NONE, 0);
      }
   }

   public float headYaw() {
      return this.headYaw;
   }

   public float headPitch() {
      return this.headPitch;
   }

   public double gait() {
      return this.gait;
   }

   public float walkAmount() {
      return this.walkAmount;
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (MOVE_SEQ.equals(key) && this.level().isClientSide) {
         // MOVE and MOVE_ARG come before MOVE_SEQ in the same data packet: they already hold the new move
         this.clientPrevMove = this.clientMove;
         this.clientPrevArg = this.clientArg;
         this.clientPrevStart = this.clientMoveStart;
         this.clientMoveStart = this.tickCount;
         this.clientMove = this.move();
         this.clientArg = this.moveArg();
      }
   }

   // ------------------------------------------------------------------ motor commands (server)

   public void walkTo(@Nullable Vec3 target) {
      this.walkTarget = target;
   }

   @Nullable
   public Vec3 walkTarget() {
      return this.walkTarget;
   }

   public void faceTowards(@Nullable Vec3 target) {
      this.faceTarget = target;
   }

   public void lookAt(@Nullable Vec3 target, boolean beam) {
      this.lookTarget = target;
      this.beamAim = beam;
   }

   @Nullable
   public Vec3 lookTarget() {
      return this.lookTarget;
   }

   // ------------------------------------------------------------------ body frame

   public static Vec3 forward(float yawDeg) {
      float r = yawDeg * Mth.DEG_TO_RAD;
      return new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
   }

   public static Vec3 left(float yawDeg) {
      float r = yawDeg * Mth.DEG_TO_RAD;
      return new Vec3(Mth.cos(r), 0.0, Mth.sin(r));
   }

   /** Minecraft yaw that faces along (dx, dz). */
   public static float yawTowards(double dx, double dz) {
      return (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
   }

   private float tilt() {
      return GodzillaRules.fallTilt(this.move(), this.moveTime(0.0F));
   }

   /** World point of a body-frame offset (fractions of HEIGHT: left, up, forward), fall roll included. */
   public Vec3 bodyPoint(double l, double u, double f) {
      return this.bodyPoint(l, u, f, this.position(), this.getYRot(), this.tilt());
   }

   public Vec3 bodyPoint(float[] p) {
      return this.bodyPoint(p[0], p[1], p[2]);
   }

   private Vec3 bodyPoint(double l, double u, double f, Vec3 at, float yaw, float tilt) {
      if (tilt > 0.001F) {
         double a = tilt * GodzillaRules.FALL_ROLL * Mth.DEG_TO_RAD;
         double ll = l - GodzillaRules.FALL_PIVOT;
         double nl = ll * Math.cos(a) + u * Math.sin(a);
         double nu = -ll * Math.sin(a) + u * Math.cos(a);
         l = nl + GodzillaRules.FALL_PIVOT;
         u = Math.max(0.02, nu);
      }
      Vec3 fw = forward(yaw);
      Vec3 lf = left(yaw);
      double h = GodzillaRules.HEIGHT;
      return new Vec3(at.x + (lf.x * l + fw.x * f) * h, at.y + u * h, at.z + (lf.z * l + fw.z * f) * h);
   }

   /** A head-frame offset (left, up, forward) turned by the head yaw / pitch about the neck pivot, in the body frame. */
   private static double[] headFrame(float[] off, float yaw, float pitch) {
      double p = pitch * Mth.DEG_TO_RAD;
      double y = yaw * Mth.DEG_TO_RAD;
      double f1 = off[2] * Math.cos(p) - off[1] * Math.sin(p);
      double u1 = off[2] * Math.sin(p) + off[1] * Math.cos(p);
      double l1 = off[0];
      double l2 = l1 * Math.cos(y) - f1 * Math.sin(y);
      double f2 = l1 * Math.sin(y) + f1 * Math.cos(y);
      float[] n = GodzillaRules.NECK_PIVOT;
      return new double[]{n[0] + l2, n[1] + u1, n[2] + f2};
   }

   public Vec3 mouth() {
      double[] p = headFrame(GodzillaRules.MOUTH_OFFSET, this.headYaw, this.headPitch);
      return this.bodyPoint(p[0], p[1], p[2]);
   }

   public Vec3 headCentre() {
      double[] p = headFrame(GodzillaRules.HEAD_OFFSET, this.headYaw, this.headPitch);
      return this.bodyPoint(p[0], p[1], p[2]);
   }

   /** World direction the head (and the beam) points to. */
   public Vec3 headDir() {
      float yaw = this.getYRot() + this.headYaw;
      double p = this.headPitch * Mth.DEG_TO_RAD;
      Vec3 fw = forward(yaw);
      return new Vec3(fw.x * Math.cos(p), Math.sin(p), fw.z * Math.cos(p));
   }

   /** Ground point of a foot (planted or not). */
   public Vec3 foot(boolean left) {
      float[] leg = GodzillaRules.PART_REST[left ? GodzillaRules.P_LEG_L : GodzillaRules.P_LEG_R];
      return this.bodyPoint(leg[0], 0.0, leg[2] + GodzillaRules.footForward(this.gait, left));
   }

   /** Centre of tail segment k (both sides; from the chain). */
   public Vec3 tailCentre(int k) {
      return this.parts[GodzillaRules.P_TAIL + k].getBoundingBox().getCenter();
   }

   // ------------------------------------------------------------------ tick

   @Override
   public void tick() {
      super.tick();
      this.setYBodyRot(this.getYRot());
      this.setYHeadRot(this.getYRot());
      if (!this.level().isClientSide) {
         ServerLevel level = (ServerLevel)this.level();
         if (!GodzillaEvent.entityTick(level, this)) {
            this.discard();
            return;
         }
         this.motor(level);
         this.look();
         this.moveTick++;
      } else {
         this.clientView();
      }
      this.updateTail();
      this.placeParts();
   }

   private void motor(ServerLevel level) {
      GodzillaMove move = this.move();
      boolean dead = this.isDeadOrDying();
      boolean locked = move.locksWalk || dead;
      float yaw = this.getYRot();
      float turned = 0.0F;
      double moved = 0.0;
      double nx = this.getX();
      double nz = this.getZ();
      if (!locked && this.walkTarget != null) {
         double dx = this.walkTarget.x - nx;
         double dz = this.walkTarget.z - nz;
         double dist = Math.sqrt(dx * dx + dz * dz);
         if (dist > 1.5) {
            float err = GodzillaRules.wrap(yawTowards(dx, dz) - yaw);
            float rate = GodzillaRules.turnRate(true, this.rage());
            turned = Mth.clamp(err, -rate, rate);
            yaw += turned;
            float align = Math.abs(err) > 60.0F ? 0.12F : Math.abs(err) > 25.0F ? 0.5F : 1.0F;
            double sp = Math.min(dist, GodzillaRules.speed(this.rage(), this.retreating()) * align * this.drag);
            Vec3 fw = forward(yaw);
            Vec3 lead = fw.scale(0.12 * GodzillaRules.HEIGHT);
            if (GodzillaEvent.canEnter(level, nx + fw.x * sp + lead.x, nz + fw.z * sp + lead.z)) {
               nx += fw.x * sp;
               nz += fw.z * sp;
               moved = sp;
            }
         }
      } else if (!dead && this.faceTarget != null && !move.grounded()) {
         float err = GodzillaRules.wrap(yawTowards(this.faceTarget.x - nx, this.faceTarget.z - nz) - yaw);
         boolean breath = move == GodzillaMove.CHARGE || move == GodzillaMove.FIRE;
         if (!locked || breath || move == GodzillaMove.ROAR) {
            float rate = breath ? (Math.abs(this.headYaw) > GodzillaRules.BODY_FOLLOW || Math.abs(err) > GodzillaRules.HEAD_YAW_MAX
               ? GodzillaRules.BODY_FOLLOW_TURN : 0.0F) : GodzillaRules.turnRate(false, this.rage());
            if (Math.abs(err) > 3.0F) {
               turned = Mth.clamp(err, -rate, rate);
               yaw += turned;
            }
         }
      }
      if (this.lunge > 0.0F && !dead) {
         Vec3 fw = forward(yaw);
         nx += fw.x * this.lunge;
         nz += fw.z * this.lunge;
         moved += this.lunge;
         this.lunge = 0.0F;
      }
      double ny = this.followGround(level, nx, nz);
      this.setPos(nx, ny, nz);
      this.setYRot(yaw);
      this.setDeltaMovement(Vec3.ZERO);
      this.turnedLastTick = Math.abs(turned) > 0.2F;
      if (Math.abs(this.entityData.get(BODY_YAW) - yaw) > 0.01F) {
         this.entityData.set(BODY_YAW, yaw);
      }
      this.walkAmount = Mth.approach(this.walkAmount, moved > 0.05 || Math.abs(turned) > 0.2F ? 1.0F : 0.0F, 0.08F);
      this.setFlag(FLAG_WALKING, moved > 0.05 || Math.abs(turned) > 0.2F);
      if (!locked && (moved > 0.0 || turned != 0.0F)) {
         double before = this.gait;
         this.gait += GodzillaRules.gaitAdvance(moved, turned);
         int foot = GodzillaRules.footfall(before, this.gait);
         if (foot != 0) {
            GodzillaEvent.onFootfall(level, this, foot == 1);
         }
      }
      if (this.gait > 1000.0) {
         this.gait -= 1000.0;
      }
      this.entityData.set(GAIT, (float)(this.gait - Math.floor(this.gait)));
   }

   /** Street height under the body (the city walk grid, else the lowest ground of the footprint), approached slowly. */
   private double followGround(ServerLevel level, double x, double z) {
      if (this.sinking) {
         return this.getY() - 0.22;
      }
      if (--this.groundRecheck <= 0 || Double.isNaN(this.groundY)) {
         this.groundRecheck = 5;
         double g = GodzillaEvent.groundAt(level, x, z, this.getY());
         if (!Double.isNaN(g)) {
            this.groundY = g;
         }
      }
      if (Double.isNaN(this.groundY)) {
         return this.getY();
      }
      double d = this.groundY - this.getY();
      return this.getY() + Mth.clamp(d, -GodzillaRules.CLIMB_PER_TICK, GodzillaRules.CLIMB_PER_TICK);
   }

   private void look() {
      float wantYaw = 0.0F;
      float wantPitch = 0.0F;
      if (this.lookTarget != null && !this.move().grounded() && this.isAlive()) {
         Vec3 n = this.bodyPoint(GodzillaRules.NECK_PIVOT);
         Vec3 d = this.lookTarget.subtract(n);
         double flat = Math.sqrt(d.x * d.x + d.z * d.z);
         wantYaw = GodzillaRules.clampYaw(GodzillaRules.wrap(yawTowards(d.x, d.z) - this.getYRot()));
         wantPitch = GodzillaRules.clampPitch((float)(Mth.atan2(d.y, flat) * Mth.RAD_TO_DEG));
      }
      float rate = this.beamAim ? GodzillaRules.beamTurn(this.rage()) : GodzillaRules.HEAD_TURN;
      this.headYaw = GodzillaRules.approach(this.headYaw, wantYaw, rate);
      this.headPitch = GodzillaRules.approach(this.headPitch, wantPitch, rate);
      if (Math.abs(this.entityData.get(HEAD_YAW) - this.headYaw) > 0.05F) {
         this.entityData.set(HEAD_YAW, this.headYaw);
      }
      if (Math.abs(this.entityData.get(HEAD_PITCH) - this.headPitch) > 0.05F) {
         this.entityData.set(HEAD_PITCH, this.headPitch);
      }
   }

   /** Server: the body turned last tick (the tail swings through things even on the spot). */
   public boolean turning() {
      return this.turnedLastTick;
   }

   /** Client: the precise body yaw (the vanilla rotation packet is quantised to 1.4 degrees, a lot at this size). */
   public float renderYaw(float partial) {
      return this.clientYawReady ? Mth.rotLerp(partial, this.clientYawO, this.clientYaw) : Mth.rotLerp(partial, this.yRotO, this.getYRot());
   }

   private void clientView() {
      float synced = this.entityData.get(BODY_YAW);
      if (!this.clientYawReady) {
         this.clientYaw = synced;
         this.clientYawO = synced;
         this.clientYawReady = true;
      }
      this.clientYawO = this.clientYaw;
      this.clientYaw += Mth.wrapDegrees(synced - this.clientYaw) * 0.5F;
      this.setYRot(this.clientYaw);
      this.yRotO = this.clientYawO;
      this.setYBodyRot(this.clientYaw);
      this.clientHeadYawO = this.clientHeadYaw;
      this.clientHeadPitchO = this.clientHeadPitch;
      this.clientHeadYaw += (this.entityData.get(HEAD_YAW) - this.clientHeadYaw) * 0.5F;
      this.clientHeadPitch += (this.entityData.get(HEAD_PITCH) - this.clientHeadPitch) * 0.5F;
      this.headYaw = this.clientHeadYaw;
      this.headPitch = this.clientHeadPitch;
      this.clientGaitO = this.clientGait;
      float g = this.entityData.get(GAIT);
      float d = g - (this.clientGait - Mth.floor(this.clientGait));
      if (d < -0.5F) {
         d += 1.0F;
      } else if (d > 0.5F) {
         d -= 1.0F;
      }
      this.clientGait += d;
      this.gait = this.clientGait;
      this.clientWalkO = this.clientWalk;
      this.clientWalk = Mth.approach(this.clientWalk, this.flag(FLAG_WALKING) && !this.move().locksWalk ? 1.0F : 0.0F, 0.08F);
      this.walkAmount = this.clientWalk;
      this.clientBeamEndO = this.clientBeamEnd;
      Vec3 end = this.beamEnd();
      this.clientBeamEnd = this.clientBeamEnd.lengthSqr() < 1.0 || this.clientBeamEnd.distanceToSqr(end) > 400.0 * 400.0 ? end
         : this.clientBeamEnd.add(end.subtract(this.clientBeamEnd).scale(0.6));
   }

   /** The lagging tail: each segment follows the one before it (inertia), plus the walk sway and the tail strike. */
   private void updateTail() {
      float body = this.getYRot();
      if (!this.tailReady) {
         for (int k = 0; k < this.tailYaw.length; k++) {
            this.tailYaw[k] = body;
            this.tailYawO[k] = body;
         }
         this.tailReady = true;
      }
      GodzillaMove move = this.move();
      float t = this.moveTime(0.0F);
      int side = this.moveArg() >= 0 ? 1 : -1;
      boolean strike = move == GodzillaMove.TAIL;
      float twist = strike ? GodzillaRules.hipsTwist(t, side) : 0.0F;
      for (int k = 0; k < this.tailYaw.length; k++) {
         this.tailYawO[k] = this.tailYaw[k];
         float base = k == 0 ? body + twist : this.tailYaw[k - 1];
         float target = base + GodzillaRules.tailSway(k, this.gait, this.walkAmount) + (strike ? GodzillaRules.tailStrike(k, t, side) : 0.0F);
         this.tailYaw[k] += GodzillaRules.wrap(target - this.tailYaw[k]) * GodzillaRules.TAIL_FOLLOW[k];
      }
   }

   /** World yaw of tail segment k at a partial tick (renderer). */
   public float tailYaw(int k, float partial) {
      return Mth.rotLerp(partial, this.tailYawO[k], this.tailYaw[k]);
   }

   private void placeParts() {
      AABB box = null;
      for (int i = 0; i < GodzillaRules.P_TAIL; i++) {
         Vec3 c;
         if (i == GodzillaRules.P_HEAD) {
            c = this.headCentre();
         } else if (i == GodzillaRules.P_LEG_L || i == GodzillaRules.P_LEG_R) {
            float[] r = GodzillaRules.PART_REST[i];
            c = this.bodyPoint(r[0], r[1], r[2] + 0.5F * GodzillaRules.footForward(this.gait, i == GodzillaRules.P_LEG_L));
         } else {
            c = this.bodyPoint(GodzillaRules.PART_REST[i]);
         }
         this.parts[i].placeCentre(c.x, c.y, c.z);
         box = box == null ? this.parts[i].getBoundingBox() : box.minmax(this.parts[i].getBoundingBox());
      }
      // tail chain in the body frame (relative yaws), then through the same roll as the body
      float body = this.getYRot();
      float[] root = GodzillaRules.TAIL_ROOT;
      double l = root[0];
      double u = root[1];
      double f = root[2];
      for (int k = 0; k < GodzillaRules.TAIL_SEGMENTS; k++) {
         double rel = GodzillaRules.wrap(this.tailYaw[k] - body) * Mth.DEG_TO_RAD;
         double p = GodzillaRules.TAIL_PITCH[k] * Mth.DEG_TO_RAD;
         double len = GodzillaRules.TAIL_LENGTH[k];
         double dl = Math.sin(rel) * Math.cos(p) * len;
         double du = -Math.sin(p) * len;
         double df = -Math.cos(rel) * Math.cos(p) * len;
         Vec3 c = this.bodyPoint(l + dl * 0.5, Math.max(0.03, u + du * 0.5), f + df * 0.5);
         this.parts[GodzillaRules.P_TAIL + k].placeCentre(c.x, c.y, c.z);
         box = box.minmax(this.parts[GodzillaRules.P_TAIL + k].getBoundingBox());
         l += dl;
         u = Math.max(0.03, u + du);
         f += df;
      }
      if (this.level().isClientSide && this.flag(FLAG_BEAM)) {
         Vec3 e = this.clientBeamEnd;
         box = box.minmax(new AABB(e, e).inflate(8.0));
      }
      this.cullBox = box.inflate(4.0);
   }

   // ------------------------------------------------------------------ damage and death

   public boolean hurtFromPart(GodzillaPart part, DamageSource source, float amount) {
      this.lastHitAt = part.getBoundingBox().getCenter();
      return this.hurtZone(source, amount, part.damageFactor, part.index);
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      this.lastHitAt = null;
      return this.hurtZone(source, amount, GodzillaRules.PART_FACTOR[GodzillaRules.P_BELLY], -1);
   }

   private boolean hurtZone(DamageSource source, float amount, float factor, int zone) {
      if (this.level().isClientSide) {
         return false;
      }
      Entity by = source.getEntity();
      if (by == this || source.getDirectEntity() == this || by instanceof GodzillaPart || this.isDeadOrDying()) {
         return false;
      }
      if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING)
         || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypeTags.IS_FIRE)
         || source.is(DamageTypes.FREEZE) || source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_ANVIL)) {
         return false;
      }
      boolean bypass = source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD);
      float dealt = GodzillaRules.hit(amount, factor, bypass);
      if (source.is(DamageTypeTags.IS_EXPLOSION) && !bypass) {
         dealt *= 0.5F;
      }
      long now = this.level().getGameTime();
      if (now != this.damageTick) {
         this.damageTick = now;
         this.damageThisTick = 0.0F;
      }
      if (!bypass) {
         dealt = GodzillaRules.allowance(this.damageThisTick, dealt);
      }
      if (dealt <= 0.0F) {
         return false;
      }
      this.invulnerableTime = 0;
      boolean hit = super.hurt(source, dealt);
      this.invulnerableTime = 0;
      this.hurtTime = 0;
      if (hit) {
         this.damageThisTick += dealt;
         Vec3 at = this.lastHitAt != null ? this.lastHitAt : this.bodyPoint(GodzillaRules.PART_REST[GodzillaRules.P_BELLY]);
         GodzillaEvent.onHurt((ServerLevel)this.level(), this, source, dealt, zone, at);
      }
      return hit;
   }

   @Override
   public void knockback(double strength, double x, double z) {
   }

   @Override
   public void die(DamageSource source) {
      super.die(source);
      if (this.level() instanceof ServerLevel level) {
         GodzillaEvent.onDeath(level, this);
      }
   }

   @Override
   protected void tickDeath() {
      this.deathTime = Math.min(this.deathTime + 1, 10000);
      // the session removes the body; a stray corpse goes on its own
      if (!this.level().isClientSide && this.deathTime > GodzillaMove.DEATH.length + GodzillaRules.CORPSE_TICKS + 200) {
         this.remove(RemovalReason.KILLED);
      }
   }

   @Override
   public boolean isInvulnerableTo(DamageSource source) {
      return super.isInvulnerableTo(source) || source.getEntity() instanceof GodzillaEntity;
   }

   // ------------------------------------------------------------------ misc

   @Override
   public boolean isInWall() {
      return false;
   }

   @Override
   public boolean canBreatheUnderwater() {
      return true;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   protected void pushEntities() {
   }

   @Override
   public boolean canBeCollidedWith() {
      return false;
   }

   @Override
   public boolean isPickable() {
      return this.isAlive();
   }

   @Override
   public boolean isPushedByFluid() {
      return false;
   }

   @Override
   public boolean canChangeDimensions() {
      return false;
   }

   @Override
   public boolean canFreeze() {
      return false;
   }

   @Override
   public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
      return false;
   }

   @Override
   protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
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
   public void checkDespawn() {
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) {
      return false;
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double distance) {
      return distance < 768.0 * 768.0;
   }

   @Override
   public AABB getBoundingBoxForCulling() {
      return this.cullBox;
   }

   @Override
   protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
      return null;
   }

   @Override
   protected net.minecraft.sounds.SoundEvent getDeathSound() {
      return null;
   }

   @Override
   protected net.minecraft.sounds.SoundEvent getAmbientSound() {
      return null;
   }

   /** Footfalls are the event's own (GodzillaAudio, synced to the gait), never the block step sounds. */
   @Override
   protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
   }

   /** No vanilla step, swim or game-event noise from moving: the kaiju's motion is all its own. */
   @Override
   protected net.minecraft.world.entity.Entity.MovementEmission getMovementEmission() {
      return net.minecraft.world.entity.Entity.MovementEmission.NONE;
   }

   /** Wading through water: no vanilla splash sound or bubbles. */
   @Override
   protected void doWaterSplashEffect() {
   }
}
