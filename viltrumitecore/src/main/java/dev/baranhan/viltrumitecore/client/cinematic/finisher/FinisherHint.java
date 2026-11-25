package dev.baranhan.viltrumitecore.client.cinematic.finisher;

import dev.baranhan.viltrumitecore.client.AbilityInputManager;
import dev.baranhan.viltrumitecore.client.gui.KeyLabels;
import dev.baranhan.viltrumitecore.client.hero.CinematicHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * HUD prompt of the finisher (wave1-22, task T6): one short line under the crosshair while the local
 * hero has a body it could finish ({@link FinisherTargeting}). Nothing is drawn with F1, in a menu, as
 * a spectator or while a scene owns the clean screen. Prediction only: the server re-checks every rule.
 */
@EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT)
public final class FinisherHint {
   private static final int GOLD = 0xFFFFD54A;

   private FinisherHint() {
   }

   @SubscribeEvent
   public static void onRenderGui(RenderGuiEvent.Post event) {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null || client.level == null || client.options.hideGui || client.screen != null) {
         return;
      }
      if (client.player.isSpectator() || CinematicHud.hiding() || FinisherTargeting.candidate() == null) {
         return;
      }

      GuiGraphics graphics = event.getGuiGraphics();
      Component hint = Component.translatable("hud.viltrumitecore.finisher.hint", KeyLabels.shortName(AbilityInputManager.finisherKey));
      int width = client.font.width(hint);
      int x = graphics.guiWidth() / 2 - width / 2;
      int y = graphics.guiHeight() / 2 + 18;
      graphics.fill(x - 4, y - 3, x + width + 4, y + 11, 0x80000000);
      graphics.drawString(client.font, hint, x, y, GOLD, true);
   }
}
