package dev.baranhan.viltrumitecore.client.sinclair;

import dev.baranhan.viltrumitecore.client.hero.PanelStyle.Bounds;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairFlags;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import dev.baranhan.viltrumitecore.hero.sinclair.act.SinclairRepertoire;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Sinclair's HUD beside the ability panel: Prestige's magazine (12 bursts) with the reload fill,
 * the captured act's card with its name and the window countdown, the veil and Savannah timers and
 * the Ovation flash. Only reads the snapshot.
 */
public final class SinclairHud {
   static final int PIP = 3;

   private SinclairHud() {
   }

   /** Pure: seconds with one decimal for a tick count. */
   static String seconds(int ticks) {
      return String.format(java.util.Locale.ROOT, "%.1f", Math.max(0, ticks) / 20.0F);
   }

   /** Pure: filled magazine pips (0..MAGAZINE). */
   static int pips(int magazine) {
      return Math.max(0, Math.min(SinclairRules.MAGAZINE, magazine));
   }

   public static void draw(GuiGraphics graphics, Bounds bar, Player player, HeroPublicSnapshot snapshot) {
      Font font = Minecraft.getInstance().font;
      int x = bar.x() - PIP - 4;
      int y = bar.y() + 2;
      int filled = pips(snapshot.resource());
      boolean reloading = SinclairView.flag(snapshot, SinclairFlags.RELOADING);
      float reload = reloading ? 1.0F - snapshot.cooldowns()[SinclairFlags.COOLDOWN_RELOAD] / (float) SinclairRules.RELOAD_TICKS : 0.0F;
      for (int i = 0; i < SinclairRules.MAGAZINE; i++) {
         int py = y + i * (PIP + 1);
         boolean on = reloading ? i >= SinclairRules.MAGAZINE - Math.round(reload * SinclairRules.MAGAZINE) : i >= SinclairRules.MAGAZINE - filled;
         graphics.fill(x - 1, py - 1, x + PIP + 1, py + PIP + 1, 0xA015121A);
         graphics.fill(x, py, x + PIP, py + PIP, on ? (reloading ? 0xFF9B6BFF : 0xFFF2C14E) : 0xFF3B2A63);
      }

      List<Line> lines = new ArrayList<>();
      ActShape shape = SinclairView.shape(snapshot);
      if (shape != null && (SinclairView.flag(snapshot, SinclairFlags.CARD_HELD) || SinclairView.move(snapshot) != null && SinclairView.move(snapshot).isAct())) {
         Component name = Component.translatable(SinclairRepertoire.nameKey(shape));
         if (SinclairView.flag(snapshot, SinclairFlags.FALLBACK)) {
            name = Component.translatable("hud.viltrumitecore.sinclair.fallback", name);
         }

         lines.add(new Line(name, 0xFF000000 | SinclairPalette.GOLD, true));
         if (SinclairView.flag(snapshot, SinclairFlags.CARD_HELD)) {
            lines.add(new Line(Component.translatable("hud.viltrumitecore.sinclair.window", seconds(SinclairView.extra(snapshot, SinclairFlags.EXTRA_WINDOW))),
               0xFFC9B6FF, false));
         }
      }

      if (SinclairView.flag(snapshot, SinclairFlags.VEIL)) {
         lines.add(new Line(Component.translatable("hud.viltrumitecore.sinclair.veil", seconds(SinclairView.extra(snapshot, SinclairFlags.EXTRA_VEIL))), 0xFFC9B6FF, false));
      }

      if (SinclairView.flag(snapshot, SinclairFlags.ASSIST_ON_STAGE)) {
         lines.add(new Line(Component.translatable("hud.viltrumitecore.sinclair.assistant", seconds(SinclairView.extra(snapshot, SinclairFlags.EXTRA_ASSIST_LEFT)),
            SinclairView.extra(snapshot, SinclairFlags.EXTRA_SWAPS_LEFT)), 0xFFB07CFF, false));
      }

      if (SinclairView.extra(snapshot, SinclairFlags.EXTRA_OVATION) > 0) {
         lines.add(new Line(Component.translatable("hud.viltrumitecore.sinclair.ovation"), 0xFFF2C14E, false));
      }

      int ty = y;
      int right = x - 4;
      for (Line line : lines) {
         int w = font.width(line.text());
         int lx = right - w - (line.card() ? 12 : 0);
         graphics.fill(lx - 2, ty - 2, right + 2, ty + 10, 0x9015121A);
         if (line.card()) {
            graphics.blit(SinclairAssets.ACT_CARD_ICON, right - 10, ty - 1, 10, 10, 0.0F, 0.0F, 16, 16, 16, 16);
         }

         graphics.drawString(font, line.text(), lx, ty, line.color(), true);
         ty += 13;
      }
   }

   record Line(Component text, int color, boolean card) {
   }
}
