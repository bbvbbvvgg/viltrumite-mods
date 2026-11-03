package dev.baranhan.viltrumitecore.t1000;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.citizen.CitizenState;
import dev.baranhan.viltrumitecore.city.citizen.PathBudget;
import dev.baranhan.viltrumitecore.city.profile.WalkGrid;
import dev.baranhan.viltrumitecore.entity.Bystander;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.worldevent.aftermath.CityAftermath;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The mind of the T-1000 (server only, one per entity; design §5). A small state machine over
 * {@link T1000State}: it blends in, picks a target, follows it at a citizen's pace, keeps out of its gaze, strikes
 * when the moment is right (or when found out), chases with leaps and by flowing through thin obstacles, fights
 * with blade, hook, grab and counter, melts and reforms, then hides and changes its face. Paths are requested
 * through the shared {@link PathBudget} at most every few ticks (never a global search per tick).
 */
public final class T1000Brain {
   private final T1000Entity e;
   private final RandomSource rnd;
   private T1000State state = T1000State.DISGUISE;
   private int stateTicks;
   private int huntCooldown;
   @Nullable
   private UUID targetId;
   @Nullable
   private LivingEntity target;
   private Vec3 lastKnown = Vec3.ZERO;
   private int unseen;
   private float suspicion;
   private int watched;
   private int repathIn;
   @Nullable
   private Vec3 pathGoal;
   @Nullable
   private Vec3 sidestep;
   private int farTicks;
   private int crowdNearTarget;
   private boolean revealFast;
   private T1000State beforeMelt = T1000State.CHASE;
   // movement memory
   private double lastX;
   private double lastZ;
   private int stuckStrikes;
   private int leapCd;
   private int flowCd;
   private int smashCd;
   @Nullable
   private Vec3 leapTo;
   // combat
   private int stabCd;
   private int slashCd;
   private int hookCd;
   private int grabCd;
   private int counterCd;
   private T1000Action cur = T1000Action.NONE;
   private int curTick;
   @Nullable
   private LivingEntity curTarget;
   private boolean hookPending;
   private int hookWait;
   @Nullable
   private LivingEntity grabbed;
   // concealment
   @Nullable
   private Vec3 hideAt;
   private int unseenFor;
   private boolean transforming;

   T1000Brain(T1000Entity e) {
      this.e = e;
      this.rnd = e.getRandom();
      this.huntCooldown = T1000Rules.FIRST_HUNT_MIN + this.rnd.nextInt(T1000Rules.FIRST_HUNT_RANDOM);
   }

   public T1000State state() {
      return this.state;
   }

   @Nullable
   public LivingEntity target() {
      return this.target;
   }

   /** Players the AI may hunt. */
   static boolean huntable(Player p) {
      return p.isAlive() && !p.isSpectator() && !p.isCreative();
   }

   private void set(T1000State s) {
      if (this.state != s) {
         this.state = s;
         this.stateTicks = 0;
         this.repathIn = 0;
         this.pathGoal = null;
      }
   }

   /** Operator/QA: skip the calm phase (next tick it looks for a target). */
   public void wakeUp() {
      this.huntCooldown = 0;
   }

   // --- tick -------------------------------------------------------------------------------------------------

   void tick() {
      if (!(this.e.level() instanceof ServerLevel level) || this.state == T1000State.DESTROYED) {
         return;
      }
      this.stateTicks++;
      this.cooldowns();
      this.resolveTarget(level);
      this.tickAction();
      if (this.e.form().grounded() && this.state != T1000State.RECOVER) {
         this.beforeMelt = this.state;
         this.set(T1000State.RECOVER);
      }
      switch (this.state) {
         case DISGUISE -> this.disguise(level);
         case SEARCH -> this.search(level);
         case STALK -> this.stalk(level);
         case REVEAL -> this.reveal(level);
         case CHASE -> this.chase(level);
         case RECOVER -> this.recover(level);
         case CONCEAL -> this.conceal(level);
         default -> {
         }
      }
      if (this.e.form().revealed() && this.e.form() != T1000Form.CONCEALING && this.e.tickCount % 20 == 0) {
         CityManager city = CityManager.of(level);
         if (city != null) {
            // the crowd learns there is something terrible here (and runs from it)
            city.dangerMap().track(this.e.getId(), this.e.getX(), this.e.getY(), this.e.getZ(), 10.0F, 26.0F, city.now(), 60);
         }
      }
   }

   private void cooldowns() {
      if (this.huntCooldown > 0) {
         this.huntCooldown--;
      }
      this.leapCd = Math.max(0, this.leapCd - 1);
      this.flowCd = Math.max(0, this.flowCd - 1);
      this.smashCd = Math.max(0, this.smashCd - 1);
      this.stabCd = Math.max(0, this.stabCd - 1);
      this.slashCd = Math.max(0, this.slashCd - 1);
      this.hookCd = Math.max(0, this.hookCd - 1);
      this.grabCd = Math.max(0, this.grabCd - 1);
      this.counterCd = Math.max(0, this.counterCd - 1);
   }

   private void resolveTarget(ServerLevel level) {
      if (this.targetId == null) {
         this.target = null;
         return;
      }
      if (this.target == null || this.target.isRemoved() || this.target.level() != level) {
         Entity found = level.getEntity(this.targetId);
         this.target = found instanceof LivingEntity l ? l : null;
      }
   }

   private void setTarget(@Nullable LivingEntity t) {
      this.target = t;
      this.targetId = t == null ? null : t.getUUID();
      this.unseen = 0;
      this.suspicion = 0.0F;
      if (t != null) {
         this.lastKnown = t.position();
      }
   }

   private boolean valid(@Nullable LivingEntity t) {
      if (t == null || !t.isAlive() || t.level() != this.e.level() || t instanceof T1000Entity) {
         return false;
      }
      return !(t instanceof Player p) || huntable(p);
   }

   // --- DISGUISE -----------------------------------------------------------------------------------------------

