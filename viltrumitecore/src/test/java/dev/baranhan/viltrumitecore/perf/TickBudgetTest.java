package dev.baranhan.viltrumitecore.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TickBudgetTest {
   @Test
   void criticalIsNeverDeniedEvenPastCapacity() {
      TickBudget b = new TickBudget("t", 10);
      assertEquals(25, b.take(PerfPriority.CRITICAL, 25));
      assertEquals(0, b.take(PerfPriority.HIGH, 1), "an overfilled budget has nothing left for the others");
      b.reset(10);
      assertEquals(2.5, b.lastFill(), 1.0E-9);
   }

   @Test
   void lowerPrioritiesLeaveAReserveForHigherOnes() {
      TickBudget b = new TickBudget("t", 100);
      assertEquals(40, b.take(PerfPriority.AMBIENT, 1000));
      assertEquals(20, b.take(PerfPriority.LOW, 1000));
      assertEquals(25, b.take(PerfPriority.NORMAL, 1000));
      assertEquals(15, b.take(PerfPriority.HIGH, 1000));
      assertEquals(0, b.take(PerfPriority.HIGH, 1));
      assertTrue(b.tryTake(PerfPriority.CRITICAL));
   }

   @Test
   void theReserveHoldsWhateverTheOrder() {
      TickBudget b = new TickBudget("t", 100);
      assertEquals(90, b.take(PerfPriority.HIGH, 90));
      assertEquals(10, b.take(PerfPriority.LOW, 1000), "low work still uses what high work left");
      b.reset(100);
      assertEquals(60, b.take(PerfPriority.LOW, 1000));
      assertEquals(40, b.take(PerfPriority.HIGH, 1000), "high work always finds its reserve");
   }

   @Test
   void underCapacityEverythingIsGrantedUnchanged() {
      TickBudget b = new TickBudget("t", 1536);
      assertEquals(900, b.take(PerfPriority.HIGH, 900), "the collapse default fits untouched");
      assertEquals(40, b.take(PerfPriority.LOW, 40), "the fire default fits untouched after it");
      assertEquals(520, b.take(PerfPriority.NORMAL, 520), "Godzilla's rage budget fits too");
      b.reset(1536);
      assertEquals(0, b.lastDenied());
   }

   @Test
   void resetSnapshotsTheFinishedTickAndRefundGivesBack() {
      TickBudget b = new TickBudget("t", 10);
      b.take(PerfPriority.HIGH, 8);
      b.refund(PerfPriority.HIGH, 3);
      assertEquals(5, b.used());
      b.take(PerfPriority.HIGH, 20);
      b.reset(4);
      assertEquals(10, b.lastUsed());
      assertEquals(15, b.lastDenied());
      assertEquals(0, b.used());
      assertEquals(4, b.capacity());
      assertEquals(15, b.deniedTotal());
   }
}
