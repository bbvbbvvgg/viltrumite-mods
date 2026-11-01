package dev.baranhan.viltrumitecore.client.ghostrider;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;

/** World VFX of the Ghost Rider (client). */
public final class GhostRiderVfx {
   /** Entity id -> client tick until which it burns with hellfire. */
   static final Int2IntOpenHashMap HELLFIRE = new Int2IntOpenHashMap();
   static int clientTicks;

   private GhostRiderVfx() {
   }

   public static void receiveHellfire(int entityId, int ticks) {
      if (Minecraft.getInstance().level == null) {
         return;
      }
      if (ticks <= 0) {
         HELLFIRE.remove(entityId);
      } else {
         HELLFIRE.put(entityId, clientTicks + ticks);
      }
   }
}
