package dev.baranhan.viltrumitecore.world.collapse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DamageLedgerTest {
   static void hit(DamageLedger l, int x0, int y0, int z0, int n, float w, long now) {
      int c = 0;
      for (int x = x0; c < n; x++) {
         for (int y = y0; y < y0 + 4 && c < n; y++) {
            for (int z = z0; z < z0 + 4 && c < n; z++) {
               l.record(x, y, z, w, now);
               c++;
            }
         }
      }
   }

   @Test
   void settlesOnlyAfterQuietTicks() {
      DamageLedger l = new DamageLedger();
      hit(l, 0, 64, 0, 120, 1.0F, 100);
      assertTrue(l.settled(100 + CollapseRules.SETTLE_TICKS - 1, 1).isEmpty(), "still being hit");
      List<DamageLedger.Candidate> c = l.settled(100 + CollapseRules.SETTLE_TICKS, 1);
      assertEquals(1, c.size());
      assertTrue(c.get(0).score() >= CollapseRules.SCORE_THRESHOLD);
   }

   @Test
   void twoBuildingsHitTogetherAreBothReturned() {
      // found in game: a second building damaged in the same tick was dropped with the weaker cells
      DamageLedger l = new DamageLedger();
      hit(l, 0, 64, 0, 160, 1.0F, 100);
      hit(l, 60, 64, 0, 120, 1.0F, 100);
      long now = 100 + CollapseRules.SETTLE_TICKS;
      List<DamageLedger.Candidate> first = l.settled(now, 1);
      assertEquals(1, first.size());
      List<DamageLedger.Candidate> second = l.settled(now + 1, 1);
      assertEquals(1, second.size(), "the other building waits for the next tick");
      assertTrue(Math.abs(first.get(0).x() - second.get(0).x()) > 30);
      assertTrue(l.settled(now + 2, 1).isEmpty(), "and the same building is not returned twice");
   }

   @Test
   void weakDamageNeverTriggers() {
      DamageLedger l = new DamageLedger();
      hit(l, 0, 64, 0, 40, 0.35F, 100); // 40 TNT-ish blocks: score 14
      assertTrue(l.settled(200, 1).isEmpty());
   }

   @Test
   void zeroWeightIsIgnored() {
      DamageLedger l = new DamageLedger();
      hit(l, 0, 64, 0, 200, 0.0F, 100);
      assertEquals(0, l.size());
   }

   @Test
   void oldDamageIsPruned() {
      DamageLedger l = new DamageLedger();
      hit(l, 0, 64, 0, 100, 1.0F, 100);
      l.prune(100 + CollapseRules.WINDOW_TICKS + 1);
      assertEquals(0, l.size());
      assertTrue(l.settled(100 + CollapseRules.WINDOW_TICKS + 20, 1).isEmpty());
   }

   @Test
   void impactSettlesFaster() {
      DamageLedger l = new DamageLedger();
      hit(l, 0, 64, 0, 120, 1.0F, 100);
      l.impact(1, 65, 1, 100);
      assertFalse(l.settled(100 + CollapseRules.IMPACT_SETTLE_TICKS, 1).isEmpty());
   }

   @Test
   void consumeForgetsTheCollapsedArea() {
      DamageLedger l = new DamageLedger();
      hit(l, 0, 64, 0, 120, 1.0F, 100);
      l.consume(-10, 0, -10, 40, 120, 40);
      assertEquals(0, l.size());
      assertTrue(l.settled(400, 1).isEmpty());
   }

   @Test
   void levelCapHolds() {
      DamageLedger l = new DamageLedger();
      for (int i = 0; i < 80; i++) {
         hit(l, i * 16, 64, 0, 500, 1.0F, 100 + i);
      }
      assertTrue(l.size() <= CollapseRules.LEVEL_CAP);
   }

   @Test
   void aroundReturnsNearbyPositions() {
      DamageLedger l = new DamageLedger();
      hit(l, 0, 64, 0, 64, 1.0F, 100);
      hit(l, 200, 64, 0, 64, 1.0F, 100);
      assertEquals(64, l.around(1, 65, 1, CollapseRules.MAX_HALF, 24, CollapseRules.MAX_UP).size());
   }
}
