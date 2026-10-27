package dev.baranhan.viltrumitecore.hero;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hero-agnostic seam: a running scene may hold a hero change until it ends
 * (the scene then applies it through {@link HeroRegistry#changeHero}).
 * Server main thread.
 */
public final class HeroChangeDeferral {
   /** True = the change is taken over (deferred) by this owner. */
   @FunctionalInterface
   public interface Deferrer {
      boolean defer(ServerPlayer player, HeroId next);
   }

   private static final List<Deferrer> DEFERRERS = new CopyOnWriteArrayList<>();

   private HeroChangeDeferral() {
   }

   public static void register(Deferrer deferrer) {
      if (deferrer != null && !DEFERRERS.contains(deferrer)) {
         DEFERRERS.add(deferrer);
      }
   }

   /** True when some scene took the change over; the caller must not change the hero now. */
   public static boolean defer(ServerPlayer player, HeroId next) {
      for (Deferrer deferrer : DEFERRERS) {
         if (deferrer.defer(player, next)) {
            return true;
         }
      }

      return false;
   }

   /** Tests only. */
   public static void resetForTests() {
      DEFERRERS.clear();
   }
}
