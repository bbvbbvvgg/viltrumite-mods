package dev.baranhan.viltrumitecore.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MsptWindowTest {
   @Test
   void statsIgnoreEmptySlots() {
      long[] t = new long[100];
      for (int i = 0; i < 20; i++) {
         t[i] = (i + 1) * 1_000_000L;
      }
      MsptWindow.Stats s = MsptWindow.of(t);
      assertEquals(20, s.samples());
      assertEquals(10.5, s.avgMs(), 1.0E-9);
      assertEquals(19.0, s.p95Ms(), 1.0E-9);
      assertEquals(20.0, s.maxMs(), 1.0E-9);
      assertEquals(0, MsptWindow.of(new long[5]).samples());
   }

   @Test
   void tpsComesFromTickSpacingAndIsCapped() {
      MsptWindow w = new MsptWindow(100);
      for (int i = 0; i < 50; i++) {
         w.tickStarted(i * 100_000_000L);
      }
      assertEquals(10.0, w.tps(), 1.0E-9);
      w.clear();
      for (int i = 0; i < 50; i++) {
         w.tickStarted(i * 10_000_000L);
      }
      assertEquals(20.0, w.tps(), 1.0E-9);
   }

   @Test
   void blockBudgetScalesFromBaseToFloor() {
      assertEquals(BlockUpdateBudget.BASE, BlockUpdateBudget.capacityFor(0.0));
      assertEquals(BlockUpdateBudget.FLOOR, BlockUpdateBudget.capacityFor(1.0));
      int mid = BlockUpdateBudget.capacityFor(0.5);
      assertEquals((BlockUpdateBudget.BASE + BlockUpdateBudget.FLOOR) / 2.0, mid, 1.0);
   }
}
