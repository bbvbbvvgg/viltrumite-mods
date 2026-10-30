package dev.baranhan.viltrumitecore.hero.wish;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client copy of "which entities are inside a wish ritual" (written by the client scene, read by the
 * shared {@link dev.baranhan.viltrumitecore.hero.ActionLocks} provider and the item). Common class with
 * no client imports so the lock provider loads on a dedicated server too.
 */
public final class WishSceneView {
   private static final Set<Integer> ACTIVE = ConcurrentHashMap.newKeySet();

   private WishSceneView() {
   }

   public static void set(int entityId, boolean active) {
      if (active) {
         ACTIVE.add(entityId);
      } else {
         ACTIVE.remove(entityId);
      }
   }

   public static boolean active(int entityId) {
      return ACTIVE.contains(entityId);
   }

   public static void clear() {
      ACTIVE.clear();
   }
}
