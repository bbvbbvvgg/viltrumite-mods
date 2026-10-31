package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.hero.PanelStyle;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Knight panel (spec §9.1): white frames with a gold rim, blue slot cells and
 * the knight emblem crowning the ability bar (above the page number). Mode
 * icons and the Reid bar come from {@link ReinhardHud}.
 */
public final class ReinhardPanelStyle implements PanelStyle {
   public static final ReinhardPanelStyle INSTANCE = new ReinhardPanelStyle();
   private static final ResourceLocation EMBLEM = new ResourceLocation("viltrumitecore", "textures/gui/reinhard/emblem.png");
   private static final int WHITE = 0xD8F4F1EA;
   private static final int GOLD = 0xFFE8C15A;
   private static final int GOLD_DARK = 0xFF9C7A2E;
   private static final int BLUE = 0xFF2F5DA8;
   private static final int BLUE_EMPTY = 0x902F5DA8;
   private static final int COOLDOWN = 0xA0101830;

   private ReinhardPanelStyle() {
   }

   /** The mode icons, the Reid bar and the mouse hint left of them ({@link ReinhardHud#HINT_LEFT}). */
   @Override
   public int attachmentsLeft() {
      return ReinhardHud.HINT_LEFT + 4;
   }

   @Override
   public int attachmentsUp() {
      return ReinhardHud.HINT_RISE + 2;
   }

   @Override
   public void drawFrames(GuiGraphics graphics, Bounds flightBar, Bounds abilityBar) {
      frame(graphics, flightBar);
      frame(graphics, abilityBar);
      // Emblem centred over the bar, 2 px above the page number (bar.y - 14).
      int x = abilityBar.x() + (abilityBar.width() - 16) / 2;
      graphics.blit(EMBLEM, x, abilityBar.y() - 32, 0.0F, 0.0F, 16, 16, 16, 16);
   }

   @Override
   public void drawSlot(GuiGraphics graphics, int x, int y, boolean empty) {
      if (empty) {
         graphics.fill(x, y, x + 16, y + 16, BLUE_EMPTY);
         graphics.fill(x + 7, y + 7, x + 9, y + 9, GOLD_DARK);
      } else {
         graphics.fill(x, y, x + 16, y + 16, BLUE);
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
      ReinhardHud.draw(graphics, abilityBar, snapshot);
   }

   private static void frame(GuiGraphics graphics, Bounds b) {
      int x0 = b.x();
      int y0 = b.y();
      int x1 = x0 + b.width();
      int y1 = y0 + b.height();
      graphics.fill(x0, y0, x1, y1, WHITE);
      graphics.fill(x0, y0, x1, y0 + 1, GOLD);
      graphics.fill(x0, y1 - 1, x1, y1, GOLD);
      graphics.fill(x0, y0, x0 + 1, y1, GOLD);
      graphics.fill(x1 - 1, y0, x1, y1, GOLD);
      // Darker gold inner line for the "plate" bevel.
      graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, GOLD_DARK);
   }
}
