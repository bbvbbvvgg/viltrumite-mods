package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.reinhard.reid.ReidDrawLight;
import dev.baranhan.viltrumitecore.client.reinhard.reid.ReidDrawSceneVfx;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;

/** REINHARD OVERHAUL item 15: the light of the draw rises, trembles, flashes and decays on the draw clock, without jumps. */
class ReidDrawLightTest {
   private static final double STEP = 0.01;

   /** Largest change of an envelope between two samples STEP apart over the whole draw and its tail. */
   private static double maxJump(DoubleUnaryOperator f) {
      double worst = 0, previous = f.applyAsDouble(-5);
      for (double d = -5 + STEP; d <= 140; d += STEP) {
         double value = f.applyAsDouble(d);
         assertTrue(Double.isFinite(value), "finite at " + d);
         worst = Math.max(worst, Math.abs(value - previous));
         previous = value;
      }
      return worst;
   }

   @Test
   void everyEnvelopeIsContinuousAcrossTheWholeDraw() {
      // Even the release surge takes ATTACK ticks: at most ~0.06 per 0.01 tick (no single-frame pop).
      assertTrue(maxJump(ReidDrawLight::beam) < 0.06, "beam");
      assertTrue(maxJump(ReidDrawLight::flash) < 0.05, "flash");
      assertTrue(maxJump(ReidDrawLight::blade) < 0.06, "blade");
      assertTrue(maxJump(ReidDrawLight::tremble) < 0.06, "tremble");
      assertTrue(maxJump(ReidDrawLight::shake) < 0.1, "shake");
      assertTrue(maxJump(ReidDrawLight::ripplePeriod) < 0.02, "ripple period");
      assertTrue(maxJump(d -> ReidDrawLight.widths(d)[0]) < 0.01, "core width");
      assertTrue(maxJump(d -> ReidDrawLight.widths(d)[2]) < 0.15, "halo width");
      assertTrue(maxJump(ReidDrawLight::tail) < 0.01, "tail");
   }

   @Test
   void darkBreathFirstThenTheLeakAtTheKoiguchi() {
      for (double d = 0; d < ReidDrawLight.LEAK; d += 0.5) {
         assertEquals(0, ReidDrawLight.beam(d), 1e-6, "no light before the koiguchi at " + d);
         assertEquals(0, ReidDrawLight.blade(d), 1e-6);
         assertEquals(0, ReidDrawLight.reach(d), 1e-6);
      }
      assertTrue(ReidDrawLight.beam(ReidDrawChoreo.KOIGUCHI + 6) > 0, "a thread of light after the koiguchi");
      assertTrue(ReidDrawLight.beam(ReidDrawChoreo.KOIGUCHI + 6) < 0.3, "only a thread");
   }

   @Test
   void theColumnGrowsWithTheBladeAndReachesTheSky() {
      float previous = 0;
      for (double d = ReidDrawLight.LEAK; d <= ReidDrawLight.FULL_SKY; d += 0.5) {
         float reach = ReidDrawLight.reach(d);
         assertTrue(reach >= previous - 1e-6, "the column never shrinks while it climbs at " + d);
         previous = reach;
      }
      assertEquals(1, ReidDrawLight.reach(ReidDrawLight.FULL_SKY), 1e-6);
      assertEquals(1, ReidDrawLight.reach(ReinhardRules.REID_RELEASE_TICK), 1e-6);
      assertTrue(ReidDrawLight.beam(80) > ReidDrawLight.beam(50), "brighter as more blade is out");
   }

   @Test
   void theInhaleDimsTheLightJustBeforeTheRelease() {
      assertTrue(ReidDrawLight.beam(95.2) < ReidDrawLight.beam(ReidDrawChoreo.STRAIN - 0.5), "the calm before the release");
   }

   @Test
   void theFlashPeaksExactlyAtTheRelease() {
      double release = ReinhardRules.REID_RELEASE_TICK;
      assertEquals(1, ReidDrawLight.flash(release), 1e-6);
      assertEquals(0, ReidDrawLight.flash(release - ReidDrawLight.ATTACK - 0.01), 1e-6);
      for (double d = release - 3; d < release + 30; d += 0.05) {
         assertTrue(ReidDrawLight.flash(d) <= 1 + 1e-6);
      }
      assertTrue(ReidDrawLight.flash(release + 12) < 0.05, "the flash is gone ~12 ticks later");
      double peak = 0, at = 0;
      for (double d = 0; d < 140; d += 0.01) {
         if (ReidDrawLight.beam(d) > peak) {
            peak = ReidDrawLight.beam(d);
            at = d;
         }
      }
      assertEquals(release, at, 0.05, "the column is brightest at the release");
   }

