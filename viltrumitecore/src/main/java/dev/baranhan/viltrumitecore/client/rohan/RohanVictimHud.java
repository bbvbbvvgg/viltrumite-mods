package dev.baranhan.viltrumitecore.client.rohan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * The opened player's side: "your body has turned into a book", the seconds left and the struggle hint. Every press
 * of jump while open is one struggle (STRUGGLE packet, rate limited); the server decides whether the book tears free.
 * No camera lock and no input lock beyond what the server's OPEN directive already enforces.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class RohanVictimHud {
   private RohanVictimHud() {
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         RohanBookClient.reset();
         return;
      }
      RohanBookClient.tick();
      if (RohanBookClient.victim() != null && mc.screen == null) {
         while (mc.options.keyJump.consumeClick()) {
            RohanBookClient.struggle();
         }
      }
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      RohanBookClient.reset();
   }

   @SubscribeEvent
   public static void onRenderGui(RenderGuiEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      RohanBookClient.Victim v = RohanBookClient.victim();
      if (v == null || mc.options.hideGui) {
         return;
      }
      GuiGraphics g = event.getGuiGraphics();
      int w = g.guiWidth(), h = g.guiHeight();
      Component title = Component.translatable("gui.viltrumitecore.rohan.victim");
      Component hint = Component.translatable("gui.viltrumitecore.rohan.victim_hint", String.format(java.util.Locale.ROOT, "%.1f", v.secondsLeft()));
      int y = h / 4;
      int tw = Math.max(mc.font.width(title), mc.font.width(hint)) + 16;
      int x0 = w / 2 - tw / 2;
      // a torn paper slip
      g.fill(x0, y - 6, x0 + tw, y + 26, 0xE8F1E6CF);
      g.fill(x0, y + 26, x0 + tw, y + 27, 0xFF9C8B6A);
      for (int x = x0; x < x0 + tw; x += 4) {
         g.fill(x, y - 7 - ((x / 4) & 1), x + 2, y - 6, 0xE8F1E6CF);
      }
      g.drawString(mc.font, title, w / 2 - mc.font.width(title) / 2, y, 0xFF1A1424, false);
      g.drawString(mc.font, hint, w / 2 - mc.font.width(hint) / 2, y + 13, 0xFF7A2030, false);
   }
}
