package dev.baranhan.viltrumitecore.hero.homelander;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EyeHeatTest {
   private static final float EPS = 1.0E-3F;

   private static EyeHeat run(EyeHeat heat, int ticks, boolean laser, boolean focus) {
      for (int i = 0; i < ticks; i++) {
         heat.tick(laser, focus);
      }
      return heat;
   }

   @Test
   void laserFillsIn100Ticks() {
      EyeHeat heat = run(new EyeHeat(), 99, true, false);
      assertEquals(99.0F, heat.heat(), EPS);
      assertFalse(heat.locked());
      heat.tick(true, false);
      assertEquals(100.0F, heat.heat(), EPS);
      assertTrue(heat.locked());
      assertTrue(heat.justOverheated());
   }

   @Test
   void focusFillsIn400Ticks() {
      EyeHeat heat = run(new EyeHeat(), 399, false, true);
      assertFalse(heat.locked());
      heat.tick(false, true);
      assertTrue(heat.locked());
   }

   @Test
   void sourcesAdd() {
      EyeHeat heat = run(new EyeHeat(), 1, true, true);
      assertEquals(1.25F, heat.heat(), EPS);
   }

   @Test
   void coolingWaits20Ticks() {
      EyeHeat heat = run(new EyeHeat(), 50, true, false);
      run(heat, 20, false, false);
      assertEquals(50.0F, heat.heat(), EPS);
      heat.tick(false, false);
      assertEquals(50.0F - 100.0F / 120.0F, heat.heat(), EPS);
   }

   @Test
   void coolsFrom100In120Ticks() {
      EyeHeat heat = run(new EyeHeat(), 100, true, false);
      run(heat, 20 + 119, false, false);
      assertTrue(heat.heat() > 0.0F);
      heat.tick(false, false);
      assertEquals(0.0F, heat.heat(), EPS);
      run(heat, 5, false, false);
      assertEquals(0.0F, heat.heat(), 0.0F);
   }

   @Test
   void overheatStopsBothSources() {
      EyeHeat heat = new EyeHeat();
      while (!heat.locked()) {
         heat.tick(true, true);
      }
      assertFalse(heat.sourcesAllowed());
      float atLock = heat.heat();
      heat.tick(true, true);
      assertTrue(heat.heat() <= atLock);
      assertFalse(heat.justOverheated());
   }

   @Test
   void lockLasts60Ticks() {
      EyeHeat heat = run(new EyeHeat(), 100, true, false);
      run(heat, 59, false, false);
      assertTrue(heat.locked());
      heat.tick(false, false);
      assertFalse(heat.locked());
      assertTrue(heat.sourcesAllowed());
   }

   @Test
   void resetClears() {
      EyeHeat heat = run(new EyeHeat(), 100, true, false);
      heat.reset();
      assertEquals(0.0F, heat.heat(), 0.0F);
      assertFalse(heat.locked());
   }

   @Test
   void snapshotScale() {
      EyeHeat heat = run(new EyeHeat(), 40, true, false);
      assertEquals(400, heat.snapshotValue());
   }

   @Test
   void coolingScaled() {
      EyeHeat full = run(new EyeHeat(), 50, true, false);
      EyeHeat half = run(new EyeHeat(), 50, true, false);
      for (int i = 0; i < 20 + 12; i++) {
         full.tick(false, false, 1.0F);
         half.tick(false, false, 0.5F);
      }

      assertEquals(50.0F - 12 * 100.0F / 120.0F, full.heat(), EPS);
      assertEquals(50.0F - 6 * 100.0F / 120.0F, half.heat(), EPS);
      // Heating is never scaled.
      EyeHeat hot = new EyeHeat();
      hot.tick(true, false, 0.5F);
      assertEquals(run(new EyeHeat(), 1, true, false).heat(), hot.heat(), EPS);
   }
}
