package dev.baranhan.viltrumitecore.client.wish;

import dev.baranhan.viltrumitecore.hero.wish.WishSceneView;
import net.minecraft.client.Minecraft;

/** Client entry points that open the wish screen (the item, the server's first-choice packet). */
public final class WishScreens {
   private WishScreens() {
   }

   public static void open(boolean first) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || WishSceneView.active(mc.player.getId())) {
         return;
      }
      if (mc.screen instanceof WishScreen open && (open.firstChoice() || !first)) {
         return;
      }
      mc.setScreen(new WishScreen(first));
   }
}
