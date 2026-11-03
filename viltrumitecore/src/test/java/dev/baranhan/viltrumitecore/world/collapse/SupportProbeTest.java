package dev.baranhan.viltrumitecore.world.collapse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.world.collapse.CollapseAnalyzerTest.World;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

/**
 * Support loss (#135) on synthetic volumes: ground is NATURAL at y <= 63. The regression scenarios of the issue:
 * a base cut by plain removals, a tower far taller than the old 30-row box, a building wider than the old 25-block
 * box, a section above the old 1600-block limit; plus the false-collapse guards (terrain, sky platform, a wall that
 * still stands, a partly cut base).
 */
class SupportProbeTest {
   static SupportProbe probe(World w, long[] removed, LongOpenHashSet assumed, boolean single) {
      int cut = Integer.MAX_VALUE;
      for (long k : removed) {
         cut = Math.min(cut, BlockKey.y(k));
      }
      long c = removed[removed.length / 2];
      return new SupportProbe(w, removed, assumed, single, cut, -64, 1023, CollapseRules.SUPPORT_MAX_NODES, CollapseRules.SUPPORT_SEARCH_RADIUS,
         BlockKey.x(c), BlockKey.z(c));
   }

   /** Hollow tower: four walls and a floor slab every 4 rows. */
   static void tower(World w, int x0, int z0, int size, int y0, int y1) {
      int x1 = x0 + size - 1, z1 = z0 + size - 1;
      w.box(x0, y0, z0, x1, y1, z0, CollapseAnalyzer.STRUCTURE);
      w.box(x0, y0, z1, x1, y1, z1, CollapseAnalyzer.STRUCTURE);
      w.box(x0, y0, z0, x0, y1, z1, CollapseAnalyzer.STRUCTURE);
      w.box(x1, y0, z0, x1, y1, z1, CollapseAnalyzer.STRUCTURE);
      for (int y = y0 + 3; y <= y1; y += 4) {
         w.box(x0, y, z0, x1, y, z1, CollapseAnalyzer.STRUCTURE);
      }
   }

   @Test
   void tallTowerCutAtTheBaseFloatsWhole() {
      World w = new World();
      tower(w, 0, 0, 9, 64, 163); // 100 rows: three times the old analysis box
      long[] removed = w.carve(0, 64, 0, 8, 65, 8);
      SupportProbe p = probe(w, removed, null, false);
      assertEquals(SupportProbe.Status.FLOATING, p.run(), p.reason());
      long[] f = p.floatingBlocks();
      int top = Integer.MIN_VALUE, low = Integer.MAX_VALUE;
      for (long k : f) {
         top = Math.max(top, BlockKey.y(k));
         low = Math.min(low, BlockKey.y(k));
      }
      assertEquals(163, top, "the roof is part of the severed structure, the box top no longer holds it");
      assertEquals(66, low);
      assertTrue(f.length > 1600, "more than the old per-collapse limit: " + f.length);
      // it stood before the cut
      SupportProbe before = new SupportProbe(w, removed, new LongOpenHashSet(removed), true, 64, -64, 1023, CollapseRules.SUPPORT_MAX_NODES,
         CollapseRules.SUPPORT_SEARCH_RADIUS, 4, 4);
      assertEquals(SupportProbe.Status.GROUNDED, before.run());
   }

   @Test
   void blocksRemovedOneByOneOnlyTheLastOneSevers() {
      World w = new World();
      w.box(0, 70, 0, 9, 72, 9, CollapseAnalyzer.STRUCTURE); // slab on four pillars
      int[][] pillars = {{0, 0}, {9, 0}, {0, 9}, {9, 9}};
      for (int[] c : pillars) {
         w.box(c[0], 64, c[1], c[0], 69, c[1], CollapseAnalyzer.STRUCTURE);
      }
      for (int i = 0; i < 3; i++) {
         long[] r = w.carve(pillars[i][0], 64, pillars[i][1], pillars[i][0], 64, pillars[i][1]);
         assertEquals(SupportProbe.Status.GROUNDED, probe(w, r, null, false).run(), "pillar " + i + ": the others still carry it");
      }
      long[] last = w.carve(9, 64, 9, 9, 64, 9);
      SupportProbe p = probe(w, last, null, false);
      assertEquals(SupportProbe.Status.FLOATING, p.run());
      assertEquals(300 + 4 * 5, p.floatingBlocks().length, "slab plus the four pillar stubs");
   }

