package dev.baranhan.viltrumitecore.client.collapse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.world.collapse.CollapseRules;
import dev.baranhan.viltrumitecore.world.collapse.CollapseVariant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** #152: fragments fly until a real contact (no fixed-length track, no hovering, no vanishing mid-air). */
class DebrisContactTest {
   /** Street at y=63 (surface 64); a neighbour's roof (solid y=99) over x in [20,30). */
   static int city(int x, int y, int z) {
      if (y <= 63) {
         return ColumnGround.SOLID;
      }
      if (x >= 20 && x < 30 && y <= 99) {
         return ColumnGround.SOLID;
      }
      return ColumnGround.AIR;
   }

   @Test
   void columnGroundFindsRoofsFloorsAndTheStreet() {
      ColumnGround g = new ColumnGround(DebrisContactTest::city, -64, 64);
      assertEquals(100, g.surface(25, 5, 140), "lands on the neighbour's roof, not the street");
      assertEquals(64, g.surface(5, 5, 140), "open column: the street");
      assertEquals(64, g.surface(5, 5, 70));
      // a query from below the cached range and then above it (range extension both ways)
      ColumnGround h = new ColumnGround(DebrisContactTest::city, -64, 64);
      assertEquals(71, h.surface(25, 5, 70), "a point inside solid blocks: the free block above it");
      assertEquals(100, h.surface(25, 5, 150));
      assertTrue(h.solid(25, 80, 5));
      assertFalse(h.solid(5, 80, 5));
   }

   @Test
   void unloadedColumnsAreUnknownAndTheVoidIsBelowTheWorld() {
      ColumnGround g = new ColumnGround((x, y, z) -> x > 100 ? ColumnGround.UNLOADED : ColumnGround.AIR, -64, 64);
      assertEquals(CollapseMotion.Ground.UNKNOWN, g.surface(200, 0, 90));
      assertTrue(g.surface(0, 0, 90) < -64, "nothing down to the bottom: the void");
   }

   @Test
   void slabGapsAreLandingSurfaces() {
      // floors every 4 blocks of a gutted tower: a piece inside lands on the next slab under it
      ColumnGround g = new ColumnGround((x, y, z) -> y <= 63 || (y > 63 && y % 4 == 0 && y < 120) ? ColumnGround.SOLID : ColumnGround.AIR, -64, 64);
      assertEquals(89, g.surface(0, 0, 90));
      assertEquals(85, g.surface(0, 0, 87));
      assertEquals(64, g.surface(0, 0, 63));
   }

   static List<CollapseMotion.Piece> build(CollapseVariant v, int[][] t, CollapseMotion.Ground ground, int base) {
      int impact = CollapseRules.impactTicks(v, base - 64, 16);
      return CollapseMotion.build(t[0], t[1], t[2], v, 11L, 1.0F, 0.0F, 64, impact, 160, ground);
   }

   @Test
   void piecesFromVeryHighAlwaysReachTheGround() {
      // a sky-high slab (y 200..215): the old fixed-length track (impact*3+60) ended mid-air for pieces like this
      int[][] t = CollapseMotionTest.tower(6, 200, 16);
      for (CollapseVariant v : CollapseVariant.values()) {
         List<CollapseMotion.Piece> pieces = build(v, t, (x, z) -> 64, 200);
         for (CollapseMotion.Piece p : pieces) {
            assertTrue(p.land >= 0, v + ": never landed");
            assertEquals(p.land + 1, p.length, v + ": the track ends on the contact");
            float y = p.y[p.at(p.land)];
            assertTrue(y >= 64 && y < 66, v + ": rests on the street, y=" + y);
            assertFalse(p.airborne(p.land + 5));
            assertTrue(p.airborne(p.land - 1));
         }
      }
   }

