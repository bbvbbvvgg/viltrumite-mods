package dev.baranhan.viltrumitecore.entity.chitauri;

import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriDestruction;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriInvasion;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriSounds;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriTerrain;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriNet;
import java.util.List;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Chitauri chariot: a flying sled with a pilot standing at the back and a gunner or two troopers
 * at the front rail. Own steering (no navigation): entry out of the portal, patrol, attack runs
 * from changing azimuths with the nose cannon, troop drops on streets and roofs, evasion of heroes.
 * Look-ahead feelers keep it off buildings, separation off other chariots and Leviathans, a
 * clearance above the surface, and it never flies into unloaded chunks. Death: smoke spiral,
 * explosion on impact, breakup into falling wreck pieces (client).
 */
public class ChitauriChariot extends Mob implements ChitauriMob {
   private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(ChitauriChariot.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(ChitauriChariot.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(ChitauriChariot.class, EntityDataSerializers.FLOAT);

   public static final byte KIND_GUNSHIP = 0, KIND_CARRIER = 1;
   public static final byte STATE_FLY = 0, STATE_DYING = 1;
   public static final byte EVENT_CANNON = 75;
   /** Seat offsets (right, up, forward) in blocks from the hull centre. */
   static final double[][] SEATS = {{0.0, 0.42, 0.75}, {-0.5, 0.42, -0.55}, {0.5, 0.42, -0.55}};

   enum Mode { ENTRY, PATROL, ATTACK, DROP, EVADE }

   private Mode mode = Mode.ENTRY;
   private int modeTicks;
   private Vec3 entryDir = new Vec3(0, -0.6, 1).normalize();
   @Nullable
   private Vec3 waypoint;
   @Nullable
   private LivingEntity target;
   private double azimuth;
   private int stage;
   private int runs;
   private int cannonCooldown;
   @Nullable
   private Vec3 dropPoint;
   @Nullable
   private Vec3 passGoal;
   private Vec3 evadeDir = Vec3.ZERO;
   private Mode afterEvade = Mode.PATROL;
   private int evadeCooldown;
   private int noPilotTicks;
   private int dyingTicks;
   private float spin;
   private float roll;
   public int squad = -1;

   // client
   public float clientRollO;
   public float clientRoll;
   public int clientCannonStart = -1000;
   public double clientSpeed;

   public ChitauriChariot(EntityType<? extends ChitauriChariot> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
      this.xpReward = 0;
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 70.0)
         .add(Attributes.ARMOR, 8.0)
         .add(Attributes.FOLLOW_RANGE, 64.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
         .add(Attributes.FLYING_SPEED, 0.6);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(KIND, KIND_GUNSHIP);
      this.entityData.define(STATE, STATE_FLY);
      this.entityData.define(ROLL, 0.0F);
   }

   public byte kind() {
      return this.entityData.get(KIND);
   }

   public void setKind(byte kind) {
      this.entityData.set(KIND, kind);
   }

   public boolean dying() {
      return this.entityData.get(STATE) == STATE_DYING;
   }

   public float syncedRoll() {
      return this.entityData.get(ROLL);
   }

   public void launch(Vec3 dir) {
      this.entryDir = dir.normalize();
      this.setDeltaMovement(this.entryDir.scale(0.35));
      this.mode = Mode.ENTRY;
      this.modeTicks = 0;
      float yaw = (float)(Mth.atan2(-this.entryDir.x, this.entryDir.z) * Mth.RAD_TO_DEG);
      this.setYRot(yaw);
      this.yBodyRot = yaw;
      this.yHeadRot = yaw;
   }

   // ------------------------------------------------------------------ riders

   @Override
   protected boolean canAddPassenger(Entity passenger) {
      return passenger instanceof ChitauriSoldier && this.getPassengers().size() < SEATS.length;
   }

   @Override
   @Nullable
   public LivingEntity getControllingPassenger() {
      return null; // the chariot flies itself; the pilot only stands at the controls
   }

   @Override
   protected void positionRider(Entity passenger, Entity.MoveFunction move) {
      if (!this.hasPassenger(passenger)) {
         return;
      }
      int seat = passenger instanceof ChitauriSoldier s && s.seat() >= 0 ? s.seat() : this.getPassengers().indexOf(passenger);
      Vec3 at = this.seatPosition(Mth.clamp(seat, 0, SEATS.length - 1), this.level().isClientSide ? this.clientRoll : this.roll);
      move.accept(passenger, at.x, at.y, at.z);
      if (passenger instanceof LivingEntity living) {
         living.yBodyRot = this.getYRot();
         living.yBodyRotO = this.yRotO;
      }
   }

   /** World position of a seat (feet), banked with the hull. */
   public Vec3 seatPosition(int seat, float rollDeg) {
      double[] s = SEATS[seat];
      float yaw = this.getYRot() * Mth.DEG_TO_RAD;
      Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
      Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
      double r = rollDeg * Mth.DEG_TO_RAD;
      Vec3 up = new Vec3(0, 1, 0).scale(Math.cos(r)).add(right.scale(Math.sin(r)));
      Vec3 rr = right.scale(Math.cos(r)).subtract(new Vec3(0, 1, 0).scale(Math.sin(r)));
      double pitch = -this.getXRot() * Mth.DEG_TO_RAD;
      Vec3 center = this.position().add(0, 0.55, 0);
      return center.add(rr.scale(s[0])).add(up.scale(s[1])).add(fwd.scale(s[2])).add(0, Math.sin(pitch) * s[2], 0);
   }

   @Override
   public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
      return passenger.position();
   }

   @Nullable
   private ChitauriSoldier seated(int seat) {
      for (Entity e : this.getPassengers()) {
         if (e instanceof ChitauriSoldier s && s.seat() == seat) {
            return s;
         }
      }
      return null;
   }

   private void ejectSeat(int seat) {
      ChitauriSoldier s = this.seated(seat);
      if (s != null) {
         s.stopRiding();
         s.setSeat(-1);
         Vec3 v = this.getDeltaMovement().scale(0.5).add(0, -0.1, 0);
         s.setDeltaMovement(v);
         s.hasImpulse = true;
      }
   }

   private boolean hasTroopers() {
      return this.seated(1) != null || this.seated(2) != null;
   }

   // ------------------------------------------------------------------ flight

   @Override
   public void travel(Vec3 input) {
      if (this.isControlledByLocalInstance()) {
         Vec3 before = this.getDeltaMovement();
         this.move(MoverType.SELF, before);
         if (!this.level().isClientSide && (this.horizontalCollision || this.verticalCollision)) {
            if (this.dying()) {
               this.explode(true);
               return;
            }
            // glance off: lose speed, pick a new way
            this.setDeltaMovement(before.scale(-0.3).add(0, 0.15, 0));
            this.waypoint = null;
         }
      }
      this.calculateEntityAnimation(false);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         this.clientRollO = this.clientRoll;
         this.clientRoll += (this.syncedRoll() - this.clientRoll) * 0.35F;
         this.clientSpeed = new Vec3(this.getX() - this.xo, this.getY() - this.yo, this.getZ() - this.zo).length();
      }
   }

