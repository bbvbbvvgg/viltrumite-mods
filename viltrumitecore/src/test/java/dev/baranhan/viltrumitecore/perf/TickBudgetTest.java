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
   void underCapacityEverythingIsGrantedUnchanged() {
      TickBudget b = new TickBudget("t", 1536);
      assertEquals(900, b.take(PerfPriority.HIGH, 900), "the collapse default fits untouched");
      assertEquals(40, b.take(PerfPriority.LOW, 40), "the fire default fits untouched");
      b.reset(1536);
      assertEquals(0, b.lastDenied());
   }

   @Test
   void resetSnapshotsTheFinishedTickAndRefundGivesBack() {
      TickBudget b = new TickBudget("t", 10);
      b.take(PerfPriority.HIGH, 8);
      b.refund(3);
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
