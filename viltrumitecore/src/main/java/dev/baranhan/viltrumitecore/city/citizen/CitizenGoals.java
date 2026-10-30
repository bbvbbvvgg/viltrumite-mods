package dev.baranhan.viltrumitecore.city.citizen;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.danger.DangerMap;
import dev.baranhan.viltrumitecore.city.profile.WalkGrid;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;

/** The three citizen behaviours: flee from danger, get back onto the sidewalk, stroll. */
final class CitizenGoals {
   private CitizenGoals() {
   }

   private static boolean loaded(CitizenEntity c, int x, int z) {
      return c.level().hasChunk(x >> 4, z >> 4);
   }

   /** Runs away from the most threatening danger zone; checks every 10 ticks (staggered by id). */
   static final class Flee extends Goal {
      private final CitizenEntity c;
      private DangerMap.Zone zone;
      private int recheck;

      Flee(CitizenEntity c) {
         this.c = c;
         this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
      }

      @Override
      public boolean canUse() {
         if (this.c.panicTicks <= 0 && (this.c.tickCount + this.c.getId()) % 10 != 0) {
            return false;
         }

         CityManager city = CityManager.of(this.c.level());
         if (city == null) {
            return false;
         }

         long now = city.now();
         this.zone = city.dangerMap().threat(this.c.getX(), this.c.getY(), this.c.getZ(), now, 4.0);
         if (this.zone == null && this.c.panicTicks > 0) {
            // hurt with no known zone (e.g. a plain punch): flee from the spot
            city.danger(this.c.getX(), this.c.getY(), this.c.getZ(), 12.0F, 200);
            this.zone = city.dangerMap().threat(this.c.getX(), this.c.getY(), this.c.getZ(), now, 4.0);
         }

         if (this.zone == null || !PathBudget.tryAcquire(now, true)) {
            return false;
         }

         Path path = this.pickPath(city, now);
         if (path == null) {
            return false;
         }

         this.c.getNavigation().moveTo(path, CitizenEntity.WALK_SPEED * CitizenEntity.FLEE_FACTOR);
         return true;
      }

      private Path pickPath(CityManager city, long now) {
         WalkGrid grid = city.grid();
         double dx = this.c.getX() - this.zone.x;
         double dz = this.c.getZ() - this.zone.z;
         double len = Math.sqrt(dx * dx + dz * dz);
         double base = len < 0.5 ? this.c.getRandom().nextDouble() * Math.PI * 2.0 : Math.atan2(dz, dx);
         double need = Math.max(10.0, this.zone.radius + 10.0 - len);
         Path best = null;
         for (int i = 0; i < 6; i++) {
            double a = base + (this.c.getRandom().nextDouble() - 0.5) * (i < 3 ? 1.2 : 2.6);
            double d = need + this.c.getRandom().nextDouble() * 12.0;
            int tx = (int)Math.floor(this.c.getX() + Math.cos(a) * d);
            int tz = (int)Math.floor(this.c.getZ() + Math.sin(a) * d);
            long cell = grid.nearestCell(tx, tz, 4, null);
            if (cell == Long.MIN_VALUE) {
               continue;
            }

            int x = WalkGrid.unpackX(cell);
            int z = WalkGrid.unpackZ(cell);
            int y = grid.feetY(x, z);
            if (!loaded(this.c, x, z) || city.dangerMap().dangerous(x + 0.5, y, z + 0.5, now)) {
               continue;
            }

            Path p = this.c.getNavigation().createPath(new BlockPos(x, y, z), 0);
            if (p != null && p.getNodeCount() > 1) {
               if (p.canReach()) {
                  return p;
               }

               if (best == null) {
                  best = p;
               }
            }
         }

         return best;
      }

      @Override
      public void start() {
         this.c.setState(CitizenEntity.State.FLEEING);
         this.recheck = 10;
      }

      @Override
      public boolean canContinueToUse() {
         return !this.c.getNavigation().isDone();
      }

      @Override
      public void tick() {
         if (--this.recheck <= 0) {
            this.recheck = 10;
            CityManager city = CityManager.of(this.c.level());
            if (city != null && this.zone != null && this.zone.expires <= city.now()) {
               // danger is over: slow down to a walk for the rest of the way
               this.c.getNavigation().setSpeedModifier(CitizenEntity.WALK_SPEED);
            }
         }
      }