   @Override
   protected void customServerAiStep() {
      ServerLevel level = (ServerLevel)this.level();
      this.modeTicks++;
      if (this.cannonCooldown > 0) {
         this.cannonCooldown--;
      }
      if (this.evadeCooldown > 0) {
         this.evadeCooldown--;
      }
      if (ChitauriMob.heldByHero(this)) {
         return; // a hero holds it: no steering against the grab
      }
      // the pilot keeps it flying; without one it goes down
      if (this.mode != Mode.ENTRY && this.seated(0) == null) {
         if (++this.noPilotTicks > 12) {
            this.setHealth(0.0F);
            this.die(this.damageSources().generic());
            return;
         }
      } else {
         this.noPilotTicks = 0;
      }
      Vec3 pos = this.position().add(0, 0.65, 0);
      Vec3 anchor = ChitauriInvasion.anchor(level);
      if (anchor == null) {
         anchor = pos;
      }
      if (this.tickCount % 10 == 0) {
         this.scanThreats(level);
      }
      Vec3 goal;
      double speed = ChariotSteering.CRUISE;
      double arrive = 0.0;
      double clearance = 7.0;
      double accel = ChariotSteering.MAX_ACCEL;
      switch (this.mode) {
         case ENTRY -> {
            goal = pos.add(this.entryDir.scale(20.0));
            speed = ChariotSteering.CRUISE * Math.min(1.0, 0.45 + this.modeTicks / 25.0);
            clearance = 4.0;
            if (this.modeTicks > 45) {
               this.setMode(this.kind() == KIND_CARRIER && this.hasTroopers() ? Mode.DROP : Mode.PATROL);
            }
         }
         case PATROL -> {
            if (this.waypoint == null || pos.distanceToSqr(this.waypoint) < 36.0 || this.modeTicks > 240) {
               this.waypoint = this.patrolPoint(level, anchor);
               this.modeTicks = 0;
            }
            goal = this.waypoint;
            if (this.tickCount % 20 == 0) {
               LivingEntity t = this.findTarget(level, 56.0);
               if (t != null) {
                  this.target = t;
                  this.azimuth = this.random.nextDouble() * 360.0;
                  this.stage = 0;
                  this.runs = 0;
                  this.setMode(this.kind() == KIND_CARRIER && this.hasTroopers() ? Mode.DROP : Mode.ATTACK);
               }
            }
         }
         case ATTACK -> {
            if (this.target == null || !ChitauriMob.validTarget(this.target) || this.modeTicks > 700 || this.target.distanceToSqr(this) > 90.0 * 90.0) {
               this.target = null;
               this.setMode(Mode.PATROL);
               goal = pos.add(this.getDeltaMovement().scale(10.0));
               break;
            }
            Vec3 tp = this.target.position().add(0, this.target.getBbHeight() * 0.5, 0);
            speed = ChariotSteering.ATTACK;
            if (this.stage == 0) {
               goal = ChariotSteering.approachPoint(tp, this.azimuth, 30.0, 13.0);
               if (pos.distanceToSqr(goal) < 49.0) {
                  this.stage = 1;
               }
            } else if (this.stage == 1) {
               goal = tp.add(this.target.getDeltaMovement().scale(8.0)).add(0, 1.5, 0);
               clearance = 3.0;
               Vec3 to = tp.subtract(pos);
               double dist = to.length();
               Vec3 vel = this.getDeltaMovement();
               if (this.cannonCooldown <= 0 && dist < 42.0 && vel.lengthSqr() > 0.01 && vel.normalize().dot(to.normalize()) > 0.975) {
                  this.fireCannon(level, tp);
               }
               if (dist < 10.0 || this.modeTicks > 400) {
                  this.stage = 2;
                  Vec3 h = new Vec3(vel.x, 0, vel.z);
                  this.passGoal = pos.add(h.lengthSqr() > 1.0E-4 ? h.normalize().scale(22.0) : new Vec3(0, 0, 22)).add(0, 10.0, 0);
               }
            } else {
               goal = this.passGoal == null ? pos.add(0, 8, 0) : this.passGoal;
               if (this.passGoal == null || pos.distanceToSqr(this.passGoal) < 36.0) {
                  this.runs++;
                  this.azimuth = ChariotSteering.nextAzimuth(this.azimuth, this.random.nextDouble());
                  this.stage = 0;
                  if (this.runs >= 3) {
                     this.target = null;
                     this.setMode(Mode.PATROL);
                  }
               }
            }
         }
         case DROP -> {
            if (this.dropPoint == null) {
               this.dropPoint = this.pickDropPoint(level, anchor);
               this.stage = 0;
               if (this.dropPoint == null) {
                  this.setMode(Mode.PATROL);
                  goal = pos.add(0, 2, 0);
                  break;
               }
            }
            Vec3 dp = this.dropPoint;
            if (this.stage == 0) {
               goal = dp.add(0, 12.0, 0);
               if (pos.distanceToSqr(goal) < 25.0) {
                  this.stage = 1;
               }
            } else if (this.stage == 1) {
               goal = dp.add(0, 1.9, 0);
               speed = 0.4;
               arrive = 7.0;
               clearance = 0.0;
               if (pos.distanceToSqr(goal) < 1.0 || this.verticalCollision || this.modeTicks > 260) {
                  this.stage = 2;
                  this.modeTicks = 0;
               }
            } else {
               goal = pos;
               speed = 0.0;
               clearance = 0.0;
               if (this.modeTicks == 8) {
                  this.ejectSeat(1);
               } else if (this.modeTicks == 15) {
                  this.ejectSeat(2);
               } else if (this.modeTicks > 26) {
                  this.dropPoint = null;
                  this.target = this.findTarget(level, 56.0);
                  this.azimuth = this.random.nextDouble() * 360.0;
                  this.stage = 0;
                  this.runs = 0;
                  this.setMode(this.target != null ? Mode.ATTACK : Mode.PATROL);
               }
            }
         }
         case EVADE -> {
            goal = pos.add(this.evadeDir.scale(20.0));
            speed = ChariotSteering.EVADE;
            accel = 0.1;
            if (this.modeTicks > 34) {
               this.setMode(this.afterEvade == Mode.DROP && !this.hasTroopers() ? Mode.PATROL : this.afterEvade);
            }
         }
         default -> goal = pos;
      }
      Vec3 desired = ChariotSteering.seek(pos, goal, speed, arrive);
      desired = this.avoid(level, pos, desired, speed, clearance, anchor);
      Vec3 vel = ChariotSteering.steer(this.getDeltaMovement(), desired, accel, Math.max(0.05, speed * 1.08));
      if (speed == 0.0) {
         vel = this.getDeltaMovement().scale(0.7);
      }
      this.setDeltaMovement(vel);
      this.orient(vel);
   }

