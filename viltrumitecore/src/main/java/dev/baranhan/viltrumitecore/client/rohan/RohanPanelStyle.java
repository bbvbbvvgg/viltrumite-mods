package dev.baranhan.viltrumitecore.client.rohan;

import dev.baranhan.viltrumitecore.client.hero.PanelStyle;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.rohan.RohanFlags;
import dev.baranhan.viltrumitecore.hero.rohan.RohanRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Mangaka panel: manuscript paper frames with a screentone edge and a green headband stripe; left of the bar's top,
 * an ink bottle gauge (Ink, with the cost marks of Heaven's Door and the Air Sketch) and a gold Inspiration gauge
 * that lights up when the Manuscript is ready.
 */
public final class RohanPanelStyle implements PanelStyle {
   public static final RohanPanelStyle INSTANCE = new RohanPanelStyle();
   static final int PAPER = 0xE0F1E8D4;
   static final int LINE = 0xFF1E1A2E;
   static final int TONE = 0x501E1A2E;
   static final int GREEN = 0xFF2F9A6A;
   static final int GOLD = 0xFFE2B044;
   static final int SLOT = 0xFFE6DCC4;
   static final int SLOT_EMPTY = 0x90E6DCC4;
   public static final int GAUGE_W = 52, GAUGE_H = 4;

   private RohanPanelStyle() {
   }

   @Override
   public void drawFrames(GuiGraphics g, Bounds flightBar, Bounds abilityBar) {
      frame(g, flightBar);
      frame(g, abilityBar);
   }

   @Override
   public void drawSlot(GuiGraphics g, int x, int y, boolean empty) {
      g.fill(x, y, x + 16, y + 16, empty ? SLOT_EMPTY : SLOT);
      g.fill(x, y + 15, x + 16, y + 16, 0x401E1A2E);
   }

   @Override
   public void drawCooldown(GuiGraphics g, int x, int y) {
      g.fill(x, y, x + 16, y + 16, 0xA01E1A2E);
      // diagonal hatching like a manga shadow
      for (int i = 0; i < 16; i += 3) {
         g.fill(x + i, y + 15 - i, x + i + 1, y + 16 - i, 0x60FFFFFF);
      }
   }

   @Override
   public int accentColor() {
      return GREEN & 0xFFFFFF;
   }

   /** Pure: filled px of a gauge. */
   public static int fill(float value, float max, int width) {
      if (max <= 0.0F) {
         return 0;
      }
      return Math.round(Math.max(0.0F, Math.min(1.0F, value / max)) * width);
   }

   @Override
   public void drawAttachments(GuiGraphics g, Bounds abilityBar, Player player, HeroPublicSnapshot s) {
      var font = Minecraft.getInstance().font;
      int x1 = abilityBar.x() - 3, x0 = x1 - GAUGE_W;
      int y = abilityBar.y() - 6;
      float ink = RohanView.ink(s), insp = RohanView.inspiration(s);
      // ink
      g.fill(x0 - 1, y - 1, x1 + 1, y + GAUGE_H + 1, LINE);
      g.fill(x0, y, x1, y + GAUGE_H, 0xFFE6DCC4);
      g.fill(x0, y, x0 + fill(ink, RohanRules.INK_MAX, GAUGE_W), y + GAUGE_H, RohanView.flag(s, RohanFlags.IN_COMBAT) ? 0xFF262040 : 0xFF3A3466);
      for (float mark : new float[]{RohanRules.COST_DOOR, RohanRules.COST_SKETCH}) {
         int mx = x0 + fill(mark, RohanRules.INK_MAX, GAUGE_W);
         g.fill(mx, y - 1, mx + 1, y + GAUGE_H + 1, GREEN);
      }
      Component inkLabel = Component.translatable("hud.viltrumitecore.rohan.ink").append(" " + Math.round(ink));
      g.drawString(font, inkLabel, x1 - font.width(inkLabel), y - 10, 0xFFEDE6D6, true);
      // inspiration
      int y2 = y - 16;
      boolean ready = insp >= RohanRules.MANUSCRIPT_COST;
      boolean blink = (player.tickCount / 5) % 2 == 0;
      g.fill(x0 - 1, y2 - 1, x1 + 1, y2 + 2 + 1, LINE);
      g.fill(x0, y2, x0 + fill(insp, RohanRules.INSPIRATION_MAX, GAUGE_W), y2 + 2, ready && blink ? 0xFFFFF0B0 : GOLD);
      Component inspLabel = Component.translatable("hud.viltrumitecore.rohan.inspiration").append(" " + Math.round(insp));
      g.drawString(font, inspLabel, x1 - font.width(inspLabel), y2 - 10, ready ? GOLD : 0xFFE8D8A8, true);
   }

   @Override
   public int attachmentsLeft() {
      return 70;
   }

   @Override
   public int attachmentsUp() {
      return 32;
   }

   private static void frame(GuiGraphics g, Bounds b) {
      int x0 = b.x(), y0 = b.y(), x1 = x0 + b.width(), y1 = y0 + b.height();
      g.fill(x0, y0, x1, y1, PAPER);
      g.fill(x0, y0, x1, y0 + 1, LINE);
      g.fill(x0, y1 - 1, x1, y1, LINE);
      g.fill(x0, y0, x0 + 1, y1, LINE);
      g.fill(x1 - 1, y0, x1, y1, LINE);
      g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, GREEN);
      // screentone dots along the bottom edge
      for (int x = x0 + 2; x < x1 - 2; x += 3) {
         g.fill(x, y1 - 3, x + 1, y1 - 2, TONE);
      }
   }
}
