package dev.baranhan.viltrumitecore.client.aftermath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathCause;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathRules;
import org.junit.jupiter.api.Test;

/** Distance curves, chances and the voice limit of the aftermath soundscape. */
class AftermathMixerTest {
   @Test
   void falloffIsFullNearSilentAtRangeAndNeverRises() {
      assertEquals(1.0F, AftermathMixer.falloff(0.0, 40.0), 1.0E-6);
      assertEquals(0.0F, AftermathMixer.falloff(40.0, 40.0), 1.0E-6);
      assertEquals(0.0F, AftermathMixer.falloff(5.0, 0.0), 1.0E-6);
      float last = 2.0F;
      for (double d = 0.0; d <= 45.0; d += 0.5) {
         float v = AftermathMixer.falloff(d, 40.0);
         assertTrue(v <= last && v >= 0.0F, "at " + d);
         last = v;
      }
   }

   @Test
   void bedsFillTheRuinAndFadeOutsideIt() {
      assertEquals(1.0F, AftermathMixer.bed(10.0, 20.0F, 30.0), 1.0E-6);
      assertTrue(AftermathMixer.bed(35.0, 20.0F, 30.0) < 1.0F && AftermathMixer.bed(35.0, 20.0F, 30.0) > 0.0F);
      assertEquals(0.0F, AftermathMixer.bed(50.0, 20.0F, 30.0), 1.0E-6);
   }

   @Test
   void farSoundsArriveLateButNotTooLate() {
      assertEquals(0, AftermathMixer.delay(10.0));
      assertTrue(AftermathMixer.delay(120.0) > 3);
      assertEquals(30, AftermathMixer.delay(5000.0));
   }

   @Test
   void chancesFollowTheChannels() {
      float[] fresh = AftermathRules.mix(200, 1.0F, 0.9F, AftermathCause.METEOR.bit(), AftermathRules.FLAG_CITY);
      float[] dead = AftermathRules.mix(200, 1.0F, 0.9F, AftermathCause.METEOR.bit(), AftermathRules.FLAG_CITY | AftermathRules.FLAG_POWER_OUT);
      float[] silent = new float[AftermathRules.CHANNELS];
      for (int k = 0; k < AftermathMixer.KINDS; k++) {
         assertEquals(0.0F, AftermathMixer.chance(k, silent, 1.0F, true), 1.0E-6, "kind " + k);
         float c = AftermathMixer.chance(k, fresh, 0.9F, false);
         assertTrue(c >= 0.0F && c < 0.2F, "kind " + k + " is rare per tick: " + c);
         assertTrue(AftermathMixer.cooldown(k) > 0);
      }
      assertTrue(AftermathMixer.chance(AftermathMixer.SPARKS, dead, 0.9F, false) < AftermathMixer.chance(AftermathMixer.SPARKS, fresh, 0.9F, false));
      assertTrue(AftermathMixer.chance(AftermathMixer.COLLAPSE, fresh, 0.9F, true) > AftermathMixer.chance(AftermathMixer.COLLAPSE, fresh, 0.9F, false),
         "a declared collapse focus crumbles more");
      assertEquals(0.0F, AftermathMixer.chance(AftermathMixer.SIREN, fresh, 0.9F, false), 1.0E-6, "no siren seconds after the hit");
   }

   @Test
   void voiceLimitDropsTrickleFirst() {
      assertEquals(1.0F, AftermathMixer.crowd(0, AftermathMixer.CRUMBLE), 1.0E-6);
      assertTrue(AftermathMixer.crowd(5, AftermathMixer.CRUMBLE) < 0.3F);
      assertEquals(1.0F, AftermathMixer.crowd(5, AftermathMixer.COLLAPSE), 1.0E-6);
      for (int k = 0; k < AftermathMixer.KINDS; k++) {
         assertEquals(0.0F, AftermathMixer.crowd(AftermathMixer.MAX_VOICES, k), 1.0E-6);
      }
   }

   @Test
   void ruinsPauseBrieflyAfterAHitAndNotInUnison() {
      java.util.Set<Integer> seen = new java.util.HashSet<>();
      for (int id = 1; id <= 40; id++) {
         int h = ClientZone.holdTicks(id);
         assertTrue(h >= 16 && h <= 28, "zone " + id + " holds " + h);
         seen.add(h);
      }

      assertTrue(seen.size() >= 10, "pauses vary between ruins");
   }
}
