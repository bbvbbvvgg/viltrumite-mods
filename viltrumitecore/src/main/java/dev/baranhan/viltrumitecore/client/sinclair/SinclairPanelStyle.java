package dev.baranhan.viltrumitecore.client.sinclair;

import dev.baranhan.viltrumitecore.client.hero.PanelStyle;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/**
 * Stage panel: ink frames with a stage-gold rim and a velvet bevel, ghost-violet slot cells and the
 * top-hat emblem over the ability bar. The magazine, the card window and the stage timers come from
 * {@link SinclairHud}.
 */
public final class SinclairPanelStyle implements PanelStyle {
   public static final SinclairPanelStyle INSTANCE = new SinclairPanelStyle();
   static final int INK = 0xD815121A;
   static final int GOLD = 0xFFF2C14E;
   static final int VELVET = 0xFF8E1B2C;
   static final int CELL = 0xFF3B2A63;
   static final int CELL_EMPTY = 0x903B2A63;
   static final int COOLDOWN = 0xA015121A;

   private SinclairPanelStyle() {
   }

   @Override
   public void drawFrames(GuiGraphics graphics, Bounds flightBar, Bounds abilityBar) {
      frame(graphics, flightBar);
      frame(graphics, abilityBar);
      int x = abilityBar.x() + (abilityBar.width() - 16) / 2;
      graphics.blit(SinclairAssets.EMBLEM, x, abilityBar.y() - 32, 0.0F, 0.0F, 16, 16, 16, 16);
   }

   @Override
   public void drawSlot(GuiGraphics graphics, int x, int y, boolean empty) {
      if (empty) {
         graphics.fill(x, y, x + 16, y + 16, CELL_EMPTY);
         graphics.fill(x + 7, y + 7, x + 9, y + 9, VELVET);
      } else {
         graphics.fill(x, y, x + 16, y + 16, CELL);
         graphics.fill(x, y, x + 16, y + 1, 0x40C9B6FF);
      }
   }

   @Override
   public void drawCooldown(GuiGraphics graphics, int x, int y) {
      graphics.fill(x, y, x + 16, y + 16, COOLDOWN);
      graphics.fill(x, y + 15, x + 16, y + 16, GOLD);
   }

   @Override
   public int accentColor() {
      return GOLD & 0xFFFFFF;
   }

   @Override
   public void drawAttachments(GuiGraphics graphics, Bounds abilityBar, Player player, HeroPublicSnapshot snapshot) {
      SinclairHud.draw(graphics, abilityBar, player, snapshot);
   }

   private static void frame(GuiGraphics graphics, Bounds b) {
      int x0 = b.x();
      int y0 = b.y();
      int x1 = x0 + b.width();
      int y1 = y0 + b.height();
      graphics.fill(x0, y0, x1, y1, INK);
      graphics.fill(x0, y0, x1, y0 + 1, GOLD);
      graphics.fill(x0, y1 - 1, x1, y1, GOLD);
      graphics.fill(x0, y0, x0 + 1, y1, GOLD);
      graphics.fill(x1 - 1, y0, x1, y1, GOLD);
      graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, VELVET);
   }
}