   @Test
   void piecesLandOnTheRoofUnderThemNotThroughIt() {
      ColumnGround g = new ColumnGround(DebrisContactTest::city, -64, 64);
      // the collapsing section stands right over the neighbour's roof (x 20..25)
      int[][] t = CollapseMotionTest.tower(5, 104, 12);
      for (int i = 0; i < t[0].length; i++) {
         t[0][i] += 22;
      }
      List<CollapseMotion.Piece> pieces = CollapseMotion.build(t[0], t[1], t[2], CollapseVariant.PANCAKE, 3L, 0.0F, 0.0F, 64,
         CollapseRules.impactTicks(CollapseVariant.PANCAKE, 40, 12), 160, g);
      int onRoof = 0;
      for (CollapseMotion.Piece p : pieces) {
         assertTrue(p.land >= 0);
         int j = p.at(p.land);
         if (p.x[j] >= 20 && p.x[j] < 30) {
            assertTrue(p.y[j] >= 100, "fell through the roof: y=" + p.y[j]);
            onRoof++;
         }
      }
      assertTrue(onRoof > 0);
   }

   @Test
   void aRestingPieceFallsOnWhenItsSurfaceGoes() {
      ColumnGround roof = new ColumnGround(DebrisContactTest::city, -64, 64);
      CollapseMotion.Piece p = CollapseMotion.build(new int[]{25}, new int[]{120}, new int[]{5}, CollapseVariant.CRUMBLE, 1L, 0.0F, 0.0F, 64,
         20, 4, roof).get(0);
      assertTrue(p.y[p.at(p.land)] >= 100);
      int t = p.land + 10;
      assertTrue(p.resting(t));
      // the roof is gone: only the street is left
      ColumnGround street = new ColumnGround((x, y, z) -> y <= 63 ? ColumnGround.SOLID : ColumnGround.AIR, -64, 64);
      CollapseMotion.refall(p, t, street, 64);
      assertTrue(p.land > t);
      assertTrue(p.airborne(t + 1));
      float y = p.y[p.at(p.land)];
      assertTrue(y >= 64 && y < 66, "rests on the street now, y=" + y);
      for (int i = t + 1; i < p.land; i++) {
         assertTrue(p.y[i] <= p.y[i - 1] + 1.0E-4, "falls, never climbs");
      }
   }

   @Test
   void aToppleStopsAgainstTheNeighbourFacade() {
      // a tall neighbour wall at x >= 9 (no surface on top below y=140): toppling pieces cannot pass into it
      CollapseMotion.Ground g = new CollapseMotion.Ground() {
         @Override
         public int surface(int x, int z) {
            return 64;
         }

         @Override
         public boolean solid(int x, int y, int z) {
            return x >= 9 && y >= 64 && y < 140;
         }
      };
      int[][] t = CollapseMotionTest.tower(4, 68, 24);
      List<CollapseMotion.Piece> pieces = CollapseMotion.build(t[0], t[1], t[2], CollapseVariant.TOPPLE, 5L, 1.0F, 0.0F, 64,
         CollapseRules.impactTicks(CollapseVariant.TOPPLE, 4, 24), 160, g);
      for (CollapseMotion.Piece p : pieces) {
         assertTrue(p.land >= 0);
         for (int i = 0; i < p.length; i++) {
            assertTrue(p.x[i] < 9.0 + p.half + 1.0, "inside the neighbour's facade: x=" + p.x[i] + " at " + i);
         }
      }
   }

   @Test
   void scenesWithPiecesInTheAirAreNeverDropped() {
      assertFalse(CollapseScene.dropNow(true, false, true, 500, 900, 600));
      assertTrue(CollapseScene.dropNow(true, false, false, 81, 300, 600));
      assertTrue(CollapseScene.dropNow(true, true, false, 0, 300, 600));
      assertFalse(CollapseScene.dropNow(false, false, false, 0, 300, 600));
      assertTrue(CollapseScene.dropNow(false, false, true, 0, 600 + CollapseMotion.MAX_FLIGHT_TICKS + 1, 600), "hard cap");
   }

   @Test
   void evictionSkipsBusyScenes() {
      assertEquals(-1, CollapseClient.evictIndex(new boolean[]{true, true}, 8, 20));
      assertEquals(2, CollapseClient.evictIndex(new boolean[]{true, true, false, true, true, true, true, true}, 8, 20));
      assertEquals(-1, CollapseClient.evictIndex(new boolean[]{true, true, true, true, true, true, true, true}, 8, 20));
      boolean[] all = new boolean[20];
      java.util.Arrays.fill(all, true);
      assertEquals(0, CollapseClient.evictIndex(all, 8, 20));
   }
}
