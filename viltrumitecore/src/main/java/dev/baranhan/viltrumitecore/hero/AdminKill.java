package dev.baranhan.viltrumitecore.hero;

/**
 * Scope of an admin kill ({@code /kill}). Inside it, hero HP floors on
 * unblockable damage step aside, so {@code /kill} kills every hero as in
 * vanilla. Thread-local depth counter: nests, and closes on a throw.
 */
public final class AdminKill {
   private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

   private AdminKill() {
   }

   public static void run(Runnable action) {
      int[] depth = DEPTH.get();
      depth[0]++;
      try {
         action.run();
      } finally {
         depth[0]--;
      }
   }

   public static boolean active() {
      return DEPTH.get()[0] > 0;
   }
}
