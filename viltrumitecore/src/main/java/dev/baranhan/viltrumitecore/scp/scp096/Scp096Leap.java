package dev.baranhan.viltrumitecore.scp.scp096;

/**
 * Pure ballistics of SCP-096's leaps on Minecraft's discrete motion (per tick: move by the velocity, then
 * {@code vy = (vy - 0.08) * 0.98}; the horizontal speed is held by the chase steering). Covered by Scp096LeapTest.
 */
public final class Scp096Leap {
   public static final double GRAVITY = 0.08;
   public static final double DRAG = 0.98;
   public static final double MIN_VY = 0.35;
   public static final double MAX_VY = 2.6;
   public static final double MAX_HSPEED = 2.6;
   public static final int MAX_TICKS = 80;

   private Scp096Leap() {
   }

   /** A solved leap: start velocity and the tick it arrives at the target height (after the apex). */
   public record Solution(double vx, double vy, double vz, int ticks, double apex) {
   }

   /** Highest point (relative) of a jump with this start vertical speed. */
   public static double apex(double vy0) {
      double y = 0.0;
      double v = vy0;
      double best = 0.0;
      for (int i = 0; i < MAX_TICKS * 2 && v > 0.0; i++) {
         y += v;
         v = (v - GRAVITY) * DRAG;
         best = Math.max(best, y);
      }
      return best;
   }

   /** Ticks until the falling arc gets down to {@code dy} (relative), or -1 if it never reaches that height. */
   public static int arrival(double vy0, double dy) {
      double y = 0.0;
      double v = vy0;
      boolean falling = false;
      for (int i = 1; i <= MAX_TICKS; i++) {
         y += v;
         v = (v - GRAVITY) * DRAG;
         if (v < 0.0) {
            falling = true;
         }
         if (falling && y <= dy) {
            return i;
         }
      }
      return -1;
   }

   /**
    * A leap to the point (dx, dy, dz) relative to the feet, clearing it by {@code clearance} at the apex; null when
    * it is out of reach (more than {@link Scp096Rules#LEAP_MAX_UP} up, or no arc fits the speed limits).
    */
   public static Solution solve(double dx, double dy, double dz, double clearance) {
      double h = Math.sqrt(dx * dx + dz * dz);
      if (!Double.isFinite(h) || !Double.isFinite(dy) || dy > Scp096Rules.LEAP_MAX_UP || h > Scp096Rules.LEAP_MAX_H * 1.5) {
         return null;
      }
      double need = Math.max(dy, 0.0) + Math.max(0.5, clearance);
      double lo = MIN_VY;
      double hi = MAX_VY;
      if (apex(hi) < need) {
         return null;
      }
      for (int i = 0; i < 40; i++) {
         double mid = (lo + hi) * 0.5;
         if (apex(mid) >= need) {
            hi = mid;
         } else {
            lo = mid;
         }
      }
      double vy = hi;
      // a longer flight for a long gap: raise the arc until the horizontal speed fits
      for (int k = 0; k < 30; k++) {
         int t = arrival(vy, dy);
         if (t > 0) {
            double hs = h / t;
            if (hs <= MAX_HSPEED || vy >= MAX_VY - 1.0E-9) {
               double f = h < 1.0E-6 ? 0.0 : Math.min(hs, MAX_HSPEED) / h;
               return new Solution(dx * f, vy, dz * f, t, apex(vy));
            }
         }
         vy = Math.min(MAX_VY, vy + 0.08);
      }
      return null;
   }
}
