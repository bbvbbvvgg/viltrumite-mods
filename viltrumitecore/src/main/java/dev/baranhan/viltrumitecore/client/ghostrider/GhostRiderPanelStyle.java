package dev.baranhan.viltrumitecore.client.ghostrider;

import dev.baranhan.viltrumitecore.client.hero.PanelStyle;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCyclePhysics;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Spirit of Vengeance panel: black leather frames with a hellfire rim, a row of chain links along the top edge and an
 * ember seam, the flaming skull emblem. While he rides the Hell Cycle the dashboard sits left of the ability bar: the
 * speed in km/h over the rev bar (deep red to white-hot, redline mark), the gear under it and the boost timer.
 */
public final class GhostRiderPanelStyle implements PanelStyle {
   public static final GhostRiderPanelStyle INSTANCE = new GhostRiderPanelStyle();
   private static final ResourceLocation EMBLEM = GhostRiderAssets.rl("textures/gui/ghost_rider/emblem.png");
   static final int BACK = 0xD80C0908;
   static final int RIM = 0xFFFF7A1A;
   static final int RIM_DARK = 0xFF4A1C0C;
   static final int EMBER = 0xFFFFC04A;
   static final int CHAIN = 0xFF9AA0AA;
   static final int SLOT = 0xFF1E1714;
   static final int SLOT_EMPTY = 0x901E1714;
   static final int COOLDOWN = 0xB0080404;
   public static final int BAR_WIDTH = 48;
   public static final int BAR_HEIGHT = 4;
   /** Rev bar position of the redline mark (shift point of the gearbox). */
   public static final float REDLINE = 0.85F;

   private GhostRiderPanelStyle() {
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

   /** Pure: km/h of a ground speed in blocks per tick (20 ticks x 3.6). */
   public static int kmh(double blocksPerTick) {
      return (int)Math.round(Math.abs(blocksPerTick) * 72.0);
   }

   /** Pure: colour of the rev bar at 0-1 (deep red -> orange -> yellow -> white-hot past the redline), ARGB. */
   public static int rpmColor(float rpm) {
      float t = Math.max(0.0F, Math.min(1.0F, rpm));
      int r = (int)(140 + 115 * Math.min(1.0F, t * 1.6F));
      int gg = (int)(24 + 200 * t * t);
      int b = (int)(12 + Math.max(0.0F, t - REDLINE) / (1.0F - REDLINE) * 170);
      return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, gg) << 8) | Math.min(255, b);
   }

   /** Pure: width in px of a timer bar ({@code left} of {@code total} ticks) on a bar of {@code width} px. */
   public static int timerWidth(int left, int total, int width) {
      if (left <= 0 || total <= 0) {
         return 0;
      }
      return Math.round(Math.min(1.0F, left / (float)total) * width);
   }

   @Override
   public void drawAttachments(GuiGraphics g, Bounds abilityBar, Player player, HeroPublicSnapshot s) {
      if (!(player.getVehicle() instanceof HellCycleEntity bike)) {
         return;
      }
      HellCyclePhysics.State st = bike.state;
      double speed = Math.sqrt(st.velX * st.velX + st.velZ * st.velZ);
      int x1 = abilityBar.x() - 3;
      int x0 = x1 - BAR_WIDTH;
      int y = abilityBar.y() + 2;
      float rpm = Math.max(0.0F, Math.min(1.0F, bike.engineRpm()));
      boolean blink = (player.tickCount / 3) % 2 == 0;
      g.fill(x0 - 1, y - 1, x1 + 1, y + BAR_HEIGHT + 1, 0xFF000000);
      g.fill(x0, y, x1, y + BAR_HEIGHT, 0xFF150C0A);
      int fill = Math.round(rpm * BAR_WIDTH);
      for (int i = 0; i < fill; i++) {
         g.fill(x0 + i, y, x0 + i + 1, y + BAR_HEIGHT, rpmColor((i + 0.5F) / BAR_WIDTH));
      }
      if (fill > 0) {
         g.fill(x0, y, x0 + fill, y + 1, 0x50FFFFFF);
      }
      int red = x0 + Math.round(REDLINE * BAR_WIDTH);
      g.fill(red, y - 1, red + 1, y + BAR_HEIGHT + 1, rpm >= REDLINE && blink ? 0xFFFFF0C0 : 0xFFB02010);
      var font = Minecraft.getInstance().font;
      Component speedText = Component.translatable("hud.viltrumitecore.ghost_rider.speed", kmh(speed));
      g.drawString(font, speedText, x1 - font.width(speedText), y - 11, bike.dashing() ? EMBER : 0xFFFFE0B0, true);
      Component gear = Component.translatable("hud.viltrumitecore.ghost_rider.gear", Math.max(1, st.gear));
      g.drawString(font, gear, x1 - font.width(gear), y + BAR_HEIGHT + 3, RIM, true);
      int boost = timerWidth(bike.dashTicksLeft(), HellCycleRules.BOOST_TICKS, BAR_WIDTH);
      if (boost > 0) {
         int by = y + BAR_HEIGHT + 13;
         g.fill(x0 - 1, by - 1, x1 + 1, by + 2, 0xFF000000);
         g.fill(x1 - boost, by, x1, by + 1, blink ? 0xFFFFF0C0 : RIM);
         Component label = Component.translatable("hud.viltrumitecore.ghost_rider.boost");
         g.drawString(font, label, x1 - font.width(label), by + 3, EMBER, true);
      }
   }

   private static void frame(GuiGraphics g, Bounds b) {
      int x0 = b.x(), y0 = b.y(), x1 = x0 + b.width(), y1 = y0 + b.height();
      g.fill(x0, y0, x1, y1, BACK);
      g.fill(x0, y0, x1, y0 + 1, RIM);
      g.fill(x0, y1 - 1, x1, y1, RIM_DARK);
      g.fill(x0, y0, x0 + 1, y1, RIM);
      g.fill(x1 - 1, y0, x1, y1, RIM_DARK);
      g.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, EMBER);
      // chain links along the top edge: a grey pixel pair every 4 px, offset on alternate links
      for (int x = x0 + 2, i = 0; x + 2 <= x1 - 1; x += 4, i++) {
         int cy = y0 + 1 + (i & 1);
         g.fill(x, cy, x + 2, cy + 1, CHAIN);
      }
   }
}
