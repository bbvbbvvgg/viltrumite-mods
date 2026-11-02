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
      int low = Integer.MAX_VALUE, top = Integer.MIN_VALUE;
      for (long k : removed) {
         low = Math.min(low, BlockKey.y(k));
         top = Math.max(top, BlockKey.y(k));
      }
      return SupportProbeTest.probe(w, removed, null, false).within(cut.over(w, low, Math.min(top, low + 8)));
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
         assertTrue(BlockKey.x(k) < 20, "the neighbour stays (the end of the bridge next to the tower falls with it): " + BlockKey.x(k));
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
      assertEquals(p.floatingBlocks().length, BaseCut.withSheets(w, p.floatingBlocks(), 66, 319).length, "the neighbours keep their walls");
   }

   @Test
   void facadeOnThePodiumWallFallsAboveThePodiumRoof() {
      // the Atheria tower at 1215 -200: its north facade stands on the podium wall shared with the building behind
      World w = new World();
      SupportProbeTest.tower(w, 0, 0, 16, 64, 130);
      byte s = CollapseAnalyzer.STRUCTURE;
      w.box(-4, 64, -8, 19, 74, -8, s);
      w.box(-4, 64, -8, -4, 74, 0, s);
      w.box(19, 64, -8, 19, 74, 0, s);
      w.box(-4, 64, 0, 19, 74, 0, s);
      w.box(-4, 74, -8, 19, 74, 0, s);
      w.box(1, 64, 1, 14, 64, 14, s); // the lobby floor
      long[] removed = w.carve(0, 64, 1, 15, 66, 15);
      BaseCut cut = BaseCut.of(removed);
      assertNotNull(cut);
      SupportProbe p = masked(w, removed, cut);
      assertEquals(SupportProbe.Status.FLOATING, p.run(), p.reason());
      assertTrue(p.contactShare() <= BaseCut.MAX_CONTACT, "one side of four leans on the podium: " + p.contactShare());
      long[] own = p.floatingBlocks();
      for (long k : own) {
         assertTrue(BlockKey.z(k) > 0, "the probe itself leaves the facade on the podium wall");
      }
      it.unimi.dsi.fastutil.longs.LongOpenHashSet all = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(BaseCut.withSheets(w, own, 66, 319));
      assertTrue(all.contains(BlockKey.of(7, 100, 0)), "the facade above the podium falls with the tower");
      assertTrue(all.contains(BlockKey.of(0, 130, 0)) && all.contains(BlockKey.of(15, 75, 0)), "corner to corner, down to the podium roof");
      assertEquals(own.length + 16 * (130 - 74), all.size(), "the whole facade above the roof and nothing else");
      for (long k : all) {
         assertTrue(BlockKey.z(k) > 0 || BlockKey.y(k) > 74, "the podium stays: " + BlockKey.x(k) + " " + BlockKey.y(k) + " " + BlockKey.z(k));
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
   void lobbyWithAnOpenArcadeStillEnclosesItsRooms() {
      // found in Atheria (tower at 1215 -200): the lobby front is pillars with 4-block openings, nothing to remove
      World w = new World();
      SupportProbeTest.tower(w, 0, 0, 20, 64, 120);
      w.carve(1, 64, 0, 4, 66, 0);
      w.carve(6, 64, 0, 9, 66, 0);
      w.carve(11, 64, 0, 14, 66, 0);
      w.carve(16, 64, 0, 18, 66, 0);
      long[] removed = w.carve(0, 64, 0, 19, 66, 19);
      BaseCut cut = BaseCut.of(removed);
      assertNotNull(cut, "the openings between the pillars are closed");
      assertEquals(400, cut.footprint);
      assertEquals(SupportProbe.Status.FLOATING, masked(w, removed, cut).run());
   }

   @Test
   void upperFloorsOverhangingTheLobbyFallWithIt() {
      // found in Atheria: the tower's facade is wider than its lobby and touches the next building
      World w = new World();
      SupportProbeTest.tower(w, 2, 2, 12, 64, 70); // the lobby
      SupportProbeTest.tower(w, 0, 0, 16, 71, 130); // the tower above, one to two blocks wider on every side
      w.box(0, 71, 0, 15, 71, 15, CollapseAnalyzer.STRUCTURE); // its first floor rests on the lobby walls
      SupportProbeTest.tower(w, 0, -16, 16, 64, 110); // the neighbour, wall to wall with the tower's north face
      long[] removed = w.carve(2, 64, 2, 13, 66, 13);
      assertEquals(SupportProbe.Status.GROUNDED, SupportProbeTest.probe(w, removed, null, false).run(), "the neighbour holds it");
      BaseCut cut = BaseCut.of(removed);
      assertNotNull(cut);
      SupportProbe p = masked(w, removed, cut);
      assertEquals(SupportProbe.Status.FLOATING, p.run(), p.reason());
      assertTrue(p.contactShare() <= BaseCut.MAX_CONTACT, "one side of four: " + p.contactShare());
      int facade = 0;
      for (long k : p.floatingBlocks()) {
         assertTrue(BlockKey.z(k) >= 0, "the neighbour stays: " + BlockKey.z(k));
         if (BlockKey.x(k) == 0 || BlockKey.x(k) == 15) {
            facade++;
         }
      }
      assertTrue(facade > 500, "the overhanging facade falls with the tower: " + facade);
   }

   @Test
   void lastPillarOutsideTheLobbyHoldsUntilItIsCut() {
      // the Atheria tower: a lobby ring plus a row of pillars one block outside it, a neighbour to the north
      World w = new World();
      SupportProbeTest.tower(w, 2, 2, 12, 64, 70);
      w.box(0, 71, 0, 15, 71, 15, CollapseAnalyzer.STRUCTURE);
      SupportProbeTest.tower(w, 0, 0, 16, 71, 130);
      SupportProbeTest.tower(w, 0, -16, 16, 64, 110);
      for (int z = 3; z <= 13; z += 5) {
         w.box(0, 64, z, 0, 70, z, CollapseAnalyzer.STRUCTURE); // pillars under the west facade
      }
      long[] lobby = w.carve(1, 64, 1, 14, 66, 14);
      long[] pillars = join(w.carve(0, 64, 3, 0, 66, 3), w.carve(0, 64, 8, 0, 66, 8));
      long[] removed = join(lobby, pillars);
      BaseCut cut = BaseCut.of(removed);
      assertNotNull(cut);
      assertEquals(SupportProbe.Status.GROUNDED, masked(w, removed, cut).run(), "the last pillar still holds it");
      long[] last = w.carve(0, 65, 13, 0, 65, 13); // mined by hand
      long[] all = join(removed, last);
      BaseCut cut2 = BaseCut.of(all);
      assertNotNull(cut2);
      SupportProbe p = masked(w, all, cut2);
      assertEquals(SupportProbe.Status.FLOATING, p.run(), p.reason());
      for (long k : p.floatingBlocks()) {
         assertTrue(BlockKey.z(k) >= 0, "the neighbour stays");
      }
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
