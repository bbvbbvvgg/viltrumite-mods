package dev.baranhan.viltrumitecore.client.redhulk;

import dev.baranhan.viltrumitecore.client.hero.PanelStyle;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.redhulk.RedHulkRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Thunderbolt panel: charred-iron frames with a molten rim and an ember line, the red fist emblem; left of the ability
 * bar the temperature gauge (cold -> warm -> hot -> blazing, tier marks at 250 / 500 / 800, ability marks at the Heat
 * Blast and Critical Overheat thresholds), the heat number and tier name above it, a blinking OVERHEAT state with the
 * cool-down lock, and the Thermal Fury timer.
 */
public final class RedHulkPanelStyle implements PanelStyle {
   public static final RedHulkPanelStyle INSTANCE = new RedHulkPanelStyle();
   private static final ResourceLocation EMBLEM = RedHulkView.rl("textures/gui/red_hulk/emblem.png");
   static final int BACK = 0xD8190A08;
   static final int RIM = 0xFFE0571E;
   static final int RIM_DARK = 0xFF5A1A10;
   static final int EMBER = 0xFFFFB238;
   static final int SLOT = 0xFF2A1210;
   static final int SLOT_EMPTY = 0x902A1210;
   static final int COOLDOWN = 0xB0100404;
   public static final int BAR_WIDTH = 48;
   public static final int BAR_HEIGHT = 5;

   private RedHulkPanelStyle() {
   }

   @Override
   public void drawFrames(GuiGraphics g, Bounds flightBar, Bounds abilityBar) {
      frame(g, flightBar);
      frame(g, abilityBar);
      int x = abilityBar.x() + (abilityBar.width() - 16) / 2;
      g.blit(EMBLEM, x, abilityBar.y() - 32, 0.0F, 0.0F, 16, 16, 16, 16);
   }

   @Override
   public void drawSlot(GuiGraphics g, int x, int y, boolean empty) {
      g.fill(x, y, x + 16, y + 16, empty ? SLOT_EMPTY : SLOT);
      if (empty) {
         g.fill(x + 7, y + 7, x + 9, y + 9, RIM_DARK);
      }
   }

   @Override
   public void drawCooldown(GuiGraphics g, int x, int y) {
      g.fill(x, y, x + 16, y + 16, COOLDOWN);
      g.fill(x, y + 15, x + 16, y + 16, RIM);
   }

   @Override
   public int accentColor() {
      return RIM & 0xFFFFFF;
   }

   /** Pure: the gauge colour at a heat (dark red -> red -> orange -> yellow-white), ARGB. */
   public static int heatColor(int heat) {
      float h = RedHulkRules.clampHeat(heat) / (float)RedHulkRules.HEAT_MAX;
      int r = (int)(150 + 105 * Math.min(1.0F, h * 2.0F));
      int gg = (int)(20 + 200 * h * h);
      int b = (int)(10 + 150 * Math.max(0.0F, h - 0.75F) * 4.0F * 0.6F);
      return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, gg) << 8) | Math.min(255, b);
   }

   /** Pure: the lang key of the tier label (overheat wins). */
   public static String tierKey(int heat, boolean overheat) {
      if (overheat) {
         return "hud.viltrumitecore.red_hulk.overheat";
      }
      return "hud.viltrumitecore.red_hulk.tier." + RedHulkRules.tier(heat);
   }

   @Override
   public void drawAttachments(GuiGraphics g, Bounds abilityBar, Player player, HeroPublicSnapshot s) {
      int x1 = abilityBar.x() - 3;
      int x0 = x1 - BAR_WIDTH;
      int y = abilityBar.y() + 2;
      int heat = RedHulkView.heat(s);
      boolean over = RedHulkView.overheated(s);
      boolean blink = (player.tickCount / 4) % 2 == 0;
      g.fill(x0 - 1, y - 1, x1 + 1, y + BAR_HEIGHT + 1, over && blink ? 0xFFFFE6A0 : 0xFF000000);
      g.fill(x0, y, x1, y + BAR_HEIGHT, 0xFF1A0C0A);
      int fill = Math.round(heat / (float)RedHulkRules.HEAT_MAX * BAR_WIDTH);
      // the filled part as a gradient: every column takes the colour of its own heat
      for (int i = 0; i < fill; i++) {
         int c = heatColor(Math.round((i + 0.5F) / BAR_WIDTH * RedHulkRules.HEAT_MAX));
         g.fill(x0 + i, y, x0 + i + 1, y + BAR_HEIGHT, c);
      }
      if (fill > 0) {
         g.fill(x0, y, x0 + fill, y + 1, 0x60FFFFFF);
      }
      for (int mark : new int[]{RedHulkRules.WARM, RedHulkRules.HOT, RedHulkRules.BLAZING}) {
         int mx = x0 + Math.round(mark / (float)RedHulkRules.HEAT_MAX * BAR_WIDTH);
         g.fill(mx, y - 1, mx + 1, y + BAR_HEIGHT + 1, heat >= mark ? 0xFFFFF0C0 : RIM_DARK);
      }
      // ability thresholds as small ticks under the gauge
      tick(g, x0, y + BAR_HEIGHT + 1, RedHulkRules.BLAST_MIN_HEAT, heat);
      tick(g, x0, y + BAR_HEIGHT + 1, RedHulkRules.ULT_MIN_HEAT, heat);
      var font = Minecraft.getInstance().font;
      String number = String.valueOf(heat / 10);
      g.drawString(font, number, x0 - font.width(number) - 3, y - 2, heatColor(Math.max(heat, 400)), true);
      Component label = Component.translatable(tierKey(heat, over));
      int labelColor = over ? (blink ? 0xFFFFF0C0 : 0xFFFF5020) : heatColor(Math.max(heat, 300));
      g.drawString(font, label, x1 - font.width(label), y - 12, labelColor, true);
      if (over) {
         int left = Math.max(0, s.lionWindowLeft());
         int w = Math.round(Math.min(1.0F, left / (float)RedHulkRules.OVERHEAT_TICKS) * BAR_WIDTH);
         g.fill(x0, y + BAR_HEIGHT + 3, x0 + w, y + BAR_HEIGHT + 4, 0xFFB0E0FF);
      }
      int fury = Math.max(0, s.resource2());
      if (fury > 0) {
         int w = Math.round(Math.min(1.0F, fury / (float)RedHulkRules.FURY_TICKS) * BAR_WIDTH);
         g.fill(x0 - 1, y - 4, x1 + 1, y - 1, 0xFF000000);
         g.fill(x0, y - 3, x0 + w, y - 2, blink ? 0xFFFF3A1A : 0xFFFF7A2A);
      }
   }

   private static void tick(GuiGraphics g, int x0, int y, int at, int heat) {
      int mx = x0 + Math.round(at / (float)RedHulkRules.HEAT_MAX * BAR_WIDTH);
      g.fill(mx - 1, y, mx + 2, y + 1, heat >= at ? EMBER : RIM_DARK);
   }

   private static void frame(GuiGraphics g, Bounds b) {
      int x0 = b.x(), y0 = b.y(), x1 = x0 + b.width(), y1 = y0 + b.height();
      g.fill(x0, y0, x1, y1, BACK);
      g.fill(x0, y0, x1, y0 + 1, RIM);
      g.fill(x0, y1 - 1, x1, y1, RIM_DARK);
      g.fill(x0, y0, x0 + 1, y1, RIM);
      g.fill(x1 - 1, y0, x1, y1, RIM_DARK);
      g.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, EMBER);
   }
}
