package dev.baranhan.viltrumitecore.client.dio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TimeStopClockTest {
   @Test
   void fadeSettlesOnTargetWithinHalfASecond() {
      float level = 0.0F;
      for (int i = 0; i < 10; i++) {
         float next = TimeStopClock.fade(level, TimeStopClock.VOLUME);
         assertTrue(next >= level && next <= TimeStopClock.VOLUME);
         level = next;
      }
      assertTrue(level > TimeStopClock.VOLUME * 0.8F);
      for (int i = 0; i < 40; i++) {
         level = TimeStopClock.fade(level, TimeStopClock.VOLUME);
      }
      assertEquals(TimeStopClock.VOLUME, level);
   }

   @Test
   void fadeOutReachesSilence() {
      float level = TimeStopClock.VOLUME;
      for (int i = 0; i < 40; i++) {
         level = TimeStopClock.fade(level, 0.0F);
      }
      assertEquals(0.0F, level);
   }
}
