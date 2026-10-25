package dev.baranhan.viltrumitecore.hero;

/**
 * Blocks per tick the carve queue may touch, falling with the server's mean tick
 * time (Reinhard stage 4): full {@code base} up to 40 ms, linear down to
 * {@code base / 8} at 60 ms, then {@code max(50, base / 20)}. Pure.
 */
public final class CarveBudget {
   public static final double FULL_MSPT = 40.0;
   public static final double SLOW_MSPT = 60.0;
   public static final int FLOOR = 50;

   private CarveBudget() {
   }

   public static int perTick(int base, double mspt) {
      if (mspt <= FULL_MSPT) {
         return base;
      }

      if (mspt >= SLOW_MSPT) {
         return Math.max(FLOOR, base / 20);
      }

      double t = (mspt - FULL_MSPT) / (SLOW_MSPT - FULL_MSPT);
      return (int) Math.round(base + (base / 8.0 - base) * t);
   }
}
