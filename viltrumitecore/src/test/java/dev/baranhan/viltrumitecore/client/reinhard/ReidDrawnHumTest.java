package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.client.reinhard.reid.ReidDrawnHum;
import org.junit.jupiter.api.Test;

class ReidDrawnHumTest {
   @Test
   void fadesInOverASecondAndOutFaster() {
      int level = 1, ticks = 0;
      while (level < ReidDrawnHum.FADE_IN) {
         level = ReidDrawnHum.fade(level, true);
         ticks++;
      }
      assertEquals(ReidDrawnHum.FADE_IN - 1, ticks);
      assertEquals(ReidDrawnHum.FADE_IN, ReidDrawnHum.fade(level, true), "holds at full");
      int out = 0;
      while (level > 0) {
         level = ReidDrawnHum.fade(level, false);
         out++;
      }
      assertTrue(out < ticks / 2, "sheathing silences it quickly");
      assertEquals(0, ReidDrawnHum.fade(0, false));
   }

   @Test
   void quietAndBounded() {
      assertEquals(0.0F, ReidDrawnHum.volume(0));
      assertEquals(ReidDrawnHum.VOLUME, ReidDrawnHum.volume(ReidDrawnHum.FADE_IN), 1.0E-6F);
      assertEquals(ReidDrawnHum.VOLUME, ReidDrawnHum.volume(99), 1.0E-6F);
      assertTrue(ReidDrawnHum.VOLUME <= 0.3F, "an ambient bed under the strikes");
      float last = -1;
      for (int i = 0; i <= ReidDrawnHum.FADE_IN; i++) {
         assertTrue(ReidDrawnHum.volume(i) > last);
         last = ReidDrawnHum.volume(i);
      }
   }
}
