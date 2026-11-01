package dev.baranhan.viltrumitecore.worldevent.thanos;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ThanosPhaseTest {
   static final int D = ThanosRules.DURATION_TICKS;

   @Test
   void healthAndTimeGatesInOrder() {
      assertEquals(ThanosPhase.ARRIVAL, ThanosPhase.ofHealth(1.0F));
      assertEquals(ThanosPhase.ARRIVAL, ThanosPhase.ofHealth(0.76F));
      assertEquals(ThanosPhase.BLACK_ORDER, ThanosPhase.ofHealth(0.75F));
      assertEquals(ThanosPhase.INVASION, ThanosPhase.ofHealth(0.5F));
      assertEquals(ThanosPhase.GAUNTLET, ThanosPhase.ofHealth(0.1F));
      assertEquals(ThanosPhase.ARRIVAL, ThanosPhase.ofTime(0));
      assertEquals(ThanosPhase.BLACK_ORDER, ThanosPhase.ofTime(ThanosRules.BLACK_ORDER_TICK));
      assertEquals(ThanosPhase.INVASION, ThanosPhase.ofTime(ThanosRules.INVASION_TICK));
      assertEquals(ThanosPhase.GAUNTLET, ThanosPhase.ofTime(D - 1));
      ThanosPhase last = ThanosPhase.ARRIVAL;
      for (ThanosPhase p : ThanosPhase.values()) {
         assertTrue(p.timeGate() >= last.timeGate() && p.healthGate() <= last.healthGate(), p + " gates in order");
         last = p;
      }

      assertTrue(ThanosRules.GAUNTLET_TICK < D - ThanosRules.FINAL_TICKS, "the gauntlet phase starts before the final minute");
   }

   @Test
   void nanHealthStaysInTheArrival() {
      assertEquals(ThanosPhase.ARRIVAL, ThanosPhase.ofHealth(Float.NaN));
      assertEquals(ThanosPhase.ARRIVAL, ThanosPhase.advance(ThanosPhase.ARRIVAL, Float.NaN, 10, 5000));
   }

   @Test
   void advanceTakesOneStepAfterTheMinimumStay() {
      ThanosPhase a = ThanosPhase.ARRIVAL;
      // a huge hit on the first tick: the stay holds the phase
      assertEquals(a, ThanosPhase.advance(a, 0.0F, 1, 1));
      assertEquals(a, ThanosPhase.advance(a, 0.1F, 100, a.minStay() - 1));
      // stay done: one step only, even with the health of the last phase
      assertEquals(ThanosPhase.BLACK_ORDER, ThanosPhase.advance(a, 0.1F, 100, a.minStay()));
      // no gate reached: stays
      assertEquals(a, ThanosPhase.advance(a, 0.9F, ThanosRules.BLACK_ORDER_TICK - 1, 5000));
      assertEquals(ThanosPhase.BLACK_ORDER, ThanosPhase.advance(a, 0.9F, ThanosRules.BLACK_ORDER_TICK, 5000));
      assertEquals(ThanosPhase.GAUNTLET, ThanosPhase.advance(ThanosPhase.GAUNTLET, 1.0F, 0, 0));
   }

   @Test
   void everyPhaseIsSeenWithinTheTimer() {
      // no damage at all: the time gates alone walk through every phase before the clock runs out
      ThanosPhase p = ThanosPhase.ARRIVAL;
      int in = 0;
      int seen = 1;
      for (int t = 0; t < D; t++) {
         ThanosPhase next = ThanosPhase.advance(p, 1.0F, t, in);
         if (next != p) {
            assertEquals(p.ordinal() + 1, next.ordinal());
            p = next;
            in = 0;
            seen++;
         } else {
            in++;
         }
      }

      assertEquals(ThanosPhase.values().length, seen);
      // the strongest group: the stays alone keep each phase on screen
      int fastest = 0;
      for (ThanosPhase q : ThanosPhase.values()) {
         fastest += q.minStay();
      }

      assertTrue(fastest >= 2400, "at least two minutes before the gauntlet phase");
   }

   @Test
   void theFloorHoldsUntilTheLastPhase() {
      float max = 3000.0F;
      assertEquals(0.75F * max, ThanosRules.floorHealth(ThanosPhase.ARRIVAL, max), 0.01F);
      assertEquals(0.5F * max, ThanosRules.floorHealth(ThanosPhase.BLACK_ORDER, max), 0.01F);
      assertEquals(0.25F * max, ThanosRules.floorHealth(ThanosPhase.INVASION, max), 0.01F);
      assertEquals(0.0F, ThanosRules.floorHealth(ThanosPhase.GAUNTLET, max));
      for (ThanosPhase p : ThanosPhase.values()) {
         if (!p.last()) {
            assertEquals(p.next().healthGate(), p.floor(), 1.0E-6F, p + ": the floor is the next gate, so reaching it advances");
         }
      }
   }

   @Test
   void healthScalesWithTheGroupAndStops() {
      assertEquals(3000.0F, ThanosRules.maxHealth(0));
      assertEquals(3000.0F, ThanosRules.maxHealth(1));
      assertEquals(4800.0F, ThanosRules.maxHealth(2), 0.01F);
      assertEquals(3000.0F * (1.0F + 0.6F * 7.0F), ThanosRules.maxHealth(8), 0.01F);
      assertEquals(ThanosRules.maxHealth(8), ThanosRules.maxHealth(20));
      assertTrue(ThanosRules.damageScale(4) > ThanosRules.damageScale(1));
   }

   @Test
   void gauntletPhaseSharpensButKeepsTelegraphsReadable() {
      assertEquals(20, ThanosRules.telegraph(20, ThanosPhase.ARRIVAL));
      assertEquals(16, ThanosRules.telegraph(20, ThanosPhase.GAUNTLET));
      assertEquals(ThanosRules.MIN_TELEGRAPH_FINAL, ThanosRules.telegraph(6, ThanosPhase.GAUNTLET));
      assertEquals(ThanosRules.MIN_TELEGRAPH, ThanosRules.telegraph(6, ThanosPhase.ARRIVAL));
      assertEquals(70, ThanosRules.cooldown(100, ThanosPhase.GAUNTLET));
      assertEquals(ThanosRules.GAUNTLET_STONE_DAMAGE, ThanosRules.phaseDamage(ThanosMove.POWER_PUNCH, ThanosPhase.GAUNTLET));
      assertEquals(1.0F, ThanosRules.phaseDamage(ThanosMove.CLEAVE, ThanosPhase.GAUNTLET));
      assertEquals(1.0F, ThanosRules.phaseDamage(ThanosMove.POWER_PUNCH, ThanosPhase.INVASION));
      for (ThanosMove move : ThanosMove.values()) {
         for (ThanosPhase phase : ThanosPhase.values()) {
            assertTrue(ThanosRules.telegraph(move.telegraph, phase) >= 10, move + " telegraph readable in " + phase);
         }
      }
   }
}
