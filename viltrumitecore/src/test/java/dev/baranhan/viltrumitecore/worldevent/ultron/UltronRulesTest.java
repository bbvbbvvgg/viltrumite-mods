package dev.baranhan.viltrumitecore.worldevent.ultron;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UltronRulesTest {
   @Test
   void phasesRunBetweenTheirMinimumAndMaximum() {
      for (UltronPhase p : new UltronPhase[]{UltronPhase.AWAKENING, UltronPhase.SPREAD, UltronPhase.MACHINE_WAR, UltronPhase.ADAPTATION}) {
         int min = UltronRules.minTicks(p);
         int max = UltronRules.maxTicks(p);
         assertTrue(min > 0 && max > min, p.toString());
         // every early signal at once still waits for the minimum
         assertFalse(UltronRules.phaseDone(p, min - 1, 1.0F, 99, 1, 999.0F, 99, 0), p.toString());
         // nothing happening still ends at the maximum
         assertTrue(UltronRules.phaseDone(p, max, 1.0F, 0, 99, 0.0F, 0, 99), p.toString());
         assertFalse(UltronRules.phaseDone(p, min, 1.0F, 0, 99, 0.0F, 0, 99), p.toString());
      }
      assertFalse(UltronRules.phaseDone(UltronPhase.FINALE, Integer.MAX_VALUE, 1.0F, 0, 0, 0.0F, 0, 0));
      assertFalse(UltronRules.phaseDone(UltronPhase.ENDED, Integer.MAX_VALUE, 1.0F, 0, 0, 0.0F, 0, 0));
   }

   @Test
   void earlySignalsEndPhasesAfterTheMinimum() {
      int t = UltronRules.minTicks(UltronPhase.SPREAD);
      assertTrue(UltronRules.phaseDone(UltronPhase.AWAKENING, UltronRules.minTicks(UltronPhase.AWAKENING), 1.0F, 1, 6, 0.0F, 0, 1));
      assertTrue(UltronRules.phaseDone(UltronPhase.SPREAD, t, 1.0F, 6, 6, 0.0F, 0, 7));
      assertFalse(UltronRules.phaseDone(UltronPhase.SPREAD, t, 1.0F, 5, 6, 0.0F, 0, 6));
      int w = UltronRules.minTicks(UltronPhase.MACHINE_WAR);
      assertTrue(UltronRules.phaseDone(UltronPhase.MACHINE_WAR, w, 1.0F, 6, 6, UltronRules.WAR_PRESSURE_EXIT, 0, 7));
      assertTrue(UltronRules.phaseDone(UltronPhase.MACHINE_WAR, w, 1.0F, 6, 6, 0.0F, 0, UltronRules.FINALE_NODE_FLOOR + 1));
      int a = UltronRules.minTicks(UltronPhase.ADAPTATION);
      assertTrue(UltronRules.phaseDone(UltronPhase.ADAPTATION, a, 1.0F, 6, 6, 0.0F, UltronRules.ADAPT_ACTIVATIONS_EXIT, 7));
      assertFalse(UltronRules.phaseDone(UltronPhase.ADAPTATION, a, 1.0F, 6, 6, 0.0F, UltronRules.ADAPT_ACTIVATIONS_EXIT - 1, 7));
   }

   @Test
   void timeScaleStretchesThePhases() {
      int max = UltronRules.maxTicks(UltronPhase.SPREAD);
      assertFalse(UltronRules.phaseDone(UltronPhase.SPREAD, max, 1.1F, 0, 6, 0.0F, 0, 7));
      assertTrue(UltronRules.phaseDone(UltronPhase.SPREAD, Math.round(max * 1.1F), 1.1F, 0, 6, 0.0F, 0, 7));
   }

   @Test
   void earlyFinaleWhenTheNetworkCollapses() {
      assertFalse(UltronRules.earlyFinale(UltronPhase.AWAKENING, false, 0), "the awakening has its own failure (no core: the event ends)");
      assertTrue(UltronRules.earlyFinale(UltronPhase.SPREAD, false, 3));
      assertFalse(UltronRules.earlyFinale(UltronPhase.SPREAD, true, 1));
      assertTrue(UltronRules.earlyFinale(UltronPhase.MACHINE_WAR, true, UltronRules.FINALE_NODE_FLOOR - 1));
      assertFalse(UltronRules.earlyFinale(UltronPhase.MACHINE_WAR, true, UltronRules.FINALE_NODE_FLOOR));
      assertFalse(UltronRules.earlyFinale(UltronPhase.FINALE, false, 0));
   }

   @Test
   void nodeNumbersAreSane() {
      for (UltronNodeKind k : UltronNodeKind.values()) {
         assertTrue(UltronRules.hull(k) > 0 && UltronRules.shield(k) > 0);
         assertTrue(UltronRules.nodeHitCap(k) >= UltronRules.NODE_MIN_HIT);
         assertTrue(UltronRules.nodeSecondCap(k) >= UltronRules.nodeHitCap(k));
         assertEquals(UltronRules.shield(k) * UltronRules.FORTIFY_SHIELD_FACTOR, UltronRules.shield(k, true), 1.0E-4);
         // at the per-second cap a node falls in no less than ~6 seconds of focused fire
         assertTrue((UltronRules.hull(k) + UltronRules.shield(k)) / UltronRules.nodeSecondCap(k) >= 6.0F, k.toString());
      }
      assertTrue(UltronRules.shieldRegenPerTick(true, false) > UltronRules.shieldRegenPerTick(false, false));
      assertTrue(UltronRules.shieldRegenPerTick(true, true) > UltronRules.shieldRegenPerTick(true, false));
      assertEquals(0.0F, UltronRules.uploadRate(0));
      assertEquals(1.0F, UltronRules.uploadRate(1));
      assertEquals(1.5F, UltronRules.uploadRate(3), 1.0E-5);
   }

   @Test
   void phaseIdsRoundTrip() {
      for (UltronPhase p : UltronPhase.values()) {
         assertSame(p, UltronPhase.byId(p.ordinal()));
         assertSame(p, UltronPhase.byKey(p.key()));
      }
      assertSame(UltronPhase.ENDED, UltronPhase.byId(99));
      assertNull(UltronPhase.byKey("nope"));
      assertFalse(UltronPhase.AWAKENING.hostile());
      assertTrue(UltronPhase.SPREAD.hostile());
      assertSame(UltronPhase.ENDED, UltronPhase.ENDED.next());
   }

   @Test
   void uploadShareIsClampedToOne() {
      // the upload counts ticks: the HUD and /ultron status show it as a share of the scenario's upload time
      assertEquals(0.5F, UltronRules.share(1200.0F, 2400), 1.0E-6);
      assertEquals(1.0F, UltronRules.share(126625.0F, 2400));
      assertEquals(0.0F, UltronRules.share(-5.0F, 2400));
      assertEquals(0.0F, UltronRules.share(50.0F, 0));
   }

   @Test
   void theLeashRingKeepsChasesNearTheEvent() {
      double ring = 160.0 * UltronDirector.LEASH;
      assertFalse(UltronRules.outside(0.0, 0.0, ring, 200.0, 0.0));
      assertFalse(UltronRules.outside(100.0, -50.0, ring, 100.0, -50.0 + ring - 1.0));
      assertTrue(UltronRules.outside(0.0, 0.0, ring, 830.0, 0.0)); // the knocked-away player of the QA run
      assertTrue(UltronRules.outside(0.0, 0.0, ring, ring, ring));
   }

   @Test
   void machinesUnloadedWholeGoBackToTheReserve() {
      assertTrue(UltronRules.backToReserve(false, 0, 12.0F));  // unloaded with its chunk (a removed body is never "alive")
      assertFalse(UltronRules.backToReserve(true, 0, 12.0F));  // destroyed or discarded
      assertFalse(UltronRules.backToReserve(false, 0, 0.0F));  // emptied: it died
      assertFalse(UltronRules.backToReserve(false, 1, 12.0F)); // the event is over
      assertFalse(UltronRules.backToReserve(false, 2, 12.0F));
   }

   @Test
   void theSavedReserveKeepsBuildsAndQueuedMachines() {
      int n = UltronUnitKind.values().length;
      int[] reserve = new int[n];
      int[] bodies = new int[n];
      int[] pending = new int[n];
      reserve[UltronUnitKind.DRONE.ordinal()] = 1;
      bodies[UltronUnitKind.DRONE.ordinal()] = 2;
      bodies[UltronUnitKind.SENTRY.ordinal()] = 1;
      pending[UltronUnitKind.SENTRY.ordinal()] = 1;  // a build in a foundry
      pending[UltronUnitKind.BRUTE.ordinal()] = 1;   // waiting at a pad
      int[] saved = UltronRules.savedReserve(reserve, bodies, pending);
      assertEquals(3, saved[UltronUnitKind.DRONE.ordinal()]);
      assertEquals(2, saved[UltronUnitKind.SENTRY.ordinal()]);
      assertEquals(1, saved[UltronUnitKind.BRUTE.ordinal()]);
      assertEquals(0, saved[UltronUnitKind.INTERCEPTOR.ordinal()]);
      assertEquals(1, reserve[UltronUnitKind.DRONE.ordinal()]); // the live reserve stays as it is
      assertEquals(1 + 3 + 2, java.util.Arrays.stream(saved).sum());
   }
}