   @Test
   void wideBuildingBeyondTheOldBoxFloats() {
      World w = new World();
      tower(w, 0, 0, 60, 64, 90); // 60 x 60: more than twice the old 25-block box
      long[] removed = w.carve(0, 64, 0, 59, 64, 59);
      SupportProbe p = probe(w, removed, null, false);
      assertEquals(SupportProbe.Status.FLOATING, p.run(), p.reason());
      int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE;
      for (long k : p.floatingBlocks()) {
         x0 = Math.min(x0, BlockKey.x(k));
         x1 = Math.max(x1, BlockKey.x(k));
      }
      assertEquals(0, x0);
      assertEquals(59, x1);
   }

   @Test
   void partlyCutBaseStillStands() {
      World w = new World();
      tower(w, 0, 0, 9, 64, 120);
      long[] removed = w.carve(0, 64, 0, 8, 66, 5); // two thirds of the footprint
      assertEquals(SupportProbe.Status.GROUNDED, probe(w, removed, null, false).run());
   }

   @Test
   void neighbourBridgeKeepsItUp() {
      World w = new World();
      tower(w, 0, 0, 9, 64, 100);
      tower(w, 20, 0, 9, 64, 100);
      w.box(9, 90, 3, 19, 90, 5, CollapseAnalyzer.STRUCTURE); // sky bridge
      long[] removed = w.carve(0, 64, 0, 8, 65, 8);
      assertEquals(SupportProbe.Status.GROUNDED, probe(w, removed, null, false).run(), "the bridge to the other tower carries it");
      long[] bridge = w.carve(9, 90, 3, 9, 90, 5);
      LongArrayList all = new LongArrayList(removed);
      all.addElements(all.size(), bridge);
      SupportProbe p = probe(w, all.toLongArray(), null, false);
      assertEquals(SupportProbe.Status.FLOATING, p.run());
      for (long k : p.floatingBlocks()) {
         assertTrue(BlockKey.x(k) < 20, "only the severed tower (and its half of the bridge) falls");
      }
   }

   @Test
   void terrainAndCavesNeverFloat() {
      World w = new World();
      long[] removed = w.carve(0, 50, 0, 6, 60, 6); // a pit in the ground
      SupportProbe p = probe(w, removed, null, false);
      assertEquals(SupportProbe.Status.GROUNDED, p.run());
      long[] cave = w.carve(10, 40, 10, 30, 45, 30); // a cave under a hill: the sides hold
      assertEquals(SupportProbe.Status.GROUNDED, probe(w, cave, null, false).run());
   }

   @Test
   void skyPlatformThatAlreadyFloatedStays() {
      World w = new World();
      w.box(0, 120, 0, 9, 121, 9, CollapseAnalyzer.STRUCTURE); // built in the air
      long[] removed = w.carve(4, 119, 4, 4, 119, 4); // nothing there
      w.box(4, 119, 4, 4, 119, 4, CollapseAnalyzer.STRUCTURE);
      removed = w.carve(4, 119, 4, 4, 119, 4); // a lamp under the platform removed
      SupportProbe after = probe(w, removed, null, false);
      assertEquals(SupportProbe.Status.FLOATING, after.run());
      SupportProbe before = new SupportProbe(w, removed, new LongOpenHashSet(removed), true, 119, -64, 1023, CollapseRules.SUPPORT_MAX_NODES,
         CollapseRules.SUPPORT_SEARCH_RADIUS, 4, 4);
      assertEquals(SupportProbe.Status.FLOATING, before.run(), "it was not standing before either: no collapse");
   }

   @Test
   void unloadedOrUnbreakableCountsAsSupport() {
      World w = new World();
      tower(w, 0, 0, 9, 64, 100);
      long[] removed = w.carve(0, 64, 0, 8, 65, 8);
      w.unknownFromX = 6;
      assertEquals(SupportProbe.Status.GROUNDED, probe(w, removed, null, false).run(), "half the tower is in an unloaded chunk");
      World b = new World();
      tower(b, 0, 0, 9, 64, 100);
      long[] r2 = b.carve(0, 64, 0, 8, 65, 8);
      b.box(4, 70, 4, 4, 70, 4, CollapseAnalyzer.FIXED);
      b.box(4, 66, 4, 4, 69, 4, CollapseAnalyzer.STRUCTURE);
      assertEquals(SupportProbe.Status.GROUNDED, probe(b, r2, null, false).run(), "a barrier inside holds it");
   }

