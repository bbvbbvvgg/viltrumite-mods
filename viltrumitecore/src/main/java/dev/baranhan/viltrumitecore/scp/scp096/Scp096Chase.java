package dev.baranhan.viltrumitecore.scp.scp096;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The chase of one SCP-096: own steering on the velocity (no teleport), vanilla path nodes as waypoints when the
 * target is out of sight, smashing through what blocks the way, climbing walls, ballistic leaps to roofs and at
 * flying heroes, bursts when the target speeds away, the attack. Holds a moving region ticket while the target is
 * within {@link Scp096Rules#TICKET_RANGE}; farther away the creature freezes with its chunk (state in NBT).
 *
 * <p>A target it cannot run to (a roof, a floor above, a sealed room) is hunted with the tactics of
 * {@link Scp096Route} (issue #137): climbing the outer wall under the nearest edge of the target's floor and pulling
 * itself over the top, punching through ceilings and floors and jumping through the hole, tunnelling; a tactic that
 * stops making progress is dropped for the next, and a run of failures makes it pause instead of jumping forever.
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
   private Vec3 planGoal;
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
   private double climbBestY;
   private double climbStartY;
   private int climbBaseCol;
   private int climbIdle;
   private int attackCooldown;
   // ---- vertical pursuit (Scp096Route) ----
   private Scp096Route.Tactic tactic = Scp096Route.Tactic.GROUND;
   private final Scp096Route.Progress progress = new Scp096Route.Progress();
   private int planAge = 999;
   private int stalls;
   private double stallDist = Double.MAX_VALUE;
   private int pauseTicks;
   private int leapBan;
   private boolean pathReaches;
   @Nullable
   private Scp096Probe.Column column;
   private final LongOpenHashSet failedColumns = new LongOpenHashSet();
   private int wallX;
   private int wallZ;
   private double climbGoalY;
   private double climbHoldX;
   private double climbHoldZ;
   private int climbPunches;
   private int mantleTicks;
   private int mantleX;
   private int mantleZ;
   private int punchTicks;
   private boolean punchUp;
   @Nullable
   private int[] hole;
   @Nullable
   private Scp096Probe.Slab slab;
   private boolean hopping;
   private boolean hopOut;
   private int hopTicks;
   private double hopTop;
   private int hopFails;
   private int dashTicks;
   private int dashCooldown;
   private double dashX;
   private double dashZ;
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
   int punches;
   int mantles;
   int dashes;
   int stallCount;

   Scp096Chase(Scp096Entity mob) {
      this.mob = mob;
   }

   void reset() {
      this.vx = 0.0;
      this.vz = 0.0;
      this.path = null;
      this.pathGoal = null;
      this.pathAge = 999;
      this.lastTarget = null;
      this.targetVel = Vec3.ZERO;
      this.smashTicks = 0;
      this.leaping = false;
      this.climbing = false;
      this.windowStart = Double.MAX_VALUE;
      this.windowClock = 0;
      this.tactic = Scp096Route.Tactic.GROUND;
      this.progress.reset();
      this.planAge = 999;
      this.stalls = 0;
      this.stallDist = Double.MAX_VALUE;
      this.pauseTicks = 0;
      this.leapBan = 0;
      this.column = null;
      this.failedColumns.clear();
      this.stopMoves();
   }

   /** Ends every special move (climb, pull-up, punch, hop, dash) and clears their flags. */
   private void stopMoves() {
      this.climbing = false;
      this.mantleTicks = 0;
      this.punchTicks = 0;
      this.hopping = false;
      this.dashTicks = 0;
      this.mob.setFlag(Scp096Entity.FLAG_CLIMB, false);
      this.mob.setFlag(Scp096Entity.FLAG_LEAP, false);
      this.mob.setFlag(Scp096Entity.FLAG_DASH, false);
      this.mob.setFlag(Scp096Entity.FLAG_MANTLE, false);
   }

   String describe() {
      return "leaps=" + this.leaps + " climbs=" + this.climbs + " smashed=" + this.smashed + " strikes=" + this.strikes + " punches=" + this.punches
         + " mantles=" + this.mantles + " dashes=" + this.dashes + " stalls=" + this.stallCount + " tactic=" + this.tactic.key() + this.move();
   }

   Scp096Route.Tactic tactic() {
      return this.tactic;
   }

   /** The special move running now (QA). */
   private String move() {
      if (this.climbing) {
         return "/climb";
      }
      if (this.mantleTicks > 0) {
         return "/mantle";
      }
      if (this.punchTicks > 0) {
         return "/punch";
      }
      if (this.hopping) {
         return "/hop";
      }
      if (this.leaping) {
         return "/leap";
      }
      if (this.dashTicks > 0) {
         return "/dash";
      }
      return this.pauseTicks > 0 ? "/pause" : "";
   }

   private void log(String what, Object... args) {
      if (Scp096Spawner.LOG.isDebugEnabled()) {
         Scp096Spawner.LOG.debug("SCP-096 #{} route {} [{}] at {}", this.mob.getId(), String.format(Locale.ROOT, what, args), this.tactic.key(),
            String.format(Locale.ROOT, "%.1f %.1f %.1f", this.mob.getX(), this.mob.getY(), this.mob.getZ()));
      }
   }

   /** No target in this level: slow to a stop and wait. */
   void idle() {
      this.vx *= 0.6;
      this.vz *= 0.6;
      this.leaping = false;
      this.stopMoves();
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
      if (this.dashCooldown > 0) {
         this.dashCooldown--;
      }
      if (this.leapBan > 0) {
         this.leapBan--;
      }
      this.planAge++;
      this.holdTicket(level, dh);
      this.mob.getNavigation().stop();
      // ---- attack: anything in reach, in the air too ----
      double reach = Scp096Rules.ATTACK_REACH + target.getBbWidth() * 0.5;
      boolean inReach = dh < reach && dy > -2.6 && dy < 2.8;
      // a strike needs an open line to the body: never through a wall, a floor or an unbreakable block (review #129)
      if (Scp096Rules.mayStrike(inReach, this.attackCooldown, inReach && this.attackCooldown == 0 ? this.clearStrikeRays(target) : 0)) {
         this.strike(level, target);
         this.succeed("strike");
      }
      if (--this.losClock <= 0) {
         this.losClock = 4;
         this.los = this.lineOfSight(this.mob.getEyePosition(), target.getEyePosition());
      }
      boolean griefing = Scp096Destruction.griefing(level, this.mob);
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
            if (this.progress.leapLanded()) {
               // leaps that keep landing where they started: no more of them for a while (never an endless hopping)
               this.leapBan = Scp096Route.LEAP_BAN_TICKS;
               this.log("leap ban");
            }
         } else if (this.mob.horizontalCollision && this.leapTicks > 2) {
            this.leaping = false;
            this.mob.setFlag(Scp096Entity.FLAG_LEAP, false);
            this.startClimb(axisX(this.leapVx, this.leapVz), axisZ(this.leapVx, this.leapVz), this.goalY(level, target, dy), false);
         }
         return;
      }
      // ---- the special moves of the vertical pursuit ----
      if (this.mantleTicks > 0) {
         this.tickMantle(target);
         return;
      }
      if (this.hopping) {
         this.tickHop(level, target);
         return;
      }
      if (this.punchTicks > 0) {
         this.tickPunch(level, target, dy);
         return;
      }
      if (this.climbing && this.tickClimb(level, target, griefing, dy, dh)) {
         return;
      }
      if (this.pauseTicks > 0) {
         // a run of failed tactics: stand and stare for a moment instead of jumping at the ceiling forever
         this.pauseTicks--;
         this.vx *= 0.6;
         this.vz *= 0.6;
         this.mob.setDeltaMovement(this.vx, this.mob.getDeltaMovement().y, this.vz);
         this.face(0.0, 0.0, target);
         return;
      }
      // ---- the tactic for this target ----
      this.plan(level, target, tp, dy, dh, griefing);
      double toColumn = this.column == null ? dh : Math.hypot(this.column.x() + 0.5 - pos.x, this.column.z() + 0.5 - pos.z);
      if (dist < this.stallDist - 4.0) {
         this.stalls = 0;
         this.stallDist = Double.MAX_VALUE;
      }
      if (this.progress.tick(Scp096Route.measure(this.tactic, dh, dy, toColumn))) {
         this.stall("no progress", dist);
      }
      // ---- where to run ----
      boolean direct = dist < 7.0 || this.los && Math.abs(dy) < 4.0;
      Vec3 aim = switch (this.tactic) {
         case FACADE -> this.column != null ? this.column.center(pos.y) : direct ? this.lead(tp, dist) : this.followPath(tp);
         case BORE -> new Vec3(tp.x, pos.y, tp.z);
         case TUNNEL -> tp;
         default -> direct ? this.lead(tp, dist) : this.followPath(tp);
      };
      double hx = aim.x - pos.x;
      double hz = aim.z - pos.z;
      double hl = Math.sqrt(hx * hx + hz * hz);
      if (hl > 1.0E-4) {
         hx /= hl;
         hz /= hl;
      }
      boolean grounded = this.mob.onGround() || this.mob.isInWater();
      // ---- facade: at the foot of the column, up the wall ----
      if (this.tactic == Scp096Route.Tactic.FACADE && this.column != null && grounded && toColumn < 0.8) {
         Scp096Probe.Column c = this.column;
         this.startClimb(c.wallX(), c.wallZ(), c.landingY(), true);
         this.climbHoldX = c.x() + 0.5;
         this.climbHoldZ = c.z() + 0.5;
         this.climbBaseCol = c.wallX() != 0 ? c.x() : c.z();
         return;
      }
      // ---- bore: under (over) the target on this floor, punch through the slab ----
      if (this.tactic == Scp096Route.Tactic.BORE && grounded && (dh < 1.2 || dh < 3.0 && this.progress.since() > 30)) {
         if (this.beginPunch(level, target, dy > 0.0)) {
            return;
         }
         this.stall("nothing to punch", dist);
      }
      if (this.tactic == Scp096Route.Tactic.TUNNEL && griefing) {
         this.smashTicks = Math.max(this.smashTicks, 8);
      }
      // ---- leaps: roofs and ledges above, heroes in the air, the gap to the next roof ----
      if (grounded && this.leapCooldown == 0 && this.leapBan == 0 && !this.climbing && this.tactic != Scp096Route.Tactic.BORE) {
         boolean high = dy > 3.5 && dh < Scp096Rules.LEAP_MAX_H && (this.los || dh < 10.0);
         boolean flying = !target.onGround() && !target.isInWater() && dy > 1.2 && dh < 18.0;
         boolean gap = this.los && dh > 4.0 && dh < Scp096Rules.LEAP_MAX_H * 1.5 && dy > -8.0 && dy < Scp096Rules.LEAP_MAX_UP
            && this.gapAhead(level, hx, hz);
         if (high || flying || gap) {
            if (!this.tryLeap(level, tp, flying)) {
               this.leapCooldown = 10;
            } else {
               return;
            }
         }
      }
      // ---- dash: a lunge at a target in sight on this level ----
      if (this.dashTicks == 0 && this.tactic == Scp096Route.Tactic.GROUND && Scp096Route.mayDash(this.los, dy, dist, grounded, this.dashCooldown)) {
         this.startDash(level, hx, hz);
      }
      // ---- blocked: smash on the level of the target, climb toward one above ----
      boolean blocked = this.mob.horizontalCollision;
      if (blocked && !this.climbing && this.smashTicks == 0) {
         boolean onLevel = dy < 1.5 || this.tactic == Scp096Route.Tactic.BORE || this.tactic == Scp096Route.Tactic.TUNNEL;
         if (griefing && onLevel) {
            this.smashTicks = 6;
         } else {
            this.startClimb(axisX(hx, hz), axisZ(hx, hz), this.goalY(level, target, dy), false);
         }
         this.dashTicks = 0;
      }
      // ---- stuck: no progress over the window -> tunnel straight at the target ----
      this.windowClock++;
      if (this.windowClock >= Scp096Rules.STUCK_WINDOW) {
         boolean stuck = this.windowStart - dist < Scp096Rules.STUCK_PROGRESS && dist > reach;
         this.windowClock = 0;
         this.windowStart = dist;
         if (stuck && this.tactic != Scp096Route.Tactic.FACADE && this.tactic != Scp096Route.Tactic.BORE) {
            if (griefing) {
               this.smashTicks = Scp096Rules.SMASH_TICKS;
               this.path = null;
            } else if (blocked) {
               this.startClimb(axisX(hx, hz), axisZ(hx, hz), this.goalY(level, target, dy), false);
            }
         }
      }
      boolean slowed = false;
      double vy = this.mob.getDeltaMovement().y;
      if (this.smashTicks > 0) {
         this.smashTicks--;
         Vec3 dir = Math.abs(dy) > 2.0 && (this.smashTicks > 6 || this.tactic == Scp096Route.Tactic.TUNNEL) ? new Vec3(dx, dy, dz).normalize()
            : new Vec3(hx, 0.0, hz);
         List<BlockPos> broken = Scp096Destruction.carve(level, this.mob, dir, Scp096Rules.SMASH_PER_TICK, false);
         if (!broken.isEmpty()) {
            this.smashed += broken.size();
            Scp096Destruction.breach(level, this.mob, broken, this);
            slowed = broken.size() >= 3;
         }
         if (this.tactic == Scp096Route.Tactic.TUNNEL && dir.y > 0.3 && Scp096Probe.free(level, this.mob, 0.0, 0.3, 0.0)) {
            // up its own tunnel
            vy = Math.min(0.35, dir.y * 0.5);
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
      double accel = Scp096Rules.CHASE_ACCEL;
      if (this.dashTicks > 0) {
         this.dashTicks--;
         // the lunge: straight and much faster, homing a little on the target
         double mx = this.dashX * 0.7 + hx * 0.3;
         double mz = this.dashZ * 0.7 + hz * 0.3;
         double ml = Math.sqrt(mx * mx + mz * mz);
         if (ml > 1.0E-4) {
            hx = mx / ml;
            hz = mz / ml;
         }
         speed = Math.max(speed, Scp096Route.DASH_SPEED);
         accel = 0.45;
         if (this.dashTicks == 0 || dist < reach) {
            this.dashTicks = 0;
            this.mob.setFlag(Scp096Entity.FLAG_DASH, false);
         }
      }
      if (slowed) {
         speed *= 0.5;
      }
      double wx = hx * speed;
      double wz = hz * speed;
      if (this.vx * wx + this.vz * wz < 0.5 * Math.sqrt((this.vx * this.vx + this.vz * this.vz) * (wx * wx + wz * wz))) {
         accel *= 2.5;
      }
      this.vx = Scp096Rules.approach(this.vx, wx, accel);
      this.vz = Scp096Rules.approach(this.vz, wz, accel);
      this.mob.setDeltaMovement(this.vx, vy, this.vz);
      this.face(this.vx, this.vz, target);
   }

   // ------------------------------------------------------------------ the vertical pursuit (issue #137)

   /** Re-picks the tactic every {@link Scp096Route#REPLAN_TICKS} (sooner when the target moved or changed level). */
   private void plan(ServerLevel level, Entity target, Vec3 tp, double dy, double dh, boolean griefing) {
      boolean onLevel = Math.abs(dy) < Scp096Route.LEVEL_DY;
      boolean levelChanged = onLevel != (this.tactic == Scp096Route.Tactic.GROUND || this.tactic == Scp096Route.Tactic.TUNNEL);
      boolean moved = this.planGoal == null || this.planGoal.distanceToSqr(tp) > 16.0;
      if (this.planAge < Scp096Route.REPLAN_TICKS && !moved && !(levelChanged && this.planAge >= 10)) {
         return;
      }
      this.planAge = 0;
      this.planGoal = tp;
      Scp096Probe.Column col = null;
      boolean underCeiling = false;
      boolean overFloor = false;
      if (!onLevel) {
         // a walkable way (stairs, ramps, open doors) for a target on another level: the search waits its cooldown
         if (this.pathAge >= Scp096Rules.REPATH_MIN_TICKS && (this.mob.onGround() || this.mob.isInWater())) {
            this.pathAge = 0;
            this.pathGoal = tp;
            this.path = this.mob.getNavigation().createPath(BlockPos.containing(tp), 1);
         }
         this.pathReaches = this.path != null && this.path.canReach() && this.pathGoal != null && this.pathGoal.distanceToSqr(tp) < 9.0;
         if (dy > 0.0) {
            underCeiling = Scp096Probe.ceiling(level, this.mob, Scp096Probe.footprint(this.mob.getBoundingBox(), 0.0), 8) != null;
            col = Scp096Probe.facade(level, this.mob, target, griefing, this.failedColumns);
         } else {
            overFloor = Scp096Probe.floor(level, this.mob, Scp096Probe.hole(this.mob)) != null;
         }
      } else {
         this.pathReaches = false;
      }
      this.column = col;
      Scp096Route.Tactic next = Scp096Route.choose(dy, dh, this.pathReaches, underCeiling, overFloor, griefing, col != null, this.stalls);
      if (next != this.tactic) {
         this.log("tactic %s -> %s dy=%.1f dh=%.1f path=%s ceiling=%s column=%s", this.tactic.key(), next.key(), dy, dh, this.pathReaches,
            underCeiling, col == null ? "-" : col.x() + "," + col.z() + "/" + col.landingY() + " ledges=" + col.overhangs());
         this.tactic = next;
         this.progress.reset();
      }
   }

   private void stall(String why, double dist) {
      this.stalls++;
      this.stallCount++;
      if (this.stallDist == Double.MAX_VALUE) {
         this.stallDist = dist;
      }
      if (this.tactic == Scp096Route.Tactic.FACADE && this.column != null) {
         this.failedColumns.add(this.column.key());
      }
      this.planAge = 999;
      this.progress.reset();
      this.log("stall %d (%s)", this.stalls, why);
      if (this.stalls % Scp096Route.STALLS_BEFORE_PAUSE == 0) {
         this.pauseTicks = Scp096Route.PAUSE_TICKS;
         this.failedColumns.clear();
         this.log("pause");
      }
   }

   private void succeed(String what) {
      if (this.stalls > 0 || !"strike".equals(what)) {
         this.log("%s", what);
      }
      this.stalls = 0;
      this.stallDist = Double.MAX_VALUE;
      this.progress.reset();
      this.planAge = 999;
   }

   /** Feet height the climb aims for: the target's floor when it is above, otherwise only a wall top counts. */
   private double goalY(ServerLevel level, Entity target, double dy) {
      if (dy <= 1.0) {
         return Double.MAX_VALUE;
      }
      int l = Scp096Probe.landing(level, target);
      return l == Integer.MIN_VALUE ? target.getY() : l;
   }

   private static int axisX(double x, double z) {
      return Math.abs(x) >= Math.abs(z) && Math.abs(x) > 1.0E-4 ? (int)Math.signum(x) : 0;
   }

   private static int axisZ(double x, double z) {
      return Math.abs(z) > Math.abs(x) ? (int)Math.signum(z) : 0;
   }

   private void startClimb(int wx, int wz, double goalY, boolean facade) {
      if (this.climbing || wx == 0 && wz == 0) {
         return;
      }
      this.climbing = true;
      this.climbTicks = 0;
      this.pullTicks = 0;
      this.climbs++;
      this.climbPunches = 0;
      this.wallX = wx;
      this.wallZ = wz;
      this.climbGoalY = goalY;
      this.climbHoldX = Mth.floor(this.mob.getX()) + 0.5;
      this.climbHoldZ = Mth.floor(this.mob.getZ()) + 0.5;
      this.climbBaseCol = wx != 0 ? Mth.floor(this.mob.getX()) : Mth.floor(this.mob.getZ());
      this.climbStartY = this.mob.getY();
      this.climbBestY = this.mob.getY();
      this.climbIdle = 0;
      this.dashTicks = 0;
      this.mob.setFlag(Scp096Entity.FLAG_DASH, false);
      this.mob.setFlag(Scp096Entity.FLAG_CLIMB, true);
      this.log("climb wall=%d,%d goal=%s%s", wx, wz, goalY == Double.MAX_VALUE ? "top" : String.format(Locale.ROOT, "%.0f", goalY), facade ? " facade" : "");
   }

   private void stopClimb(String why) {
      this.climbing = false;
      this.mob.setFlag(Scp096Entity.FLAG_CLIMB, false);
      this.log("climb end (%s)", why);
   }

   /**
    * One tick on the wall. The climber follows the relief of the facade: it hugs the wall in a recess, moves out ahead of
    * a part that juts out above (a cornice, the floor slab over an arcade, a pilaster) and back in after it, and punches
    * away a thin ledge it cannot get around. It never drifts into a window of a lower floor, pounces at a target in
    * reach, and pulls itself over the top at the target's floor or where the wall ends under open sky. Returns false
    * when it let go.
    */
   private boolean tickClimb(ServerLevel level, Entity target, boolean griefing, double dy, double dh) {
      this.climbTicks++;
      Vec3 pos = this.mob.position();
      double vy = Scp096Route.climbSpeed(this.climbTicks);
      double dist = Math.sqrt(dh * dh + dy * dy);
      // pounce from the wall: a hero hovering by the facade, the roof edge right above with the target in sight
      if (this.climbTicks % 4 == 0 && this.leapBan == 0 && this.los && dh < Scp096Rules.LEAP_MAX_H && dy > -8.0
         && (!target.onGround() && !target.isInWater() && dy > -2.0 || dy > 0.5 && dy < 6.0)) {
         if (this.tryLeap(level, target.position(), !target.onGround())) {
            this.climbing = false;
            this.mob.setFlag(Scp096Entity.FLAG_CLIMB, false);
            this.log("pounce from the wall");
            return true;
         }
      }
      double feet = this.mob.getY();
      int feetY = Mth.floor(feet + 0.01);
      int headY = Mth.floor(feet + this.mob.getBbHeight() - 0.01);
      // ---- the relief: which column along the wall axis to hold (k > 0 toward the wall from the base column) ----
      int look = headY + 1 + (int)Math.ceil(vy * 5.0);
      int curK = this.climbK(pos);
      int k = Integer.MIN_VALUE;
      for (int c = 3; c >= -2; c--) {
         if (!this.climbColumnFree(level, c, feetY, look) || !this.lateralFree(level, curK, c, feetY, headY)) {
            continue;
         }
         boolean hug = !this.climbColumnFree(level, c + 1, feetY, headY);
         if (hug || c <= 0) {
            k = c;
            break;
         }
      }
      boolean blockedUp = !Scp096Probe.free(level, this.mob, 0.0, Math.max(vy, 0.3), 0.0);
      if (k == Integer.MIN_VALUE || blockedUp && k == curK) {
         // nowhere to go round: punch a thin ledge away, or give up
         boolean fixed = false;
         if (blockedUp && griefing && this.climbPunches < Scp096Route.MAX_OVERHANG_PUNCHES) {
            AABB bb = this.mob.getBoundingBox();
            int y = Mth.floor(bb.maxY + 0.02);
            List<BlockPos> broken = Scp096Destruction.bore(level, this.mob, Scp096Probe.footprint(bb, 0.0), y, y + 1, bb.maxY, 6);
            if (!broken.isEmpty()) {
               this.climbPunches += broken.size();
               this.smashed += broken.size();
               this.punches++;
               this.mob.markPunch();
               Scp096Destruction.breach(level, this.mob, broken, this);
               fixed = true;
               this.log("punch ledge %d", broken.size());
            }
         }
         if (!fixed && blockedUp && ++this.climbIdle > 30) {
            this.stopClimb("overhang");
            this.stall("overhang", dist);
            return false;
         }
         if (k == Integer.MIN_VALUE) {
            k = curK;
         }
      }
      double u = this.climbU(k, k == 3 || !this.climbColumnFree(level, k + 1, feetY, headY));
      double along = this.wallX != 0 ? pos.x : pos.z;
      double shift = u - along;
      if (blockedUp || Math.abs(shift) > 0.35) {
         // hold on while it moves round the ledge (the head must not hit it)
         vy = blockedUp ? 0.0 : Math.min(vy, 0.15);
      }
      // ---- the top: the target's floor with the way in free, or the end of the wall under open sky ----
      boolean frontFree = Scp096Probe.free(level, this.mob, this.wallX * 0.9, 0.2, this.wallZ * 0.9);
      int fx = Mth.floor(pos.x + this.wallX * 0.75);
      int fz = Mth.floor(pos.z + this.wallZ * 0.75);
      int fy = Mth.floor(feet + 0.2);
      boolean wallTop = feet >= this.climbStartY + 1.5 && Scp096Probe.openAbove(level, fx, fy, fz, 5)
         && Scp096Probe.solid(level, new BlockPos(fx, fy - 1, fz))
         && Scp096Probe.openAbove(level, fx + this.wallX, fy, fz + this.wallZ, 3);
      if (Scp096Route.mayMantle(feet, this.climbGoalY, frontFree, wallTop)) {
         this.startMantle(level, target);
         return true;
      }
      if (feet >= this.climbGoalY - 0.35 && !frontFree) {
         // at the target's floor: a window or a wall in the way (a roof parapet is climbed over instead)
         boolean roof = Scp096Probe.openAbove(level, fx, Mth.floor(this.climbGoalY) + 2, fz, 4);
         if (!roof && griefing) {
            List<BlockPos> broken = Scp096Destruction.carve(level, this.mob, new Vec3(this.wallX, 0.0, this.wallZ), 10, false);
            if (!broken.isEmpty()) {
               this.smashed += broken.size();
               Scp096Destruction.breach(level, this.mob, broken, this);
            }
            vy = 0.0;
         } else if (feet > this.climbGoalY + 4.0) {
            this.stopClimb("sealed");
            this.stall("sealed floor", dist);
            return false;
         }
      }
      // the wall is gone (smashed, or there never was one this high): let go
      if (!this.wallNear(level, pos)) {
         this.stopClimb("no wall");
         return false;
      }
      if (feet > this.climbBestY + 0.5) {
         this.climbBestY = feet;
         this.climbIdle = 0;
      } else if (++this.climbIdle > 60) {
         this.stopClimb("stuck");
         this.stall("climb stuck", dist);
         return false;
      }
      if (this.climbTicks > 1600) {
         this.stopClimb("too long");
         return false;
      }
      double ua = Mth.clamp(shift * 0.5, -0.25, 0.25);
      double hx;
      double hz;
      if (this.wallX != 0) {
         hx = ua;
         hz = Mth.clamp((this.climbHoldZ - pos.z) * 0.4, -0.15, 0.15);
      } else {
         hz = ua;
         hx = Mth.clamp((this.climbHoldX - pos.x) * 0.4, -0.15, 0.15);
      }
      this.vx = hx;
      this.vz = hz;
      this.mob.setDeltaMovement(hx, vy, hz);
      this.face(this.wallX * 0.1, this.wallZ * 0.1, target);
      this.mob.setFlag(Scp096Entity.FLAG_CLIMB, true);
      this.mob.fallDistance = 0.0F;
      return true;
   }

   /** Block coordinate along the wall axis of the column k steps toward the wall from the base column. */
   private int climbCol(int k) {
      return this.wallX != 0 ? this.climbBaseCol + this.wallX * k : this.climbBaseCol + this.wallZ * k;
   }

   /** The step (toward the wall) of the column the body is in now. */
   private int climbK(Vec3 pos) {
      int c = this.wallX != 0 ? Mth.floor(pos.x) : Mth.floor(pos.z);
      int w = this.wallX != 0 ? this.wallX : this.wallZ;
      return (c - this.climbBaseCol) * w;
   }

   /** Where to hold the body along the wall axis in column k: pressed to the face when there is one to hug. */
   private double climbU(int k, boolean hug) {
      int w = this.wallX != 0 ? this.wallX : this.wallZ;
      double center = this.climbCol(k) + 0.5;
      return hug ? center + w * (0.5 - Scp096Rules.WIDTH * 0.5 - 0.04) : center;
   }

   /** The column k steps toward the wall is open from y0 to y1 (inclusive) at the climb's sideways position. */
   private boolean climbColumnFree(ServerLevel level, int k, int y0, int y1) {
      int col = this.climbCol(k);
      int other = this.wallX != 0 ? Mth.floor(this.climbHoldZ) : Mth.floor(this.climbHoldX);
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int y = y0; y <= y1; y++) {
         if (this.wallX != 0 ? Scp096Probe.solid(level, m.set(col, y, other)) : Scp096Probe.solid(level, m.set(other, y, col))) {
            return false;
         }
      }
      return true;
   }

   /** Every column between the current one and the target one is open over the body (it can slide across). */
   private boolean lateralFree(ServerLevel level, int from, int to, int y0, int y1) {
      int step = Integer.signum(to - from);
      for (int c = from + step; step != 0 && c != to + step; c += step) {
         if (!this.climbColumnFree(level, c, y0, y1)) {
            return false;
         }
      }
      return true;
   }

   /** Something solid in the wall direction within reach, from a block under the feet to the head. */
   private boolean wallNear(ServerLevel level, Vec3 pos) {
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      int reachOut = 5;
      for (int d = 1; d <= reachOut; d++) {
         int x = Mth.floor(pos.x + this.wallX * (d - 0.3));
         int z = Mth.floor(pos.z + this.wallZ * (d - 0.3));
         for (int y = -1; y <= 2; y++) {
            if (Scp096Probe.solid(level, m.set(x, Mth.floor(pos.y) + y, z))) {
               return true;
            }
         }
      }
      return false;
   }

   /**
    * Lets go of the wall and pulls itself over the top. Over the wall when there is a floor beyond it (a parapet, a
    * window sill); back off the wall onto the floor on the target's side when it came up inside (a punched shaft along
    * an outer wall, whose top is a parapet with the street behind it).
    */
   private void startMantle(ServerLevel level, Entity target) {
      this.climbing = false;
      this.mob.setFlag(Scp096Entity.FLAG_CLIMB, false);
      this.mantleTicks = 1;
      this.mantles++;
      this.mantleX = this.wallX;
      this.mantleZ = this.wallZ;
      double tx = target.getX() - this.mob.getX();
      double tz = target.getZ() - this.mob.getZ();
      boolean behind = tx * this.wallX + tz * this.wallZ < 0.0;
      if (behind || !this.landable(level, this.wallX, this.wallZ)) {
         int bx = axisX(tx, tz);
         int bz = axisZ(tx, tz);
         if ((bx != 0 || bz != 0) && (bx != this.wallX || bz != this.wallZ) && this.landable(level, bx, bz)) {
            this.mantleX = bx;
            this.mantleZ = bz;
         } else if (this.landable(level, -this.wallX, -this.wallZ) && !this.landable(level, this.wallX, this.wallZ)) {
            this.mantleX = -this.wallX;
            this.mantleZ = -this.wallZ;
         }
      }
      this.mob.setFlag(Scp096Entity.FLAG_MANTLE, true);
      this.log("mantle %s", this.mantleX == this.wallX && this.mantleZ == this.wallZ ? "over" : "back");
   }

   /** A floor to step onto one block in this direction, around the height of the feet (free for the body above it). */
   private boolean landable(ServerLevel level, int dx, int dz) {
      int x = Mth.floor(this.mob.getX() + dx * 0.9);
      int z = Mth.floor(this.mob.getZ() + dz * 0.9);
      int fy = Mth.floor(this.mob.getY() + 0.5);
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int y = fy - 1; y <= fy + 1; y++) {
         if (Scp096Probe.solid(level, m.set(x, y - 1, z)) && !Scp096Probe.solid(level, m.set(x, y, z))
            && !Scp096Probe.solid(level, m.set(x, y + 1, z)) && !Scp096Probe.solid(level, m.set(x, y + 2, z))) {
            return true;
         }
      }
      return false;
   }

   /** Pulling itself over the top: up and in, until it stands on the floor. */
   private void tickMantle(Entity target) {
      this.mantleTicks++;
      double vy = this.mantleTicks <= 4 ? 0.3 : this.mob.getDeltaMovement().y;
      double hx = this.mantleX * 0.32;
      double hz = this.mantleZ * 0.32;
      this.mob.setDeltaMovement(hx, vy, hz);
      this.face(hx, hz, target);
      if (this.mantleTicks > 3 && this.mob.onGround() || this.mantleTicks > 14) {
         this.mantleTicks = 0;
         this.mob.setFlag(Scp096Entity.FLAG_MANTLE, false);
         this.vx = hx * 0.5;
         this.vz = hz * 0.5;
         this.succeed("over the top");
      }
   }

   /**
    * Starts the ceiling (floor) punch at the 2x2 hole around the nearest block corner: false when there is no slab or it
    * cannot go (unbreakable, too thick). A target right above in open air (a shaft) is jumped at instead.
    */
   private boolean beginPunch(ServerLevel level, Entity target, boolean up) {
      int[] h = Scp096Probe.hole(this.mob);
      Scp096Probe.Slab s = up ? Scp096Probe.ceiling(level, this.mob, h, 6) : Scp096Probe.floor(level, this.mob, h);
      if (s == null) {
         if (up && this.leapBan == 0) {
            return this.tryLeap(level, target.position(), false);
         }
         return false;
      }
      if (!s.breakable()) {
         this.log("slab at %d not breakable", s.near());
         return false;
      }
      this.hole = h;
      this.slab = s;
      this.punchUp = up;
      this.punchTicks = Scp096Route.PUNCH_WINDUP;
      this.mob.markPunch();
      this.log("punch %s slab=%d thick=%d", up ? "up" : "down", s.near(), s.thickness());
      return true;
   }

   private void tickPunch(ServerLevel level, Entity target, double dy) {
      int[] h = this.hole;
      Scp096Probe.Slab s = this.slab;
      if (h == null || s == null) {
         this.punchTicks = 0;
         return;
      }
      // set itself under the middle of the hole while it winds up
      Vec3 pos = this.mob.position();
      double cx = h[1];
      double cz = h[3];
      double hx = Mth.clamp((cx - pos.x) * 0.5, -0.2, 0.2);
      double hz = Mth.clamp((cz - pos.z) * 0.5, -0.2, 0.2);
      this.mob.setDeltaMovement(hx, this.mob.getDeltaMovement().y, hz);
      this.face(0.0, 0.0, target);
      if (--this.punchTicks > 0) {
         return;
      }
      int y0 = this.punchUp ? s.near() : s.near() - s.thickness() + 1;
      int y1 = this.punchUp ? s.near() + s.thickness() - 1 : s.near();
      // a storey too low for the body above the slab (a mezzanine, a crawl space): through the next slab as well
      int clear = Mth.ceil(Scp096Rules.HEIGHT);
      for (int pass = 0; pass < 2 && this.punchUp; pass++) {
         int next = Integer.MIN_VALUE;
         for (int y = y1 + 1; y <= y1 + clear && next == Integer.MIN_VALUE; y++) {
            if (anySolid(level, h, y)) {
               next = y;
            }
         }
         if (next == Integer.MIN_VALUE) {
            break;
         }
         int end = next;
         while (end < next + Scp096Route.MAX_SLAB - 1 && anySolid(level, h, end + 1)) {
            end++;
         }
         y1 = end;
      }
      List<BlockPos> broken = Scp096Destruction.bore(level, this.mob, h, y0, y1, this.punchUp ? this.mob.getBoundingBox().maxY : pos.y, 32);
      if (broken.isEmpty()) {
         this.stall("punch broke nothing", Math.abs(dy));
         return;
      }
      this.smashed += broken.size();
      this.punches++;
      Scp096Destruction.breach(level, this.mob, broken, this);
      level.playSound(null, cx, this.punchUp ? s.near() : pos.y, cz, Scp096Sounds.SMASH.get(), SoundSource.HOSTILE, 1.6F, 0.5F);
      if (!this.punchUp || dy < Scp096Route.LEVEL_DY) {
         // down: it falls through on its own; a target that fell in is on this level now
         return;
      }
      // up through the hole: a jump that clears the slab, then a step onto the floor beside the hole
      this.hopTop = y1 + 1;
      this.hopping = true;
      this.hopOut = false;
      this.hopTicks = 0;
      double vy = Scp096Leap.vyForApex(this.hopTop - pos.y + 1.2);
      this.mob.setDeltaMovement(0.0, vy, 0.0);
      this.mob.setOnGround(false);
      this.mob.hasImpulse = true;
      this.mob.setFlag(Scp096Entity.FLAG_LEAP, true);
      level.playSound(null, pos.x, pos.y, pos.z, Scp096Sounds.LEAP.get(), SoundSource.HOSTILE, 1.0F, 0.7F);
   }

   /** Any solid block in layer y over the hole columns. */
   private static boolean anySolid(ServerLevel level, int[] h, int y) {
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int x = h[0]; x <= h[1]; x++) {
         for (int z = h[2]; z <= h[3]; z++) {
            if (Scp096Probe.solid(level, m.set(x, y, z))) {
               return true;
            }
         }
      }
      return false;
   }

   private void tickHop(ServerLevel level, Entity target) {
      this.hopTicks++;
      int[] h = this.hole;
      Vec3 pos = this.mob.position();
      double hx = 0.0;
      double hz = 0.0;
      if (h != null) {
         double cx = h[1];
         double cz = h[3];
         if (pos.y >= this.hopTop + 0.05 || this.hopOut && pos.y >= this.hopTop - 0.3) {
            // above the slab: off the hole, toward the target (or any free side when it stands right over the hole),
            // and on until it lands (re-centring would pull it back into the hole)
            this.hopOut = true;
            double ox = target.getX() - cx;
            double oz = target.getZ() - cz;
            double ol = Math.sqrt(ox * ox + oz * oz);
            if (ol < 0.5) {
               ox = 1.0;
               oz = 0.0;
               for (int[] d : new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
                  if (Scp096Probe.free(level, this.mob, d[0] * 0.9, 0.05, d[1] * 0.9)) {
                     ox = d[0];
                     oz = d[1];
                     break;
                  }
               }
               ol = 1.0;
            }
            hx = ox / ol * 0.36;
            hz = oz / ol * 0.36;
         } else {
            hx = Mth.clamp((cx - pos.x) * 0.6, -0.2, 0.2);
            hz = Mth.clamp((cz - pos.z) * 0.6, -0.2, 0.2);
         }
      }
      this.mob.setDeltaMovement(hx, this.mob.getDeltaMovement().y, hz);
      this.face(hx, hz, target);
      if (this.hopTicks > 2 && (this.mob.onGround() || this.mob.isInWater()) || this.hopTicks > 50) {
         this.hopping = false;
         this.mob.setFlag(Scp096Entity.FLAG_LEAP, false);
         this.vx = hx;
         this.vz = hz;
         if (pos.y >= this.hopTop - 0.3) {
            this.hopFails = 0;
            this.succeed("up a floor");
         } else if (++this.hopFails >= 2) {
            this.hopFails = 0;
            this.stall("hop fell back", Math.abs(target.getY() - pos.y));
         }
      }
   }

   private void startDash(ServerLevel level, double hx, double hz) {
      this.dashTicks = Scp096Route.DASH_TICKS;
      this.dashCooldown = Scp096Route.DASH_COOLDOWN;
      this.dashX = hx;
      this.dashZ = hz;
      this.dashes++;
      this.mob.setFlag(Scp096Entity.FLAG_DASH, true);
      level.playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(), Scp096Sounds.LEAP.get(), SoundSource.HOSTILE, 0.9F, 0.85F);
   }

   /** A drop of four or more blocks one to three blocks ahead (a roof edge, the gap to the next building). */
   private boolean gapAhead(ServerLevel level, double hx, double hz) {
      Vec3 pos = this.mob.position();
      int fy = Mth.floor(pos.y - 0.02);
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int d = 1; d <= 3; d++) {
         int x = Mth.floor(pos.x + hx * d);
         int z = Mth.floor(pos.z + hz * d);
         boolean drop = true;
         for (int k = 0; k < 4 && drop; k++) {
            drop = !Scp096Probe.solid(level, m.set(x, fy - k, z));
         }
         if (drop) {
            return true;
         }
      }
      return false;
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
      boolean goalMoved = this.pathGoal == null || this.pathGoal.distanceToSqr(tp) > 4.0;
      boolean stale = Scp096Rules.shouldRepath(this.path != null, this.path != null && this.path.isDone(), this.pathAge, goalMoved);
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

   /**
    * Open rays from the creature's eye and chest to the target's eye, chest and shins, against block colliders. A target
    * behind a solid wall, floor or ceiling has none; one leaning over a parapet or half behind a doorframe keeps some.
    */
   private int clearStrikeRays(Entity target) {
      Vec3[] from = {this.mob.getEyePosition(), this.mob.position().add(0.0, this.mob.getBbHeight() * 0.5, 0.0)};
      Vec3[] to = {target.getEyePosition(), target.position().add(0.0, target.getBbHeight() * 0.5, 0.0),
         target.position().add(0.0, Math.min(0.3, target.getBbHeight() * 0.25), 0.0)};
      int clear = 0;
      for (Vec3 f : from) {
         for (Vec3 t : to) {
            if (this.lineOfSight(f, t)) {
               clear++;
            }
         }
      }
      return clear;
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
      level.playSound(null, pos.x, pos.y, pos.z, Scp096Sounds.LEAP.get(), SoundSource.HOSTILE, 1.2F, 0.6F);
      return true;
   }

   private void strike(ServerLevel level, Entity target) {
      this.attackCooldown = Scp096Rules.ATTACK_INTERVAL;
      this.strikes++;
      this.mob.markAttack();
      this.mob.lookAtPoint(target.getEyePosition(), 180.0F);
      target.hurt(this.mob.attackSource(), Scp096Rules.ATTACK_DAMAGE);
      level.playSound(null, target.getX(), target.getY() + 1.0, target.getZ(), Scp096Sounds.STRIKE.get(), SoundSource.HOSTILE, 1.3F,
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
