package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Fix spec 2026-10-28 item 2 (suit-up): segments from the sky, docking at the plate locks, the final reactor flash. */
class HulkbusterSuitUpSceneTest {
   @Test
   void segmentsFallFromTheSkyAndDockBeforeTheirPlatesLock() {
      for (int drop : new int[]{HulkbusterLayer.DROP_TICKS_AIR, HulkbusterLayer.DROP_TICKS_GROUND}) {
         for (int part : HulkbusterSuitUp.SEGMENTS) {
            int arrive = HulkbusterSuitUp.segmentArrive(part, drop);
            int lock = drop + HulkbusterLayer.PART_LOCK[part];
            assertTrue(arrive < lock, "docked before its plates close");
            assertTrue(arrive - HulkbusterSuitUp.SEGMENT_FLY > 0, "dropped after the capsule arrived");
            assertEquals(-1.0, HulkbusterSuitUp.segmentDrop(part, arrive - HulkbusterSuitUp.SEGMENT_FLY - 0.01, drop), "not in the sky yet");
            assertEquals(HulkbusterSuitUp.SEGMENT_HEIGHT, HulkbusterSuitUp.segmentDrop(part, arrive - HulkbusterSuitUp.SEGMENT_FLY, drop), 1.0E-9);
            double last = Double.MAX_VALUE;
            for (double t = arrive - HulkbusterSuitUp.SEGMENT_FLY; t <= arrive + 2; t += 0.25) {
               double h = HulkbusterSuitUp.segmentDrop(part, t, drop);
               assertTrue(h <= last && h >= 0.0, "falls and brakes into place at " + t);
               last = h;
            }

            assertEquals(0.0, HulkbusterSuitUp.segmentDrop(part, arrive, drop));
         }

         assertEquals(0.0, HulkbusterSuitUp.segmentDrop(1, 0.0, drop), "the torso core comes in the capsule");
      }
   }

   @Test
   void segmentsArriveOneByOne() {
      int last = -1;
      for (int part : HulkbusterSuitUp.SEGMENTS) {
         int arrive = HulkbusterSuitUp.segmentArrive(part, HulkbusterLayer.DROP_TICKS_AIR);
         assertTrue(arrive > last);
         last = arrive;
      }
   }

   @Test
   void reactorWarmsThenFlashesAfterTheHelmet() {
      assertTrue(HulkbusterSuitUp.reactor(0) < 0.2F);
      float last = 0.0F;
      for (double t = 0.0; t <= HulkbusterLayer.ASSEMBLE_TICKS; t += 0.5) {
         float r = HulkbusterSuitUp.reactor(t);
         assertTrue(r >= last, "never dims during the suit-up at " + t);
         last = r;
      }

      assertTrue(HulkbusterSuitUp.REACTOR_FLASH > HulkbusterLayer.PART_LOCK[HulkbusterLayer.PARTS - 1], "after the helmet locks");
      assertEquals(1.0F, HulkbusterSuitUp.reactor(HulkbusterSuitUp.REACTOR_FLASH), 1.0E-6F);
      assertTrue(HulkbusterSuitUp.reactor(HulkbusterSuitUp.REACTOR_FLASH - 3) < 0.6F, "the flash is a jump, not a ramp");
   }
}
