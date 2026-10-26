package dev.baranhan.viltrumitecore.hero.reinhard.saw;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

/** Item 19: the barrier voxelisation, its activation fill and its collision / crossing queries. */
class SawShellTest {
   private static final double CX = 100.5, Y0 = 64, CZ = -40.5, R = SawChoreography.RADIUS;

   /** Flat ground: everything under Y0 is solid, the air above is free. */
   private static SawShell.Occupancy flat() {
      int ground = SawShell.cellOf(Y0);
      return (ix, iy, iz) -> iy < ground ? SawShell.FULL : SawShell.FREE;
   }

   @Test void flatGroundGivesAWholeDomeAboveY0() {
      SawShell shell = SawShell.build(CX, Y0, CZ, R, flat());
      assertNotNull(shell);
      assertEquals(SawShell.enumerate(CX, Y0, CZ, R).length, shell.cellCount());
      assertEquals(shell.cellCount(), shell.activeCount(), "open air reaches the whole shell");
      for (int i = 0; i < shell.cellCount(); i++) {
         assertTrue((shell.cellY(i) + 1) * SawShell.CELL > Y0, "never below Y0");
      }
      assertTrue(shell.activeAt(CX, Y0 + R - .01, CZ), "the crown");
      assertTrue(shell.activeAt(CX + R - .01, Y0 + .3, CZ), "the foot of the wall");
      assertFalse(shell.activeAt(CX, Y0 + 3, CZ), "the inside is open");
      assertFalse(shell.activeAt(CX + R + 1.5, Y0 + 1, CZ), "the outside too");
   }

   @Test void enumerationIsAPureFunctionOfTheCentre() {
      for (double[] c : new double[][]{{CX, Y0, CZ}, {.31, -12.6, 7.92}, {-2999.5, 200.25, 1024.75}}) {
         long[] a = SawShell.enumerate(c[0], c[1], c[2], R), b = SawShell.enumerate(c[0], c[1], c[2], R);
         assertArrayEquals(a, b);
         assertTrue(a.length > 3000 && a.length < 12000, "a 12-block half shell at half-block cells: " + a.length);
      }
   }

   @Test void maskRoundTripsThroughThePacketForm() {
      SawShell.Occupancy wall = (ix, iy, iz) -> iy < SawShell.cellOf(Y0) || ix * SawShell.CELL >= CX + 5 ? SawShell.FULL : SawShell.FREE;
      SawShell shell = SawShell.build(CX, Y0, CZ, R, wall);
      SawShell copy = SawShell.fromMask(CX, Y0, CZ, R, shell.mask());
      assertEquals(shell.cellCount(), copy.cellCount());
      assertEquals(shell.activeCount(), copy.activeCount());
      for (int i = 0; i < shell.cellCount(); i++) assertEquals(shell.activeIndex(i), copy.activeIndex(i), "cell " + i);
   }

   @Test void aCliffTruncatesTheShellWhereItMeetsRealBlocks() {
      SawShell.Occupancy cliff = (ix, iy, iz) -> iy < SawShell.cellOf(Y0) || ix * SawShell.CELL >= CX + 5 ? SawShell.FULL : SawShell.FREE;
      SawShell shell = SawShell.build(CX, Y0, CZ, R, cliff);
      assertNotNull(shell);
      int inside = 0;
      for (int i = 0; i < shell.cellCount(); i++) {
         double x = shell.cellX(i) * SawShell.CELL;
         if (x >= CX + 5) assertFalse(shell.activeIndex(i), "no barrier inside the rock");
         else if (shell.activeIndex(i)) inside++;
      }
      assertTrue(inside > 0 && shell.activeCount() < shell.cellCount());
      assertTrue(shell.activeAt(CX - R + .01, Y0 + .3, CZ), "the open side stays whole");
   }

