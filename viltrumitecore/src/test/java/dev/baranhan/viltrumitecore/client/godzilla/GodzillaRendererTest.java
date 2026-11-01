package dev.baranhan.viltrumitecore.client.godzilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.worldevent.godzilla.GodzillaMove;
import dev.baranhan.viltrumitecore.worldevent.godzilla.GodzillaRules;
import org.junit.jupiter.api.Test;

/** Pure parts of the kaiju renderer and audio: LOD with hysteresis, clip choice, glow levels, distance curves. */
class GodzillaRendererTest {
   @Test
   void lodFollowsTheDistanceWithHysteresis() {
      assertEquals(0, GodzillaRenderer.lodFor(50.0, -1));
      assertEquals(1, GodzillaRenderer.lodFor(300.0, -1));
      assertEquals(2, GodzillaRenderer.lodFor(800.0, -1));
      float edge = GodzillaRenderer.LOD1_DISTANCE;
      assertEquals(0, GodzillaRenderer.lodFor(edge + 5.0, 0), "inside the band the last LOD stays");
      assertEquals(1, GodzillaRenderer.lodFor(edge - 5.0, 1));
      assertEquals(1, GodzillaRenderer.lodFor(edge + GodzillaRenderer.LOD_BAND + 1.0, 0));
      assertEquals(2, GodzillaRenderer.lodFor(GodzillaRenderer.LOD2_DISTANCE + GodzillaRenderer.LOD_BAND + 1.0, 1));
   }

   @Test
   void movesPickTheirClips() {
      assertEquals("tail_strike", GodzillaRenderer.clipName(GodzillaMove.TAIL, 1));
      assertEquals("tail_strike_m", GodzillaRenderer.clipName(GodzillaMove.TAIL, -1));
      assertEquals("stomp_m", GodzillaRenderer.clipName(GodzillaMove.STOMP, -1));
      assertEquals("claw_l", GodzillaRenderer.clipName(GodzillaMove.CLAW_L, -1));
      assertTrue(GodzillaRenderer.holdsEnd(GodzillaMove.CHARGE) && GodzillaRenderer.holdsEnd(GodzillaMove.DOWN));
      assertFalse(GodzillaRenderer.holdsEnd(GodzillaMove.ROAR));
   }

   @Test
   void platesAndMouthGlowWithTheBreath() {
      assertEquals(0.0F, GodzillaRenderer.plateLevel(GodzillaMove.NONE, 0.0F, 0.5F, false, 10.0F));
      assertEquals(0.0F, GodzillaRenderer.plateLevel(GodzillaMove.CHARGE, 0.0F, 0.5F, false, 10.0F));
      assertTrue(GodzillaRenderer.plateLevel(GodzillaMove.CHARGE, GodzillaRules.CHARGE_SPINE, 0.2F, false, 10.0F) > 0.7F);
      assertTrue(GodzillaRenderer.plateLevel(GodzillaMove.FIRE, 3.0F, 0.6F, false, 10.0F) >= 0.56F);
      assertTrue(GodzillaRenderer.plateLevel(GodzillaMove.NONE, 0.0F, 0.6F, true, 10.0F) > 0.04F, "rage keeps an ember glow");
      assertEquals(1.0F, GodzillaRenderer.mouthLevel(GodzillaMove.FIRE, 5.0F));
      assertEquals(0.0F, GodzillaRenderer.mouthLevel(GodzillaMove.ROAR, 5.0F));
      assertEquals(0.0F, GodzillaRenderer.mouthLevel(GodzillaMove.CHARGE, 0.0F), 1.0E-5F);
      assertEquals(0.5F, GodzillaRenderer.plateS("plate_0500_c3"), 1.0E-5F);
      assertEquals(-1.0F, GodzillaRenderer.plateS("head"));
   }

   @Test
   void audioCurvesFadeWithDistance() {
      assertEquals(1.0F, GodzillaAudio.near(0.0, 200.0), 1.0E-4F);
      assertTrue(GodzillaAudio.near(100.0, 200.0) < 1.0F);
      assertEquals(0.0F, GodzillaAudio.near(250.0, 200.0), 1.0E-4F);
      assertTrue(GodzillaAudio.far(10.0, 100.0, 800.0) < GodzillaAudio.far(120.0, 100.0, 800.0), "up close the near layer carries it");
      assertTrue(GodzillaAudio.far(300.0, 100.0, 800.0) > GodzillaAudio.far(700.0, 100.0, 800.0));
      assertEquals(0.0F, GodzillaAudio.far(900.0, 100.0, 800.0), 1.0E-4F);
   }
}