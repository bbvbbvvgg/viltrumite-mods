package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.render.vfx.HitStop;
import org.junit.jupiter.api.Test;

/** Client hit-stop (spec §9.3): frozen interpolation for a few ticks. */
class HitStopTest {

   @Test
   void freezesForTicks() {
      HitStop stop = new HitStop();
      stop.trigger(7, 3, 100L, 0.25F);
      assertTrue(stop.active(7, 100L, 0.5F));
      assertTrue(stop.active(7, 102L, 0.9F));
      assertEquals(0.25F, stop.frozenPartial(7, 101L, 0.8F), 1.0E-6F);
      assertEquals(1.5, stop.heldTicks(7, 101L, 0.75F), 1.0E-6, "time spent in the stop so far");
      assertFalse(stop.active(8, 101L, 0.5F), "other entities run");
      assertEquals(0.6F, stop.frozenPartial(8, 101L, 0.6F), 1.0E-6F);
   }

   @Test
   void releasesAfter() {
      HitStop stop = new HitStop();
      stop.trigger(7, 3, 100L, 0.25F);
      assertFalse(stop.active(7, 103L, 0.3F));
      assertEquals(0.3F, stop.frozenPartial(7, 103L, 0.3F), 1.0E-6F);
      assertEquals(0.0, stop.heldTicks(7, 103L, 0.3F), 1.0E-9, "the clock is not held after the stop");
   }

   @Test
   void retriggerExtends() {
      HitStop stop = new HitStop();
      stop.trigger(7, 3, 100L, 0.0F);
      stop.trigger(7, 3, 102L, 0.0F);
      assertTrue(stop.active(7, 104L, 0.5F));
   }
}
