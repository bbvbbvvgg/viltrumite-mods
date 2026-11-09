package dev.baranhan.viltrumitecore.client.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.perf.PerfPriority;
import org.junit.jupiter.api.Test;

class ParticleLodTest {
   private static double sq(double d) {
      return d * d;
   }

   @Test
   void highWithoutLoadKeepsEveryParticleAtAnyDistance() {
      for (PerfPriority p : PerfPriority.values()) {
         for (double d : new double[]{0, 50, 200, 1000}) {
            assertEquals(1.0, ParticleLod.keep(EffectQuality.HIGH, p, sq(d), 0.0), 1.0E-12, p + " at " + d);
         }
      }

      assertEquals(8000, ParticleLod.liveLimit(8000, EffectQuality.HIGH, 0.0));
      assertEquals(1500, ParticleLod.rateLimit(1500, EffectQuality.HIGH, 0.0));
   }

   @Test
   void loadThinsFarAndLowPriorityEffectsFirst() {
      double nearHigh = ParticleLod.keep(EffectQuality.HIGH, PerfPriority.HIGH, sq(10), 1.0);
      double farNormal = ParticleLod.keep(EffectQuality.HIGH, PerfPriority.NORMAL, sq(400), 1.0);
      double farLow = ParticleLod.keep(EffectQuality.HIGH, PerfPriority.LOW, sq(400), 1.0);
      assertEquals(0.6, nearHigh, 1.0E-9);
      assertTrue(farNormal < nearHigh && farLow < farNormal, nearHigh + " " + farNormal + " " + farLow);
      assertEquals(1.0, ParticleLod.keep(EffectQuality.HIGH, PerfPriority.CRITICAL, sq(5000), 1.0));
      assertTrue(ParticleLod.liveLimit(8000, EffectQuality.HIGH, 1.0) < 8000);
   }

   @Test
   void lowQualityAlwaysThinsAndUltraAllowsMore() {
      assertEquals(0.35, ParticleLod.keep(EffectQuality.LOW, PerfPriority.NORMAL, sq(10), 0.0), 1.0E-9);
      assertTrue(ParticleLod.keep(EffectQuality.LOW, PerfPriority.NORMAL, sq(200), 0.0) < 0.35);
      assertEquals(12000, ParticleLod.liveLimit(8000, EffectQuality.ULTRA, 0.0));
      assertEquals(2800, ParticleLod.liveLimit(8000, EffectQuality.LOW, 0.0));
   }

   @Test
   void keepNeverGrowsWithDistanceOrLoad() {
      for (EffectQuality q : EffectQuality.values()) {
         double prev = 2.0;
         for (int d = 0; d <= 600; d += 20) {
            double k = ParticleLod.keep(q, PerfPriority.NORMAL, sq(d), 0.5);
            assertTrue(k <= prev + 1.0E-12, q + " at " + d);
            prev = k;
         }

         prev = 2.0;
         for (int i = 0; i <= 10; i++) {
            double k = ParticleLod.keep(q, PerfPriority.LOW, sq(150), i / 10.0);
            assertTrue(k <= prev + 1.0E-12, q + " at load " + i);
            prev = k;
         }
      }
   }
}
