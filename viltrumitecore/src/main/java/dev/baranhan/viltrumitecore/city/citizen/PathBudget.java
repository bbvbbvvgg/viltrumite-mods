package dev.baranhan.viltrumitecore.city.citizen;

/**
 * Spreads path searches over ticks: at most {@link #NORMAL} stroll searches and
 * {@link #TOTAL} searches in total (flee requests use the urgent share) per game tick.
 */
public final class PathBudget {
   public static final int NORMAL = 6;
   public static final int TOTAL = 20;
   private static long tick = Long.MIN_VALUE;
   private static int used;

   private PathBudget() {
   }

   public static boolean tryAcquire(long gameTime, boolean urgent) {
      if (gameTime != tick) {
         tick = gameTime;
         used = 0;
      }

      if (used >= (urgent ? TOTAL : NORMAL)) {
         return false;
      }

      used++;
      return true;
   }
}
