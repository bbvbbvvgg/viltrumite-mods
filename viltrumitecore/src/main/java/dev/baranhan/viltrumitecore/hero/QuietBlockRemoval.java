package dev.baranhan.viltrumitecore.hero;

import java.util.function.Supplier;

/** Scoped callback policy for clean air replacement. Flags alone do not suppress onRemove. */
public final class QuietBlockRemoval {
   private static final ThreadLocal<int[]> DEPTH=ThreadLocal.withInitial(() -> new int[1]);
   private QuietBlockRemoval() {}
   public static boolean active() { return DEPTH.get()[0]>0; }
   public static <T> T run(Supplier<T> operation) {
      int[] depth=DEPTH.get(); depth[0]++;
      try { return operation.get(); } finally { depth[0]--; }
   }
   /** Quiet mode discards the block entity without inventory drops, XP or redstone callbacks. */
   public static void onRemove(Runnable normal, Runnable quietCleanup) {
      if(active()) quietCleanup.run(); else normal.run();
   }
}
