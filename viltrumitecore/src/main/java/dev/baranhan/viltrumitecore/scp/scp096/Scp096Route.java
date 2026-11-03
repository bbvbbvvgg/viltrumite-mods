package dev.baranhan.viltrumitecore.scp.scp096;

import java.util.Locale;

/**
 * Pure tactics of SCP-096's vertical pursuit (issue #137; no Minecraft state, covered by Scp096RouteTest). The chase
 * picks one tactic for a target it cannot simply run to, holds it while it makes progress and switches when it stalls:
 * <ul>
 *    <li>{@link Tactic#GROUND}: on the target's level or a walkable path that reaches it (stairs included): run, leap,
 *    smash through walls;</li>
 *    <li>{@link Tactic#FACADE}: the target is above and the creature is out in the open: run to the outer wall under the
 *    nearest edge of the target's floor, climb it (punching through ledges, past windows) and pull itself over the top;</li>
 *    <li>{@link Tactic#BORE}: the target is above or below and a ceiling or floor is in the way: get under (over) it on this
 *    floor, punch a hole through the slab and jump (drop) through, floor by floor;</li>
 *    <li>{@link Tactic#TUNNEL}: the last resort, digging straight at the target.</li>
 * </ul>
 * A tactic stalls when its own progress measure has not improved for {@link #STALL_TICKS}; the next pick skips it.
 * After {@link #STALLS_BEFORE_PAUSE} stalls in a row it pauses (no jumping, no digging) for {@link #PAUSE_TICKS}, then
 * starts over: never an endless loop of leaps under a roof.
 */
public final class Scp096Route {
   /** Below this height difference the target counts as on the same level. */
   public static final double LEVEL_DY = 2.5;
   /** Ticks without progress before a tactic counts as stalled. */
   public static final int STALL_TICKS = 70;
   /** A progress measure must drop by at least this much to count as progress. */
   public static final double PROGRESS_EPS = 0.9;
   public static final int STALLS_BEFORE_PAUSE = 6;
   public static final int PAUSE_TICKS = 40;
   public static final int MAX_PAUSE_TICKS = 320;
   /** Leaps that land without progress before leaping is banned for {@link #LEAP_BAN_TICKS}. */
   public static final int LEAPS_WITHOUT_PROGRESS = 3;
   public static final int LEAP_BAN_TICKS = 120;
   /** Ticks between two re-plans of a held tactic (the facade column, the bore spot). */
   public static final int REPLAN_TICKS = 30;
   /** Horizontal distance from a target above inside which the bore goes up through the floors right here. */
   public static final double BORE_RANGE = 14.0;
   /** Thickest slab the bore punches in one go (more is a wall, not a floor). */
   public static final int MAX_SLAB = 3;
   /** Overhanging blocks in a climb column the climber may punch away (without mobGriefing it steps around). */
   public static final int MAX_OVERHANG_PUNCHES = 24;
   /** Vertical climb speed: start and top (blocks per tick) and the ramp per tick on the wall. */
   public static final double CLIMB_START = 0.42;
   public static final double CLIMB_TOP = 0.72;
   public static final double CLIMB_RAMP = 0.012;
   /** Ticks of the ceiling punch wind-up before the slab breaks. */
   public static final int PUNCH_WINDUP = 7;
   /** Dash: a straight lunge at a target in sight on the same level. */
   public static final double DASH_MIN = 5.0;
   public static final double DASH_MAX = 20.0;
   public static final int DASH_TICKS = 9;
   public static final int DASH_COOLDOWN = 50;
   public static final double DASH_SPEED = 1.55;

   private Scp096Route() {
   }

   public enum Tactic {
      GROUND,
      FACADE,
      BORE,
      TUNNEL;

      public String key() {
         return this.name().toLowerCase(Locale.ROOT);
      }
   }

