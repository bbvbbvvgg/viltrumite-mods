package dev.baranhan.viltrumitecore.client.render.vfx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Playtest: Iron Man ESP brackets hung in empty space / did not cover the entity. */
class ScreenProjectorTest {
   @Test
   void onlyTheMainViewFeedsTheGuiProjection() {
      assertTrue(ScreenProjector.captures(false, true));
      assertFalse(ScreenProjector.captures(true, true), "shader shadow pass");
      assertFalse(ScreenProjector.captures(false, false), "another camera / off-screen render");
   }

   @Test
   void boundsCoverEveryCorner() {
      float[] r = ScreenProjector.bounds(new ScreenProjector.Point(10, 40, false), new ScreenProjector.Point(30, 5, false),
         new ScreenProjector.Point(20, 25, false));
      assertArrayEquals(new float[]{10, 5, 30, 40}, r);
   }

   @Test
   void aCornerBehindTheCameraFallsBackToTheEdgeArrow() {
      assertNull(ScreenProjector.bounds(new ScreenProjector.Point(10, 40, false), new ScreenProjector.Point(30, 5, true)));
      assertNull(ScreenProjector.bounds());
   }
}
