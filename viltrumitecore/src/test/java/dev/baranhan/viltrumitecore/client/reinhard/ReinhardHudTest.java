package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.hero.PanelStyle.Bounds;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import org.junit.jupiter.api.Test;

class ReinhardHudTest {
   /** Ability bar as ViltrumiteInGameHudMixin places it on a 427x240 screen. */
   private static final Bounds BAR = new Bounds(400, 93, 22, 120);
   /** Page indicator of the mixin: (bar.x + 5, bar.y - 14), 13x12. */
   private static final Bounds INDICATOR = new Bounds(BAR.x() + 5, BAR.y() - 14, 13, 12);

   private static boolean overlap(Bounds a, Bounds b) {
      return a.x() < b.x() + b.width() && b.x() < a.x() + a.width() && a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
   }

   @Test
   void layoutNextToBarOnScreen() {
      ReinhardHud.Layout layout = ReinhardHud.layout(BAR);
      assertEquals(3, layout.icons().length);
      for (Bounds icon : layout.icons()) {
         assertEquals(12, icon.width());
         assertEquals(12, icon.height());
         // The bar sits 5 px from the right screen edge: everything stays left of it, 2 px gap.
         assertTrue(icon.x() + icon.width() <= BAR.x() - 2, icon.toString());
         assertFalse(overlap(icon, BAR));
         assertFalse(overlap(icon, INDICATOR));
      }
      assertEquals(BAR.x() - 2, layout.icons()[2].x() + 12);
      assertEquals(layout.icons()[0].y(), layout.icons()[2].y(), "top-aligned row");
   }

   @Test
   void iconsDoNotOverlap() {
      Bounds[] icons = ReinhardHud.layout(BAR).icons();
      for (int i = 0; i < icons.length; i++) {
         for (int j = i + 1; j < icons.length; j++) {
            assertFalse(overlap(icons[i], icons[j]), i + "/" + j);
         }
      }
      assertEquals(2, icons[1].x() - (icons[0].x() + 12), "2 px gap");
   }

   @Test
   void barUnderIcons() {
      ReinhardHud.Layout layout = ReinhardHud.layout(BAR);
      Bounds reid = layout.reidBar();
      assertEquals(40, reid.width());
      assertEquals(3, reid.height());
      assertEquals(layout.icons()[0].x(), reid.x());
      assertTrue(reid.y() >= layout.icons()[0].y() + 12);
      assertFalse(overlap(reid, BAR));
      assertFalse(overlap(reid, INDICATOR));
   }

   @Test
   void reidBarHiddenAtZero() {
      assertFalse(ReinhardHud.showsReidBar(0));
      assertTrue(ReinhardHud.showsReidBar(1));
      assertEquals(0, ReinhardHud.reidFillWidth(0, 40));
      assertEquals(20, ReinhardHud.reidFillWidth(ReinhardRules.REID_IDLE_TICKS / 2, 40));
      assertEquals(40, ReinhardHud.reidFillWidth(ReinhardRules.REID_IDLE_TICKS * 3, 40));
   }
}
