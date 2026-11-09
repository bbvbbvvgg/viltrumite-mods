package dev.baranhan.viltrumitecore.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DegradeTest {
   @Test
   void rampAndScaleEndpoints() {
      assertEquals(0.0, Degrade.ramp(30, 35, 50), 1.0E-9);
      assertEquals(0.5, Degrade.ramp(42.5, 35, 50), 1.0E-9);
      assertEquals(1.0, Degrade.ramp(80, 35, 50), 1.0E-9);
      assertEquals(1.0, Degrade.scale(0.0, 0.2), 1.0E-9);
      assertEquals(0.2, Degrade.scale(1.0, 0.2), 1.0E-9);
      assertEquals(0.2, Degrade.scale(7.0, 0.2), 1.0E-9);
   }

   @Test
   void stochasticThinningKeepsTheMeanAndIsDeterministic() {
      Degrade.Thinner a = new Degrade.Thinner(42L);
      Degrade.Thinner b = new Degrade.Thinner(42L);
      long kept = 0;
      for (int i = 0; i < 20000; i++) {
         int ka = a.keep(10, 0.37);
         assertEquals(ka, b.keep(10, 0.37));
         assertTrue(ka == 3 || ka == 4);
         kept += ka;
      }
      assertEquals(3.7, kept / 20000.0, 0.05);
      assertEquals(10, a.keep(10, 1.0));
      assertEquals(0, a.keep(10, 0.0));
      assertEquals(0, Degrade.stochasticRound(Double.NaN, 0.1));
   }
}
