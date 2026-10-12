package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ParryPulseTest {
   private static final long MS = 1_000_000L;

   @Test
   void snapsInAndFadesOut() {
      ParryPulse pulse = new ParryPulse();
      Object player = new Object();
      assertEquals(0.0F, pulse.sample(player, false, 0L).weight());
      float in = pulse.sample(player, true, 100 * MS).weight();
      assertTrue(in > 0.95F, "in " + in);
      float out = pulse.sample(player, false, 150 * MS).weight();
      assertTrue(out < in && out > 0.3F, "out " + out);
      for (int i = 1; i <= 30; i++) {
         out = pulse.sample(player, false, (150 + i * 100) * MS).weight();
      }

      assertEquals(0.0F, out);
   }

   @Test
   void clipRestartsOnEveryRise() {
      ParryPulse pulse = new ParryPulse();
      Object player = new Object();
      pulse.sample(player, false, 0L);
      assertEquals(0.0, pulse.sample(player, true, 10 * MS).seconds());
      assertEquals(0.05, pulse.sample(player, true, 60 * MS).seconds(), 1.0E-9);
      pulse.sample(player, false, 100 * MS);
      assertEquals(0.0, pulse.sample(player, true, 120 * MS).seconds());
   }

   @Test
   void frameRateIndependent() {
      ParryPulse coarse = new ParryPulse();
      ParryPulse fine = new ParryPulse();
      Object a = new Object();
      Object b = new Object();
      coarse.sample(a, true, 0L);
      fine.sample(b, true, 0L);
      float c = coarse.sample(a, true, 60 * MS).weight();
      float f = 0.0F;
      for (int i = 1; i <= 6; i++) {
         f = fine.sample(b, true, i * 10 * MS).weight();
      }

      assertTrue(c > 0.5F && c < 1.0F, "c " + c);
      assertEquals(c, f, 1.0E-4F);
   }
}
