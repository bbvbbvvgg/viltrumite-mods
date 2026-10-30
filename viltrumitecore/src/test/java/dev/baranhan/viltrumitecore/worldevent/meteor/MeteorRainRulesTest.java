package dev.baranhan.viltrumitecore.worldevent.meteor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class MeteorRainRulesTest {
   @Test
   void delaysStayInsideTheWindowAndVary() {
      RandomSource random = RandomSource.create(7L);
      int min = Integer.MAX_VALUE;
      int max = Integer.MIN_VALUE;
      for (int i = 0; i < 2000; i++) {
         int d = MeteorRainRules.nextDelay(random);
         assertTrue(d >= MeteorRainRules.MIN_DELAY_TICKS && d <= MeteorRainRules.MAX_DELAY_TICKS);
         min = Math.min(min, d);
         max = Math.max(max, d);
      }
      assertEquals(MeteorRainRules.MIN_DELAY_TICKS, min);
      assertEquals(MeteorRainRules.MAX_DELAY_TICKS, max);
   }

   @Test
   void burstNeverExceedsTheInFlightCap() {
      RandomSource random = RandomSource.create(11L);
      for (int inFlight = 0; inFlight <= MeteorRainRules.MAX_IN_FLIGHT + 2; inFlight++) {
         for (int i = 0; i < 200; i++) {
            int burst = MeteorRainRules.burstSize(random, inFlight);
            assertTrue(burst >= 0 && inFlight + burst <= Math.max(inFlight, MeteorRainRules.MAX_IN_FLIGHT));
            if (inFlight >= MeteorRainRules.MAX_IN_FLIGHT) {
               assertEquals(0, burst);
            } else {
               assertTrue(burst >= 1 && burst <= MeteorRainRules.MAX_BURST);
            }
         }
      }
   }

   @Test
   void landingPointsStayInTheRingAndSomeAreClose() {
      RandomSource random = RandomSource.create(3L);
      int near = 0;
      for (int i = 0; i < 4000; i++) {
         double[] o = MeteorRainRules.landingOffset(random);
         double r = Math.hypot(o[0], o[1]);
         assertTrue(r >= MeteorRainRules.MIN_RADIUS - 1.0E-9 && r <= MeteorRainRules.MAX_RADIUS + 1.0E-9);
         if (r <= MeteorRainRules.NEAR_RADIUS) {
            near++;
         }
      }
      assertTrue(near > 4000 * MeteorRainRules.NEAR_CHANCE * 0.8, "near hits: " + near);
   }
}
