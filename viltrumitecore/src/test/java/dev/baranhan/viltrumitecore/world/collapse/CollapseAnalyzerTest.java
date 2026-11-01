package dev.baranhan.viltrumitecore.world.collapse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;

/** Analyzer scenarios on synthetic volumes: flat ground at y <= 63, buildings of STRUCTURE above. */
class CollapseAnalyzerTest {
   /** A sparse world: ground below 64 is NATURAL, set cells override, everything else is air. */
   static final class World implements CollapseAnalyzer.Volume {
      final Long2ByteOpenHashMap cells = new Long2ByteOpenHashMap();
      int unknownFromX = Integer.MAX_VALUE;
      boolean noGround;

      void box(int x0, int y0, int z0, int x1, int y1, int z1, byte m) {
         for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
               for (int z = z0; z <= z1; z++) {
                  this.cells.put(BlockKey.of(x, y, z), m);
               }
            }
         }
      }

      /** Removes a box, returns the removed (solid) positions as ledger damage. */
      long[] carve(int x0, int y0, int z0, int x1, int y1, int z1) {
         LongArrayList out = new LongArrayList();
         for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
               for (int z = z0; z <= z1; z++) {
                  long k = BlockKey.of(x, y, z);
                  if (this.at(x, y, z) != CollapseAnalyzer.AIR) {
                     out.add(k);
                  }
                  this.cells.put(k, CollapseAnalyzer.AIR);
               }
            }
         }
         return out.toLongArray();
      }

      @Override
      public byte at(int x, int y, int z) {
         if (x >= this.unknownFromX) {
            return CollapseAnalyzer.UNKNOWN;
         }
         long k = BlockKey.of(x, y, z);
         if (this.cells.containsKey(k)) {
            return this.cells.get(k);
         }
         return y <= 63 && !this.noGround ? CollapseAnalyzer.NATURAL : CollapseAnalyzer.AIR;
      }
   }

   static CollapseAnalyzer.Plan analyze(World w, int x, int y, int z, long[] damaged, int maxBlocks) {
      return CollapseAnalyzer.analyze(w, new CollapseAnalyzer.Request(x, y, z, damaged, 100.0F, CollapseCause.LEVIATHAN, 42L, maxBlocks, -64, 319));
   }

   @Test
   void towerCutThroughToppleOver() {
      World w = new World();
      w.box(0, 64, 0, 4, 90, 4, CollapseAnalyzer.STRUCTURE);
      long[] dmg = w.carve(0, 66, 0, 4, 67, 4);
      CollapseAnalyzer.Plan plan = analyze(w, 2, 66, 2, dmg, 1600);
      assertTrue(plan.accepted(), plan.reason());
      assertEquals(CollapseAnalyzer.Rule.SEVERED, plan.rule());
      assertEquals(CollapseVariant.TOPPLE, plan.variant());
      assertEquals(5 * 5 * 23, plan.blocks().length, "everything above the cut, nothing below");
      assertEquals(68, plan.minY());
      assertEquals(90, plan.maxY());
      assertEquals(65, plan.groundY(), "under the footprint it lands on the stub left below the cut");
      assertFalse(plan.truncated());
      for (long k : plan.blocks()) {
         assertTrue(BlockKey.y(k) >= 68);
      }
   }

   @Test
   void sameInputSamePlan() {
      World a = new World();
      a.box(0, 64, 0, 4, 85, 4, CollapseAnalyzer.STRUCTURE);
      long[] dmg = a.carve(0, 66, 0, 4, 67, 4);
      CollapseAnalyzer.Plan p1 = analyze(a, 2, 66, 2, dmg, 1600);
      CollapseAnalyzer.Plan p2 = analyze(a, 2, 66, 2, dmg, 1600);
      assertEquals(p1.variant(), p2.variant());
      assertEquals(p1.fallX(), p2.fallX());
      assertTrue(java.util.Arrays.equals(p1.blocks(), p2.blocks()));
   }

   @Test
   void bigSectionIsTruncatedNearTheDamage() {
      World w = new World();
      w.box(0, 64, 0, 8, 93, 8, CollapseAnalyzer.STRUCTURE);
      long[] dmg = w.carve(0, 65, 0, 8, 65, 8);
      CollapseAnalyzer.Plan plan = analyze(w, 4, 65, 4, dmg, 600);
      assertTrue(plan.accepted(), plan.reason());
      assertTrue(plan.truncated());
      assertEquals(600, plan.blocks().length);
      assertTrue(plan.maxY() < 93, "the far top stays");
   }

   @Test
   void terrainNeverCollapses() {
      World w = new World();
      w.box(-10, 64, -10, 10, 80, 10, CollapseAnalyzer.NATURAL); // a hill
      long[] dmg = w.carve(-2, 66, -2, 2, 68, 2);
      CollapseAnalyzer.Plan plan = analyze(w, 0, 66, 0, dmg, 1600);
      assertFalse(plan.accepted());
   }

   @Test
   void littleDamageIsIgnored() {
      World w = new World();
      w.box(0, 64, 0, 4, 90, 4, CollapseAnalyzer.STRUCTURE);
      long[] dmg = w.carve(0, 66, 0, 1, 67, 1); // 8 blocks
      assertFalse(analyze(w, 0, 66, 0, dmg, 1600).accepted());
   }

   @Test
   void rebuiltDamageDoesNotCount() {
      World w = new World();
      w.box(0, 64, 0, 4, 90, 4, CollapseAnalyzer.STRUCTURE);
      long[] dmg = w.carve(0, 66, 0, 4, 67, 4);
      w.box(0, 66, 0, 4, 67, 4, CollapseAnalyzer.STRUCTURE); // a player filled the hole again
      assertFalse(analyze(w, 2, 66, 2, dmg, 1600).accepted());
   }

   @Test
   void unloadedChunkInTheBoxRejects() {
      World w = new World();
      w.box(0, 64, 0, 4, 90, 4, CollapseAnalyzer.STRUCTURE);
      long[] dmg = w.carve(0, 66, 0, 4, 67, 4);
      w.unknownFromX = 7;
      CollapseAnalyzer.Plan plan = analyze(w, 2, 66, 2, dmg, 1600);
      assertFalse(plan.accepted());
      assertEquals("unloaded chunk in the box", plan.reason());
   }

   @Test
   void supportedByANeighbourStays() {
      World w = new World();
      // two towers joined by a bridge at the top: cutting one leaves it hanging on the other
      w.box(0, 64, 0, 4, 80, 4, CollapseAnalyzer.STRUCTURE);
      w.box(5, 64, 0, 30, 80, 4, CollapseAnalyzer.STRUCTURE);
      long[] dmg = w.carve(0, 66, 0, 4, 67, 4);
      CollapseAnalyzer.Plan plan = analyze(w, 2, 66, 2, dmg, 1600);
      if (plan.accepted()) {
         assertTrue(plan.rule() != CollapseAnalyzer.Rule.SEVERED, "a block row tied to the next building is never 'severed'");
      }
   }

   @Test
   void hugeHoleInAWideBlockDropsTheBandAbove() {
      World w = new World();
      w.box(0, 64, 0, 19, 75, 9, CollapseAnalyzer.STRUCTURE);
      // a Leviathan ploughed through the lower floors, 8 wide, 4 high, right through the block
      long[] dmg = w.carve(6, 65, 0, 13, 68, 9);
      CollapseAnalyzer.Plan plan = analyze(w, 10, 66, 5, dmg, 1600);
      assertTrue(plan.accepted(), plan.reason());
      for (long k : plan.blocks()) {
         assertTrue(BlockKey.y(k) > 68, "only blocks above the hole fall");
      }
   }

   @Test
   void severityFollowsSize() {
      assertEquals(CollapseSeverity.MINOR, CollapseSeverity.of(100, CollapseCause.HERO));
      assertEquals(CollapseSeverity.CATASTROPHIC, CollapseSeverity.of(1500, CollapseCause.LEVIATHAN));
   }
}