   private void setMode(Mode mode) {
      this.mode = mode;
      this.modeTicks = 0;
   }

   /** Feelers, clearance above the surface, separation, ceiling, leash and loaded-chunk guard. */
   private Vec3 avoid(ServerLevel level, Vec3 pos, Vec3 desired, double speed, double clearance, Vec3 anchor) {
      Vec3 vel = this.getDeltaMovement();
      Vec3 fwd = vel.lengthSqr() > 0.0025 ? vel.normalize() : desired.lengthSqr() > 1.0E-6 ? desired.normalize() : new Vec3(0, 0, 1);
      double look = 5.0 + vel.length() * 16.0;
      Vec3 ahead = pos.add(fwd.scale(look));
      if (!ChitauriTerrain.ticking(level, ahead.x, ahead.y, ahead.z)) {
         // never into unloaded or frozen chunks: turn back towards the portal side
         Vec3 home = anchor.add(0, 24, 0).subtract(pos);
         return home.normalize().scale(Math.max(0.3, speed));
      }
      if (clearance > 0.0 && ChitauriTerrain.clearDistance(level, pos, ahead) < look - 0.5) {
         Vec3 best = null;
         double bestClear = -1;
         Vec3 h = new Vec3(fwd.x, 0, fwd.z);
         if (h.lengthSqr() < 1.0E-4) {
            h = new Vec3(0, 0, 1);
         }
         h = h.normalize();
         Vec3[] tries = {
            h.scale(0.8).add(0, 0.6, 0).normalize(), ChariotSteering.yawed(h, 40), ChariotSteering.yawed(h, -40),
            ChariotSteering.yawed(h, 75).add(0, 0.4, 0).normalize(), ChariotSteering.yawed(h, -75).add(0, 0.4, 0).normalize(), new Vec3(0, 1, 0)
         };
         for (Vec3 t : tries) {
            double c = ChitauriTerrain.clearDistance(level, pos, pos.add(t.scale(look)));
            if (c > bestClear + 0.5) {
               bestClear = c;
               best = t;
            }
         }
         if (best != null) {
            desired = best.scale(Math.max(speed, 0.35));
         }
      }
      // clearance above the surface (buildings included) a little ahead
      Vec3 probe = pos.add(new Vec3(vel.x, 0, vel.z).scale(8.0));
      int ground = ChitauriTerrain.surfaceMax(level, probe.x, probe.z, 2);
      if (ground != ChitauriTerrain.UNKNOWN && clearance > 0.0) {
         double floor = ground + clearance;
         if (pos.y < floor) {
            desired = new Vec3(desired.x, Math.max(desired.y, Math.min(0.5, (floor - pos.y) * 0.14)), desired.z);
         }
      }
      if (pos.y > anchor.y + 58.0) {
         desired = desired.add(0, -0.08, 0);
      }
      double leash = 120.0;
      if (pos.distanceToSqr(anchor) > leash * leash) {
         desired = desired.add(anchor.subtract(pos).normalize().scale(0.25));
      }
      // keep apart from other chariots and out of Leviathans
      List<Entity> near = level.getEntities(this, this.getBoundingBox().inflate(12.0), e -> e instanceof ChitauriChariot || e instanceof LeviathanPart
         || e instanceof ChitauriLeviathan);
      for (Entity e : near) {
         double radius = e instanceof ChitauriChariot ? 9.0 : e.getBbWidth() * 0.5 + 6.0;
         desired = desired.add(ChariotSteering.separation(pos, e.getBoundingBox().getCenter(), radius, e instanceof ChitauriChariot ? 0.35 : 0.6));
      }
      return desired;
   }

