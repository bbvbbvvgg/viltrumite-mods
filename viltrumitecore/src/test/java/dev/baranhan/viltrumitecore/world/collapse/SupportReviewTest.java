package dev.baranhan.viltrumitecore.world.collapse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.world.collapse.CollapseAnalyzerTest.World;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regressions for the review of PR #142 (support-loss collapses). */
class SupportReviewTest {
   private static long[] box(int x0, int y0, int z0, int x1, int y1, int z1) {
      it.unimi.dsi.fastutil.longs.LongArrayList out = new it.unimi.dsi.fastutil.longs.LongArrayList();
      for (int x = x0; x <= x1; x++) {
         for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
               out.add(BlockKey.of(x, y, z));
            }
         }
      }
      return out.toLongArray();
   }

   private static long cell(int x, int y, int z) {
      return DamageLedger.cellKey(x, y, z);
   }

   // ---- P2: keep uncovered removal cells pending

   @Test
   void aRemovalOutsideTheProbeOfTheFirstCellStaysPending() {
      SupportLedger l = new SupportLedger();
      l.record(0, 64, 0, 100);
      l.record(72, 64, 0, 101); // 9 cells away: inside the busy band, outside the probe box around (4, 68, 4)
      long now = 101 + CollapseRules.SETTLE_TICKS;
      assertEquals(cell(0, 64, 0), l.settled(now));
      assertTrue(l.isPending(cell(72, 64, 0)), "the probe never saw this removal: it must get a probe of its own");
      assertEquals(cell(72, 64, 0), l.settled(now));
      assertEquals(0, l.pending());
   }

   @Test
   void coverageIsTheWholeCellInsideTheProbeBox() {
      int r = CollapseRules.SUPPORT_CLUSTER_RADIUS, b = CollapseRules.SUPPORT_CLUSTER_BAND;
      long c = cell(0, 64, 0);
      assertTrue(SupportLedger.covered(c, c, r, b));
      assertTrue(SupportLedger.covered(c, cell(56, 64, 0), r, b), "blocks 56..63 are within 4 + 64");
      assertFalse(SupportLedger.covered(c, cell(64, 64, 0), r, b), "blocks 64..71: 69..71 are beyond the probe");
      assertTrue(SupportLedger.covered(c, cell(-56, 64, 0), r, b));
      assertFalse(SupportLedger.covered(c, cell(-64, 64, 0), r, b), "blocks -64..-57: -64..-61 are beyond the probe");
      assertFalse(SupportLedger.covered(c, cell(0, 80, 0), r, b), "the vertical band is narrower");
   }

   // ---- P2: a busy cluster never blocks a distant settled one

   @Test
   void aBusyClusterIsSkippedNotBlocking() {
      SupportLedger l = new SupportLedger();
      l.record(0, 64, 0, 100);
      l.record(1000, 64, 0, 100);
      long now = 100 + CollapseRules.SETTLE_TICKS + 20;
      l.record(8, 64, 0, now - 1); // still being damaged next to the oldest cell
      assertEquals(cell(1000, 64, 0), l.settled(now), "the distant base cut is checked while the destruction nearby goes on");
      assertTrue(l.isPending(cell(0, 64, 0)), "the busy cluster stays pending");
      assertEquals(Long.MIN_VALUE, l.settled(now));
      assertNotEquals(Long.MIN_VALUE, l.settled(now + CollapseRules.SETTLE_TICKS));
   }

   // ---- P2: an unloaded neighbour stays inconclusive in a footprint-restricted probe

   @Test
   void anUnloadedSideNeverMakesAMaskedBaseCutFloat() {
      World w = new World();
      SupportProbeTest.tower(w, 0, 0, 9, 64, 100);
      w.unknownFromX = 9; // the chunk next to the tower's east wall is not loaded
      long[] removed = w.carve(0, 64, 0, 8, 65, 8);
      BaseCut cut = BaseCut.of(removed);
      assertNotNull(cut);
      SupportProbe p = BaseCutTest.masked(w, removed, cut);
      assertNotEquals(SupportProbe.Status.FLOATING, p.run(), "nobody knows what the unloaded side holds");
   }

   // ---- P2: the before-check runs per floating component

   @Test
   void floatingComponentsAreSplitLargestFirst() {
      long[] big = box(0, 80, 0, 4, 84, 4);
      long[] small = box(20, 80, 0, 21, 80, 0);
      List<long[]> comps = SupportWatch.components(BaseCutTest.join(small, big), 24);
      assertEquals(2, comps.size());
      assertEquals(big.length, comps.get(0).length);
      assertEquals(small.length, comps.get(1).length);
      assertEquals(1, SupportWatch.components(BaseCutTest.join(small, big), 1).size(), "capped");
   }

   @Test
   void eachComponentGetsItsOwnBeforeCheck() throws IOException {
      String s = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/world/collapse/SupportWatch.java"));
      assertTrue(s.contains("List<long[]> comps = components(own, MAX_BEFORE_COMPONENTS);"));
      assertTrue(s.contains("long[] nextComp = job.components.pollFirst();"));
      assertTrue(s.contains("job.accepted.addAll(LongArrayList.wrap(job.floatingBlocks));"), "only a grounded component is accepted");
   }

   // ---- P2: support chains respect the active-collapse limit

   @Test
   void chainsWaitForAFreeCollapseSlot() throws IOException {
      assertFalse(SupportWatch.capacity(0, 0), "a limit of zero starts nothing");
      assertFalse(SupportWatch.capacity(3, 3));
      assertTrue(SupportWatch.capacity(2, 3));
      String s = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/world/collapse/SupportWatch.java"));
      int check = s.indexOf("if (!capacity(CollapseDirector.activeCount(), CollapseDirector.maxActive())) {");
      int next = s.indexOf("CollapseAnalyzer.Plan plan = c.plan.next(");
      assertTrue(check > 0 && next > check, "the limit is checked before the plan advances");
   }
}
