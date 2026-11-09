package dev.baranhan.viltrumitecore.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DistanceLodTest {
   @Test
   void fullDetailNearFloorFarAndCriticalAlwaysFull() {
      for (PerfPriority p : PerfPriority.values()) {
         assertEquals(1.0, DistanceLod.factor(p, 10, 96, 320), 1.0E-9);
         assertEquals(p.lodFloor, DistanceLod.factor(p, 1000, 96, 320), 1.0E-9);
      }
      assertEquals(1.0, DistanceLod.factor(PerfPriority.CRITICAL, 5000, 96, 320), 1.0E-9);
      assertEquals(0.0, DistanceLod.factor(PerfPriority.AMBIENT, 320, 96, 320), 1.0E-9);
   }

   @Test
   void fallsOffMonotonicallyAndSquaredFormAgrees() {
      double prev = 1.0;
      for (int d = 0; d <= 400; d += 8) {
         double f = DistanceLod.factor(PerfPriority.NORMAL, d, 96, 320);
         assertTrue(f <= prev + 1.0E-12);
         assertEquals(f, DistanceLod.factorSq(PerfPriority.NORMAL, (double)d * d, 96, 320), 1.0E-9);
         prev = f;
      }
   }
}
