package dev.baranhan.viltrumitecore.hero.wish;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

/** Package bridge for the QA self-test ({@code hero/wish/qa}); not used in play. */
public final class WishQaAccess {
   private WishQaAccess() {
   }

   @Nullable
   public static String lastRefusal() {
      return WishRitual.lastRefusal;
   }

   public static void clearLastRefusal() {
      WishRitual.lastRefusal = null;
   }

   public static boolean satisfied(Wish wish, ServerPlayer player) {
      return wish.grant().satisfied(player);
   }
}