   private void disguise(ServerLevel level) {
      this.e.setChaseMode(false);
      if (this.e.tickCount % 20 == 0) {
         ServerPlayer near = this.nearestPlayer(level, Double.MAX_VALUE);
         double d = near == null ? Double.MAX_VALUE : near.distanceTo(this.e);
         this.farTicks = d > T1000Rules.FAR_FROM_PLAYERS ? this.farTicks + 20 : 0;
         if (this.farTicks >= T1000Rules.FAR_TICKS && this.e.form() == T1000Form.HUMAN) {
            // nobody is anywhere near: it slips away and turns up near a player later
            this.e.vanish();
            return;
         }
      }
      if (this.huntCooldown > 0 || this.e.form() != T1000Form.HUMAN || this.e.tickCount % 40 != 0) {
         return;
      }
      CityManager city = CityManager.of(level);
      ServerPlayer best = null;
      double bd = T1000Rules.ACQUIRE_RANGE * T1000Rules.ACQUIRE_RANGE;
      for (ServerPlayer p : this.e.huntablePlayers()) {
         if (city != null && !city.profile().inArena(p.getBlockX(), p.getBlockY(), p.getBlockZ())) {
            continue;
         }
         double d = p.distanceToSqr(this.e);
         if (d < bd) {
            bd = d;
            best = p;
         }
      }
      if (best != null) {
         this.setTarget(best);
         this.set(T1000State.SEARCH);
      }
   }

   @Nullable
   private ServerPlayer nearestPlayer(ServerLevel level, double max) {
      ServerPlayer best = null;
      double bd = max;
      for (ServerPlayer p : level.players()) {
         if (p.isSpectator()) {
            continue;
         }
         double d = p.distanceToSqr(this.e);
         if (d < bd) {
            bd = d;
            best = p;
         }
      }
      return best;
   }

   private void calmDown(int cooldown) {
      this.setTarget(null);
      this.huntCooldown = cooldown;
      this.e.getNavigation().stop();
      this.e.setChaseMode(false);
      this.e.citizenPose(CitizenState.WALKING);
      this.set(T1000State.DISGUISE);
   }

   // --- SEARCH and STALK -------------------------------------------------------------------------------------

   private void search(ServerLevel level) {
      LivingEntity t = this.target;
      if (!this.valid(t) || this.e.distanceTo(t) > 96.0 || this.stateTicks > T1000Rules.SEARCH_MAX_TICKS) {
         this.calmDown(600);
         return;
      }
      double d = this.e.distanceTo(t);
      if (d <= T1000Rules.STALK_MAX + 2.0 && this.e.getSensing().hasLineOfSight(t)) {
         this.set(T1000State.STALK);
         return;
      }
      if (--this.repathIn <= 0 || this.e.getNavigation().isDone()) {
         this.repathIn = T1000Rules.STALK_REPATH;
         Vec3 goal = this.behind(level, t, T1000Rules.SEARCH_BEHIND);
         this.walkTo(level, goal, d > 40.0 ? T1000Rules.SEARCH_SPEED * 1.12 : T1000Rules.SEARCH_SPEED, t);
      }
      this.e.citizenPose(this.e.getNavigation().isDone() ? CitizenState.IDLE : CitizenState.WALKING);
   }

   private void stalk(ServerLevel level) {
      LivingEntity t = this.target;
      if (!this.valid(t) || this.e.distanceTo(t) > 64.0) {
         this.calmDown(600);
         return;
      }
      double d = this.e.distanceTo(t);
      boolean seen = this.e.getSensing().hasLineOfSight(t);
      boolean gaze = this.watchedBy(t, d);
      this.suspicion = Mth.clamp(this.suspicion + T1000Rules.suspicionStep(gaze, d), 0.0F, T1000Rules.SUSPICION_MAX);
      if (this.e.tickCount % 20 == 0) {
         this.crowdNearTarget = level.getEntitiesOfClass(CitizenEntity.class, t.getBoundingBox().inflate(7.0), c -> !(c instanceof T1000Entity)).size();
      }
      // found out: stared at too long, or walked right up to
      if (this.suspicion >= T1000Rules.SUSPICION_MAX || gaze && d < T1000Rules.TOO_CLOSE) {
         this.beginReveal(false);
         return;
      }
      double away = this.lookAwayDeg(t);
      boolean alone = this.crowdNearTarget <= 1 && this.otherPlayersNear(level, t, 10.0) == 0;
      boolean indoors = !level.canSeeSky(t.blockPosition().above());
      float health = t.getHealth() / Math.max(1.0F, t.getMaxHealth());
      if (!gaze && seen && T1000Rules.opportunity(d, away, alone, indoors, health)) {
         this.beginReveal(false);
         return;
      }
      if (this.stateTicks > T1000Rules.STALK_MAX_TICKS) {
         if (d < 12.0 && seen) {
            this.beginReveal(false);
         } else {
            this.calmDown(T1000Rules.HUNT_COOLDOWN_MIN);
         }
         return;
      }
      if (gaze) {
         // looked at: stop, look elsewhere like anyone would; a long stare -> change the route
         this.watched++;
         if (this.watched > 50 && d < 18.0 && this.sidestep == null) {
            this.sidestep = this.sideStreet(level, t);
            if (this.sidestep != null) {
               this.walkTo(level, this.sidestep, T1000Rules.STALK_SPEED, t);
            }
         }
         if (this.sidestep == null) {
            this.e.getNavigation().stop();
            this.e.citizenPose(CitizenState.IDLE);
            float yaw = this.e.getYRot() + (this.e.getId() % 2 == 0 ? 70.0F : -70.0F);
            Vec3 at = this.e.getEyePosition().add(-Math.sin(Math.toRadians(yaw)) * 4.0, -0.4, Math.cos(Math.toRadians(yaw)) * 4.0);
            this.e.getLookControl().setLookAt(at.x, at.y, at.z, 10.0F, 10.0F);
         }
         return;
      }
      this.watched = 0;
      if (this.sidestep != null) {
         if (this.e.getNavigation().isDone() || this.e.position().distanceToSqr(this.sidestep) < 4.0) {
            this.sidestep = null;
         } else {
            this.e.citizenPose(CitizenState.WALKING);
            return;
         }
      }
      if (--this.repathIn <= 0 || this.e.getNavigation().isDone()) {
         this.repathIn = T1000Rules.STALK_REPATH;
         if (d > T1000Rules.STALK_MAX) {
            this.walkTo(level, this.behind(level, t, (T1000Rules.STALK_MIN + T1000Rules.STALK_MAX) * 0.5), T1000Rules.SEARCH_SPEED, t);
         } else if (d < T1000Rules.STALK_MIN) {
            this.e.getNavigation().stop();
         } else {
            this.walkTo(level, this.behind(level, t, T1000Rules.STALK_MIN + 1.0), T1000Rules.STALK_SPEED, t);
         }
      }
      // a glance now and then, never a stare
      if (this.e.tickCount % 60 < 12) {
         this.e.getLookControl().setLookAt(t, 8.0F, 8.0F);
      }
      this.e.citizenPose(this.e.getNavigation().isDone() ? CitizenState.IDLE : CitizenState.WALKING);
   }