   private void orient(Vec3 vel) {
      double h = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
      float yaw = this.getYRot();
      float delta = 0.0F;
      if (h > 0.03) {
         float want = (float)(Mth.atan2(-vel.x, vel.z) * Mth.RAD_TO_DEG);
         delta = Mth.clamp(Mth.wrapDegrees(want - yaw), -7.0F, 7.0F);
         yaw += delta;
      }
      this.setYRot(yaw);
      this.yBodyRot = yaw;
      this.yHeadRot = yaw;
      float pitch = (float)Mth.clamp(-Mth.atan2(vel.y, Math.max(h, 0.05)) * Mth.RAD_TO_DEG, -35.0, 35.0);
      this.setXRot(this.getXRot() + (pitch - this.getXRot()) * 0.25F);
      this.roll += (ChariotSteering.bank(delta) - this.roll) * 0.2F;
      this.entityData.set(ROLL, this.roll);
   }

   private Vec3 patrolPoint(ServerLevel level, Vec3 anchor) {
      for (int i = 0; i < 8; i++) {
         double a = this.random.nextDouble() * Math.PI * 2.0;
         double r = 12.0 + this.random.nextDouble() * 48.0;
         double x = anchor.x + Math.cos(a) * r;
         double z = anchor.z + Math.sin(a) * r;
         int s = ChitauriTerrain.surface(level, x, z);
         if (s == ChitauriTerrain.UNKNOWN || !ChitauriTerrain.ticking(level, x, s, z)) {
            continue;
         }
         return new Vec3(x, Math.max(s + 10.0 + this.random.nextDouble() * 12.0, anchor.y + 10.0), z);
      }
      return anchor.add(0, 22, 0);
   }

