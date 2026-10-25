package dev.baranhan.viltrumitecore.client.render.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Spec §9.3: the sonic ring bursts out, the air trail fades along its length and with age. */
class AirTrailVfxTest {
   @Test
   void ringGrowsToItsEndRadius() {
      assertEquals(AirTrailVfx.RING_START_RADIUS, AirTrailVfx.ringRadius(0.0F), 1.0E-6F);
      assertEquals(AirTrailVfx.RING_END_RADIUS, AirTrailVfx.ringRadius(1.0F), 1.0E-6F);
      assertTrue(AirTrailVfx.ringRadius(0.3F) > AirTrailVfx.ringRadius(0.1F));
   }

   @Test
   void trailFadesAlongAndWithAge() {
      assertTrue(AirTrailVfx.trailAlpha(0, 10, 0) > AirTrailVfx.trailAlpha(8, 10, 0), "the newest point is the brightest");
      assertTrue(AirTrailVfx.trailAlpha(0, 10, 5) >= AirTrailVfx.trailAlpha(0, 10, 35), "older trail is dimmer");
      assertEquals(0, AirTrailVfx.trailAlpha(0, 10, AirTrailVfx.TRAIL_TICKS), "gone after its life");
      assertEquals(40, AirTrailVfx.TRAIL_TICKS);
   }
}
