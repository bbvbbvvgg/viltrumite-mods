package dev.baranhan.viltrumitecore.world.collapse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.world.collapse.CollapseAnalyzerTest.World;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;

/**
 * Base cut footprint (#135): an Atheria building attached to its neighbours (a sky bridge, a shared wing, a wall
 * against the next house) still comes down when every wall of it is cut at the base; a strip of wall, a doorway, a
 * room dug into a solid block or a building wedged between others stays.
 */
class BaseCutTest {
   static SupportProbe masked(World w, long[] removed, BaseCut cut) {
      return SupportProbeTest.probe(w, removed, null, false).within(cut);
   }

   static long[] join(long[]... parts) {
      LongArrayList all = new LongArrayList();
      for (long[] p : parts) {
         all.addElements(all.size(), p);
      }
      return all.toLongArray();
   }

   @Test
   void buildingOnASkyBridgeFallsWhenItsBaseIsCut() {
      World w = new World();
      SupportProbeTest.tower(w, 0, 0, 9, 64, 100);
      SupportProbeTest.tower(w, 20, 0, 9, 64, 100);
      w.box(9, 90, 3, 19, 90, 5, CollapseAnalyzer.STRUCTURE);
      long[] removed = w.carve(0, 64, 0, 8, 65, 8);
      assertEquals(SupportProbe.Status.GROUNDED, SupportProbeTest.probe(w, removed, null, false).run(), "plain connectivity: the bridge holds");
      BaseCut cut = BaseCut.of(removed);
      assertNotNull(cut);
      assertEquals(81, cut.footprint, "the ring of cut walls and the rooms inside it");
      assertEquals(32, cut.columns);
      SupportProbe p = masked(w, removed, cut);
      assertEquals(SupportProbe.Status.FLOATING, p.run(), p.reason());
      assertTrue(p.contactShare() < 0.05F, "only the bridge touches it: " + p.contactShare());
      for (long k : p.floatingBlocks()) {
         assertTrue(BlockKey.x(k) <= 8, "the neighbour and the bridge stay: " + BlockKey.x(k));
      }
      assertTrue(p.floatingBlocks().length > 1000);
   }

   @Test
   void houseInARowFallsWhenItsFourWallsAreCut() {
      World w = new World();
      // three houses wall to wall (each its own walls), a floor every 4 rows
      SupportProbeTest.tower(w, 0, 0, 10, 64, 90);
      SupportProbeTest.tower(w, 10, 0, 10, 64, 90);
      SupportProbeTest.tower(w, 20, 0, 10, 64, 90);
      long[] removed = w.carve(10, 64, 0, 19, 66, 9);
      assertEquals(SupportProbe.Status.GROUNDED, SupportProbeTest.probe(w, removed, null, false).run());
      BaseCut cut = BaseCut.of(removed);
      assertNotNull(cut);
      SupportProbe p = masked(w, removed, cut);
      assertEquals(SupportProbe.Status.FLOATING, p.run(), p.reason());
      assertTrue(p.contactShare() <= BaseCut.MAX_CONTACT, "two sides of four lean on the neighbours: " + p.contactShare());
      for (long k : p.floatingBlocks()) {
         assertTrue(BlockKey.x(k) >= 10 && BlockKey.x(k) <= 19);
      }
   }

   @Test
   void buildingWedgedOnThreeSidesStays() {
      World w = new World();
      w.box(-10, 64, -10, 30, 90, -1, CollapseAnalyzer.STRUCTURE); // a solid block behind
      w.box(-10, 64, 0, -1, 90, 9, CollapseAnalyzer.STRUCTURE); // and on both sides
      w.box(10, 64, 0, 30, 90, 9, CollapseAnalyzer.STRUCTURE);
      SupportProbeTest.tower(w, 0, 0, 10, 64, 90);
      long[] removed = w.carve(0, 64, 0, 9, 66, 9);
      BaseCut cut = BaseCut.of(removed);
      assertNotNull(cut);
      SupportProbe p = masked(w, removed, cut);
      p.run();
      assertTrue(p.status() != SupportProbe.Status.FLOATING || p.contactShare() > BaseCut.MAX_CONTACT, "held on three sides: " + p.contactShare());
   }

   @Test
   void wallStripsDoorwaysAndCornersAreNoCut() {
      World w = new World();
      SupportProbeTest.tower(w, 0, 0, 20, 64, 120);
      assertNull(BaseCut.of(w.carve(0, 64, 0, 19, 66, 0)), "one facade");
      assertNull(BaseCut.of(join(w.carve(0, 64, 1, 0, 66, 19), w.carve(1, 64, 19, 19, 66, 19))), "two facades in an L: nothing inside it");
      assertNull(BaseCut.of(w.carve(5, 64, 0, 7, 67, 0)), "a doorway");
   }

   @Test
   void ringWithADoorwayStillEnclosesTheRooms() {
      World w = new World();
      SupportProbeTest.tower(w, 0, 0, 12, 64, 100);
      w.carve(5, 64, 0, 6, 65, 0); // an existing doorway, the lintel above it
      long[] removed = w.carve(0, 64, 0, 11, 66, 11); // the player cuts the ground floor walls: the lintel goes too
      BaseCut cut = BaseCut.of(removed);
      assertNotNull(cut);
      assertEquals(144, cut.footprint);
      assertEquals(44, cut.columns, "only the walls were cut, the rooms inside are enclosed");
      assertEquals(SupportProbe.Status.FLOATING, masked(w, removed, cut).run());
   }

   @Test
   void roomDugIntoASolidBlockStays() {
      World w = new World();
      w.box(0, 64, 0, 30, 100, 30, CollapseAnalyzer.STRUCTURE);
      long[] removed = w.carve(10, 64, 10, 20, 68, 20);
      BaseCut cut = BaseCut.of(removed);
      assertNotNull(cut, "a filled footprint is a cut out area");
      SupportProbe p = masked(w, removed, cut);
      p.run();
      assertTrue(p.status() != SupportProbe.Status.FLOATING || p.contactShare() > BaseCut.MAX_CONTACT, "the ceiling is part of the block: " + p.contactShare());
   }

   @Test
   void standingBuildingInsideTheFootprintIsGrounded() {
      World w = new World();
      SupportProbeTest.tower(w, 0, 0, 9, 64, 100);
      // a trench dug around the house: the footprint encloses it, its walls still reach the ground
      long[] removed = join(w.carve(-2, 63, -2, 10, 63, -2), w.carve(-2, 63, 10, 10, 63, 10), w.carve(-2, 63, -1, -2, 63, 9),
         w.carve(10, 63, -1, 10, 63, 9));
      BaseCut cut = BaseCut.of(removed);
      assertNotNull(cut);
      // the ground under the trench is natural: the house stands on the ground inside the footprint
      assertEquals(SupportProbe.Status.GROUNDED, masked(w, removed, cut).run());
   }
}