   @Nullable
   private LivingEntity findTarget(ServerLevel level, double range) {
      LivingEntity best = null;
      double bestScore = Double.MAX_VALUE;
      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(range), ChitauriMob::validTarget)) {
         double d = e.distanceToSqr(this);
         if (e instanceof Player) {
            d *= 0.5; // heroes first
         }
         if (d < bestScore) {
            bestScore = d;
            best = e;
         }
      }
      return best;
   }

   @Nullable
   private Vec3 pickDropPoint(ServerLevel level, Vec3 anchor) {
      LivingEntity t = this.findTarget(level, 64.0);
      Vec3 base = t != null ? t.position() : anchor;
      Vec3 best = null;
      double bestScore = -1.0E9;
      for (int i = 0; i < 10; i++) {
         double a = this.random.nextDouble() * Math.PI * 2.0;
         double r = (t != null ? 7.0 : 10.0) + this.random.nextDouble() * 16.0;
         double x = base.x + Math.cos(a) * r;
         double z = base.z + Math.sin(a) * r;
         int s = ChitauriTerrain.surface(level, x, z);
         if (s == ChitauriTerrain.UNKNOWN || !ChitauriTerrain.ticking(level, x, s, z)) {
            continue;
         }
         Vec3 p = new Vec3(Mth.floor(x) + 0.5, s, Mth.floor(z) + 0.5);
         if (!level.getBlockState(net.minecraft.core.BlockPos.containing(p.x, s - 1, p.z)).getFluidState().isEmpty()) {
            continue;
         }
         // the column above must take the hull
         boolean clear = true;
         for (double ox = -1.3; ox <= 1.31 && clear; ox += 1.3) {
            for (double oz = -1.3; oz <= 1.31 && clear; oz += 1.3) {
               Vec3 low = p.add(ox, 1.2, oz);
               clear = ChitauriTerrain.clear(level, low, low.add(0, 13, 0));
            }
         }
         if (!clear) {
            continue;
         }
         double score = -Math.abs(s - base.y) * 0.8 - p.distanceTo(base) * 0.1;
         if (score > bestScore) {
            bestScore = score;
            best = p;
         }
      }
      return best;
   }

   private void scanThreats(ServerLevel level) {
      if (this.evadeCooldown > 0 || this.mode == Mode.EVADE || this.mode == Mode.ENTRY || this.dying()) {
         return;
      }
      for (Player p : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(7.0), p -> ChitauriMob.validTarget(p) && !p.onGround())) {
         this.evadeFrom(p.position());
         return;
      }
   }

   private void evadeFrom(Vec3 threat) {
      Vec3 away = this.position().subtract(threat);
      Vec3 h = new Vec3(away.x, 0, away.z);
      if (h.lengthSqr() < 1.0E-4) {
         h = new Vec3(1, 0, 0);
      }
      this.evadeDir = ChariotSteering.yawed(h.normalize(), (this.random.nextBoolean() ? 1 : -1) * (35 + this.random.nextInt(40))).add(0, 0.45, 0).normalize();
      this.afterEvade = this.mode == Mode.EVADE ? this.afterEvade : this.mode;
      this.setMode(Mode.EVADE);
      this.evadeCooldown = 80;
   }

   private void fireCannon(ServerLevel level, Vec3 aim) {
      float yaw = this.getYRot() * Mth.DEG_TO_RAD;
      Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
      Vec3 from = this.position().add(0, 0.55, 0).add(fwd.scale(1.9));
      ChitauriBolt.shoot(level, this, from, aim.subtract(from), 2.8, 2.0F, 1.4F);
      this.cannonCooldown = 7;
      this.level().broadcastEntityEvent(this, EVENT_CANNON);
      ChitauriSounds.play(level, from, ChitauriSounds.CANNON_FIRE, 1.5F, 0.95F + this.random.nextFloat() * 0.1F);
      ChitauriDestruction.danger(level, aim, 18.0F, 80);
   }

   // ------------------------------------------------------------------ damage and death

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (ChitauriMob.is(source.getEntity()) || ChitauriMob.is(source.getDirectEntity())) {
         return false;
      }
      if (this.dying()) {
         if (!this.level().isClientSide && amount >= 8.0F) {
            this.explode(false); // shot down for good
         }
         return false;
      }
      boolean hit = super.hurt(source, amount);
      if (hit && !this.level().isClientSide) {
         if (this.isDeadOrDying() && amount >= 45.0F) {
            this.explode(false); // torn apart in the air
            return true;
         }
         Entity attacker = source.getEntity();
         if (attacker instanceof Player && !this.isDeadOrDying() && this.evadeCooldown <= 0 && this.mode != Mode.ENTRY) {
            this.evadeFrom(attacker.position());
         }
      }
      return hit;
   }

   @Override
   public void die(DamageSource source) {
      boolean first = !this.dying();
      super.die(source);
      if (first && !this.level().isClientSide) {
         this.entityData.set(STATE, STATE_DYING);
         this.spin = (this.random.nextBoolean() ? 1 : -1) * (6.0F + this.random.nextFloat() * 6.0F);
         ServerLevel level = (ServerLevel)this.level();
         ChitauriSounds.playWide(level, this.position(), ChitauriSounds.CHARIOT_EXPLODE, 1.2F, 1.35F, 64.0);
         ChitauriNet.fx(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.EXPLOSION, this.position().add(0, 0.6, 0), this.getDeltaMovement(), 0.6F,
            this.random.nextInt()), this.position(), 128);
         for (Entity e : List.copyOf(this.getPassengers())) {
            e.stopRiding();
            if (e instanceof ChitauriSoldier s) {
               s.setSeat(-1);
            }
            e.setDeltaMovement(this.getDeltaMovement().add((this.random.nextDouble() - 0.5) * 0.4, 0.2, (this.random.nextDouble() - 0.5) * 0.4));
            e.hasImpulse = true;
         }
         ChitauriInvasion.onKilled(this, source);
      }
   }

   @Override
   protected void tickDeath() {
      if (this.level().isClientSide) {
         return;
      }
      this.dyingTicks++;
      Vec3 v = this.getDeltaMovement();
      double h = Math.sqrt(v.x * v.x + v.z * v.z);
      Vec3 fwd = h > 0.05 ? new Vec3(v.x / h, 0, v.z / h) : new Vec3(-Mth.sin(this.getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(this.getYRot() * Mth.DEG_TO_RAD));
      fwd = ChariotSteering.yawed(fwd, this.spin * 0.6);
      double speed = Math.max(0.35, Math.min(0.75, h));
      this.setDeltaMovement(fwd.x * speed, Math.max(-1.1, v.y - 0.045), fwd.z * speed);
      this.setYRot(this.getYRot() + this.spin);
      this.yBodyRot = this.getYRot();
      this.setXRot(Math.min(40.0F, this.getXRot() + 1.5F));
      this.roll += this.spin * 0.8F;
      this.entityData.set(ROLL, Mth.wrapDegrees(this.roll));
      if (this.dyingTicks % 2 == 0) {
         ChitauriDestruction.danger((ServerLevel)this.level(), this.position(), 14.0F, 40);
      }
      if (this.onGround() || this.dyingTicks > 90 || this.isInWater() || this.getY() < this.level().getMinBuildHeight()) {
         this.explode(true);
      }
   }

   /** Explosion and breakup; {@code impact} also smashes the blocks it hit. */
   public void explode(boolean impact) {
      if (this.isRemoved() || !(this.level() instanceof ServerLevel level)) {
         return;
      }
      Vec3 at = this.position().add(0, 0.6, 0);
      Vec3 v = this.getDeltaMovement();
      ChitauriNet.fxAlways(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.CHARIOT_BREAKUP, at, v, this.getYRot(), Float.floatToIntBits(this.roll)), at, 160);
      ChitauriNet.fxAlways(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.EXPLOSION, at, v, 1.0F, this.random.nextInt()), at, 160);
      ChitauriSounds.playWide(level, at, ChitauriSounds.CHARIOT_EXPLODE, 2.2F, 0.9F + this.random.nextFloat() * 0.15F, 96.0);
      ChitauriDestruction.blastEntities(level, at, 4.5, 10.0F, this, this);
      if (impact) {
         ChitauriDestruction.smash(level, at, 2.3, 2.0F, 40, v.scale(0.5), 6);
      }
      if (!this.isDeadOrDying()) {
         ChitauriInvasion.onKilled(this, this.damageSources().generic());
      }
      this.ejectPassengers();
      this.discard();
   }

   // ------------------------------------------------------------------ misc

   @Override
   public void handleEntityEvent(byte id) {
      if (id == EVENT_CANNON) {
         this.clientCannonStart = this.tickCount;
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
      return distance < 192.0 * 192.0;
   }

   @Override
   public AABB getBoundingBoxForCulling() {
      return this.getBoundingBox().inflate(2.0);
   }

   @Override
   protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
      return ChitauriSounds.LEVIATHAN_HIT.get();
   }

   @Override
   protected float getSoundVolume() {
      return 0.6F;
   }

   @Override
   public float getVoicePitch() {
      return 1.6F;
   }
}
