package dev.baranhan.viltrumitecore.client.wish;

import dev.baranhan.viltrumitecore.hero.wish.WishSceneView;
import net.minecraft.client.Minecraft;

/** Client entry points that open the wish screen (the item, the server's first-choice packet). */
public final class WishScreens {
   /** The server asked for the first choice while another screen (the world loading screen) was up. */
   private static boolean pendingFirst;

   private WishScreens() {
   }

   public static void open(boolean first) {
      Minecraft mc = Minecraft.getInstance();
      if (first && (mc.player == null || mc.screen != null && !(mc.screen instanceof WishScreen))) {
         // at login the packet can arrive under the level loading screen, which would close us: wait for it
         pendingFirst = true;
         return;
      }
      if (mc.player == null || WishSceneView.active(mc.player.getId())) {
         return;
      }
      if (mc.screen instanceof WishScreen open && (open.firstChoice() || !first)) {
         return;
      }
      mc.setScreen(new WishScreen(first));
   }

   /** Client tick: open a deferred first choice once nothing else is on screen. */
   static void tick(Minecraft mc) {
      if (pendingFirst && mc.player != null && mc.level != null && mc.screen == null) {
         pendingFirst = false;
         open(true);
      }
   }

   static void clear() {
      pendingFirst = false;
   }
}