   @Test void partialCellsNextToTheAirKeepTheShellSealed() {
      int ground = SawShell.cellOf(Y0);
      // a snow layer over the ground: the first cell above Y0 is only partly filled everywhere
      SawShell.Occupancy snow = (ix, iy, iz) -> iy < ground ? SawShell.FULL : iy == ground ? SawShell.PARTIAL : SawShell.FREE;
      SawShell shell = SawShell.build(CX, Y0, CZ, R, snow);
      assertNotNull(shell, "the seed climbs over the layer");
      assertTrue(shell.activeAt(CX + R - .01, Y0 + .2, CZ), "the partial foot cell is barrier, no gap under the wall");
   }

   @Test void aBuriedCentreRefusesTheShell() {
      assertNull(SawShell.build(CX, Y0, CZ, R, (ix, iy, iz) -> SawShell.FULL));
   }

   @Test void collisionAddsOnlyTheActiveCellsTouchingTheBox() {
      SawShell shell = SawShell.build(CX, Y0, CZ, R, flat());
      List<VoxelShape> none = new ArrayList<>();
      boolean[] copied = {false};
      assertSame(none, shell.collect(new AABB(CX - .3, Y0, CZ - .3, CX + .3, Y0 + 1.8, CZ + .3), none, copied), "the caster's box is clear");
      assertFalse(copied[0]);
      List<VoxelShape> base = new ArrayList<>();
      List<VoxelShape> out = shell.collect(new AABB(CX + R - .5, Y0, CZ - .3, CX + R + .1, Y0 + 1.8, CZ + .3), base, copied);
      assertTrue(copied[0] && !out.isEmpty() && base.isEmpty(), "a box in the wall collides with fresh shapes, the vanilla list is untouched");
      for (VoxelShape s : out) assertTrue(s.bounds().getXsize() <= SawShell.CELL + 1E-9);
   }

   @Test void segmentsThroughTheWallCrossIt() {
      SawShell shell = SawShell.build(CX, Y0, CZ, R, flat());
      assertTrue(shell.crosses(CX, Y0 + 2, CZ, CX + 30, Y0 + 2, CZ), "inside -> outside");
      assertTrue(shell.crosses(CX + 30, Y0 + 2, CZ, CX, Y0 + 2, CZ), "outside -> inside");
      assertFalse(shell.crosses(CX - 4, Y0 + 2, CZ, CX + 4, Y0 + 5, CZ + 3), "inside only");
      assertFalse(shell.crosses(CX + 20, Y0 + 2, CZ, CX + 30, Y0 + 2, CZ), "outside only");
   }

   @Test void blockCollisionClassifiesHalfBlockCells() {
      List<AABB> full = List.of(new AABB(0, 0, 0, 1, 1, 1));
      List<AABB> slab = List.of(new AABB(0, 0, 0, 1, .5, 1));
      List<AABB> snow = List.of(new AABB(0, 0, 0, 1, .125, 1));
      assertEquals(SawShell.FULL, SawSphere.cellOccupancy(full, .5, .5, 0, 1, 1, .5));
      assertEquals(SawShell.FULL, SawSphere.cellOccupancy(slab, 0, 0, 0, .5, .5, .5));
      assertEquals(SawShell.FREE, SawSphere.cellOccupancy(slab, 0, .5, 0, .5, 1, .5), "the top half of a slab is air");
      assertEquals(SawShell.PARTIAL, SawSphere.cellOccupancy(snow, .5, 0, .5, 1, .5, 1));
      assertEquals(SawShell.FREE, SawSphere.cellOccupancy(List.of(), 0, 0, 0, .5, .5, .5));
   }

   @Test void discFootprintTouchesBoxesByItsNearestPoint() {
      AABB block = new AABB(10, 0, 10, 11, 1, 11);
      assertTrue(SawSphereScene.discTouches(block, 10.5, 10.5, SawChoreography.SAW_RADIUS), "over the block");
      assertTrue(SawSphereScene.discTouches(block, 13.7, 10.5, SawChoreography.SAW_RADIUS), "rim reaches the face");
      assertFalse(SawSphereScene.discTouches(block, 13.8, 13.8, SawChoreography.SAW_RADIUS), "the corner is out of reach");
   }
}
