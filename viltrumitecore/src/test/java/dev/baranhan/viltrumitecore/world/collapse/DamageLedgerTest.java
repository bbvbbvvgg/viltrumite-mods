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
   void eachBuildingKeepsItsOwnCause() {
      // found in game: a hero strike and a Leviathan cut in the same tick were both named "leviathan"
      DamageLedger l = new DamageLedger();
      for (int i = 0; i < 120; i++) {
         l.record(i % 4, 64 + i / 16, (i / 4) % 4, 1.0F, 100, CollapseCause.HERO);
         l.record(60 + i % 4, 64 + i / 16, (i / 4) % 4, 1.0F, 100, CollapseCause.LEVIATHAN);
      }
      l.record(1, 65, 1, 1.0F, 100, CollapseCause.LEVIATHAN); // a stray hit does not rename the hero's building
      assertEquals(CollapseCause.HERO, l.dominantCause(1, 64, 1, CollapseRules.MAX_HALF, 24, CollapseRules.MAX_UP));
      assertEquals(CollapseCause.LEVIATHAN, l.dominantCause(61, 64, 1, CollapseRules.MAX_HALF, 24, CollapseRules.MAX_UP));
      assertEquals(null, l.dominantCause(200, 64, 0, CollapseRules.MAX_HALF, 24, CollapseRules.MAX_UP));
      l.prune(100 + CollapseRules.WINDOW_TICKS + 1);
      assertEquals(null, l.dominantCause(1, 64, 1, CollapseRules.MAX_HALF, 24, CollapseRules.MAX_UP), "pruned entries lose their cause too");
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

   @Test
   void settledReturnsEveryReadyClusterNotOnlyTheStrongest() {
      // T4b pin (docs §8 / .memory-bank/city-collapse.md): in game the second building settled in the same
      // tick was dropped, because settled handed the director one candidate and lost the rest
      DamageLedger l = new DamageLedger();
      hit(l, 0, 64, 0, 128, 1.0F, 100);
      hit(l, 60, 64, 0, 128, 1.0F, 100);
      hit(l, 120, 64, 0, 128, 1.0F, 100);
      long now = 100 + CollapseRules.SETTLE_TICKS;
      List<DamageLedger.Candidate> all = l.settled(now, 3);
      assertEquals(3, all.size(), "all three settled buildings come out in one call");
      for (int i = 0; i < all.size(); i++) {
         for (int j = i + 1; j < all.size(); j++) {
            assertTrue(Math.abs(all.get(i).x() - all.get(j).x()) > 30, "three different buildings");
         }
      }
      assertTrue(l.settled(now + 1, 3).isEmpty(), "and nothing is served twice");
   }

   @Test
   void settledWithMaxOneKeepsTheOtherBuildingsForTheNextCalls() {
      // T4b pin: with a full quota the remaining ready clusters must stay dirty, never be consumed
      DamageLedger l = new DamageLedger();
      hit(l, 0, 64, 0, 128, 1.0F, 100);
      hit(l, 60, 64, 0, 128, 1.0F, 100);
      hit(l, 120, 64, 0, 128, 1.0F, 100);
      long now = 100 + CollapseRules.SETTLE_TICKS;
      assertEquals(1, l.settled(now, 1).size(), "one cluster per call when the quota is one");
      assertEquals(1, l.settled(now + 1, 1).size(), "the second building waits one tick, not forever");
      assertEquals(1, l.settled(now + 2, 1).size(), "the third as well");
      assertTrue(l.settled(now + 3, 1).isEmpty());
   }

   @Test
   void weakNoiseNeverCrowdsOutAReadyCluster() {
      // T4b pin: clusters below the threshold are consumed silently, they must not eat the quota
      DamageLedger l = new DamageLedger();
      hit(l, 0, 64, 0, 128, 1.0F, 100);      // ready (score 128)
      hit(l, 200, 64, 0, 40, 0.35F, 100);    // settles at 14: consumed, not returned
      long now = 100 + CollapseRules.SETTLE_TICKS;
      List<DamageLedger.Candidate> out = l.settled(now, 1);
      assertEquals(1, out.size());
      assertTrue(out.get(0).x() < 100, "the ready cluster is the one returned");
      assertTrue(l.settled(now + 1, 1).isEmpty(), "the weak one left the dirty set without a candidate");
   }

   @Test
   void dominantCauseIsTheLocalWeightNotTheLastRecordedCause() {
      // T4a pin (docs §8 / .memory-bank/city-collapse.md): in game two towers that fell in one tick shared
      // one cause - the last one recorded level-wide. The cause must be read around each spot.
      DamageLedger l = new DamageLedger();
      // tower A: 128 hero-weight blocks (128 x 0.8) at x 0..3
      for (int i = 0; i < 128; i++) {
         l.record(i % 4, 64 + i / 16, (i / 4) % 4, 0.8F, 100, CollapseCause.HERO);
      }
      // one Leviathan edge inside tower A's window, recorded after all of the hero's blocks
      l.record(10, 66, 2, 1.0F, 100, CollapseCause.LEVIATHAN);
      // tower B: 128 Leviathan-weight blocks (128 x 1.0) at x 60..63
      for (int i = 0; i < 128; i++) {
         l.record(60 + i % 4, 64 + i / 16, (i / 4) % 4, 1.0F, 100, CollapseCause.LEVIATHAN);
      }
      // one hero edge inside tower B's window, recorded last of the whole tick
      l.record(52, 66, 2, 0.8F, 100, CollapseCause.HERO);
      assertEquals(CollapseCause.HERO, l.dominantCause(2, 66, 2, CollapseRules.MAX_HALF, 24, CollapseRules.MAX_UP),
         "128x0.8 beats one Leviathan block: weight wins, recency does not");
      assertEquals(CollapseCause.LEVIATHAN, l.dominantCause(62, 66, 2, CollapseRules.MAX_HALF, 24, CollapseRules.MAX_UP),
         "and in reverse: the late hero edge does not rename the Leviathan tower");
   }
}
