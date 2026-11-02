package dev.baranhan.viltrumitecore.world.collapse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class SupportLedgerTest {
   @Test
   void settlesAfterQuietTicksAndCoversNeighbours() {
      SupportLedger l = new SupportLedger();
      l.record(0, 64, 0, 100);
      l.record(20, 64, 0, 100); // another cell of the same cut
      assertEquals(Long.MIN_VALUE, l.settled(104), "still being cut");
      long c = l.settled(100 + CollapseRules.SETTLE_TICKS);
      assertNotEquals(Long.MIN_VALUE, c);
      assertEquals(0, l.pending(), "the probe around it covers the neighbour cell");
      assertEquals(2, l.around(10, 64, 0, CollapseRules.SUPPORT_CLUSTER_RADIUS, CollapseRules.SUPPORT_CLUSTER_BAND).size());
   }

   @Test
   void waitsWhileANeighbourIsStillBeingCut() {
      SupportLedger l = new SupportLedger();
      l.record(0, 64, 0, 100);
      l.record(9, 64, 0, 106);
      assertEquals(Long.MIN_VALUE, l.settled(110));
      assertNotEquals(Long.MIN_VALUE, l.settled(114));
   }

   @Test
   void pruneAndConsume() {
      SupportLedger l = new SupportLedger();
      l.record(0, 64, 0, 0);
      l.record(1, 64, 0, 2000);
      l.prune(2000);
      assertEquals(1, l.size());
      l.consume(0, 60, 0, 4, 70, 4);
      assertEquals(0, l.size());
      assertEquals(0, l.pending());
   }

   @Test
   void cellCapDropsOldest() {
      SupportLedger l = new SupportLedger();
      for (int i = 0; i < CollapseRules.CELL_CAP + 10; i++) {
         l.record(i % 8, 64 + i / 64 % 8, i / 8 % 8, i);
      }
      assertEquals(CollapseRules.CELL_CAP, l.size());
   }
}