   /**
    * The tactic for this moment. {@code stalls} counts the stalls since the last real progress and rotates through the
    * candidates, so a stalled tactic is not picked again right away.
    *
    * @param dy           target feet minus own feet
    * @param dh           horizontal distance to the target
    * @param pathReaches  a walkable path reaches the target (stairs, ramps, open doors)
    * @param underCeiling a solid slab is right above the head
    * @param overFloor    a solid floor with open space below it is under the feet (for a target below)
    * @param griefing     mobGriefing (and Forge) allow it to break blocks
    * @param facade       an outer wall column under an edge of the target's floor was found
    */
   public static Tactic choose(double dy, double dh, boolean pathReaches, boolean underCeiling, boolean overFloor, boolean griefing, boolean facade,
         int stalls) {
      if (pathReaches && stalls == 0) {
         return Tactic.GROUND;
      }
      Tactic[] order;
      if (Math.abs(dy) < LEVEL_DY) {
         order = griefing ? new Tactic[]{Tactic.GROUND, Tactic.TUNNEL} : new Tactic[]{Tactic.GROUND};
      } else if (dy > 0.0) {
         boolean boreFirst = griefing && underCeiling && dh < BORE_RANGE;
         if (boreFirst) {
            order = facade ? new Tactic[]{Tactic.BORE, Tactic.FACADE, Tactic.TUNNEL} : new Tactic[]{Tactic.BORE, Tactic.TUNNEL};
         } else if (facade) {
            order = griefing ? new Tactic[]{Tactic.FACADE, Tactic.BORE, Tactic.FACADE, Tactic.TUNNEL} : new Tactic[]{Tactic.FACADE, Tactic.GROUND};
         } else {
            order = griefing ? new Tactic[]{Tactic.BORE, Tactic.TUNNEL, Tactic.GROUND} : new Tactic[]{Tactic.GROUND};
         }
      } else {
         boolean digDown = griefing && overFloor && dh < BORE_RANGE;
         order = digDown ? new Tactic[]{Tactic.BORE, Tactic.GROUND, Tactic.TUNNEL} : griefing ? new Tactic[]{Tactic.GROUND, Tactic.TUNNEL}
            : new Tactic[]{Tactic.GROUND};
      }
      return order[Math.floorMod(stalls, order.length)];
   }

   /**
    * Progress measure of a tactic (lower is better): the climb counts the height still to go plus the run to the
    * column, the bore the floors between, the rest the distance with height weighted up (a metre up is harder won).
    */
   public static double measure(Tactic tactic, double dh, double dy, double toColumn) {
      return switch (tactic) {
         case FACADE -> Math.max(0.0, toColumn) + Math.abs(dy) * 1.2;
         case BORE -> dh * 0.6 + Math.abs(dy) * 1.5;
         default -> dh + Math.abs(dy) * 1.5;
      };
   }

   /**
    * Score of an outer wall column to climb (lower is better): the run to it, half the walk from its top to the target,
    * every block that juts into the column (a ledge to punch, or to go around without mobGriefing) and a column that
    * already failed.
    */
   public static double facadeScore(double runDistance, double topToTarget, int overhangs, boolean griefing, boolean failedBefore) {
      double s = runDistance + topToTarget * 0.5 + overhangs * (griefing ? 1.5 : 5.0);
      return failedBefore ? s + 1000.0 : s;
   }

   /** Length of the n-th pause in a row without any gain (n from 1): doubles each time, capped. */
   public static int pauseTicks(int cycle) {
      return Math.min(MAX_PAUSE_TICKS, PAUSE_TICKS << Math.min(4, Math.max(0, cycle - 1)));
   }

   /** Vertical climb speed after this many ticks on the wall: a hard start, then the frantic scramble. */
   public static double climbSpeed(int ticks) {
      return Math.min(CLIMB_TOP, CLIMB_START + Math.max(0, ticks) * CLIMB_RAMP);
   }

   /** Pure: a dash may start now (target in sight on the same level, at lunge range, on the ground, off cooldown). */
   public static boolean mayDash(boolean los, double dy, double dist, boolean onGround, int cooldown) {
      return los && onGround && cooldown <= 0 && Math.abs(dy) < 2.0 && dist >= DASH_MIN && dist <= DASH_MAX;
   }

   /**
    * Pure: the climber may let go of the wall and pull itself in: it is at the target's floor (or above the wall top) and
    * the space in front is free. Below the target's floor it ignores openings (windows of lower floors).
    */
   public static boolean mayMantle(double feetY, double landingY, boolean frontFree, boolean openAbove) {
      return frontFree && (feetY >= landingY - 0.35 || openAbove);
   }

   /** Stall bookkeeping of one tactic: best measure so far and the ticks since it last improved. */
   public static final class Progress {
      private double best = Double.MAX_VALUE;
      private int since;
      private int leaps;

      /** Feeds this tick's measure; true when the tactic just stalled (the counter starts again). */
      public boolean tick(double measure) {
         if (measure < this.best - PROGRESS_EPS) {
            this.best = measure;
            this.since = 0;
            this.leaps = 0;
            return false;
         }
         if (++this.since >= STALL_TICKS) {
            this.since = 0;
            this.best = measure;
            return true;
         }
         return false;
      }

      /** A leap landed: true when too many landed without progress (leaping should be banned for a while). */
      public boolean leapLanded() {
         return ++this.leaps >= LEAPS_WITHOUT_PROGRESS;
      }

      public void reset() {
         this.best = Double.MAX_VALUE;
         this.since = 0;
         this.leaps = 0;
      }

      public int since() {
         return this.since;
      }

      public double best() {
         return this.best;
      }
   }
}
