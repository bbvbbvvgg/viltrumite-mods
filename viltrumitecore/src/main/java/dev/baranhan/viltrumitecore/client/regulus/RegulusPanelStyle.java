package dev.baranhan.viltrumitecore.client.regulus;

import dev.baranhan.viltrumitecore.client.hero.PanelStyle;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/**
 * The Archbishop's panel: ivory-white frames with a thin gold rim and a glass inner bevel, pale
 * cells, the lion-heart emblem over the ability bar. A white suit, nothing loud. Attachments
 * (strain, heart host, subjects, combo, judgement) come from {@link RegulusHud}.
 */
public final class RegulusPanelStyle implements PanelStyle {
   public static final RegulusPanelStyle INSTANCE = new RegulusPanelStyle();
   static final int BACK = 0xD81E1C24;
   static final int RIM = 0xFF000000 | RegulusPalette.GOLD;
   static final int BEVEL = 0x80000000 | RegulusPalette.GLASS;
   static final int CELL = 0xFF34313C;
   static final int CELL_EMPTY = 0x9034313C;
   static final int COOLDOWN = 0xB01E1C24;

   private RegulusPanelStyle() {
   }

   @Override
   public void drawFrames(GuiGraphics graphics, Bounds flightBar, Bounds abilityBar) {
      frame(graphics, flightBar);
      frame(graphics, abilityBar);
      int x = abilityBar.x() + (abilityBar.width() - 16) / 2;
      graphics.blit(RegulusAssets.EMBLEM, x, abilityBar.y() - 32, 16, 16, 0.0F, 0.0F, 32, 32, 32, 32);
   }

   @Override
   public void drawSlot(GuiGraphics graphics, int x, int y, boolean empty) {
      if (empty) {
         graphics.fill(x, y, x + 16, y + 16, CELL_EMPTY);
         graphics.fill(x + 7, y + 7, x + 9, y + 9, 0xA0000000 | RegulusPalette.GREY);
      } else {
         graphics.fill(x, y, x + 16, y + 16, CELL);
         graphics.fill(x, y, x + 16, y + 1, 0x50000000 | RegulusPalette.IVORY);
      }
   }

   @Override
   public void drawCooldown(GuiGraphics graphics, int x, int y) {
      graphics.fill(x, y, x + 16, y + 16, COOLDOWN);
      graphics.fill(x, y + 15, x + 16, y + 16, RIM);
   }

   @Override
   public int accentColor() {
      return RegulusPalette.GOLD;
   }

   @Override
   public void drawAttachments(GuiGraphics graphics, Bounds abilityBar, Player player, HeroPublicSnapshot snapshot) {
      RegulusHud.draw(graphics, abilityBar, player, snapshot);
   }

   private static void frame(GuiGraphics graphics, Bounds b) {
      int x0 = b.x();
      int y0 = b.y();
      int x1 = x0 + b.width();
      int y1 = y0 + b.height();
      graphics.fill(x0, y0, x1, y1, BACK);
      graphics.fill(x0, y0, x1, y0 + 1, RIM);
      graphics.fill(x0, y1 - 1, x1, y1, RIM);
      graphics.fill(x0, y0, x0 + 1, y1, RIM);
      graphics.fill(x1 - 1, y0, x1, y1, RIM);
      graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, BEVEL);
   }
}
