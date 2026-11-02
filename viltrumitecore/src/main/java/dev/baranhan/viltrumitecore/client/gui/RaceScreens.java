package dev.baranhan.viltrumitecore.client.gui;

import dev.baranhan.viltrumitecore.hero.wish.WishSceneView;
import net.minecraft.client.Minecraft;

/**
 * Client entry point of the hero choice screen. The server asks for it only on purpose: the operator command
 * {@code /viltrumite choose}, or a refused choice that stays open. It never opens on login (heroes come from
 * Od Lagna's Wish).
 */
public final class RaceScreens {
   private RaceScreens() {
   }

   public static void open() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.screen instanceof RaceSelectionScreen || WishSceneView.active(mc.player.getId())) {
         return;
      }
      mc.setScreen(new RaceSelectionScreen());
   }
}