   @Test
   void theLightDecaysAfterTheGuardAndIsGone() {
      assertTrue(ReidDrawLight.beam(110) < ReidDrawLight.beam(100));
      for (double d = ReidDrawLight.DECAY_END; d < 160; d += 0.5) {
         assertEquals(0, ReidDrawLight.beam(d), 1e-3, "dark again at " + d);
      }
      assertTrue(ReidDrawLight.DECAY_END <= ReinhardRules.REID_DRAW_TICKS + dev.baranhan.viltrumitecore.client.reinhard.reid.ReidDrawScene.TAIL_TICKS,
         "the light is gone before the scene's tail ends");
      assertEquals(0, ReidDrawLight.blade(130), 1e-6);
   }

   @Test
   void theRingSweepsToTheWaveRadiusAfterTheRelease() {
      assertEquals(-1, ReidDrawLight.wave(ReinhardRules.REID_RELEASE_TICK - 0.01), 1e-6);
      assertTrue(ReidDrawLight.wave(ReinhardRules.REID_RELEASE_TICK) > 0);
      assertTrue(ReidDrawLight.wave(ReinhardRules.REID_RELEASE_TICK + 10) > ReidDrawLight.wave(ReinhardRules.REID_RELEASE_TICK + 5));
      assertEquals(ReinhardRules.REID_WAVE_RADIUS, ReidDrawLight.WAVE_RADIUS, 1e-6, "the light ring is the server wave");
      assertEquals(-1, ReidDrawLight.wave(ReinhardRules.REID_RELEASE_TICK + 20), 1e-6);
   }

   @Test
   void ripplesBreatheFasterInTheStrainAndStopAtTheRelease() {
      assertFalse(ReidDrawLight.ripples(ReidDrawChoreo.KOIGUCHI - 0.5));
      assertTrue(ReidDrawLight.ripples(ReidDrawChoreo.KOIGUCHI));
      assertFalse(ReidDrawLight.ripples(ReinhardRules.REID_RELEASE_TICK));
      assertTrue(ReidDrawLight.ripplePeriod(90) < ReidDrawLight.ripplePeriod(40));
      int count = 0;
      for (float t = ReidDrawChoreo.KOIGUCHI; ReidDrawLight.ripples(t); t += ReidDrawLight.ripplePeriod(t)) {
         count++;
      }
      assertTrue(count >= 5 && count <= 12, "a handful of air ripples, not a strobe: " + count);
   }

   @Test
   void theShakeBuildsHoldsBreathAndHitsAtTheRelease() {
      assertEquals(0, ReidDrawLight.shake(0), 1e-6);
      assertTrue(ReidDrawLight.shake(60) > ReidDrawLight.shake(30));
      assertTrue(ReidDrawLight.shake(89) > ReidDrawLight.shake(60), "the strain");
      assertTrue(ReidDrawLight.shake(95.3) < ReidDrawLight.shake(89), "the inhale");
      assertEquals(2.6, ReidDrawLight.shake(ReinhardRules.REID_RELEASE_TICK), 1e-4, "the hit");
      assertTrue(ReidDrawLight.shake(ReinhardRules.REID_RELEASE_TICK + 15) < 0.01, "and it settles");
      assertTrue(ReidDrawLight.CASTER_SHAKE < 1, "his own camera shakes less");
   }

   @Test
   void theBrightestLayerIsTheNarrowestCore() {
      // The colour layers are halo, glow, core; the widths are core, glow, halo. A white pillar as wide
      // as the halo was the result of mixing the two orders up.
      for (double d = ReidDrawLight.LEAK; d <= 125; d += 0.5) {
         float[] widths = ReidDrawLight.widths(d);
         for (int layer = 0; layer + 1 < 3; layer++) {
            assertTrue(ReidDrawSceneVfx.layerAlpha(layer) < ReidDrawSceneVfx.layerAlpha(layer + 1), "inner layers are brighter");
            assertTrue(widths[ReidDrawSceneVfx.widthIndex(layer)] >= widths[ReidDrawSceneVfx.widthIndex(layer + 1)],
               "inner layers are narrower at " + d);
         }
      }
      assertTrue(ReidDrawLight.widths(80)[0] < 0.3F, "the core is a thin blade of light");
   }
}
