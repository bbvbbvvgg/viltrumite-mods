package dev.baranhan.viltrumitecore.hero.combat.clash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Mash input: rate cap, sliding window, beats, surges and lag forgiveness. */
class MashMeterTest {
   private static final int ORIGIN = 10;

   @Test
   void burstIsCappedPerWindow() {
      MashMeter meter = new MashMeter();
      int ignored = 0;
      for (int i = 0; i < 15; i++) {
         if (meter.pulse(5, ORIGIN, 0) == MashMeter.Pulse.IGNORED) {
            ignored++;
         }
      }

      assertEquals(5, ignored);
      assertEquals(ClashRules.MAX_PULSES_PER_WINDOW, meter.pulsesInWindow(5));
   }

   @Test
   void bunchedPacketsAfterLagStillCount() {
      MashMeter meter = new MashMeter();
      for (int i = 0; i < 6; i++) {
         meter.pulse(4, ORIGIN, 0);
      }

      assertEquals(6, meter.pulsesInWindow(4), "six pulses arriving in one tick are not collapsed into one");
   }

   @Test
   void oldPulsesLeaveTheWindow() {
      MashMeter meter = new MashMeter();
      meter.pulse(0, ORIGIN, 0);
      meter.pulse(1, ORIGIN, 0);
      assertEquals(2, meter.pulsesInWindow(ClashRules.PULSE_WINDOW - 1));
      assertEquals(1, meter.pulsesInWindow(ClashRules.PULSE_WINDOW));
      assertEquals(0, meter.pulsesInWindow(ClashRules.PULSE_WINDOW + 1));
   }

   @Test
   void pulseOnTheBeatSurgesOncePerBeat() {
      MashMeter meter = new MashMeter();
      assertEquals(MashMeter.Pulse.SURGE, meter.pulse(ORIGIN, ORIGIN, 0));
      assertEquals(MashMeter.Pulse.PUSH, meter.pulse(ORIGIN + 1, ORIGIN, 0), "one surge per beat");
      assertTrue(meter.surging(ORIGIN + ClashRules.SURGE_TICKS - 1));
      assertFalse(meter.surging(ORIGIN + ClashRules.SURGE_TICKS));
      assertEquals(MashMeter.Pulse.SURGE, meter.pulse(ORIGIN + ClashRules.BEAT_PERIOD - 2, ORIGIN, 0), "slightly early for the next beat");
      assertEquals(2, meter.surges());
   }

   @Test
   void offBeatPulseOnlyPushes() {
      MashMeter meter = new MashMeter();
      assertEquals(MashMeter.Pulse.PUSH, meter.pulse(ORIGIN + 10, ORIGIN, 0));
      assertEquals(MashMeter.Pulse.PUSH, meter.pulse(ORIGIN - 8, ORIGIN, 0), "before the first beat");
   }

   @Test
   void latencyWidensTheLateSideOfTheBeat() {
      int late = ORIGIN + ClashRules.BEAT_LATE + 3;
      assertEquals(MashMeter.Pulse.PUSH, new MashMeter().pulse(late, ORIGIN, 0));
      assertEquals(MashMeter.Pulse.SURGE, new MashMeter().pulse(late, ORIGIN, 3));
      int tooLate = ORIGIN + ClashRules.BEAT_LATE + ClashRules.MAX_LATENCY_TICKS + 1;
      assertEquals(MashMeter.Pulse.PUSH, new MashMeter().pulse(tooLate, ORIGIN, 1000), "forgiveness is capped");
   }

   @Test
   void pressureGrowsWithPulsesAndSurges() {
      MashMeter meter = new MashMeter();
      assertEquals(1.0, meter.pressure(0, 1.0), 1.0E-9);
      meter.pulse(ORIGIN + 10, ORIGIN, 0);
      meter.pulse(ORIGIN + 10, ORIGIN, 0);
      assertEquals(1.2, meter.pressure(ORIGIN + 10, 1.0), 1.0E-9);
      MashMeter surging = new MashMeter();
      surging.pulse(ORIGIN, ORIGIN, 0);
      assertEquals(2.0 * 1.1 * ClashRules.SURGE_MULTIPLIER, surging.pressure(ORIGIN, 2.0), 1.0E-9);
      assertEquals(3, meter.accepted() + surging.accepted());
   }
}