   /** The target looks at it: inside its view cone, close enough, a clear line. */
   private boolean watchedBy(LivingEntity t, double d) {
      if (d > T1000Rules.GAZE_RANGE) {
         return false;
      }
      Vec3 look = t.getViewVector(1.0F);
      Vec3 to = this.e.getEyePosition().subtract(t.getEyePosition());
      double len = to.length();
      if (len < 1.0E-4) {
         return true;
      }
      double cos = look.dot(to) / len;
      return cos > Math.cos(Math.toRadians(T1000Rules.GAZE_HALF_DEG)) && t.hasLineOfSight(this.e);
   }

   /** How far the target's look points away from it, degrees (180 = its back is turned). */
   private double lookAwayDeg(LivingEntity t) {
      Vec3 look = t.getViewVector(1.0F);
      return T1000Rules.angleDeg(look.x, look.z, this.e.getX() - t.getX(), this.e.getZ() - t.getZ());
   }

   private int otherPlayersNear(ServerLevel level, LivingEntity t, double r) {
      int n = 0;
      for (ServerPlayer p : level.players()) {
         if (p != t && huntable(p) && p.distanceToSqr(t) < r * r) {
            n++;
         }
      }
      return n;
   }

   /** A point behind the target (on the pedestrian grid when there is one), away from dangers and fresh ruins. */
   private Vec3 behind(ServerLevel level, LivingEntity t, double dist) {
      Vec3 look = t.getViewVector(1.0F);
      double lx = look.x;
      double lz = look.z;
      double l = Math.sqrt(lx * lx + lz * lz);
      if (l < 1.0E-3) {
         lx = 0.0;
         lz = 1.0;
         l = 1.0;
      }
      lx /= l;
      lz /= l;
      double side = (this.e.getId() % 3 - 1) * 2.5;
      double x = t.getX() - lx * dist - lz * side;
      double z = t.getZ() - lz * dist + lx * side;
      CityManager city = CityManager.of(level);
      if (city != null) {
         WalkGrid grid = city.grid();
         int ty = t.getBlockY();
         long cell = grid.nearestCell(Mth.floor(x), Mth.floor(z), 6, fy -> Math.abs(fy - ty) <= 6);
         if (cell != Long.MIN_VALUE) {
            int cx = WalkGrid.unpackX(cell);
            int cz = WalkGrid.unpackZ(cell);
            int fy = grid.feetY(cx, cz);
            if (!city.dangerMap().dangerous(cx + 0.5, fy, cz + 0.5, city.now()) && CityAftermath.welcome(level, cx + 0.5, fy, cz + 0.5) >= 1.0F) {
               return new Vec3(cx + 0.5, fy, cz + 0.5);
            }
         }
      }
      return new Vec3(x, t.getY(), z);
   }

   /** A cell to the side of the target's line of sight (changing the route like a passer-by). */
   @Nullable
   private Vec3 sideStreet(ServerLevel level, LivingEntity t) {
      CityManager city = CityManager.of(level);
      Vec3 look = t.getViewVector(1.0F);
      double sx = -look.z;
      double sz = look.x;
      double sign = this.rnd.nextBoolean() ? 1.0 : -1.0;
      double x = this.e.getX() + sx * sign * 9.0;
      double z = this.e.getZ() + sz * sign * 9.0;
      if (city == null) {
         return new Vec3(x, this.e.getY(), z);
      }
      long cell = city.grid().nearestCell(Mth.floor(x), Mth.floor(z), 5, null);
      if (cell == Long.MIN_VALUE) {
         return null;
      }
      int cx = WalkGrid.unpackX(cell);
      int cz = WalkGrid.unpackZ(cell);
      return new Vec3(cx + 0.5, city.grid().feetY(cx, cz), cz + 0.5);
   }

   /** Walks like a citizen: grid navigation when the goal is on the grid, ground navigation otherwise. */
   private void walkTo(ServerLevel level, Vec3 goal, double speed, @Nullable LivingEntity t) {
      CityManager city = CityManager.of(level);
      BlockPos g = BlockPos.containing(goal);
      boolean onGrid = city != null && city.grid().standable(g.getX(), g.getY(), g.getZ());
      boolean targetOffGrid = t != null && city != null && !city.grid().standable(t.getBlockX(), t.getBlockY(), t.getBlockZ());
      this.e.setChaseMode(!onGrid || targetOffGrid && this.e.distanceTo(t) < 20.0);
      if (!PathBudget.tryAcquire(level.getGameTime(), false)) {
         this.repathIn = 5;
         return;
      }
      this.pathGoal = goal;
      if (!this.e.getNavigation().moveTo(goal.x, goal.y, goal.z, speed) && t != null && this.e.chaseMode()) {
         this.e.getNavigation().moveTo(t, speed);
      }
   }

   // --- REVEAL -------------------------------------------------------------------------------------------------

   private void beginReveal(boolean fast) {
      this.revealFast = fast;
      this.sidestep = null;
      this.e.getNavigation().stop();
      this.e.citizenPose(CitizenState.IDLE);
      T1000Form f = this.e.form();
      if (f == T1000Form.HUMAN || f == T1000Form.CONCEALING) {
         this.e.setForm(T1000Form.REVEALING);
         this.e.playSound(T1000Sounds.REVEAL.get(), 0.9F, 1.0F);
      }
      if (!fast) {
         this.e.setAction(T1000Action.TILT);
      }
      this.e.setArm(true, T1000Arm.BLADE, !fast);
      this.transforming = false;
      this.set(T1000State.REVEAL);
   }

