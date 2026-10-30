package dev.baranhan.viltrumitecore.client.wish;

import dev.baranhan.viltrumitecore.hero.wish.WishSceneView;
import net.minecraft.client.Minecraft;

/** Client entry point that opens the wish screen: only the item (RMB), never the server on login. */
public final class WishScreens {
   private WishScreens() {
   }

   public static void open() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || WishSceneView.active(mc.player.getId()) || mc.screen instanceof WishScreen) {
         return;
      }
      mc.setScreen(new WishScreen());
   }
}