      @Override
      public void stop() {
         this.c.setState(CitizenEntity.State.IDLE);
         this.c.idleTicks = 20 + this.c.getRandom().nextInt(40);
         this.zone = null;
      }
   }

   /** Knocked off the sidewalk (into a yard, onto a car, into water): walk straight back. */
   static final class ReturnToGrid extends Goal {
      private final CitizenEntity c;
      private double tx;
      private double ty;
      private double tz;
      private int time;

      ReturnToGrid(CitizenEntity c) {
         this.c = c;
         this.setFlags(EnumSet.of(Goal.Flag.MOVE));
      }

      @Override
      public boolean canUse() {
         if ((this.c.tickCount + this.c.getId()) % 20 != 0 || !this.c.getNavigation().isDone()) {
            return false;
         }

         if (!this.c.onGround() && !this.c.isInWater() || this.c.onGrid()) {
            this.c.offGridTicks = 0;
            return false;
         }

         this.c.offGridTicks += 20;
         CityManager city = CityManager.of(this.c.level());
         if (city == null) {
            return false;
         }

         if (this.c.offGridTicks > 400) {
            this.c.discard();
            return false;
         }

         BlockPos p = this.c.blockPosition();
         long cell = city.grid().nearestCell(p.getX(), p.getZ(), 10, y -> Math.abs(y - p.getY()) <= 2);
         if (cell == Long.MIN_VALUE) {
            return false;
         }

         this.tx = WalkGrid.unpackX(cell) + 0.5;
         this.tz = WalkGrid.unpackZ(cell) + 0.5;
         this.ty = city.grid().feetY(WalkGrid.unpackX(cell), WalkGrid.unpackZ(cell));
         return true;
      }

      @Override
      public void start() {
         this.time = 0;
         this.c.setState(CitizenEntity.State.WALKING);
      }

      @Override
      public boolean canContinueToUse() {
         return this.time < 100 && !this.c.onGrid() && this.c.distanceToSqr(this.tx, this.ty, this.tz) > 0.3;
      }

      @Override
      public void tick() {
         this.time++;
         this.c.getMoveControl().setWantedPosition(this.tx, this.ty, this.tz, CitizenEntity.WALK_SPEED);
      }

      @Override
      public void stop() {
         this.c.setState(CitizenEntity.State.IDLE);
      }
   }

   /** Idle a few seconds, then walk to a random sidewalk/plaza cell 8-24 blocks away. */
   static final class Stroll extends Goal {
      private final CitizenEntity c;
      private int time;

      Stroll(CitizenEntity c) {
         this.c = c;
         this.setFlags(EnumSet.of(Goal.Flag.MOVE));
      }

      @Override
      public boolean canUse() {
         if (this.c.idleTicks > 0) {
            this.c.idleTicks--;
            return false;
         }

         CityManager city = CityManager.of(this.c.level());
         if (city == null || !this.c.onGrid()) {
            return false;
         }

         long now = city.now();
         if (!PathBudget.tryAcquire(now, false)) {
            return false;
         }

         boolean preferred = this.c.getRandom().nextFloat() < 0.85F;
         long cell = city.grid().randomCell(this.c.getRandom()::nextDouble, this.c.getX(), this.c.getZ(), 8.0, 24.0, preferred, 10,
            (x, y, z) -> loaded(this.c, x, z) && Math.abs(y - this.c.getY()) < 12 && !city.dangerMap().dangerous(x + 0.5, y, z + 0.5, now));
         if (cell == Long.MIN_VALUE) {
            this.c.idleTicks = 20;
            return false;
         }

         int x = WalkGrid.unpackX(cell);
         int z = WalkGrid.unpackZ(cell);
         Path p = this.c.getNavigation().createPath(new BlockPos(x, city.grid().feetY(x, z), z), 0);
         if (p == null || p.getNodeCount() < 2) {
            this.c.idleTicks = 20;
            return false;
         }

         this.c.getNavigation().moveTo(p, CitizenEntity.WALK_SPEED);
         return true;
      }

      @Override
      public void start() {
         this.time = 0;
         this.c.setState(CitizenEntity.State.WALKING);
      }

      @Override
      public boolean canContinueToUse() {
         return ++this.time < 900 && !this.c.getNavigation().isDone();
      }

      @Override
      public void stop() {
         this.c.getNavigation().stop();
         this.c.setState(CitizenEntity.State.IDLE);
         this.c.idleTicks = 40 + this.c.getRandom().nextInt(160);
      }
   }
}
