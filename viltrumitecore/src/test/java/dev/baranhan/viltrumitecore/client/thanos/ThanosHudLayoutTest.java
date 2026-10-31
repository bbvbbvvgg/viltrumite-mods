package dev.baranhan.viltrumitecore.client.thanos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.hero.PanelStyle;
import dev.baranhan.viltrumitecore.client.hero.PanelStyle.Bounds;
import dev.baranhan.viltrumitecore.client.reinhard.ReinhardHud;
import dev.baranhan.viltrumitecore.client.reinhard.ReinhardPanelStyle;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Thanos timer panel and warning strip never cover a hero's own HUD at the GUI sizes players get
 * (the automatic GUI scale keeps the GUI at least 320x240: 1280x720, 1920x1200, 2560x1440 and 3840x2160 land on
 * a height of 240), and on a roomy screen nothing moves.
 */
class ThanosHudLayoutTest {
   static final int PANEL_W = 118, PANEL_H = 40;
   /** One boss bar: the panel's top (bossBottom 31 + 2). */
   static final int PANEL_Y = 33;
   static final int[][] SIZES = {{320, 240}, {341, 256}, {384, 240}, {426, 240}, {455, 256}, {480, 270}, {533, 300}, {640, 360}, {320, 256}, {427, 240}};

   record Box(int x, int y, int w, int h) {
      boolean hits(Box o) {
         return this.x < o.x + o.w && o.x < this.x + this.w && this.y < o.y + o.h && o.y < this.y + this.h;
      }
   }

   static Bounds bar(int width, int height) {
      return new Bounds(width - ThanosHudLayout.BAR_FROM_RIGHT, height - ThanosHudLayout.BAR_FROM_BOTTOM, 22, 120);
   }

   /** What the hero HUD draws around the ability bar (ViltrumiteInGameHudMixin, the panel styles, ReinhardHud). */
   static List<Box> heroParts(int width, int height, boolean reinhard) {
      Bounds b = bar(width, height);
      List<Box> parts = new ArrayList<>();
      parts.add(new Box(b.x(), b.y(), b.width(), b.height()));
      // page badge (y - 14, 12 high) and the 16 px hero emblem centred over the bar at y - 32
      parts.add(new Box(b.x() + 5, b.y() - 14, 13, 12));
      parts.add(new Box(b.x() + (b.width() - 16) / 2, b.y() - 32, 16, 16));
      // gauge 44-46 wide, 3 px left of the bar, its number left of it (up to 4 digits), 2 px above its top
      parts.add(new Box(b.x() - 3 - 46 - 2 - 24, b.y() - 2, 24 + 2 + 46 + 1, 9));
      // Regulus' «Reply» label, right-aligned 3 px left of the bar, 22 px above its top (longest: «Antwort»)
      parts.add(new Box(b.x() - 3 - 45, b.y() - 22, 45, 9));
      if (reinhard) {
         ReinhardHud.Layout l = ReinhardHud.layout(b);
         for (Bounds i : l.icons()) {
            parts.add(new Box(i.x(), i.y(), i.width(), i.height()));
         }
         parts.add(new Box(l.reidBar().x(), l.reidBar().y(), l.reidBar().width(), l.reidBar().height()));
         // the mouse hint: 0.65 scale, up to ~190 px of text, 10 px above the icon row
         int hy = Math.max(2, l.icons()[0].y() - 10);
         assertEquals(b.y() - ReinhardHud.HINT_RISE, hy, "the hint rise the style reports");
         parts.add(new Box(Math.max(2, b.x() - ReinhardHud.HINT_LEFT), hy, Math.round(190 * 0.65F), 7));
      }

      return parts;
   }

   static PanelStyle style(boolean reinhard) {
      return reinhard ? ReinhardPanelStyle.INSTANCE : PanelStyle.DEFAULT;
   }

   static int maxRight(int width, int height, int bottom, boolean hero, boolean reinhard) {
      return ThanosHudLayout.maxRight(width, height, bottom, hero, style(reinhard).attachmentsLeft(), style(reinhard).attachmentsUp());
   }