   private void reveal(ServerLevel level) {
      LivingEntity t = this.target;
      if (t != null) {
         this.e.getLookControl().setLookAt(t, 20.0F, 20.0F);
      }
      if (this.stateTicks >= (this.revealFast ? 6 : 22)) {
         if (this.valid(t)) {
            this.set(T1000State.CHASE);
         } else {
            this.set(T1000State.CONCEAL);
         }
      }
   }

   // --- CHASE --------------------------------------------------------------------------------------------------

   private void chase(ServerLevel level) {
      LivingEntity t = this.target;
      this.e.setChaseMode(true);
      if (!this.valid(t)) {
         this.releaseGrab();
         if (this.stateTicks > 20) {
            this.toConceal();
         }
         return;
      }
      double d = this.e.distanceTo(t);
      boolean see = this.e.getSensing().hasLineOfSight(t);
      if (see) {
         this.lastKnown = t.position();
         this.unseen = 0;
      } else {
         this.unseen++;
      }
      if (this.unseen > T1000Rules.LOST_TICKS || d > 80.0) {
         this.toConceal();
         return;
      }
      if (see) {
         this.e.getLookControl().setLookAt(t, 30.0F, 30.0F);
      }
      // the second blade after a while, unless the hook is wanted
      if (this.stateTicks == 60 && !this.hookPending && this.e.leftArm() == T1000Arm.HAND) {
         this.e.setArm(false, this.rnd.nextInt(3) == 0 ? T1000Arm.SPIKE : T1000Arm.BLADE, false);
      }
      if (this.e.flowing() || this.cur == T1000Action.LEAP && this.leapTo != null) {
         return;
      }
      this.fight(level, t, d, see);
      if (this.cur == T1000Action.GRAB && this.grabbed != null || this.cur == T1000Action.COUNTER) {
         this.e.getNavigation().stop();
         return;
      }
      double speed = d > 12.0 ? T1000Rules.CHASE_SPEED_FAR : T1000Rules.CHASE_SPEED;
      if (this.e.form() == T1000Form.REVEALING) {
         speed *= 0.8;
      }
      if (this.cur.attack() && this.curTick < this.cur.hitTick) {
         speed *= 0.4;
      }
      if (d < 1.4) {
         this.e.getNavigation().stop();
      } else if (d < 3.2 && see && Math.abs(t.getY() - this.e.getY()) < 1.0) {
         this.e.getNavigation().stop();
         this.e.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), speed);
      } else {
         Vec3 goal = see ? t.position() : this.lastKnown;
         boolean moved = this.pathGoal == null || this.pathGoal.distanceToSqr(goal) > 2.25;
         if (--this.repathIn <= 0 && (moved || this.e.getNavigation().isDone())) {
            if (PathBudget.tryAcquire(level.getGameTime(), true)) {
               this.repathIn = T1000Rules.CHASE_REPATH;
               this.pathGoal = goal;
               this.e.getNavigation().moveTo(goal.x, goal.y, goal.z, speed);
            } else {
               this.repathIn = 2;
            }
         }
         if (!see && this.e.getNavigation().isDone() && this.e.position().distanceToSqr(this.lastKnown) < 4.0) {
            // reached the last known position: look around a moment, then a guess along its last heading
            this.lastKnown = this.lastKnown.add(t.getDeltaMovement().multiply(20.0, 0.0, 20.0));
         }
      }
      this.leapIfUseful(level, t, d, see);
      this.unstick(level, t);
   }

   private void toConceal() {
      this.releaseGrab();
      this.hookPending = false;
      this.hideAt = null;
      this.unseenFor = 0;
      this.transforming = false;
      this.set(T1000State.CONCEAL);
   }

   /** Ballistic leap to a higher ledge or across a gap the path cannot take. */
   private void leapIfUseful(ServerLevel level, LivingEntity t, double d, boolean see) {
      if (this.leapCd > 0 || !this.e.onGround() || !see || this.cur != T1000Action.NONE) {
         return;
      }
      double dy = t.getY() - this.e.getY();
      double h = Math.sqrt((t.getX() - this.e.getX()) * (t.getX() - this.e.getX()) + (t.getZ() - this.e.getZ()) * (t.getZ() - this.e.getZ()));
      Path p = this.e.getNavigation().getPath();
      boolean pathBad = p == null || !p.canReach();
      boolean up = dy >= 1.6 && dy <= 6.0 && h <= 7.5 && h >= 1.0;
      boolean gap = pathBad && h >= 4.0 && h <= 10.0 && dy > -4.0 && dy < 3.0;
      if ((up && (pathBad || h < 4.0) || gap) && t.onGround()) {
         this.startLeap(t.position());
      }
   }

   private void startLeap(Vec3 to) {
      this.leapTo = to;
      this.leapCd = T1000Rules.LEAP_COOLDOWN;
      this.e.getNavigation().stop();
      this.start(T1000Action.LEAP, null);
   }

   /** Stuck against something: flow through it when thin, smash glass, leap a low wall, or try another way. */
   private void unstick(ServerLevel level, LivingEntity t) {
      if (this.e.tickCount % 10 != 0) {
         return;
      }
      double moved = Math.abs(this.e.getX() - this.lastX) + Math.abs(this.e.getZ() - this.lastZ);
      this.lastX = this.e.getX();
      this.lastZ = this.e.getZ();
      boolean wants = this.e.distanceTo(t) > 2.0 && this.cur == T1000Action.NONE;
      if (!wants || moved > 0.5) {
         this.stuckStrikes = 0;
         return;
      }
      this.stuckStrikes++;
      if (this.stuckStrikes < 2) {
         return;
      }
      Vec3 aim = this.nextStep(t);
      double dx = aim.x - this.e.getX();
      double dz = aim.z - this.e.getZ();
      double l = Math.sqrt(dx * dx + dz * dz);
      if (l < 1.0E-3) {
         return;
      }
      dx /= l;
      dz /= l;
      if (this.flowCd == 0 && this.tryFlow(level, dx, dz)) {
         this.stuckStrikes = 0;
         return;
      }
      if (this.smashCd == 0 && this.trySmash(level, dx, dz)) {
         this.stuckStrikes = 0;
         return;
      }
      if (this.leapCd == 0 && this.e.onGround()) {
         Vec3 over = this.e.position().add(dx * 2.5, 1.2, dz * 2.5);
         if (this.fits(level, over)) {
            this.startLeap(over);
            this.stuckStrikes = 0;
            return;
         }
      }
      // another way round: forget the path, aim at the last known position from a side
      this.e.getNavigation().stop();
      this.pathGoal = null;
      this.repathIn = 0;
   }

   private Vec3 nextStep(LivingEntity t) {
      Path p = this.e.getNavigation().getPath();
      if (p != null && !p.isDone()) {
         BlockPos n = p.getNextNodePos();
         return new Vec3(n.getX() + 0.5, n.getY(), n.getZ() + 0.5);
      }
      return t.position();
   }

   /** Thin: bars, panes, fences, walls, doors, gates, trapdoors, chains — anything that is not a full cube. */
   private static boolean thin(ServerLevel level, BlockPos pos, BlockState s) {
      if (s.getCollisionShape(level, pos).isEmpty()) {
         return true;
      }
      if (s.getBlock() instanceof DoorBlock || s.getBlock() instanceof FenceGateBlock || s.getBlock() instanceof TrapDoorBlock
         || s.getBlock() instanceof IronBarsBlock || s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS)) {
         return true;
      }
      return !s.isCollisionShapeFullBlock(level, pos) && s.getDestroySpeed(level, pos) >= 0.0F;
   }

   private boolean tryFlow(ServerLevel level, double dx, double dz) {
      BlockPos feet = this.e.blockPosition();
      BlockPos first = BlockPos.containing(this.e.getX() + dx * 0.9, this.e.getY() + 0.1, this.e.getZ() + dz * 0.9);
      BlockState a = level.getBlockState(first);
      BlockState b = level.getBlockState(first.above());
      boolean blocked = !a.getCollisionShape(level, first).isEmpty() || !b.getCollisionShape(level, first.above()).isEmpty();
      if (!blocked || !thin(level, first, a) || !thin(level, first.above(), b)) {
         return false;
      }
      for (int k = 2; k <= T1000Rules.FLOW_MAX_DEPTH + 1; k++) {
         Vec3 dest = new Vec3(feet.getX() + 0.5 + dx * k, feet.getY(), feet.getZ() + 0.5 + dz * k);
         BlockPos dp = BlockPos.containing(dest);
         if (this.fits(level, dest) && !level.getBlockState(dp.below()).getCollisionShape(level, dp.below()).isEmpty()) {
            this.e.flowThrough(dest, 8 + k * 4);
            this.flowCd = T1000Rules.FLOW_COOLDOWN;
            return true;
         }
         BlockState mid = level.getBlockState(dp);
         if (!thin(level, dp, mid) || !thin(level, dp.above(), level.getBlockState(dp.above()))) {
            return false;
         }
      }
      return false;
   }

   private boolean fits(ServerLevel level, Vec3 feet) {
      AABB box = new AABB(feet.x - 0.3, feet.y + 0.01, feet.z - 0.3, feet.x + 0.3, feet.y + T1000Rules.HEIGHT, feet.z + 0.3);
      return level.noCollision(box) && level.getFluidState(BlockPos.containing(feet)).isEmpty();
   }

   /** Glass in the way: through it (journaled by the city, so the restore queue repairs it). */
   private boolean trySmash(ServerLevel level, double dx, double dz) {
      if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
         return false;
      }
      boolean any = false;
      for (int up = 0; up <= 1; up++) {
         BlockPos p = BlockPos.containing(this.e.getX() + dx * 0.9, this.e.getY() + 0.1 + up, this.e.getZ() + dz * 0.9);
         BlockState s = level.getBlockState(p);
         if (s.getSoundType() == SoundType.GLASS && s.getDestroySpeed(level, p) >= 0.0F && s.getDestroySpeed(level, p) < 1.0F) {
            level.destroyBlock(p, false, this.e);
            any = true;
         }
      }
      if (any) {
         this.smashCd = 20;
      }
      return any;
   }

   // --- combat -------------------------------------------------------------------------------------------------

   private void fight(ServerLevel level, LivingEntity t, double d, boolean see) {
      if (this.cur != T1000Action.NONE || this.e.form() == T1000Form.REVEALING && this.e.formAge(0.0F) < 14.0F) {
         return;
      }
      if (this.hookPending) {
         this.hookWait++;
         if (this.e.leftArm() == T1000Arm.HOOK && this.e.armReady(false)) {
            this.hookPending = false;
            if (see && d >= T1000Rules.HOOK_MIN - 1.0 && d <= T1000Rules.HOOK_MAX + 1.0) {
               this.start(T1000Action.HOOK, t);
               return;
            }
         } else if (this.hookWait > 30) {
            this.hookPending = false;
         }
      }
      boolean facing = this.facing(t, 60.0);
      if (d <= T1000Rules.STAB_REACH && this.e.armReady(true) && facing) {
         if (this.grabCd == 0 && d <= T1000Rules.GRAB_REACH && t.getBbWidth() < 1.0F && !t.isPassenger() && this.rnd.nextInt(6) == 0) {
            this.start(T1000Action.GRAB, t);
         } else if (this.slashCd == 0 && (this.rnd.nextInt(3) == 0 || this.enemiesNear(level, T1000Rules.SLASH_REACH) >= 2)) {
            this.start(T1000Action.SLASH, t);
         } else if (this.stabCd == 0) {
            this.start(T1000Action.STAB, t);
         }
         return;
      }
      if (!this.hookPending && this.hookCd == 0 && see && d >= T1000Rules.HOOK_MIN && d <= T1000Rules.HOOK_MAX && this.rnd.nextInt(25) == 0) {
         this.hookPending = true;
         this.hookWait = 0;
         this.hookCd = T1000Rules.HOOK_COOLDOWN;
         this.e.setArm(false, T1000Arm.HOOK, false);
      }
   }

   private int enemiesNear(ServerLevel level, double r) {
      return level.getEntitiesOfClass(LivingEntity.class, this.e.getBoundingBox().inflate(r), l -> l != this.e && this.hostileTo(l)).size();
   }

   private boolean hostileTo(LivingEntity l) {
      if (!l.isAlive() || l instanceof T1000Entity || Bystander.is(l)) {
         return false;
      }
      if (l instanceof Player p) {
         return huntable(p);
      }
      return l == this.target || l.getLastHurtMobTimestamp() > 0 && l.getLastHurtMob() == this.e;
   }

   private boolean facing(LivingEntity t, double halfDeg) {
      Vec3 look = this.e.getViewVector(1.0F);
      return T1000Rules.angleDeg(look.x, look.z, t.getX() - this.e.getX(), t.getZ() - this.e.getZ()) <= halfDeg;
   }

   private void start(T1000Action a, @Nullable LivingEntity t) {
      this.cur = a;
      this.curTick = 0;
      this.curTarget = t;
      this.e.setAction(a);
      switch (a) {
         case STAB -> this.stabCd = T1000Rules.STAB_COOLDOWN;
         case SLASH -> {
            this.slashCd = T1000Rules.SLASH_COOLDOWN;
            this.stabCd = Math.max(this.stabCd, 10);
         }
         case GRAB -> this.grabCd = T1000Rules.GRAB_COOLDOWN;
         case HOOK -> this.e.playSound(T1000Sounds.STRETCH.get(), 1.0F, 1.1F);
         default -> {
         }
      }
      if (a == T1000Action.STAB || a == T1000Action.SLASH || a == T1000Action.COUNTER) {
         this.e.playSound(T1000Sounds.SWISH.get(), 0.9F, 0.95F + this.rnd.nextFloat() * 0.2F);
      }
   }

   private void tickAction() {
      if (this.cur == T1000Action.NONE) {
         return;
      }
      if (this.e.action() != this.cur) {
         // a reaction (heavy hit, head shot, freezing) took over
         this.releaseGrab();
         this.cur = T1000Action.NONE;
         this.leapTo = null;
         return;
      }
      this.curTick++;
      LivingEntity t = this.curTarget;
      if (this.curTick == this.cur.hitTick) {
         this.impact(t);
      }
      if (this.cur == T1000Action.GRAB && this.grabbed != null) {
         this.holdGrab();
      }
      if (this.cur == T1000Action.STAB && this.curTick == this.cur.hitTick - 2) {
         Vec3 f = this.e.getViewVector(1.0F);
         this.e.setDeltaMovement(this.e.getDeltaMovement().add(f.x * 0.3, 0.0, f.z * 0.3));
      }
      if (this.curTick >= this.cur.ticks) {
         if (this.cur == T1000Action.HOOK) {
            this.e.setArm(false, T1000Arm.BLADE, false);
         }
         this.releaseGrab();
         this.cur = T1000Action.NONE;
         this.leapTo = null;
      }
   }

   private void impact(@Nullable LivingEntity t) {
      DamageSource src = this.e.damageSources().mobAttack(this.e);
      switch (this.cur) {
         case STAB -> {
            if (t != null && this.e.distanceTo(t) <= T1000Rules.STAB_REACH + 0.5 && this.facing(t, 75.0) && this.clearPath(t) && t.hurt(src, T1000Rules.STAB_DAMAGE)) {
               t.knockback(0.25, this.e.getX() - t.getX(), this.e.getZ() - t.getZ());
               this.e.playSound(T1000Sounds.BLADE_HIT.get(), 1.0F, 0.95F + this.rnd.nextFloat() * 0.15F);
            }
         }
         case SLASH -> {
            boolean hit = false;
            if (this.e.level() instanceof ServerLevel level) {
               List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, this.e.getBoundingBox().inflate(T1000Rules.SLASH_REACH),
                  l -> l != this.e && (l == t || this.hostileTo(l)));
               for (LivingEntity l : near) {
                  if (this.e.distanceTo(l) <= T1000Rules.SLASH_REACH + 0.4 && this.facing(l, T1000Rules.SLASH_ARC_DEG) && this.clearPath(l) && l.hurt(src, T1000Rules.SLASH_DAMAGE)) {
                     l.knockback(0.45, this.e.getX() - l.getX(), this.e.getZ() - l.getZ());
                     hit = true;
                  }
               }
            }
            if (hit) {
               this.e.playSound(T1000Sounds.BLADE_HIT.get(), 1.0F, 0.85F);
            }
         }
         case HOOK -> {
            if (t != null && this.e.distanceTo(t) <= T1000Rules.HOOK_MAX + 1.5 && this.e.getSensing().hasLineOfSight(t)) {
               if (t.hurt(src, T1000Rules.HOOK_DAMAGE)) {
                  this.e.playSound(T1000Sounds.BLADE_HIT.get(), 0.9F, 1.15F);
               }
               Vec3 pull = this.e.position().subtract(t.position());
               double l = pull.length();
               if (l > 1.0E-3 && canPull(t)) {
                  double s = Math.min(1.35, l * 0.2) / l;
                  t.setDeltaMovement(pull.x * s, 0.32, pull.z * s);
                  t.hurtMarked = true;
               }
            }
         }
         case GRAB -> {
            if (t != null && this.e.distanceTo(t) <= T1000Rules.GRAB_REACH + 0.6 && !t.isPassenger() && this.clearPath(t)) {
               if (canHold(t)) {
                  this.grabbed = t;
               }
               t.hurt(src, T1000Rules.GRAB_DAMAGE);
            }
         }
         case COUNTER -> {
            if (t != null && this.e.distanceTo(t) <= 3.6 && this.clearPath(t) && t.hurt(src, T1000Rules.COUNTER_DAMAGE)) {
               t.knockback(0.6, this.e.getX() - t.getX(), this.e.getZ() - t.getZ());
               this.e.playSound(T1000Sounds.BLADE_HIT.get(), 1.0F, 0.8F);
            }
         }
         case LEAP -> {
            Vec3 to = this.leapTo;
            if (to != null) {
               double[] v = T1000Rules.leap(to.x - this.e.getX(), to.y - this.e.getY(), to.z - this.e.getZ());
               this.e.setDeltaMovement(v[0], v[1], v[2]);
               this.e.hasImpulse = true;
               this.e.playSound(T1000Sounds.STRETCH.get(), 0.7F, 1.3F);
            }
            this.leapTo = null;
         }
         default -> {
         }
      }
   }

   /** Holds the grabbed body at arm's length, throws it at the end. */
   private void holdGrab() {
      LivingEntity g = this.grabbed;
      if (g == null || !g.isAlive() || g.level() != this.e.level() || !canHold(g)) {
         // Lion's Heart, an anchor or a resistant body can break the hold at any moment
         this.grabbed = null;
         return;
      }
      Vec3 f = this.e.getViewVector(1.0F);
      Vec3 hold = this.e.position().add(f.x * 1.0, 0.7, f.z * 1.0);
      if (this.curTick < 24) {
         Vec3 v = hold.subtract(g.position()).scale(0.5);
         g.setDeltaMovement(v);
         g.hurtMarked = true;
         g.fallDistance = 0.0F;
      } else if (this.curTick == 24) {
         g.hurt(this.e.damageSources().mobAttack(this.e), T1000Rules.THROW_DAMAGE);
         if (g.isAlive() && HeroRegistry.allowsImpulse(g)) {
            g.setDeltaMovement(f.x * 1.3, 0.5, f.z * 1.3);
            g.hurtMarked = true;
         }
         this.e.playSound(T1000Sounds.STRETCH.get(), 0.8F, 0.8F);
         this.grabbed = null;
      }
   }

   private void releaseGrab() {
      this.grabbed = null;
   }

   /** A blade, a counter or a grab needs an unobstructed path: cover works against the T-1000. */
   private boolean clearPath(LivingEntity t) {
      return this.e.hasLineOfSight(t);
   }

   /** Victim-side policy for the hook's pull (PULL + knockback impulse, not anchored). */
   static boolean canPull(LivingEntity t) {
      return HeroRegistry.allowsExternalControl(t, ControlKind.PULL) && HeroRegistry.allowsImpulse(t);
   }

   /** Victim-side policy for carrying a body (grab kind + impulse, not anchored); rechecked every held tick. */
   static boolean canHold(LivingEntity t) {
      return HeroRegistry.allowsExternalControl(t, ControlKind.VILTRUMITE_GRAB) && HeroRegistry.allowsImpulse(t);
   }

   /** The body is held by a control stun or NoAI: drop what it holds, stop the running action. */
   void onDisabled() {
      this.releaseGrab();
      this.leapTo = null;
      if (this.cur != T1000Action.NONE) {
         this.cur = T1000Action.NONE;
         this.e.setAction(T1000Action.NONE);
      }
      this.e.getNavigation().stop();
   }

   /** A melee blow at a ready blade may be parried and answered. */
   boolean tryParry(DamageSource source, @Nullable Entity attacker, float amount, T1000Damage.Kind kind) {
      if (this.state != T1000State.CHASE || this.e.form() != T1000Form.LIQUID || kind != T1000Damage.Kind.NORMAL || this.counterCd > 0
         || this.cur != T1000Action.NONE || !(attacker instanceof LivingEntity la) || source.getDirectEntity() != attacker
         || this.e.distanceTo(la) > 4.0 || !this.e.armReady(true) || !this.facing(la, 70.0) || this.rnd.nextDouble() >= T1000Rules.COUNTER_CHANCE) {
         return false;
      }
      this.counterCd = T1000Rules.COUNTER_COOLDOWN;
      this.start(T1000Action.COUNTER, la);
      this.e.playSound(T1000Sounds.CLANG.get(), 1.0F, 0.9F + this.rnd.nextFloat() * 0.2F);
      return true;
   }

   void onHurt(@Nullable Entity attacker, float amount, T1000Damage.Kind kind) {
      if (this.state == T1000State.DESTROYED) {
         return;
      }
      LivingEntity la = attacker instanceof LivingEntity l && l != this.e && !(l instanceof T1000Entity) && this.validAttacker(l) ? l : null;
      switch (this.state) {
         case DISGUISE, SEARCH, STALK, CONCEAL -> {
            if (la != null) {
               this.setTarget(la);
               this.beginReveal(true);
            } else if (this.state == T1000State.CONCEAL && this.e.form() == T1000Form.CONCEALING && this.valid(this.target)) {
               this.beginReveal(true);
            }
         }
         case CHASE, REVEAL -> {
            if (la != null && la != this.target && (!this.valid(this.target) || this.e.distanceTo(la) + 2.0 < this.e.distanceTo(this.target))) {
               this.setTarget(la);
            }
         }
         default -> {
         }
      }
   }

   private boolean validAttacker(LivingEntity l) {
      return !(l instanceof Player p) || huntable(p);
   }

   void onMelt() {
      this.releaseGrab();
      this.cur = T1000Action.NONE;
      this.hookPending = false;
      if (this.state != T1000State.RECOVER) {
         this.beforeMelt = this.state;
         this.set(T1000State.RECOVER);
      }
   }

   void onDestroyed() {
      this.releaseGrab();
      this.cur = T1000Action.NONE;
      this.set(T1000State.DESTROYED);
   }

   // --- RECOVER ------------------------------------------------------------------------------------------------

   private void recover(ServerLevel level) {
      T1000Form f = this.e.form();
      this.e.setChaseMode(true);
      if (f == T1000Form.PUDDLE) {
         // crawl away from whoever is closest, out of fire
         LivingEntity threat = this.valid(this.target) ? this.target : this.nearestPlayer(level, 24.0 * 24.0);
         if (threat != null && this.e.tickCount % 10 == 0) {
            Vec3 away = this.e.position().subtract(threat.position());
            double l = Math.max(1.0E-3, away.horizontalDistance());
            Vec3 to = this.e.position().add(away.x / l * 3.0, 0.0, away.z / l * 3.0);
            this.e.getMoveControl().setWantedPosition(to.x, to.y, to.z, 0.45);
         }
         return;
      }
      if (f == T1000Form.LIQUID) {
         if (this.valid(this.target) && this.e.distanceTo(this.target) < 40.0 && this.beforeMelt != T1000State.CONCEAL) {
            // melt() turned both arms back into hands: the fight needs the right blade again
            this.e.setArm(true, T1000Arm.BLADE, false);
            this.set(T1000State.CHASE);
         } else {
            this.toConceal();
         }
      } else if (f == T1000Form.HUMAN) {
         this.calmDown(T1000Rules.HUNT_COOLDOWN_MIN);
      }
   }

   // --- CONCEAL ------------------------------------------------------------------------------------------------

   private void conceal(ServerLevel level) {
      T1000Form f = this.e.form();
      if (this.transforming) {
         this.e.getNavigation().stop();
         if (f == T1000Form.HUMAN) {
            this.transforming = false;
            this.calmDown(T1000Rules.HUNT_COOLDOWN_MIN + this.rnd.nextInt(T1000Rules.HUNT_COOLDOWN_RANDOM));
         }
         return;
      }
      if (f == T1000Form.HUMAN) {
         this.calmDown(T1000Rules.HUNT_COOLDOWN_MIN);
         return;
      }
      this.e.setChaseMode(true);
      if (this.hideAt == null || this.e.getNavigation().isDone() && this.stateTicks % 60 == 0) {
         this.hideAt = this.hidingSpot(level);
         if (this.hideAt != null && PathBudget.tryAcquire(level.getGameTime(), true)) {
            this.e.getNavigation().moveTo(this.hideAt.x, this.hideAt.y, this.hideAt.z, T1000Rules.CHASE_SPEED);
         }
      }
      if (this.e.tickCount % 10 == 0) {
         boolean unseen = this.unobserved(level, T1000Rules.CONCEAL_UNSEEN);
         this.unseenFor = unseen ? this.unseenFor + 10 : 0;
         boolean late = this.stateTicks > T1000Rules.CONCEAL_MAX_TICKS && this.nearestPlayer(level, 14.0 * 14.0) == null;
         if (this.unseenFor >= 20 || late) {
            this.changeFace(level);
         }
      }
      this.unstickSimple();
   }

   private void unstickSimple() {
      if (this.e.tickCount % 20 != 0) {
         return;
      }
      double moved = Math.abs(this.e.getX() - this.lastX) + Math.abs(this.e.getZ() - this.lastZ);
      this.lastX = this.e.getX();
      this.lastZ = this.e.getZ();
      if (moved < 0.3 && !this.e.getNavigation().isDone()) {
         this.hideAt = null;
      }
   }

   /** Away from every player, on the grid if there is one. */
   @Nullable
   private Vec3 hidingSpot(ServerLevel level) {
      double ax = 0.0;
      double az = 0.0;
      int n = 0;
      for (ServerPlayer p : level.players()) {
         if (!p.isSpectator() && p.distanceToSqr(this.e) < 64.0 * 64.0) {
            ax += p.getX();
            az += p.getZ();
            n++;
         }
      }
      double dx;
      double dz;
      if (n == 0) {
         float yaw = this.rnd.nextFloat() * 360.0F;
         dx = -Math.sin(Math.toRadians(yaw));
         dz = Math.cos(Math.toRadians(yaw));
      } else {
         dx = this.e.getX() - ax / n;
         dz = this.e.getZ() - az / n;
         double l = Math.max(1.0E-3, Math.sqrt(dx * dx + dz * dz));
         dx /= l;
         dz /= l;
         double a = (this.rnd.nextDouble() - 0.5) * 1.2;
         double c = Math.cos(a);
         double s = Math.sin(a);
         double rx = dx * c - dz * s;
         dz = dx * s + dz * c;
         dx = rx;
      }
      double x = this.e.getX() + dx * 24.0;
      double z = this.e.getZ() + dz * 24.0;
      CityManager city = CityManager.of(level);
      if (city != null) {
         long cell = city.grid().nearestCell(Mth.floor(x), Mth.floor(z), 8, null);
         if (cell != Long.MIN_VALUE) {
            int cx = WalkGrid.unpackX(cell);
            int cz = WalkGrid.unpackZ(cell);
            return new Vec3(cx + 0.5, city.grid().feetY(cx, cz), cz + 0.5);
         }
      }
      return new Vec3(x, this.e.getY(), z);
   }

   /** No player closer than 8 blocks, and none within {@code range} looking at it with a clear line. */
   boolean unobserved(ServerLevel level, double range) {
      for (ServerPlayer p : level.players()) {
         if (p.isSpectator()) {
            continue;
         }
         double d2 = p.distanceToSqr(this.e);
         if (d2 < 64.0) {
            return false;
         }
         if (d2 > range * range * 4.0) {
            continue;
         }
         Vec3 look = p.getViewVector(1.0F);
         if (!T1000SpawnRules.outsideView(look.x, look.z, this.e.getX() - p.getX(), this.e.getZ() - p.getZ()) && p.hasLineOfSight(this.e)) {
            return false;
         }
      }
      return true;
   }

   /** Unseen: copies a citizen nearby (who stays where they are) and flows back into human form. */
   private void changeFace(ServerLevel level) {
      List<CitizenEntity> near = level.getEntitiesOfClass(CitizenEntity.class, this.e.getBoundingBox().inflate(16.0),
         c -> !(c instanceof T1000Entity) && c.isAlive());
      T1000Disguises.Disguise copy = null;
      if (!near.isEmpty()) {
         copy = T1000Disguises.capture(near.get(this.rnd.nextInt(near.size())));
      }
      if (copy != null) {
         this.e.setDisguise(copy.kind(), copy.payload());
      } else if (this.rnd.nextInt(3) == 0) {
         this.e.setDisguise(T1000Disguises.CITIZEN, T1000Disguises.freshSeed(this.rnd, this.rnd.nextInt(5) == 0));
      }
      this.e.getNavigation().stop();
      this.e.setArm(true, T1000Arm.HAND, false);
      this.e.setArm(false, T1000Arm.HAND, false);
      this.e.setForm(T1000Form.CONCEALING);
      this.e.playSound(T1000Sounds.REVEAL.get(), 0.6F, 0.8F);
      this.transforming = true;
   }

   // --- QA hooks -----------------------------------------------------------------------------------------------

   /** /t1000 debug reveal: reveal at the nearest player. */
   public void debugReveal(@Nullable LivingEntity t) {
      if (t != null) {
         this.setTarget(t);
      }
      this.beginReveal(false);
   }

   /** /t1000 debug conceal. */
   public void debugConceal() {
      this.toConceal();
   }
}
