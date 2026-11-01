package dev.baranhan.viltrumitecore.scp.scp096;

import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The chase of one SCP-096: own steering on the velocity (no teleport), vanilla path nodes as waypoints when the
 * target is out of sight, smashing through what blocks the way, climbing walls, ballistic leaps to roofs and at
 * flying heroes, bursts when the target speeds away, the attack. Holds a moving region ticket while the target is
 * within {@link Scp096Rules#TICKET_RANGE}; farther away the creature freezes with its chunk (state in NBT).
 */
final class Scp096Chase {
   private static final TicketType<ChunkPos> TICKET = TicketType.create("viltrumitecore_scp096", Comparator.comparingLong(ChunkPos::toLong), 60);
   private final Scp096Entity mob;
   private double vx;
   private double vz;
   @Nullable
   private Path path;
   private int pathAge = 999;
   @Nullable
   private Vec3 pathGoal;
   @Nullable
   private Vec3 lastTarget;
   private Vec3 targetVel = Vec3.ZERO;
   private int losClock;
   private boolean los;
   private int windowClock;
   private double windowStart = Double.MAX_VALUE;
   private int smashTicks;
   private boolean leaping;
   private int leapTicks;
   private double leapVx;
   private double leapVz;
   private int leapCooldown;
   private boolean climbing;
   private int climbTicks;
   private int pullTicks;
   private int attackCooldown;
   private long ticketChunk = Long.MIN_VALUE;
   private int ticketClock;
   long lastSmashMark = -1000L;
   long lastRuin = -1000L;
   long lastGrid = -1000L;
   /** QA counters (/scp096 status). */
   int leaps;
   int climbs;
   int smashed;
   int strikes;

   Scp096Chase(Scp096Entity mob) {
      this.mob = mob;
   }

   void reset() {
      this.vx = 0.0;
      this.vz = 0.0;
      this.path = null;
      this.pathGoal = null;
      this.lastTarget = null;
      this.targetVel = Vec3.ZERO;
      this.smashTicks = 0;
      this.leaping = false;
      this.climbing = false;
      this.windowStart = Double.MAX_VALUE;
      this.windowClock = 0;
      this.mob.setFlag(Scp096Entity.FLAG_CLIMB, false);
      this.mob.setFlag(Scp096Entity.FLAG_LEAP, false);
   }

   String describe() {
      return "leaps=" + this.leaps + " climbs=" + this.climbs + " smashed=" + this.smashed + " strikes=" + this.strikes;
   }

   /** No target in this level: slow to a stop and wait. */
   void idle() {
      this.vx *= 0.6;
      this.vz *= 0.6;
      this.leaping = false;
      this.climbing = false;
      this.mob.setFlag(Scp096Entity.FLAG_CLIMB, false);
      this.mob.setFlag(Scp096Entity.FLAG_LEAP, false);
      this.mob.setDeltaMovement(this.vx, this.mob.getDeltaMovement().y, this.vz);
   }

   void tick(ServerLevel level, Entity target) {
      Vec3 pos = this.mob.position();
      Vec3 tp = target.position();
      if (this.lastTarget != null) {
         Vec3 d = tp.subtract(this.lastTarget);
         if (d.lengthSqr() < 400.0) {
            this.targetVel = this.targetVel.scale(0.6).add(d.scale(0.4));
         } else {
            this.targetVel = Vec3.ZERO;
         }
      }
      this.lastTarget = tp;
      double dx = tp.x - pos.x;
      double dy = tp.y - pos.y;
      double dz = tp.z - pos.z;
      double dh = Math.sqrt(dx * dx + dz * dz);
      double dist = Math.sqrt(dh * dh + dy * dy);
      if (this.attackCooldown > 0) {
         this.attackCooldown--;
      }
      if (this.leapCooldown > 0) {
         this.leapCooldown--;
      }
      this.holdTicket(level, dh);
      this.mob.getNavigation().stop();
      // ---- attack: anything in reach, in the air too ----
      double reach = Scp096Rules.ATTACK_REACH + target.getBbWidth() * 0.5;
      if (dh < reach && dy > -2.6 && dy < 2.8 && this.attackCooldown == 0) {
         this.strike(level, target);
      }
      // ---- a leap in flight ----
      if (this.leaping) {
         this.leapTicks++;
         this.mob.setDeltaMovement(this.leapVx, this.mob.getDeltaMovement().y, this.leapVz);
         this.face(this.leapVx, this.leapVz, target);
         if (this.leapTicks > 2 && (this.mob.onGround() || this.mob.isInWater()) || this.leapTicks > 90) {
            this.leaping = false;
            this.leapCooldown = 8;
            this.mob.setFlag(Scp096Entity.FLAG_LEAP, false);
            this.vx = this.leapVx * 0.5;
            this.vz = this.leapVz * 0.5;
         } else if (this.mob.horizontalCollision && this.leapTicks > 2) {
            this.leaping = false;
            this.mob.setFlag(Scp096Entity.FLAG_LEAP, false);
            this.startClimb();
         }
         return;
      }
      // ---- where to run ----
      if (--this.losClock <= 0) {
         this.losClock = 4;
         this.los = this.lineOfSight(this.mob.getEyePosition(), target.getEyePosition());
      }
      boolean direct = dist < 7.0 || this.los && Math.abs(dy) < 4.0;
      Vec3 aim = direct ? this.lead(tp, dist) : this.followPath(tp);
      // ---- leaps: roofs and ledges above, heroes in the air ----
      if (this.mob.onGround() && this.leapCooldown == 0 && !this.climbing) {
         boolean high = dy > 3.5 && dh < Scp096Rules.LEAP_MAX_H && (this.los || dh < 10.0);
         boolean flying = !target.onGround() && !target.isInWater() && dy > 1.2 && dh < 18.0;
         if (high || flying) {
            if (!this.tryLeap(level, tp, flying)) {
               this.leapCooldown = 10;
            } else {
               return;
            }
         }
      }
      // ---- blocked: smash on the level of the target, climb toward one above ----
      boolean griefing = Scp096Destruction.griefing(level, this.mob);
      boolean blocked = this.mob.horizontalCollision;
      if (blocked && !this.climbing && this.smashTicks == 0) {
         if (griefing && dy < 1.5) {
            this.smashTicks = 6;
         } else {
            this.startClimb();
         }
      }
      // ---- stuck: no progress over the window -> tunnel straight at the target ----
      this.windowClock++;
      if (this.windowClock >= Scp096Rules.STUCK_WINDOW) {
         boolean stuck = this.windowStart - dist < Scp096Rules.STUCK_PROGRESS && dist > reach;
         this.windowClock = 0;
         this.windowStart = dist;
         if (stuck) {
            if (griefing) {
               this.smashTicks = Scp096Rules.SMASH_TICKS;
               this.path = null;
            } else if (blocked) {
               this.startClimb();
            }
         }
      }
      double hx = aim.x - pos.x;
      double hz = aim.z - pos.z;
      double hl = Math.sqrt(hx * hx + hz * hz);
      if (hl > 1.0E-4) {
         hx /= hl;
         hz /= hl;
      }
      boolean slowed = false;
      if (this.smashTicks > 0) {
         this.smashTicks--;
         Vec3 dir = Math.abs(dy) > 2.0 && this.smashTicks > 6 ? new Vec3(dx, dy, dz).normalize() : new Vec3(hx, 0.0, hz);
         List<BlockPos> broken = Scp096Destruction.carve(level, this.mob, dir, Scp096Rules.SMASH_PER_TICK, false);
         if (!broken.isEmpty()) {
            this.smashed += broken.size();
            Scp096Destruction.breach(level, this.mob, broken, this);
            slowed = broken.size() >= 3;
         }
      } else if (this.mob.getDeltaMovement().horizontalDistanceSqr() > 0.04) {
         List<BlockPos> plowed = Scp096Destruction.carve(level, this.mob, new Vec3(hx, 0.0, hz), 8, true);
         if (!plowed.isEmpty()) {
            this.smashed += plowed.size();
            Scp096Destruction.breach(level, this.mob, plowed, this);
         }
      }
      // ---- velocity ----
      double tv = Math.sqrt(this.targetVel.x * this.targetVel.x + this.targetVel.z * this.targetVel.z);
      double speed = Scp096Rules.chaseSpeed(tv, dist);
      if (dh < 3.0) {
         speed = Math.min(speed, 0.15 + dh * 0.25);
      }
      if (slowed) {
         speed *= 0.5;
      }
      double vy = this.mob.getDeltaMovement().y;
      if (this.climbing) {
         this.climbTicks++;
         speed = Math.min(speed, 0.18);
         if (this.mob.horizontalCollision) {
            vy = Scp096Rules.CLIMB_SPEED;
            this.pullTicks = 0;
         } else {
            this.pullTicks++;
            vy = 0.24;
            speed = 0.35;
            if (this.pullTicks > 4 || this.mob.onGround() && this.pullTicks > 1) {
               this.climbing = false;
            }
         }
         if (this.climbTicks > 240) {
            this.climbing = false;
         }
         this.mob.setFlag(Scp096Entity.FLAG_CLIMB, this.climbing);
      }
      double wx = hx * speed;
      double wz = hz * speed;
      double accel = Scp096Rules.CHASE_ACCEL;
      if (this.vx * wx + this.vz * wz < 0.5 * Math.sqrt((this.vx * this.vx + this.vz * this.vz) * (wx * wx + wz * wz))) {
         accel *= 2.5;
      }
      this.vx = Scp096Rules.approach(this.vx, wx, accel);
      this.vz = Scp096Rules.approach(this.vz, wz, accel);
      this.mob.setDeltaMovement(this.vx, vy, this.vz);
      this.face(this.vx, this.vz, target);
   }

   private void face(double mx, double mz, Entity target) {
      float yaw;
      if (mx * mx + mz * mz > 0.0025) {
         yaw = (float)(Mth.atan2(mz, mx) * (180.0 / Math.PI)) - 90.0F;
      } else {
         Vec3 d = target.position().subtract(this.mob.position());
         yaw = (float)(Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F;
      }
      float body = Mth.approachDegrees(this.mob.yBodyRot, yaw, 40.0F);
      this.mob.setYRot(body);
      this.mob.yBodyRot = body;
      this.mob.lookAtPoint(target.getEyePosition(), 50.0F);
   }

   /** Where a target in sight will be: a short lead on its motion, more for a fast one. */
   private Vec3 lead(Vec3 tp, double dist) {
      double t = Math.min(10.0, dist / Math.max(0.4, Scp096Rules.CHASE_SPEED)) * 0.5;
      return tp.add(this.targetVel.x * t, 0.0, this.targetVel.z * t);
   }

   private Vec3 followPath(Vec3 tp) {
      this.pathAge++;
      boolean stale = this.path == null || this.path.isDone() || this.pathAge > 40 || this.pathGoal == null
         || this.pathGoal.distanceToSqr(tp) > 4.0 && this.pathAge > 10;
      if (stale && (this.mob.onGround() || this.mob.isInWater())) {
         this.pathAge = 0;
         this.pathGoal = tp;
         this.path = this.mob.getNavigation().createPath(BlockPos.containing(tp), 1);
      }
      if (this.path == null || this.path.getNodeCount() == 0) {
         return tp;
      }
      Vec3 pos = this.mob.position();
      while (!this.path.isDone()) {
         Vec3 n = Vec3.atBottomCenterOf(this.path.getNextNodePos());
         double hx = n.x - pos.x;
         double hz = n.z - pos.z;
         if (hx * hx + hz * hz < 1.44 && Math.abs(n.y - pos.y) < 1.6) {
            this.path.advance();
         } else {
            break;
         }
      }
      return this.path.isDone() ? tp : Vec3.atBottomCenterOf(this.path.getNextNodePos());
   }

   private boolean lineOfSight(Vec3 from, Vec3 to) {
      HitResult hit = this.mob.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mob));
      return hit.getType() == HitResult.Type.MISS;
   }

   private boolean tryLeap(ServerLevel level, Vec3 tp, boolean flying) {
      Vec3 pos = this.mob.position();
      Scp096Leap.Solution s = Scp096Leap.solve(tp.x - pos.x, tp.y - pos.y, tp.z - pos.z, flying ? 0.6 : 1.2);
      if (s != null && flying) {
         // intercept: aim where the hero will be when the arc gets there
         Vec3 ahead = tp.add(this.targetVel.scale(Math.min(30, s.ticks())));
         Scp096Leap.Solution s2 = Scp096Leap.solve(ahead.x - pos.x, ahead.y - pos.y, ahead.z - pos.z, 0.6);
         if (s2 != null) {
            s = s2;
         }
      }
      if (s == null) {
         return false;
      }
      // headroom: no ceiling right above
      if (!level.noCollision(this.mob, this.mob.getBoundingBox().expandTowards(0.0, Math.min(4.0, s.apex()), 0.0))) {
         return false;
      }
      this.leaping = true;
      this.leapTicks = 0;
      this.leapVx = s.vx();
      this.leapVz = s.vz();
      this.leaps++;
      this.mob.setFlag(Scp096Entity.FLAG_LEAP, true);
      this.mob.setFlag(Scp096Entity.FLAG_CLIMB, false);
      this.mob.setDeltaMovement(s.vx(), s.vy(), s.vz());
      this.mob.setOnGround(false);
      this.mob.hasImpulse = true;
      level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.2F, 0.6F);
      return true;
   }

   private void startClimb() {
      if (!this.climbing) {
         this.climbing = true;
         this.climbTicks = 0;
         this.pullTicks = 0;
         this.climbs++;
         this.mob.setFlag(Scp096Entity.FLAG_CLIMB, true);
      }
   }

   private void strike(ServerLevel level, Entity target) {
      this.attackCooldown = Scp096Rules.ATTACK_INTERVAL;
      this.strikes++;
      this.mob.markAttack();
      this.mob.lookAtPoint(target.getEyePosition(), 180.0F);
      target.hurt(this.mob.attackSource(), Scp096Rules.ATTACK_DAMAGE);
      level.playSound(null, target.getX(), target.getY() + 1.0, target.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.3F,
         0.75F + level.random.nextFloat() * 0.2F);
   }

   private void holdTicket(ServerLevel level, double dh) {
      if (dh > Scp096Rules.TICKET_RANGE) {
         return;
      }
      ChunkPos cp = this.mob.chunkPosition();
      if (++this.ticketClock < 20 && cp.toLong() == this.ticketChunk) {
         return;
      }
      this.ticketClock = 0;
      this.ticketChunk = cp.toLong();
      level.getChunkSource().addRegionTicket(TICKET, cp, 3, cp);
   }
}
