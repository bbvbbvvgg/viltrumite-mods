package dev.baranhan.viltrumitecore.client.captain;

import dev.baranhan.viltrumitecore.client.hero.PanelStyle;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.captain.CaptainFlags;
import dev.baranhan.viltrumitecore.hero.captain.CaptainRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Sentinel of Liberty panel: navy frames with a silver rim and a red-white stripe, the star-shield
 * emblem; attachments left of the ability bar: the kinetic charge of the vibranium (blue, empower
 * mark at {@link CaptainRules#EMPOWER_COST}), a shield-away marker and the Avengers' fight timer.
 */
public final class CaptainPanelStyle implements PanelStyle {
   public static final CaptainPanelStyle INSTANCE = new CaptainPanelStyle();
   private static final ResourceLocation EMBLEM = CaptainView.rl("textures/gui/captain/emblem.png");
   static final int BACK = 0xD80E1830;
   static final int RIM = 0xFFC9D2E0;
   static final int RIM_DARK = 0xFF3A4C70;
   static final int RED = 0xFFC8283A;
   static final int SLOT = 0xFF16223E;
   static final int SLOT_EMPTY = 0x9016223E;
   static final int COOLDOWN = 0xB0060A16;
   static final int KINETIC = 0xFF4FA8FF;
   static final int KINETIC_HI = 0xFFBFE4FF;
   public static final int BAR_WIDTH = 44;
   public static final int BAR_HEIGHT = 4;

   private CaptainPanelStyle() {
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
      g.fill(x, y + 15, x + 16, y + 16, RED);
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
      g.fill(x0 - 1, y - 1, x1 + 1, y + BAR_HEIGHT + 1, 0xFF000000);
      g.fill(x0, y, x1, y + BAR_HEIGHT, 0xFF101A30);
      int fill = Math.round(CaptainView.kineticFraction(s) * BAR_WIDTH);
      boolean ready = CaptainView.kinetic(s) >= CaptainRules.EMPOWER_COST;
      g.fill(x0, y, x0 + fill, y + BAR_HEIGHT, KINETIC);
      g.fill(x0, y, x0 + fill, y + 1, ready ? 0xFFFFFFFF : KINETIC_HI);
      int mark = x0 + Math.round(CaptainRules.EMPOWER_COST / (float)CaptainRules.KINETIC_MAX * BAR_WIDTH);
      g.fill(mark, y - 1, mark + 1, y + BAR_HEIGHT + 1, ready ? 0xFFFFFFFF : RED);
      var font = Minecraft.getInstance().font;
      String charge = String.valueOf(CaptainView.kinetic(s));
      g.drawString(font, charge, x0 - font.width(charge) - 2, y - 2, KINETIC_HI, true);
      if (!CaptainView.shieldInHand(s)) {
         // the shield is away: a small blinking ring until it is back on the arm
         boolean on = (player.tickCount / 5) % 2 == 0;
         int cx = x1 - 4, cy = y - 9;
         int c = CaptainView.flag(s, CaptainFlags.CATCH_BONUS) ? KINETIC : (on ? RIM : RIM_DARK);
         g.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF000000);
         g.fill(cx - 2, cy - 2, cx + 3, cy + 3, c);
         g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF16223E);
         g.fill(cx, cy, cx + 1, cy + 1, 0xFFFFFFFF);
      }
      int team = CaptainView.teamLeft(s);
      if (team > 0) {
         int dy = y - 4;
         int w = Math.round(Math.min(1.0F, team / (float)CaptainRules.FIGHT_TICKS) * BAR_WIDTH);
         g.fill(x0 - 1, dy - 1, x1 + 1, dy + 3, 0xFF000000);
         g.fill(x0, dy, x0 + w, dy + 2, RED);
      }
   }

   private static void frame(GuiGraphics g, Bounds b) {
      int x0 = b.x(), y0 = b.y(), x1 = x0 + b.width(), y1 = y0 + b.height();
      g.fill(x0, y0, x1, y1, BACK);
      g.fill(x0, y0, x1, y0 + 1, RIM);
      g.fill(x0, y1 - 1, x1, y1, RIM);
      g.fill(x0, y0, x0 + 1, y1, RIM);
      g.fill(x1 - 1, y0, x1, y1, RIM);
      g.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, RED);
   }
}
