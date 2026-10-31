package dev.baranhan.viltrumitecore.hero.budget.multiverse;

import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Discards loaded multiverse NPCs; unloaded ones are never saved anyway. NPCs in
 * chunks that were only hidden (no player nearby) are invisible to the sweep, so
 * ended sessions are remembered and such NPCs discard themselves once they tick again.
 */
final class MultiverseCleanup {
   private static final java.util.Set<Integer> ENDED = java.util.concurrent.ConcurrentHashMap.newKeySet();

   private MultiverseCleanup() {
   }

   /** True for a session of an event or Boss Rush that is already over. */
   static boolean ended(int session) {
      return ENDED.contains(session);
   }

   static int discard(MinecraftServer server, Predicate<MultiverseFighter> which) {
      int count = 0;
      for (ServerLevel level : server.getAllLevels()) {
         List<MultiverseFighter> found = new ArrayList<>();
         for (Entity entity : level.getEntities(ViltrumiteEntities.MULTIVERSE_FIGHTER.get(), e -> true)) {
            if (entity instanceof MultiverseFighter fighter && which.test(fighter)) {
               found.add(fighter);
            }
         }

         for (MultiverseFighter fighter : found) {
            fighter.discard();
            count++;
         }
      }

      return count;
   }

   static int discardSession(MinecraftServer server, int session) {
      ENDED.add(session);
      return discard(server, fighter -> fighter.session() == session);
   }
}