   @Test
   void tooBigIsUndecided() {
      World w = new World();
      tower(w, 0, 0, 9, 64, 163);
      long[] removed = w.carve(0, 64, 0, 8, 65, 8);
      SupportProbe p = new SupportProbe(w, removed, null, false, 64, -64, 1023, 500, 192, 4, 4);
      assertEquals(SupportProbe.Status.UNDECIDED, p.run());
   }

   @Test
   void incrementalStepsGiveTheSameAnswer() {
      World w = new World();
      tower(w, 0, 0, 9, 64, 140);
      long[] removed = w.carve(0, 64, 0, 8, 64, 8);
      SupportProbe a = probe(w, removed, null, false);
      a.run();
      SupportProbe b = probe(w, removed, null, false);
      int steps = 0;
      while (b.step(97) == SupportProbe.Status.RUNNING) {
         steps++;
      }
      assertTrue(steps > 10);
      assertEquals(a.status(), b.status());
      assertEquals(a.floatingBlocks().length, b.floatingBlocks().length);
   }

   @Test
   void chainCoversEveryBlockTopFirstWithinPacketLimits() {
      World w = new World();
      tower(w, 0, 0, 30, 64, 263); // 200 rows, 30 x 30
      long[] removed = w.carve(0, 64, 0, 29, 64, 29);
      SupportProbe p = probe(w, removed, null, false);
      assertEquals(SupportProbe.Status.FLOATING, p.run());
      long[] f = p.floatingBlocks();
      SupportChainPlan chain = new SupportChainPlan(f, 7L);
      LongOpenHashSet seen = new LongOpenHashSet();
      int lastMax = Integer.MAX_VALUE, stages = 0;
      CollapseAnalyzer.Plan plan;
      while ((plan = chain.next(w, 3200, CollapseCause.SUPPORT, 1L, 1.0F, 0.0F)) != null) {
         stages++;
         assertTrue(plan.blocks().length <= 3200);
         int sx = plan.maxX() - plan.minX() + 1, sy = plan.maxY() - plan.minY() + 1, sz = plan.maxZ() - plan.minZ() + 1;
         assertTrue(sx <= 255 && sy <= 255 && sz <= 255 && (long)sx * sy * sz <= CollapseRules.PACKET_MAX_CELLS, "packet box " + sx + "x" + sy + "x" + sz);
         assertTrue(plan.maxY() <= lastMax, "top floors first");
         lastMax = plan.maxY();
         assertEquals(63, plan.groundY(), "every stage lands on the ground, not on the floors below it");
         for (long k : plan.blocks()) {
            assertTrue(seen.add(k), "a block in two stages");
            w.cells.put(k, CollapseAnalyzer.AIR); // the stage fell
         }
         assertEquals(chain.done(), !plan.truncated());
      }
      assertEquals(f.length, seen.size(), "nothing is left hanging");
      assertTrue(stages > 3);
      assertNull(chain.next(w, 3200, CollapseCause.SUPPORT, 1L, 1.0F, 0.0F));
   }

   @Test
   void smallSeveredTowerFallsInOneToppleStage() {
      World w = new World();
      tower(w, 0, 0, 9, 64, 88); // the PR #126 tower (9 x 9 x 24)
      long[] removed = w.carve(0, 64, 0, 8, 64, 8);
      SupportProbe p = probe(w, removed, null, false);
      assertEquals(SupportProbe.Status.FLOATING, p.run());
      SupportChainPlan chain = new SupportChainPlan(p.floatingBlocks(), 3L);
      CollapseAnalyzer.Plan plan = chain.next(w, 3200, CollapseCause.SUPPORT, 1L, 0.0F, 1.0F);
      assertNotNull(plan);
      assertEquals(CollapseVariant.TOPPLE, plan.variant());
      assertFalse(plan.truncated());
      assertEquals(63, plan.groundY(), "lands on the ground under the cut");
      assertTrue(chain.done());
   }
}
