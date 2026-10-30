package dev.baranhan.viltrumitecore.client.dio;

import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Blood bar above the hotbar (marks at the time-stop minimum and full for the
 * ult) and, while DIO's own time is stopped, a thin clock bar that drains.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class DioHud {
   static final int WIDTH = 80;
   static final int HEIGHT = 4;

   private DioHud() {
   }

   /** Pure: filled width of the blood bar. */
   public static int fill(float blood, int width) {
      return Math.round(width * Mth.clamp(blood / DioRules.BLOOD_MAX, 0.0F, 1.0F));
   }

   @SubscribeEvent
   public static void onRenderGui(RenderGuiEvent.Post event) {
      if (RollerCinema.active()) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      if (client.options.hideGui || client.player == null || client.player.isSpectator()) {
         return;
      }

      HeroPublicSnapshot snapshot = DioView.of(client.player);
      if (snapshot == null) {
         return;
      }

      GuiGraphics g = event.getGuiGraphics();
      int x = g.guiWidth() / 2 - WIDTH / 2;
      int y = g.guiHeight() - 59;
      float blood = DioView.blood(snapshot);
      boolean sun = snapshot.heroFlag(DioRules.FLAG_SUN);
      g.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0xC0100608);
      int fill = fill(blood, WIDTH);
      int color = sun && (client.player.tickCount / 6) % 2 == 0 ? 0xFF8A3A2A : blood >= DioRules.ROLLER_BLOOD ? 0xFFE8B83A : 0xFFB0121E;
      g.fill(x, y, x + fill, y + HEIGHT, color);
      g.fill(x, y, x + fill, y + 1, 0x50FFFFFF);
      int mark = x + fill(DioRules.TIME_STOP_MIN_BLOOD, WIDTH);
      g.fill(mark, y - 1, mark + 1, y + HEIGHT + 1, 0xC0FFE7A0);
      if (snapshot.heroFlag(DioRules.FLAG_INFINITE_BLOOD)) {
         // Debug infinite blood: a pulsing sheen over the full bar and an infinity mark.
         int pulse = (int)(40 + 30 * Math.sin(client.player.tickCount * 0.25));
         g.fill(x, y, x + WIDTH, y + HEIGHT, (pulse << 24) | 0xFFFFFF);
         g.drawString(client.font, "\u221E", x + WIDTH + 4, y - 3, 0xFFE8B83A, true);
      }

      int left = snapshot.extraCooldown(DioRules.EX_FIELD_LEFT), total = snapshot.extraCooldown(DioRules.EX_FIELD_TOTAL);
      if (snapshot.heroFlag(DioRules.FLAG_TIME_STOP) && total > 0) {
         int w = Math.round(WIDTH * Mth.clamp(left / (float)total, 0.0F, 1.0F));
         int clock = snapshot.heroFlag(DioRules.FLAG_RESUMING) ? 0xFFFFFFFF : 0xFF9FD8FF;
         g.fill(x, y - 4, x + w, y - 2, clock);
      }
   }
}
