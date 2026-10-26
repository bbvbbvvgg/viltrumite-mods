package dev.baranhan.viltrumitecore.client.reinhard.cut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.render.vfx.SkyTint;
import org.junit.jupiter.api.Test;

/** Item 11: the World Cut keeps its drama but never blacks the screen out or hides the carve. */
class CutAtmosphereTest {
   @Test
   void peakTintDimsTheSkyWithoutABlackout() {
      double[] sky = SkyTint.apply(1, 1, 1, CutAtmosphere.PEAK);
      for (double channel : sky) {
         assertTrue(channel >= .30, "sky keeps at least 30 % of its light: " + channel);
         assertTrue(channel <= .60, "still a dramatic dusk: " + channel);
      }
      assertTrue(sky[2] > sky[0], "cool blue cast");
      assertEquals(0F, CutAtmosphere.PEAK.rain(), 0F, "rain stays hushed during the cut");
   }

   @Test
   void fogKeepsTheTrenchInSight() {
      // 12 chunk render distance: vanilla terrain fog from 172 to 192 blocks.
      float near = CutAtmosphere.fogNear(172, 1), far = CutAtmosphere.fogFar(192, 1);
      assertTrue(near >= 12, "no fog right in front of the camera: " + near);
      assertTrue(far >= 128, "the first hundred blocks of the trench stay readable: " + far);
      assertTrue(far < 192, "far chunk updates are still softened");
      assertTrue(near < far);
   }

   @Test
   void fogNeverExceedsVanillaAndEasesWithTheWeight() {
      assertEquals(40, CutAtmosphere.fogNear(40, 0), 1E-4);
      assertEquals(64, CutAtmosphere.fogFar(64, 1), 1E-4, "a short render distance is not pushed out");
      assertEquals(8, CutAtmosphere.fogNear(8, 1), 1E-4, "under water the closer vanilla fog wins");
      float previous = Float.MAX_VALUE;
      for (int i = 0; i <= 10; i++) {
         float far = CutAtmosphere.fogFar(192, i / 10F);
         assertTrue(far <= previous, "monotonic");
         previous = far;
      }
      assertEquals(CutAtmosphere.fogFar(192, 1), CutAtmosphere.fogFar(192, 3), 1E-4, "weight is clamped");
   }
}
