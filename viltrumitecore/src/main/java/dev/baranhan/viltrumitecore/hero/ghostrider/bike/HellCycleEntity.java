package dev.baranhan.viltrumitecore.hero.ghostrider.bike;

import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.HellCycleControlC2SPacket;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Hell Cycle: a rideable hellfire motorcycle (spec docs/design/2026-11-02-ghost-rider-design.md §5.2).
 *
 * <p>Boat-style ownership of the motion: the rider's own client runs {@link HellCyclePhysics} and moves the bike
 * with the vanilla collision ({@link #move}), the server receives it through the vanilla vehicle path
 * ({@code ServerboundMoveVehiclePacket}, checked by {@code handleMoveVehicle}). A nose and a tail probe keep the long
 * model out of walls the square box would miss; one-block steps are climbed by the step height, taller ones are walls.
 * The rider's client also sends the visual controls ({@link HellCycleControlC2SPacket}); the server mirrors them into
 * synced data so every other client draws the same lean, lifts, drift and engine. The server owns the lifecycle
 * (forming, burning out, the unattended timer), the dash and the contacts with bodies ({@link HellCycleContacts}).
 * Never saved: a hellfire construct, gone with its owner.
 */
public class HellCycleEntity extends Entity {
   private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(HellCycleEntity.class, EntityDataSerializers.OPTIONAL_UUID);
   /** tickCount when burning out started, -1 while alive. */
   private static final EntityDataAccessor<Integer> DATA_BURN = SynchedEntityData.defineId(HellCycleEntity.class, EntityDataSerializers.INT);
   /** Bumped on every Hell Dash; clients start their own dash clock on the change. */
   private static final EntityDataAccessor<Integer> DATA_DASH = SynchedEntityData.defineId(HellCycleEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Byte> DATA_THROTTLE = SynchedEntityData.defineId(HellCycleEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> DATA_STEER = SynchedEntityData.defineId(HellCycleEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> DATA_LEAN = SynchedEntityData.defineId(HellCycleEntity.class, EntityDataSerializers.BYTE);
   /** Stoppie (+) / wheelie (-) lift, percent. */
   private static final EntityDataAccessor<Byte> DATA_LIFT = SynchedEntityData.defineId(HellCycleEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> DATA_RPM = SynchedEntityData.defineId(HellCycleEntity.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(HellCycleEntity.class, EntityDataSerializers.BYTE);
   /** (serial << 8) | one-tick event bits of the rider's client, for the other clients' sounds and FX. */
   private static final EntityDataAccessor<Integer> DATA_EVENT = SynchedEntityData.defineId(HellCycleEntity.class, EntityDataSerializers.INT);

   /** Bits of {@link #DATA_FLAGS}. */
   public static final int FLAG_DRIFT = 1;
   public static final int FLAG_SKID = 1 << 1;
   public static final int FLAG_BRAKE = 1 << 2;
   public static final int FLAG_AIR = 1 << 3;
   public static final int FLAG_REVERSE = 1 << 4;

   /** Client callbacks (input, sounds, FX), installed by the client setup; a no-op on a dedicated server. */
   public interface ClientHooks {
      HellCyclePhysics.Input sample(HellCycleEntity bike);

      void tick(HellCycleEntity bike);

      void events(HellCycleEntity bike, int events);

      void removed(HellCycleEntity bike);
   }

   private static final ClientHooks NO_CLIENT = new ClientHooks() {
      @Override
      public HellCyclePhysics.Input sample(HellCycleEntity bike) {
         return HellCyclePhysics.Input.NONE;
      }

      @Override
      public void tick(HellCycleEntity bike) {
      }

      @Override
      public void events(HellCycleEntity bike, int events) {
      }

      @Override
      public void removed(HellCycleEntity bike) {
      }
   };
   private static volatile ClientHooks client = NO_CLIENT;

   public static void installClient(ClientHooks hooks) {
      client = hooks == null ? NO_CLIENT : hooks;
   }

   /** Physics state of the side that drives (the rider's client, or the server while parked). */
   public final HellCyclePhysics.State state = new HellCyclePhysics.State();

   // --- client visuals (every client) ---
   public float leanO;
   public float lean;
   public float liftO;
   public float lift;
   public float steerO;
   public float steer;
   public float pitchO;
   public float pitch;
   public float wheelSpinO;
   public float wheelSpin;
   public double visualSpeed;
   public float rpm;
   public int dashLeft;
   private int lastDashSerial;
   private int lastEventSerial;
   private float terrainPitch;
   /** Yaw handed to the rider this tick (deg), like the boat's deltaRotation. */
   private float riderYawDelta;
   private int mountTicks = 100;
   private Vec3 mountFrom = Vec3.ZERO;

   // --- lerp of remote copies ---
   private int lerpSteps;
   private double lerpX;
   private double lerpY;
   private double lerpZ;
   private double lerpYRot;

   // --- server ---
   private int unattended;
   private int serverDashLeft;
   private int dashSerial;
   private int eventSerial;
   private Vec3 lastServerPos;
   private double serverSpeed;
   @Nullable private UUID pendingRider;
   private int sentAge;
   private int lastSentHash;

   public HellCycleEntity(EntityType<? extends HellCycleEntity> type, Level level) {
      super(type, level);
      this.setMaxUpStep(HellCycleRules.STEP_HEIGHT);
      this.blocksBuilding = true;
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(DATA_OWNER, Optional.empty());
      this.entityData.define(DATA_BURN, -1);
      this.entityData.define(DATA_DASH, 0);
      this.entityData.define(DATA_THROTTLE, (byte)0);
      this.entityData.define(DATA_STEER, (byte)0);
      this.entityData.define(DATA_LEAN, (byte)0);
      this.entityData.define(DATA_LIFT, (byte)0);
      this.entityData.define(DATA_RPM, (byte)0);
      this.entityData.define(DATA_FLAGS, (byte)0);
      this.entityData.define(DATA_EVENT, 0);
   }

   // ------------------------------------------------------------------ ownership and lifecycle

   public void setOwner(Player owner) {
      this.entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
   }

   @Nullable
   public UUID ownerId() {
      return this.entityData.get(DATA_OWNER).orElse(null);
   }

   public boolean isOwner(@Nullable Entity e) {
      UUID id = this.ownerId();
      return e != null && id != null && id.equals(e.getUUID());
   }

   /** Server: the owner mounts by himself as soon as the bike has formed. */
   public void mountWhenFormed(Player rider) {
      this.pendingRider = rider.getUUID();
   }

   public boolean forming() {
      return this.tickCount < HellCycleRules.MATERIALIZE_TICKS && !this.burning();
   }

   /** 0 (just summoned) to 1 (complete). */
   public float formProgress(float partial) {
      return Mth.clamp((this.tickCount + partial) / HellCycleRules.MATERIALIZE_TICKS, 0.0F, 1.0F);
   }

   public boolean burning() {
      return this.entityData.get(DATA_BURN) >= 0;
   }

   /** 0 (starts burning out) to 1 (gone). */
   public float burnProgress(float partial) {
      int start = this.entityData.get(DATA_BURN);
      return start < 0 ? 0.0F : Mth.clamp((this.tickCount - start + partial) / HellCycleRules.DEMATERIALIZE_TICKS, 0.0F, 1.0F);
   }

   /** Server: throw the rider off and burn the bike out (dismiss, timeout, owner gone). */
   public void burnOut() {
      if (this.level().isClientSide || this.burning()) {
         return;
      }
      this.ejectPassengers();
      this.entityData.set(DATA_BURN, this.tickCount);
   }

   /** Server: start a Hell Dash (the rider's client applies it on the synced serial). */
   public void startDash() {
      this.serverDashLeft = HellCycleRules.BOOST_TICKS;
      this.dashSerial++;
      this.entityData.set(DATA_DASH, this.dashSerial);
   }

   public boolean dashing() {
      return this.level().isClientSide ? this.dashLeft > 0 : this.serverDashLeft > 0;
   }

   public int dashTicksLeft() {
      return this.level().isClientSide ? this.dashLeft : this.serverDashLeft;
   }

   /** Server: ground speed over the last tick (b/t), measured from the accepted positions. */
   public double serverSpeed() {
      return this.serverSpeed;
   }

   // ------------------------------------------------------------------ synced visuals

   public float syncedThrottle() {
      return this.entityData.get(DATA_THROTTLE) / 100.0F;
   }

   public float syncedSteer() {
      return this.entityData.get(DATA_STEER) / 100.0F;
   }

   public int syncedFlags() {
      return this.entityData.get(DATA_FLAGS);
   }

   public boolean flag(int bit) {
      return this.isControlledByLocalInstance() && this.level().isClientSide ? (localFlags() & bit) != 0 : (this.syncedFlags() & bit) != 0;
   }

   /** Server: the rider's client reports its visual controls (already validated by the packet). */
   public void applyRiderControl(int throttle, int steer, int lean, int lift, int rpm, int flags, int events) {
      this.entityData.set(DATA_THROTTLE, (byte)Mth.clamp(throttle, -100, 100));
      this.entityData.set(DATA_STEER, (byte)Mth.clamp(steer, -100, 100));
      this.entityData.set(DATA_LEAN, (byte)Mth.clamp(lean, -60, 60));
      this.entityData.set(DATA_LIFT, (byte)Mth.clamp(lift, -100, 100));
      this.entityData.set(DATA_RPM, (byte)Mth.clamp(rpm, 0, 100));
      this.entityData.set(DATA_FLAGS, (byte)(flags & 0x1F));
      if (events != 0) {
         this.eventSerial = (this.eventSerial + 1) & 0xFFFFFF;
         this.entityData.set(DATA_EVENT, this.eventSerial << 8 | events & 0xFF);
      }
   }

   private int localFlags() {
      HellCyclePhysics.State s = this.state;
      int f = 0;
      if (s.drifting) f |= FLAG_DRIFT;
      if (s.skidding) f |= FLAG_SKID;
      if (s.braking) f |= FLAG_BRAKE;
      if (!s.onGround) f |= FLAG_AIR;
      if (s.forward() < -0.01) f |= FLAG_REVERSE;
      return f;
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (!this.level().isClientSide) {
         return;
      }
      if (DATA_DASH.equals(key)) {
         int serial = this.entityData.get(DATA_DASH);
         if (serial != this.lastDashSerial) {
            this.lastDashSerial = serial;
            if (serial != 0) {
               this.dashLeft = HellCycleRules.BOOST_TICKS;
            }
         }
      } else if (DATA_EVENT.equals(key)) {
         int packed = this.entityData.get(DATA_EVENT);
         int serial = packed >>> 8;
         if (serial != this.lastEventSerial) {
            this.lastEventSerial = serial;
            if (!this.isControlledByLocalInstance()) {
               client.events(this, packed & 0xFF);
            }
         }
      }
   }

   // ------------------------------------------------------------------ tick

   @Override
   public void tick() {
      super.tick();
      this.tickLerp();
      if (this.mountTicks < 100) {
         this.mountTicks++;
      }
      this.riderYawDelta = 0.0F;
      if (this.isControlledByLocalInstance()) {
         this.drive();
      } else {
         this.setDeltaMovement(Vec3.ZERO);
      }
      if (this.level().isClientSide) {
         this.clientVisuals();
         client.tick(this);
      } else {
         this.serverTick();
      }
   }

   /** The driving side: rider's client, or the server with nobody aboard. */
   private void drive() {
      HellCyclePhysics.State s = this.state;
      boolean local = this.level().isClientSide;
      HellCyclePhysics.Input in;
      if (this.forming() || this.burning()) {
         in = HellCyclePhysics.Input.PARK;
      } else if (local) {
         HellCyclePhysics.Input sampled = client.sample(this);
         in = new HellCyclePhysics.Input(sampled.throttle(), sampled.steer(), sampled.jump(), sampled.drift(), this.dashLeft > 0);
      } else {
         in = HellCyclePhysics.Input.PARK;
      }
      this.unstick();
      s.yaw = this.getYRot();
      s.onGround = this.onGround();
      s.pitch = this.terrainPitch;
      double travelBefore = Math.toDegrees(Math.atan2(-s.velX, s.velZ));
      boolean wasAir = !s.onGround;
      HellCyclePhysics.step(s, in);
      if (this.isInWater()) {
         s.velX *= 0.8;
         s.velZ *= 0.8;
         s.velY = Math.max(s.velY, -0.04);
      }

      Vec3 want = new Vec3(s.velX, s.velY, s.velZ);
      boolean probeHit = false;
      Vec3 motion = want;
      if (!this.loadedAhead(motion)) {
         motion = new Vec3(0.0, motion.y, 0.0);
      } else {
         Vec3 probed = this.probe(motion);
         probeHit = probed.x != motion.x || probed.z != motion.z;
         motion = probed;
      }
      Vec3 start = this.position();
      this.move(MoverType.SELF, motion);
      Vec3 moved = this.position().subtract(start);

      double rad = Math.toRadians(s.yaw);
      double hx = -Math.sin(rad);
      double hz = Math.cos(rad);
      if (probeHit || this.horizontalCollision) {
         double blocked = (want.x * hx + want.z * hz) - (moved.x * hx + moved.z * hz);
         HellCyclePhysics.hitWall(s, Math.max(0.0, blocked), moved.x, moved.z);
      }
      if (this.verticalCollision) {
         if (want.y < 0.0) {
            if (wasAir) {
               HellCyclePhysics.land(s);
            }
            s.velY = 0.0;
         } else if (want.y > 0.0) {
            s.velY = 0.0;
         }
      }
      this.setYRot((float)s.yaw);
      this.setDeltaMovement(moved);
      this.measureTerrain();

      // the rider's view follows the heading (and, in a drift, half way the direction of travel)
      float headingDelta = (float)s.yawDelta;
      if (s.drifting) {
         double travelAfter = Math.toDegrees(Math.atan2(-s.velX, s.velZ));
         headingDelta = (float)(0.5 * s.yawDelta + 0.5 * Mth.wrapDegrees(travelAfter - travelBefore));
      }
      this.riderYawDelta = headingDelta;

      if (local) {
         if (s.events != 0) {
            client.events(this, s.events);
         }
         this.sendControl(in);
      }
   }

   /** Rider's client: the visual controls for everybody else (on change, at least every 10 ticks). */
   private void sendControl(HellCyclePhysics.Input in) {
      HellCyclePhysics.State s = this.state;
      int throttle = (int)Math.round(Mth.clamp(in.throttle(), -1.0, 1.0) * 100);
      int steer = (int)Math.round(s.steer * 100);
      int lean = (int)Math.round(s.lean);
      int lift = (int)Math.round((s.stoppie - s.wheelie) * 100);
      int rpm = (int)Math.round(Mth.clamp(s.rpm, 0.0, 1.0) * 100);
      int flags = this.localFlags();
      int hash = ((((throttle * 31 + steer) * 31 + lean) * 31 + lift) * 31 + rpm / 4) * 31 + flags;
      this.sentAge++;
      if (hash != this.lastSentHash || s.events != 0 || this.sentAge >= 10) {
         this.lastSentHash = hash;
         this.sentAge = 0;
         CoreMessages.sendToServer(new HellCycleControlC2SPacket(this.getId(), throttle, steer, lean, lift, rpm, flags, s.events));
      }
   }

   /** Do not drive into chunks the client has not got (the box would fall through nothing). */
   private boolean loadedAhead(Vec3 motion) {
      if (motion.x * motion.x + motion.z * motion.z < 1.0E-6) {
         return true;
      }
      double x = this.getX() + motion.x * 6.0;
      double z = this.getZ() + motion.z * 6.0;
      return this.level().hasChunkAt(BlockPos.containing(x, this.getY(), z));
   }

   /**
    * Nose (or tail when reversing) probe: a small box at the wheel end of the long model. Blocked even when lifted by
    * the step height means a wall; the blocked axis is cut, the free one is kept (sliding along the wall).
    */
   private Vec3 probe(Vec3 motion) {
      double rad = Math.toRadians(this.getYRot());
      double hx = -Math.sin(rad);
      double hz = Math.cos(rad);
      double fwd = motion.x * hx + motion.z * hz;
      if (Math.abs(fwd) < 1.0E-4) {
         return motion;
      }
      double end = fwd > 0.0 ? HellCycleRules.NOSE : -HellCycleRules.NOSE;
      double cx = this.getX() + hx * end;
      double cz = this.getZ() + hz * end;
      double p = HellCycleRules.PROBE_HALF;
      AABB box = new AABB(cx - p, this.getY() + 0.3, cz - p, cx + p, this.getY() + 1.1, cz + p);
      if (this.wall(box, motion.x, motion.z)) {
         boolean xFree = !this.wall(box, motion.x, 0.0);
         boolean zFree = !this.wall(box, 0.0, motion.z);
         return new Vec3(xFree ? motion.x : 0.0, motion.y, zFree ? motion.z : 0.0);
      }
      return motion;
   }

   private boolean wall(AABB box, double dx, double dz) {
      AABB moved = box.move(dx, 0.0, dz);
      if (this.level().noCollision(this, moved)) {
         return false;
      }
      return !this.level().noCollision(this, moved.move(0.0, HellCycleRules.STEP_HEIGHT, 0.0));
   }

   /** Pushed into a block (a block placed on it, a bad dismount spot): climb out upwards. */
   private void unstick() {
      AABB box = this.getBoundingBox().deflate(0.01);
      if (this.level().noCollision(this, box)) {
         return;
      }
      for (int i = 1; i <= 8; i++) {
         if (this.level().noCollision(this, box.move(0.0, i * 0.25, 0.0))) {
            this.setPos(this.getX(), this.getY() + i * 0.25, this.getZ());
            return;
         }
      }
   }

   /** Pitch of the frame from the ground under the two axles (deg, positive nose up). */
   private void measureTerrain() {
      double rad = Math.toRadians(this.getYRot());
      double hx = -Math.sin(rad);
      double hz = Math.cos(rad);
      double half = HellCycleRules.WHEELBASE / 2.0;
      double front = this.groundBelow(this.getX() + hx * half, this.getZ() + hz * half);
      double rear = this.groundBelow(this.getX() - hx * half, this.getZ() - hz * half);
      float target = 0.0F;
      if (this.onGround()) {
         target = (float)Math.toDegrees(Math.atan2(front - rear, HellCycleRules.WHEELBASE));
         target = Mth.clamp(target, -35.0F, 35.0F);
      }
      this.terrainPitch += (target - this.terrainPitch) * 0.5F;
   }

   /** Height of the ground (relative to the bike) under a point, searched one step up and two down. */
   private double groundBelow(double x, double z) {
      double top = this.getY() + HellCycleRules.STEP_HEIGHT + 0.01;
      AABB probe = new AABB(x - 0.1, top - 0.02, z - 0.1, x + 0.1, top, z + 0.1);
      for (int i = 0; i <= 12; i++) {
         AABB down = probe.move(0.0, -i * 0.25, 0.0);
         if (!this.level().noCollision(this, down)) {
            return down.maxY - this.getY();
         }
      }
      return -2.0;
   }

   // ------------------------------------------------------------------ client visuals

   private void clientVisuals() {
      this.leanO = this.lean;
      this.liftO = this.lift;
      this.steerO = this.steer;
      this.pitchO = this.pitch;
      this.wheelSpinO = this.wheelSpin;
      if (this.dashLeft > 0) {
         this.dashLeft--;
      }
      double dx = this.getX() - this.xo;
      double dz = this.getZ() - this.zo;
      double rad = Math.toRadians(this.getYRot());
      double fwd = dx * -Math.sin(rad) + dz * Math.cos(rad);
      this.visualSpeed = Math.sqrt(dx * dx + dz * dz);
      if (this.isControlledByLocalInstance()) {
         HellCyclePhysics.State s = this.state;
         this.lean = (float)s.lean;
         this.lift = (float)(s.stoppie * HellCycleRules.STOPPIE_MAX_DEG - s.wheelie * HellCycleRules.WHEELIE_MAX_DEG);
         this.steer = (float)s.steer;
         this.rpm = (float)s.rpm;
         this.pitch = this.terrainPitch + (s.onGround ? 0.0F : (float)Mth.clamp(s.velY * 30.0, -20.0, 12.0));
      } else {
         float syncedLift = this.entityData.get(DATA_LIFT) / 100.0F;
         float liftDeg = syncedLift >= 0.0F ? syncedLift * (float)HellCycleRules.STOPPIE_MAX_DEG : syncedLift * (float)HellCycleRules.WHEELIE_MAX_DEG;
         this.lean += (this.entityData.get(DATA_LEAN) - this.lean) * 0.5F;
         this.lift += (liftDeg - this.lift) * 0.5F;
         this.steer += (this.syncedSteer() - this.steer) * 0.5F;
         this.rpm += (this.entityData.get(DATA_RPM) / 100.0F - this.rpm) * 0.4F;
         this.measureTerrain();
         double dy = this.getY() - this.yo;
         this.pitch = this.terrainPitch + (this.onGround() ? 0.0F : (float)Mth.clamp(dy * 30.0, -20.0, 12.0));
      }
      // wheel radius 6 px = 0.375 block: one block of travel turns the wheel 152.8 degrees
      this.wheelSpin += (float)Math.toDegrees(fwd / 0.375);
      if (this.wheelSpin > 3600.0F) {
         this.wheelSpin -= 3600.0F;
         this.wheelSpinO -= 3600.0F;
      } else if (this.wheelSpin < -3600.0F) {
         this.wheelSpin += 3600.0F;
         this.wheelSpinO += 3600.0F;
      }
   }

   /** Local physics (rider's client) or the rider's synced values (others): engine speed 0-1. */
   public float engineRpm() {
      return this.rpm;
   }

   // ------------------------------------------------------------------ server

   private void serverTick() {
      if (this.burning()) {
         if (this.burnProgress(0.0F) >= 1.0F) {
            this.discard();
         }
         return;
      }
      ServerPlayer owner = this.owner();
      if (owner == null || !owner.isAlive() || owner.level() != this.level() || owner.isSpectator()) {
         this.burnOut();
         return;
      }
      if (this.pendingRider != null && !this.forming()) {
         if (owner.getUUID().equals(this.pendingRider) && !owner.isPassenger() && owner.distanceToSqr(this) < 64.0) {
            owner.startRiding(this);
         }
         this.pendingRider = null;
      }
      if (this.serverDashLeft > 0) {
         this.serverDashLeft--;
      }
      Vec3 now = this.position();
      if (this.lastServerPos != null) {
         double dx = now.x - this.lastServerPos.x;
         double dz = now.z - this.lastServerPos.z;
         this.serverSpeed = Math.sqrt(dx * dx + dz * dz);
      }
      Entity rider = this.getControllingPassenger();
      if (rider instanceof ServerPlayer player && this.lastServerPos != null) {
         this.unattended = 0;
         HellCycleContacts.tick(this, player, this.lastServerPos, now, this.serverSpeed);
      } else if (!this.forming() && this.pendingRider == null) {
         if (++this.unattended >= HellCycleRules.UNATTENDED_TICKS) {
            this.burnOut();
         }
      }
      if (this.isUnderWater()) {
         this.burnOut();
      }
      this.lastServerPos = now;
   }

   @Nullable
   public ServerPlayer owner() {
      UUID id = this.ownerId();
      if (id == null || this.level().isClientSide) {
         return null;
      }
      Player p = this.level().getPlayerByUUID(id);
      return p instanceof ServerPlayer sp ? sp : null;
   }

   // ------------------------------------------------------------------ riding

   @Nullable
   @Override
   public LivingEntity getControllingPassenger() {
      return this.getFirstPassenger() instanceof Player p ? p : null;
   }

   @Override
   protected boolean canAddPassenger(Entity passenger) {
      return this.getPassengers().isEmpty() && this.isOwner(passenger) && !this.burning();
   }

   @Override
   protected void addPassenger(Entity passenger) {
      super.addPassenger(passenger);
      this.mountTicks = 0;
      this.mountFrom = passenger.position().subtract(this.position());
      this.unattended = 0;
      if (this.isControlledByLocalInstance() && this.lerpSteps > 0) {
         this.lerpSteps = 0;
         this.absMoveTo(this.lerpX, this.lerpY, this.lerpZ, (float)this.lerpYRot, this.getXRot());
      }
      this.state.setVelocity(0.0, 0.0, 0.0);
      this.state.yaw = this.getYRot();
   }

   @Override
   protected void removePassenger(Entity passenger) {
      super.removePassenger(passenger);
      this.state.setVelocity(0.0, this.state.velY, 0.0);
      this.state.drifting = false;
   }

   /** Ticks since the rider got on (8-tick mount hop), capped at 100. */
   public int mountTicks() {
      return this.mountTicks;
   }

   @Override
   public double getPassengersRidingOffset() {
      return HellCycleRules.SEAT_Y;
   }

   @Override
   protected void positionRider(Entity passenger, Entity.MoveFunction move) {
      if (!this.hasPassenger(passenger)) {
         return;
      }
      double rad = Math.toRadians(this.getYRot());
      double back = 0.18; // the seat sits behind the centre of the wheelbase
      double x = this.getX() + Math.sin(rad) * back;
      double z = this.getZ() - Math.cos(rad) * back;
      double y = this.getY() + HellCycleRules.SEAT_Y + passenger.getMyRidingOffset() + 0.35;
      if (this.mountTicks < HellCycleRules.MOUNT_TICKS) {
         // the mount hop: from where he stood, over the seat, down onto it
         float t = (this.mountTicks + 1) / (float)HellCycleRules.MOUNT_TICKS;
         float e = t * t * (3.0F - 2.0F * t);
         x = Mth.lerp(e, this.getX() + this.mountFrom.x, x);
         z = Mth.lerp(e, this.getZ() + this.mountFrom.z, z);
         y = Mth.lerp(e, this.getY() + this.mountFrom.y, y) + Math.sin(t * Math.PI) * 0.45;
      }
      move.accept(passenger, x, y, z);
      if (passenger instanceof LivingEntity) {
         passenger.setYRot(passenger.getYRot() + this.riderYawDelta);
         passenger.setYHeadRot(passenger.getYHeadRot() + this.riderYawDelta);
         this.clampRotation(passenger);
      }
   }

   /** The rider's body faces the bars; the head may look round up to 120 degrees. */
   protected void clampRotation(Entity passenger) {
      passenger.setYBodyRot(this.getYRot());
      float rel = Mth.wrapDegrees(passenger.getYRot() - this.getYRot());
      float clamped = Mth.clamp(rel, -120.0F, 120.0F);
      passenger.yRotO += clamped - rel;
      passenger.setYRot(passenger.getYRot() + clamped - rel);
      passenger.setYHeadRot(passenger.getYRot());
   }

   @Override
   public void onPassengerTurned(Entity passenger) {
      this.clampRotation(passenger);
   }

   /** Off to the left (kickstand side), else right, behind, ahead, else on top. */
   @Override
   public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
      double rad = Math.toRadians(this.getYRot());
      double hx = -Math.sin(rad);
      double hz = Math.cos(rad);
      double lx = Math.cos(rad);
      double lz = Math.sin(rad);
      double side = this.getBbWidth() * 0.5 + passenger.getBbWidth() * 0.5 + 0.15;
      double[][] offsets = {{lx * side, lz * side}, {-lx * side, -lz * side}, {-hx * 1.6, -hz * 1.6}, {hx * 1.6, hz * 1.6}};
      for (double[] o : offsets) {
         double x = this.getX() + o[0];
         double z = this.getZ() + o[1];
         BlockPos pos = BlockPos.containing(x, this.getBoundingBox().maxY, z);
         for (BlockPos candidate : new BlockPos[]{pos, pos.below(), pos.below(2)}) {
            double floor = this.level().getBlockFloorHeight(candidate);
            if (DismountHelper.isBlockFloorValid(floor)) {
               Vec3 at = new Vec3(x, candidate.getY() + floor, z);
               for (Pose pose : passenger.getDismountPoses()) {
                  if (DismountHelper.canDismountTo(this.level(), at, passenger, pose)) {
                     passenger.setPose(pose);
                     return at;
                  }
               }
            }
         }
      }
      return super.getDismountLocationForPassenger(passenger);
   }

   @Override
   public InteractionResult interact(Player player, InteractionHand hand) {
      if (player.isSecondaryUseActive() || this.burning() || this.forming() || !this.isOwner(player) || this.isVehicle()) {
         return InteractionResult.PASS;
      }
      if (!this.level().isClientSide) {
         return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
      }
      return InteractionResult.SUCCESS;
   }

   // ------------------------------------------------------------------ remote interpolation

   @Override
   public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
      this.lerpX = x;
      this.lerpY = y;
      this.lerpZ = z;
      this.lerpYRot = yRot;
      this.lerpSteps = 3;
   }

   private void tickLerp() {
      if (this.isControlledByLocalInstance()) {
         this.lerpSteps = 0;
         this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
      }
      if (this.lerpSteps > 0) {
         double x = this.getX() + (this.lerpX - this.getX()) / this.lerpSteps;
         double y = this.getY() + (this.lerpY - this.getY()) / this.lerpSteps;
         double z = this.getZ() + (this.lerpZ - this.getZ()) / this.lerpSteps;
         double dYaw = Mth.wrapDegrees(this.lerpYRot - this.getYRot());
         this.setYRot(this.getYRot() + (float)dYaw / this.lerpSteps);
         this.lerpSteps--;
         this.setPos(x, y, z);
         this.setRot(this.getYRot(), this.getXRot());
      }
   }

   // ------------------------------------------------------------------ body rules

   @Override
   public boolean isPickable() {
      return !this.isRemoved() && !this.isVehicle();
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public boolean canBeCollidedWith() {
      return false;
   }

   @Override
   public boolean canCollideWith(Entity other) {
      return false;
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      return false;
   }

   @Override
   public boolean isInvulnerableTo(DamageSource source) {
      return true;
   }

   @Override
   public boolean displayFireAnimation() {
      return false;
   }

   @Override
   public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
      return false;
   }

   @Override
   protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
      this.resetFallDistance();
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   public boolean isAttackable() {
      return false;
   }

   @Override
   public void remove(Entity.RemovalReason reason) {
      if (this.level().isClientSide) {
         client.removed(this);
      }
      super.remove(reason);
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }
}
