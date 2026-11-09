package dev.baranhan.viltrumitecore.combat.feel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeelMathTest {
   @Test
   void falloffIsLinearAndSafe() {
      assertEquals(1.0F, FeelMath.falloff(0.0, 10.0), 0.0F);
      assertEquals(0.5F, FeelMath.falloff(5.0, 10.0), 1.0E-6F);
      assertEquals(0.0F, FeelMath.falloff(12.0, 10.0), 0.0F);
      assertEquals(0.0F, FeelMath.falloff(1.0, 0.0), 0.0F);
      assertEquals(0.0F, FeelMath.falloff(Double.NaN, 10.0), 0.0F);
      assertEquals(0.0F, FeelMath.falloff(1.0, Double.NaN), 0.0F);
   }

   @Test
   void ringGrowsFromZeroToItsMaximum() {
      assertEquals(0.0F, FeelMath.waveRadius(0.0F, 0.9F), 0.0F);
      assertEquals(0.9F, FeelMath.waveRadius(1.0F, 0.9F), 1.0E-6F);
      float last = -1.0F;
      for (int i = 0; i <= 100; i++) {
         float r = FeelMath.waveRadius(i / 100.0F, 0.9F);
         assertTrue(r >= last);
         last = r;
      }
      assertEquals(0.9F, FeelMath.waveRadius(7.0F, 0.9F), 1.0E-6F);
   }

   @Test
   void ringStrengthRisesThenFadesToNothing() {
      assertEquals(0.0F, FeelMath.waveStrength(0.0F), 0.0F);
      assertEquals(0.0F, FeelMath.waveStrength(1.0F), 0.0F);
      assertEquals(0.0F, FeelMath.waveStrength(Float.NaN), 0.0F);
      assertTrue(FeelMath.waveStrength(0.08F) > 0.85F);
      assertTrue(FeelMath.waveStrength(0.5F) < FeelMath.waveStrength(0.1F));
      for (int i = 0; i <= 100; i++) {
         float s = FeelMath.waveStrength(i / 100.0F);
         assertTrue(s >= 0.0F && s <= 1.0F);
      }
   }

   @Test
   void impactFrameTimeline() {
      assertEquals(FeelMath.PHASE_INVERT, FeelMath.impactPhase(0L, true));
      assertEquals(FeelMath.PHASE_LINES, FeelMath.impactPhase(0L, false), "no inversion: lines at once");
      assertEquals(FeelMath.PHASE_LINES, FeelMath.impactPhase(FeelMath.IMPACT_INVERT_MS, true));
      assertEquals(FeelMath.PHASE_FADE, FeelMath.impactPhase(FeelMath.IMPACT_LINES_MS, true));
      assertEquals(FeelMath.PHASE_NONE, FeelMath.impactPhase(FeelMath.IMPACT_TOTAL_MS, true));
      assertEquals(FeelMath.PHASE_NONE, FeelMath.impactPhase(-5L, true));
      assertEquals(1.0F, FeelMath.impactAlpha(10L), 0.0F);
      assertEquals(0.0F, FeelMath.impactAlpha(FeelMath.IMPACT_TOTAL_MS), 0.0F);
      float mid = FeelMath.impactAlpha((FeelMath.IMPACT_LINES_MS + FeelMath.IMPACT_TOTAL_MS) / 2L);
      assertTrue(mid > 0.4F && mid < 0.6F);
   }

   @Test
   void theInvertedFrameIsShorterThanTwoFramesAt30Fps() {
      assertTrue(FeelMath.IMPACT_INVERT_MS <= 66L);
   }

   @Test
   void impactFramesAreRateLimitedForPhotosensitivity() {
      assertTrue(FeelMath.impactAllowed(1000L, -1L));
      assertFalse(FeelMath.impactAllowed(1100L, 1000L));
      assertTrue(FeelMath.impactAllowed(1000L + FeelMath.IMPACT_COOLDOWN_MS, 1000L));
      // a 60 FPS spam of requests over 10 seconds: never more than 3 frames in any second
      long last = -1L;
      java.util.List<Long> shown = new java.util.ArrayList<>();
      for (long now = 0L; now < 10000L; now += 16L) {
         if (FeelMath.impactAllowed(now, last)) {
            last = now;
            shown.add(now);
         }
      }
      for (int i = 0; i + 3 < shown.size(); i++) {
         assertTrue(shown.get(i + 3) - shown.get(i) >= 1000L, "4 impact frames inside one second");
      }
   }

   @Test
   void saneMultipliers() {
      assertEquals(0.0F, FeelMath.sane(Float.NaN, 1.0F), 0.0F);
      assertEquals(0.0F, FeelMath.sane(-2.0F, 1.0F), 0.0F);
      assertEquals(1.0F, FeelMath.sane(5.0F, 1.0F), 0.0F);
      assertEquals(0.4F, FeelMath.sane(0.4F, 1.0F), 0.0F);
      assertEquals(0.35F, FeelMath.REDUCED_MOTION, 0.0F);
   }
}
