package dev.baranhan.viltrumitecore.hero.reinhard.finalpunch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** THE FINAL PUNCH timeline, speech pages, beats and config clamps (pure). */
class FinalPunchRulesTest {
   @Test
   void phasesRunInOrderInside24Seconds() {
      assertEquals(480, FinalPunchRules.LENGTH, "24 s at 20 tps");
      int[] order = {0, FinalPunchRules.CAMERA_IN, FinalPunchRules.WINDUP_START, FinalPunchRules.LOOK_UP, FinalPunchRules.LEG_LIFT,
         FinalPunchRules.LEG_UP, FinalPunchRules.WINDUP_MAX, FinalPunchRules.SILENCE, FinalPunchRules.STRIKE, FinalPunchRules.CONTACT,
         FinalPunchRules.LAUNCH, FinalPunchRules.LOWER_FIST, FinalPunchRules.CAMERA_OUT, FinalPunchRules.LENGTH};
      for (int i = 1; i < order.length; i++) assertTrue(order[i - 1] < order[i], "phase " + i + " after " + (i - 1));
      int[] fist = {FinalPunchRules.SPARKS, FinalPunchRules.FIRE, FinalPunchRules.RINGS, FinalPunchRules.ARCS, FinalPunchRules.SUN, FinalPunchRules.SILENCE};
      for (int i = 1; i < fist.length; i++) assertTrue(fist[i - 1] < fist[i], "fist layer " + i);
      assertTrue(FinalPunchRules.WARP > FinalPunchRules.ARCS && FinalPunchRules.WARP < FinalPunchRules.SILENCE, "chaos inside phase IV");
      assertEquals(FinalPunchRules.CONTACT + FinalPunchRules.HIT_STOP_TICKS, FinalPunchRules.LAUNCH);
      assertTrue(FinalPunchRules.HIT_STOP_TICKS > 0 && FinalPunchRules.HIT_STOP_TICKS <= 8, "short hit-stop");
   }

   @Test
   void twelveSpeechPagesNeverOverlapAndEndBeforeTheSilence() {
      assertEquals(12, FinalPunchRules.LINES);
      assertEquals(FinalPunchRules.LINE_AT.length, FinalPunchRules.LINE_TICKS.length);
      for (int i = 0; i < FinalPunchRules.LINES; i++) {
         assertEquals(i, FinalPunchRules.lineAt(FinalPunchRules.LINE_AT[i]), "page " + i);
         assertEquals(FinalPunchRules.LINE_TICKS[i], FinalPunchRules.lineTicks(i));
         int end = FinalPunchRules.LINE_AT[i] + FinalPunchRules.LINE_TICKS[i];
         if (i + 1 < FinalPunchRules.LINES) assertTrue(end <= FinalPunchRules.LINE_AT[i + 1], "page " + i + " ends before the next");
         else assertTrue(end <= FinalPunchRules.SILENCE, "the last page is said before the silence");
      }
      assertEquals(-1, FinalPunchRules.lineAt(0));
      assertEquals(-1, FinalPunchRules.lineAt(FinalPunchRules.SILENCE));
   }

   @Test
   void oneContactOneLaunchThenTheEnd() {
      Map<FinalPunchRules.Beat, Integer> count = new EnumMap<>(FinalPunchRules.Beat.class);
      for (int t = 0; t <= FinalPunchRules.LENGTH + 5; t++) count.merge(FinalPunchRules.beat(t), 1, Integer::sum);
      assertEquals(1, count.get(FinalPunchRules.Beat.CONTACT), "exactly one hit");
      assertEquals(FinalPunchRules.Beat.CONTACT, FinalPunchRules.beat(FinalPunchRules.CONTACT));
      assertEquals(FinalPunchRules.HIT_STOP_TICKS - 1, count.get(FinalPunchRules.Beat.HIT_STOP));
      assertEquals(1, count.get(FinalPunchRules.Beat.LAUNCH));
      assertEquals(FinalPunchRules.Beat.END, FinalPunchRules.beat(FinalPunchRules.LENGTH));
      assertEquals(FinalPunchRules.Beat.HOLD, FinalPunchRules.beat(FinalPunchRules.CONTACT - 1));
   }

   @Test
   void envelopesStayInRangeAndTheSilenceIsSilent() {
      for (double t = -5; t <= FinalPunchRules.LENGTH + 5; t += 0.5) {
         for (double v : new double[]{FinalPunchRules.storm(t), FinalPunchRules.charge(t), FinalPunchRules.strike(t)}) {
            assertTrue(v >= 0 && v <= 1, "envelope at " + t);
         }
         assertEquals(t >= FinalPunchRules.SILENCE, FinalPunchRules.silent(t));
      }
      assertEquals(0.0, FinalPunchRules.storm(FinalPunchRules.SILENCE), "the storm freezes in the silence");
      assertEquals(1.0, FinalPunchRules.charge(FinalPunchRules.SUN), 1e-9, "a small sun at SUN");
      assertEquals(0.0, FinalPunchRules.charge(FinalPunchRules.CONTACT), "spent on contact");
      assertEquals(1.0, FinalPunchRules.strike(FinalPunchRules.CONTACT), 1e-9);
      int pulses = 0;
      for (int t = 0; t < FinalPunchRules.LENGTH; t++) if (FinalPunchRules.stormPulse(t)) {
         pulses++;
         assertTrue(t >= FinalPunchRules.WARP && t < FinalPunchRules.SILENCE, "storm pulse " + t);
      }
      assertTrue(pulses > 10, "the storm keeps throwing things: " + pulses);
   }

   @Test
   void cooldownAndPowerAreClamped() {
      assertEquals(0, FinalPunchRules.cooldownTicks(-30));
      assertEquals(600, FinalPunchRules.cooldownTicks(30));
      assertEquals(FinalPunchRules.MAX_COOLDOWN_SECONDS * 20, FinalPunchRules.cooldownTicks(Integer.MAX_VALUE));
      assertEquals(0.0F, FinalPunchRules.damage(Float.NaN));
      assertEquals(FinalPunchRules.MAX_DAMAGE, FinalPunchRules.damage(1.0E9F));
      assertEquals(0.0F, FinalPunchRules.damage(-4));
      assertEquals(0.0, FinalPunchRules.launchSpeed(Double.NaN));
      assertEquals(FinalPunchRules.MAX_LAUNCH_SPEED, FinalPunchRules.launchSpeed(1.0E6));
      assertFalse(FinalPunchRules.launchSpeed(3.0) > FinalPunchRules.MAX_LAUNCH_SPEED, "no infinite velocity");
      assertEquals(1, FinalPunchRules.sustainTicks(-1));
      assertEquals(FinalPunchRules.MAX_SUSTAIN_TICKS, FinalPunchRules.sustainTicks(1000));
   }
}