   static Box panel(int width, int height, boolean hero, boolean reinhard) {
      int right = maxRight(width, height, PANEL_Y + PANEL_H, hero, reinhard);
      return new Box(ThanosHudLayout.panelX(width, PANEL_W, right), PANEL_Y, PANEL_W, PANEL_H);
   }

   static Box strip(int width, int height, int want, int lines, boolean hero, boolean reinhard) {
      Box p = panel(width, height, hero, reinhard);
      int y = p.y + p.h + 3;
      int right = maxRight(width, height, y + ThanosHudLayout.maxStripHeight(), hero, reinhard);
      int w = ThanosHudLayout.stripWidth(want, width, right);
      return new Box(ThanosHudLayout.stripX(p.x + p.w / 2, w, right), y, w, ThanosHudLayout.stripHeight(lines));
   }

   @Test
   void panelAndStripNeverCoverTheHeroHud() {
      for (int[] s : SIZES) {
         for (boolean reinhard : new boolean[]{false, true}) {
            List<Box> parts = heroParts(s[0], s[1], reinhard);
            Box p = panel(s[0], s[1], true, reinhard);
            for (int want : new int[]{90, 180, 260, 400}) {
               for (int lines = 1; lines <= ThanosHudLayout.MAX_HINT_LINES; lines++) {
                  Box st = strip(s[0], s[1], want, lines, true, reinhard);
                  for (Box part : parts) {
                     assertFalse(p.hits(part), "panel over the hero HUD at " + s[0] + "x" + s[1] + " reinhard=" + reinhard + ": " + p + " / " + part);
                     assertFalse(st.hits(part), "strip over the hero HUD at " + s[0] + "x" + s[1] + " reinhard=" + reinhard + ": " + st + " / " + part);
                  }

                  assertTrue(st.x >= ThanosHudLayout.MARGIN && st.x + st.w <= s[0] - ThanosHudLayout.MARGIN, "strip on screen " + st);
                  assertTrue(st.w >= ThanosHudLayout.MIN_STRIP, "strip keeps a readable width " + st);
               }
            }

            assertTrue(p.x >= ThanosHudLayout.MARGIN && p.x + p.w <= s[0], "panel on screen " + p);
         }
      }
   }

   @Test
   void theOldOverlapIsGone() {
      // 640x480 at GUI scale 2, Reinhard: the centred panel's stone row crossed the mouse hint
      Box centred = new Box((320 - PANEL_W) / 2, PANEL_Y, PANEL_W, PANEL_H);
      Box hint = heroParts(320, 240, true).get(heroParts(320, 240, true).size() - 1);
      assertTrue(centred.hits(hint));
      assertFalse(panel(320, 240, true, true).hits(hint));
   }

   @Test
   void nothingMovesWithoutAHeroHudOrOnARoomyScreen() {
      for (int[] s : SIZES) {
         assertEquals((s[0] - PANEL_W) / 2, panel(s[0], s[1], false, false).x, "no hero HUD: centred at " + s[0] + "x" + s[1]);
      }

      for (boolean reinhard : new boolean[]{false, true}) {
         assertEquals((480 - PANEL_W) / 2, panel(480, 270, true, reinhard).x, "1920x1080 (GUI 480x270): centred");
         assertEquals((640 - PANEL_W) / 2, panel(640, 360, true, reinhard).x, "GUI 640x360: centred");
      }

      // a strip that fits keeps the old width and stays centred under the panel
      Box st = strip(640, 360, 200, 1, true, true);
      assertEquals(200, st.w);
      assertEquals(320 - 100, st.x);
      assertEquals(22, st.h);
   }

   @Test
   void stripWidthAndHeight() {
      assertEquals(22, ThanosHudLayout.stripHeight(1));
      assertEquals(29, ThanosHudLayout.stripHeight(2));
      assertEquals(36, ThanosHudLayout.stripHeight(3));
      assertEquals(36, ThanosHudLayout.stripHeight(7));
      assertEquals(300, ThanosHudLayout.stripWidth(400, 320, 317), "screen margins as before (width - 20)");
      assertEquals(ThanosHudLayout.MIN_STRIP, ThanosHudLayout.stripWidth(400, 320, 20));
   }
}
