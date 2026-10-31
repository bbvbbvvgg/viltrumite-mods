package dev.baranhan.viltrumitecore.client.chitauri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriLeviathan;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Pure client maths of the invasion: sound loop levels and engine slots, beam glare and storm puff fades. */
class ChitauriClientMathTest {
   @Test
   void portalHumFollowsTheOpeningAndDistance() {
      assertEquals(0.0F, ChitauriLoops.portalVolume(0.0F, 10.0));
      float near = ChitauriLoops.portalVolume(1.0F, 20.0);
      float far = ChitauriLoops.portalVolume(1.0F, 400.0);
      assertTrue(near > far && far > 0.0F, near + " / " + far);
      assertTrue(ChitauriLoops.portalVolume(0.5F, 20.0) < near);
      assertTrue(near <= 1.0F);
      // never fully silent while open, even across the city
      assertTrue(ChitauriLoops.portalVolume(1.0F, 5000.0) >= 0.15F);
   }

   @Test
   void enginesRiseWithSpeedAndDyingOnesWhine() {
      assertTrue(ChitauriLoops.engineVolume(0.9, false) > ChitauriLoops.engineVolume(0.0, false));
      assertTrue(ChitauriLoops.enginePitch(0.9, false, 0) > ChitauriLoops.enginePitch(0.0, false, 0));
      assertTrue(ChitauriLoops.enginePitch(0.9, true, 0) < ChitauriLoops.enginePitch(0.9, false, 0));
      for (int t = 0; t < 40; t++) {
         float p = ChitauriLoops.enginePitch(0.5, true, t);
         assertTrue(p > 0.5F && p < 2.0F);
      }
      assertEquals(ChitauriLoops.engineVolume(5.0, false), ChitauriLoops.engineVolume(0.9, false));
   }

   @Test
   void leviathanRumbleStopsWithTheCorpse() {
      assertEquals(1.0F, ChitauriLoops.leviathanVolume(ChitauriLeviathan.ALIVE));
      assertTrue(ChitauriLoops.leviathanVolume(ChitauriLeviathan.FALLING) > 0.5F);
      assertEquals(0.0F, ChitauriLoops.leviathanVolume(ChitauriLeviathan.CRASHED));
      assertEquals(0.0F, ChitauriLoops.leviathanVolume(ChitauriLeviathan.FADING));
   }

   @Test
   void fadesStepWithoutOvershoot() {
      assertEquals(ChitauriLoops.FADE_UP, ChitauriLoops.approach(0.0F, 1.0F), 1.0E-6F);
      assertEquals(1.0F - ChitauriLoops.FADE_DOWN, ChitauriLoops.approach(1.0F, 0.0F), 1.0E-6F);
      assertEquals(0.5F, ChitauriLoops.approach(0.49F, 0.5F));
      assertEquals(0.5F, ChitauriLoops.approach(0.52F, 0.5F));
      float v = 0.0F;
      for (int i = 0; i < 100; i++) {
         v = ChitauriLoops.approach(v, 0.8F);
      }
      assertEquals(0.8F, v);
   }

   @Test
   void flybyFiresOnceAtClosestApproach() {
      // closing in: nothing
      assertFalse(ChitauriLoops.flyby(10.0, 8.0, 0.8));
      // turned away inside the range at speed: whoosh
      assertTrue(ChitauriLoops.flyby(6.0, 6.5, 0.8));
      // too slow, too far or unknown history: nothing
      assertFalse(ChitauriLoops.flyby(6.0, 6.5, 0.2));
      assertFalse(ChitauriLoops.flyby(30.0, 30.5, 0.8));
      assertFalse(ChitauriLoops.flyby(-1.0, 6.5, 0.8));
   }

   @Test
   void engineSlotsHaveOneRankOfHysteresis() {
      int max = ChitauriLoops.MAX_ENGINES;
      assertFalse(ChitauriLoops.startEngine(null, 0), "untracked chariots never start");
      assertTrue(ChitauriLoops.startEngine(0, 0));
      assertTrue(ChitauriLoops.startEngine(max - 1, max));
      assertFalse(ChitauriLoops.startEngine(max, 0), "only the nearest MAX_ENGINES start");
      assertFalse(ChitauriLoops.startEngine(0, max + 1), "never more than MAX_ENGINES + 1 loops");
      assertTrue(ChitauriLoops.keepEngine(max), "a running loop survives one rank outside");
      assertFalse(ChitauriLoops.keepEngine(max + 1));
   }

   @Test
   void engineRangeHasDistanceHysteresis() {
      double start = ChitauriLoops.ENGINE_RANGE;
      double keep = ChitauriLoops.ENGINE_KEEP;
      assertTrue(keep > start);
      assertTrue(ChitauriLoops.engineInRange(start, false));
      assertFalse(ChitauriLoops.engineInRange(start + 0.5, false), "no new loop beyond the start range");
      assertTrue(ChitauriLoops.engineInRange(start + 0.5, true), "a running loop is kept a little further");
      assertTrue(ChitauriLoops.engineInRange(keep, true));
      assertFalse(ChitauriLoops.engineInRange(keep + 0.5, true));
   }

   @Test
   void beamGlareFadesWhenTheEyeIsInsideTheColumn() {
      assertEquals(0.15F, ChitauriPortalClient.beamNearFade(0.0F), 1.0E-6F);
      assertEquals(0.15F, ChitauriPortalClient.beamNearFade(1.0F), 1.0E-6F);
      assertEquals(1.0F, ChitauriPortalClient.beamNearFade(9.0F), 1.0E-6F);
      assertEquals(1.0F, ChitauriPortalClient.beamNearFade(500.0F), 1.0E-6F);
      float prev = 0.0F;
      for (float d = 0.0F; d < 12.0F; d += 0.5F) {
         float f = ChitauriPortalClient.beamNearFade(d);
         assertTrue(f >= prev - 1.0E-6F, "monotone at " + d);
         prev = f;
      }
   }

   @Test
   void closestPointOfTheBeamSegment() {
      Vector3f p0 = new Vector3f(3, -10, 0);
      Vector3f p1 = new Vector3f(3, 50, 0);
      Vector3f c = ChitauriPortalClient.closestOnSegment(p0, p1);
      assertEquals(3.0F, c.x, 1.0E-5F);
      assertEquals(0.0F, c.y, 1.0E-5F);
      assertEquals(3.0F, c.length(), 1.0E-5F);
      // beyond the ends: clamped to the nearer end
      Vector3f above = ChitauriPortalClient.closestOnSegment(new Vector3f(0, 5, 0), new Vector3f(0, 40, 0));
      assertEquals(5.0F, above.y, 1.0E-5F);
      // degenerate segment
      Vector3f dot = ChitauriPortalClient.closestOnSegment(new Vector3f(1, 2, 3), new Vector3f(1, 2, 3));
      assertEquals(new Vector3f(1, 2, 3), dot);
   }

   @Test
   void stormPuffsVanishAroundTheEye() {
      assertEquals(0.0F, ChitauriPortalClient.puffFade(new Vector3f(0, 0, 0), 10.0F));
      assertEquals(0.0F, ChitauriPortalClient.puffFade(new Vector3f(5, 0, 0), 10.0F));
      assertEquals(1.0F, ChitauriPortalClient.puffFade(new Vector3f(0, 40, 0), 10.0F));
      float mid = ChitauriPortalClient.puffFade(new Vector3f(0, 14, 0), 10.0F);
      assertTrue(mid > 0.0F && mid < 1.0F, "soft edge " + mid);
   }
}
