package dev.baranhan.viltrumitecore.client.sinclair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpringTest {
   @Test
   void settlesOnTargetIndependentOfFrameRate() {
      Spring fast = new Spring(90.0F, 9.0F, 80.0F);
      Spring slow = new Spring(90.0F, 9.0F, 80.0F);
      for (int i = 0; i < 240; i++) {
         fast.step(30.0F, 1.0F / 240.0F);
      }

      for (int i = 0; i < 30; i++) {
         slow.step(30.0F, 1.0F / 30.0F);
      }

      assertEquals(fast.value, slow.value, 0.5F);
      for (int i = 0; i < 600; i++) {
         fast.step(30.0F, 1.0F / 60.0F);
      }

      assertEquals(30.0F, fast.value, 0.05F);
   }

   @Test
   void underdampedClothOvershootsOnce() {
      Spring s = new Spring(90.0F, 9.0F, 80.0F);
      float max = 0.0F;
      for (int i = 0; i < 120; i++) {
         max = Math.max(max, s.step(20.0F, 1.0F / 60.0F));
      }

      assertTrue(max > 20.5F && max < 30.0F, "overshoot " + max);
   }

   @Test
   void clampsAndSurvivesLongGaps() {
      Spring s = new Spring(90.0F, 9.0F, 25.0F);
      s.kick(10000.0F);
      s.step(0.0F, 0.05F);
      assertTrue(Math.abs(s.value) <= 25.0F);
      s.step(12.0F, 5.0F);
      assertEquals(12.0F, s.value, 1.0E-6F);
      assertEquals(0.0F, s.velocity, 1.0E-6F);
      assertEquals(12.0F, s.step(40.0F, Float.NaN), 1.0E-6F);
   }
}
