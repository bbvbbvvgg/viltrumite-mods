package dev.baranhan.viltrumitecore.hero.thor.combat;

/** Pure growth of the held-RMB storm (#77, #114): radius by ticks held, ease-out. */
public final class StormCurve {
   private StormCurve() {
   }

   public static double radius(int ticks, int maxTicks, double start, double max) {
      if (maxTicks <= 0) {
         return max;
      }

      double t = Math.max(0.0, Math.min(1.0, ticks / (double)maxTicks));
      double eased = 1.0 - (1.0 - t) * (1.0 - t);
      return start + (max - start) * eased;
   }

   /** Damage of the closing burst grows with the storm (PLACEHOLDER). */
   public static float burst(float base, double radius, double max) {
      return (float)(base * (0.5 + 0.5 * Math.min(1.0, radius / Math.max(1.0E-3, max))));
   }
}
