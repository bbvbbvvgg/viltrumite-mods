package dev.baranhan.viltrumitecore.client.jinwoo;

import dev.baranhan.viltrumitecore.client.hero.PanelStyle;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Shadow Monarch panel: black frames with a violet rim, ink slots, the helmet emblem; attachments
 * left of the ability bar: the mana bar (violet), the army counter (active/roster) and the domain timer.
 */
public final class JinwooPanelStyle implements PanelStyle {
   public static final JinwooPanelStyle INSTANCE = new JinwooPanelStyle();
   private static final ResourceLocation EMBLEM = JinwooView.rl("textures/gui/jinwoo/emblem.png");
   static final int BACK = 0xD80C0A14;
   static final int RIM = 0xFF9650FF;
   static final int RIM_DARK = 0xFF4A2A80;
   static final int SLOT = 0xFF1A1426;
   static final int SLOT_EMPTY = 0x901A1426;
   static final int COOLDOWN = 0xB0050308;
   static final int MANA = 0xFF8C5CFF;
   static final int MANA_HI = 0xFFC7A8FF;
   public static final int BAR_WIDTH = 44;
   public static final int BAR_HEIGHT = 4;

   private JinwooPanelStyle() {
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

   @Override
   public void drawAttachments(GuiGraphics g, Bounds abilityBar, Player player, HeroPublicSnapshot s) {
      int x1 = abilityBar.x() - 3;
      int x0 = x1 - BAR_WIDTH;
      int y = abilityBar.y();
      // mana
      g.fill(x0 - 1, y - 1, x1 + 1, y + BAR_HEIGHT + 1, 0xFF000000);
      g.fill(x0, y, x1, y + BAR_HEIGHT, 0xFF1A1426);
      int fill = Math.round(JinwooView.manaFraction(s) * BAR_WIDTH);
      g.fill(x0, y, x0 + fill, y + BAR_HEIGHT, MANA);
      g.fill(x0, y, x0 + fill, y + 1, MANA_HI);
      // ult threshold tick
      int ult = x0 + Math.round(JinwooRules.ULT_MANA / JinwooRules.MANA_MAX * BAR_WIDTH);
      g.fill(ult, y - 1, ult + 1, y + BAR_HEIGHT + 1, 0xFFE8C15A);
      var font = Minecraft.getInstance().font;
      String mana = String.valueOf(Math.round(JinwooView.mana(s)));
      g.drawString(font, mana, x0 - font.width(mana) - 2, y - 2, 0xFFC7A8FF, true);
      // army counter and domain timer sit above the bar: the key labels own the column below it
      String army = JinwooView.active(s) + "/" + JinwooView.roster(s);
      g.drawString(font, army, x1 - font.width(army), y - 13, 0xFFB8C8FF, true);
      int domain = JinwooView.domainLeft(s);
      if (domain > 0) {
         int dy = y - 4;
         int w = Math.round(domain / (float)JinwooRules.ULT_DOMAIN * BAR_WIDTH);
         g.fill(x0 - 1, dy - 1, x1 + 1, dy + 3, 0xFF000000);
         g.fill(x0, dy, x0 + w, dy + 2, 0xFF5A8CFF);
      }
   }

   private static void frame(GuiGraphics g, Bounds b) {
      int x0 = b.x(), y0 = b.y(), x1 = x0 + b.width(), y1 = y0 + b.height();
      g.fill(x0, y0, x1, y1, BACK);
      g.fill(x0, y0, x1, y0 + 1, RIM);
      g.fill(x0, y1 - 1, x1, y1, RIM);
      g.fill(x0, y0, x0 + 1, y1, RIM);
      g.fill(x1 - 1, y0, x1, y1, RIM);
      g.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, RIM_DARK);
   }
}
