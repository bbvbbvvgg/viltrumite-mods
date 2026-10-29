package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** R4, spec §3/§7: the WRATH storm builds up slowly to contact, then trails off for a long time. */
class WrathStormCurveTest {
   private static final int[][] TABLES = {{60, 100}, {72, 120}, {84, 140}, {60, 140}};

   @Test
   void buildUpGrowsToContact() {
      for (int[] t : TABLES) {
         WrathStormCurve curve = new WrathStormCurve(t[0], t[1]);
         float previous = -1.0F;
         for (int e = 0; e < curve.contact(); e++) {
            float build = curve.build(e);
            assertTrue(build >= previous, "monotonic build-up");
            previous = build;
         }

         assertTrue(curve.build(curve.contact() * 0.25F) < 0.2F, "heavy, slow start");
         assertEquals(1.0F, curve.build(curve.contact()));
         assertEquals(WrathStormCurve.Phase.GATHER, curve.phase(1));
         assertEquals(WrathStormCurve.Phase.CHARGE, curve.phase(curve.contact() - 1));
         assertEquals(WrathStormCurve.Phase.STRIKE, curve.phase(curve.contact()));
         assertTrue(curve.arcInterval(1) > curve.arcInterval(curve.contact() - 1), "arcs come faster towards the strike");
         assertTrue(curve.arcWidth(curve.contact() - 1) > curve.arcWidth(0) * 2.0F, "threads become ropes");
         assertTrue(curve.rumble(curve.contact() - 1) > curve.rumble(0) * 5.0F);
         assertTrue(curve.hammerBolts(curve.contact() - 1) && !curve.hammerBolts(curve.contact() * 0.3F));
      }
   }

   @Test
   void tailLastsToTheEndOfTheMove() {
      for (int[] t : TABLES) {
         WrathStormCurve curve = new WrathStormCurve(t[0], t[1]);
         assertTrue(curve.tailEnd() - curve.contact() >= WrathStormCurve.MIN_TAIL - 20 && curve.tailEnd() >= t[1]);
         assertEquals(WrathStormCurve.Phase.TAIL, curve.phase(curve.contact() + WrathStormCurve.STRIKE_TICKS));
         assertEquals(0, curve.arcInterval(curve.contact() + 1), "the strike preset owns its first ticks");
         assertTrue(curve.arcInterval(curve.contact() + WrathStormCurve.STRIKE_TICKS) > 0, "aftershocks");
         assertTrue(curve.density(curve.tailEnd() - 1) > 0.3F, "the cloud still hangs at the end of the move");
         assertTrue(curve.radius(curve.tailEnd() - 1) > curve.radius(curve.contact()), "and spreads");
         assertEquals(WrathStormCurve.Phase.DONE, curve.phase(curve.end()));
         assertEquals(0.0F, curve.density(curve.end()), 1.0E-6);
      }
   }

   @Test
   void shortTableStillGetsALongVisualTail() {
      // Today's placeholder WRATH (24 + 8 + 20) still shows three seconds of tail.
      WrathStormCurve curve = new WrathStormCurve(24, 52);
      assertEquals(24 + WrathStormCurve.MIN_TAIL, curve.tailEnd());
      WrathStormCurve degenerate = new WrathStormCurve(0, 0);
      assertTrue(degenerate.contact() >= 1 && degenerate.length() > degenerate.contact());
   }
}
