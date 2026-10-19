package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure checks of the belt carry (no client classes): the sheathed sword is big enough,
 * hangs off the belt without reaching the ground, and the draw travel clears the sheath.
 */
class ReinhardSwordGeometryTest {
   /** The assembly is drawn 14 px up before the flip, so the mouth sits 3.3 px over the guard. */
   private static final float MOUTH_OVER_GUARD_PX = 3.3F;

   @Test
   void swordIsNearlyTwiceTheOldSize() {
      assertEquals(0.6F, ReinhardSword.SWORD_SCALE, 1.0E-6);
      assertTrue(ReinhardSword.SWORD_SCALE >= 0.55F, "the sword must read as a two-hander, not a dagger");
   }

   @Test
   void sheathedTipStaysAboveTheFeet() {
      assertTrue(ReinhardSword.TIP_HEIGHT_PX > 2.0F, "sheathed tip over the feet: " + ReinhardSword.TIP_HEIGHT_PX);
      assertTrue(ReinhardSword.TIP_HEIGHT_PX < ReinhardSword.HIP_Y, "the sheath hangs below the belt");
   }

   @Test
   void beltCarryIsNearlyAlongTheThigh() {
      // 20-30 degrees above horizontal: the scabbard lies along the thigh, not upright.
      float fromVertical = ReinhardSword.TILT_X;
      assertTrue(fromVertical >= 60.0F && fromVertical <= 78.0F, "tilt from vertical: " + fromVertical);
   }

   @Test
   void drawTravelClearsTheSheath() {
      // The tip must rise from the sheath's closed end past the mouth: the whole blade
      // length minus the little the guard already sits above the mouth.
      float needed = ReinhardSword.BLADE_TIP_PX - MOUTH_OVER_GUARD_PX;
      assertTrue(ReinhardSword.DRAW_OUT_PX >= needed, "draw travel " + ReinhardSword.DRAW_OUT_PX + " must clear " + needed);
      assertTrue(ReinhardSword.DRAW_OUT_PX <= ReinhardSword.BLADE_TIP_PX + 10.0F, "no needless overshoot");
   }
}
