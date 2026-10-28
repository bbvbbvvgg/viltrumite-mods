package dev.baranhan.viltrumitecore.client.hero.speech;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Pixel art of the subtitle panels (reference look): a dark outer edge, a
 * 1 px light-grey line with cut corners, a dark translucent fill with a soft
 * inner shadow and highlight, and a small riveted plate in each inner corner.
 * Coordinates are relative to the current pose (the caller snaps it to pixels).
 */
public final class SubtitleRenderer {
   public static final int NAME_COLOR = 0xA6AAAF;
   public static final int TEXT_COLOR = 0xF2F2F2;
   private static final int OUTER = 0x0C0D10;
   private static final int LINE = 0x8E9398;
   private static final int LINE_SHADE = 0x757A7F;
   private static final int FILL_TOP = 0x25272B;
   private static final int FILL_BOTTOM = 0x18191C;
   private static final int BRIGHT = 0xD3D6D9;
   private static final int PLATE = 0x63676C;
   private static final int PLATE_EDGE = 0x45484C;
   /** Corner plate, top-left orientation: B bright rivet line, M plate, m plate edge. */
   private static final String[] ORNAMENT = {
      "BMMMMm",
      "MBMMm.",
      "MMBm..",
      "MMm...",
      "Mm....",
      "m....."
   };

   private SubtitleRenderer() {
   }

   public static void panel(GuiGraphics g, int w, int h, float alpha) {
      int outer = argb(0.82F * alpha, OUTER);
      g.fill(1, 0, w - 1, 1, outer);
      g.fill(1, h - 1, w - 1, h, outer);
      g.fill(0, 1, 1, h - 1, outer);
      g.fill(w - 1, 1, w, h - 1, outer);
      g.fillGradient(2, 2, w - 2, h - 2, argb(0.74F * alpha, FILL_TOP), argb(0.78F * alpha, FILL_BOTTOM));
      int line = argb(0.92F * alpha, LINE);
      int shade = argb(0.92F * alpha, LINE_SHADE);
      g.fill(2, 1, w - 2, 2, line);
      g.fill(1, 2, 2, h - 2, line);
      g.fill(2, h - 2, w - 2, h - 1, shade);
      g.fill(w - 2, 2, w - 1, h - 2, shade);
      // Cut corners: the light line turns through the dark edge colour.
      g.fill(1, 1, 2, 2, outer);
      g.fill(w - 2, 1, w - 1, 2, outer);
      g.fill(1, h - 2, 2, h - 1, outer);
      g.fill(w - 2, h - 2, w - 1, h - 1, outer);
      // Inner shadow under the top/left line, a faint highlight above the bottom/right one.
      g.fill(2, 2, w - 2, 3, argb(0.30F * alpha, 0));
      g.fill(2, 3, 3, h - 2, argb(0.30F * alpha, 0));
      g.fill(3, 3, w - 2, 4, argb(0.12F * alpha, 0));
      g.fill(3, 4, 4, h - 2, argb(0.12F * alpha, 0));
      g.fill(3, h - 3, w - 2, h - 2, argb(0.05F * alpha, 0xFFFFFF));
      g.fill(w - 3, 3, w - 2, h - 3, argb(0.04F * alpha, 0xFFFFFF));
      ornament(g, 2, 2, false, false, alpha);
      ornament(g, w - 2 - ORNAMENT.length, 2, true, false, alpha);
      ornament(g, 2, h - 2 - ORNAMENT.length, false, true, alpha);
      ornament(g, w - 2 - ORNAMENT.length, h - 2 - ORNAMENT.length, true, true, alpha);
   }

   private static void ornament(GuiGraphics g, int x, int y, boolean flipX, boolean flipY, float alpha) {
      int size = ORNAMENT.length;
      for (int row = 0; row < size; row++) {
         String pattern = ORNAMENT[row];
         for (int col = 0; col < size; col++) {
            int color = switch (pattern.charAt(col)) {
               case 'B' -> argb(0.95F * alpha, BRIGHT);
               case 'M' -> argb(0.88F * alpha, PLATE);
               case 'm' -> argb(0.80F * alpha, PLATE_EDGE);
               default -> 0;
            };
            if (color != 0) {
               int px = x + (flipX ? size - 1 - col : col);
               int py = y + (flipY ? size - 1 - row : row);
               g.fill(px, py, px + 1, py + 1, color);
            }
         }
      }
   }

   /** Name and the page's lines; nothing when too transparent (vanilla font draws alpha < 4 opaque). */
   public static void text(GuiGraphics g, Font font, SubtitleLayout.Result layout, SubtitleLayout.Page page, float alpha, float textAlpha, float drift) {
      int nameColor = argb(alpha, NAME_COLOR);
      if ((nameColor >>> 24) >= 4) {
         g.drawString(font, layout.name(), SubtitleLayout.PAD_LEFT, layout.nameY(), nameColor, true);
      }

      int bodyColor = argb(alpha * textAlpha, TEXT_COLOR);
      if ((bodyColor >>> 24) < 4) {
         return;
      }

      g.pose().pushPose();
      g.pose().translate(0.0F, drift, 0.0F);
      int y = layout.bodyY();
      for (String line : page.lines()) {
         g.drawString(font, line, SubtitleLayout.PAD_LEFT, y, bodyColor, true);
         y += SubtitleLayout.LINE_PITCH;
      }

      g.pose().popPose();
   }

   public static int argb(float alpha, int rgb) {
      int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
      return a << 24 | rgb & 0xFFFFFF;
   }
}
