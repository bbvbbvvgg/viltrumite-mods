package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutGeometry;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutGeometry.Cell;

import org.junit.jupiter.api.Test;

/** Production regressions caught: wrong dimensions, clipping, seed or ordering. */
class WorldCutGeometryTest {
   private static WorldCutGeometry shape(long seed) {
      return new WorldCutGeometry(.5, 100, 2.5, 0, 1, seed, 200, 17, 45, 50, 20);
   }

   @Test
   void naturalVolumeMatchesMask() {
      WorldCutGeometry shape = shape(123);
      assertTrue(shape.volume() > 60000 && shape.volume() < 153000);
      int count = 0;
      var wave = shape.wave(-64, 320);
      while (wave.hasNext()) { if(wave.carve(wave.next()))count++; }
      assertEquals(shape.volume(), count);
   }

   @Test
   void taperedDepthAndSmoothInterior() {
      for (long seed : new long[] {0, 1, 123, Long.MIN_VALUE, Long.MAX_VALUE}) {
         WorldCutGeometry shape = shape(seed);
         for (int along = 0; along < 200; along++) {
            int depth = shape.depthAt(along);
            assertTrue(depth >= 1 && depth <= 50);
            if (along > 20 && along < 175) assertTrue(Math.abs(depth - shape.depthAt(along - 1)) <= 1, "smooth interior noise");
         }
      }
   }

   @Test
   void waveIsMonotonicAlong() {
      Iterator<Cell> wave = shape(12).wave(-64, 320);
      int lastZ = Integer.MIN_VALUE;
      int lastY = Integer.MAX_VALUE;
      Set<Cell> unique = new HashSet<>();
      while (wave.hasNext()) {
         Cell pos = wave.next();
         assertTrue(pos.z() >= lastZ);
         if (pos.z() == lastZ) assertTrue(pos.y() <= lastY, "top-down within each slice");
         assertTrue(unique.add(pos), "duplicate block");
         lastZ = pos.z(); lastY = pos.y();
      }
      assertTrue(unique.stream().allMatch(p -> p.z() >= 2 && p.z() < 202));
      assertThrows(java.util.NoSuchElementException.class, wave::next);
   }

   @Test
   void widthIs17() {
      WorldCutGeometry shape = shape(42);
      for (int x = -10; x <= 10; x++) assertEquals(Math.abs(x) <= shape.halfWidth(0,0), shape.contains(0, x, 0));
      assertFalse(shape.contains(-1, 0, 0));
      assertFalse(shape.contains(200, 0, 0));
      assertFalse(shape.contains(0, 0, -1));
      assertFalse(shape.contains(0, 0, shape.depthAt(0)));
      assertEquals(17, shape.hitBox(0).widthX(), 1E-8);
   }

   @Test
   void clipsToWorld() {
      Iterator<Cell> wave = shape(9).wave(80, 90, along -> 100, p -> p.z() < 12);
      int count = 0;
      while (wave.hasNext()) {
         Cell pos = wave.next();
         assertTrue(pos.y() >= 80 && pos.y() < 90);
         assertTrue(pos.z() >= 2 && pos.z() < 12);
         count++;
      }
      assertTrue(count>0 && count<=10*17*10);
      assertFalse(shape(9).wave(110, 120).hasNext());
      assertFalse(shape(9).wave(90, 90).hasNext());
   }

   @Test
   void deterministicForSeed() {
      WorldCutGeometry a = shape(11), b = shape(11), c = shape(1234);
      boolean differs = false;
      for (int i = 0; i < 200; i++) {
         assertEquals(a.depthAt(i), b.depthAt(i));
         differs |= a.depthAt(i) != c.depthAt(i);
      }
      assertTrue(differs);
   }

   @Test
   void followsSurfaceAndIgnoresPitch() {
      WorldCutGeometry shape = new WorldCutGeometry(.5, 100, 2.5, 0, 2, 4, 200, 17, 45, 50, 20);
      assertEquals(0, shape.dx());
      assertEquals(1, shape.dz());
      Iterator<Cell> wave = shape.wave(-64, 320, along -> 100 + along, p -> true);
      while (wave.hasNext()) {
         Cell p = wave.next();
         int along = p.z() - 2;
         assertTrue(p.y() <= 100 + along);
         assertTrue(p.y() > 100 + along - shape.depthAt(along));
      }
   }

   @Test
   void rotatedWaveHasUniqueVoxels() {
      for (int degrees : new int[] {0, 30, 45, 90, 135, 180, 270}) {
         double angle = Math.toRadians(degrees);
         WorldCutGeometry shape = new WorldCutGeometry(.5, 100, 2.5,
            Math.sin(angle), Math.cos(angle), 123, 200, 17, 45, 50, 20);
         var wave = shape.wave(-64, 320);
         Set<Long> positions = new HashSet<>();
         int last = -1;
         int carved=0;
         while (wave.hasNext()) {
            Cell p = wave.next();
            if(wave.carve(p))carved++;
            assertTrue(p.along() >= last);
            last = p.along();
            long packed = ((long) (p.x() & 0x3FFFFFF) << 38)
               | ((long) (p.z() & 0x3FFFFFF) << 12) | (p.y() & 0xFFF);
            assertTrue(positions.add(packed), "duplicate at yaw " + degrees);
         }
         assertEquals(shape.volume(), carved, shape.volume() * .10, "whole-voxel rounding at yaw " + degrees);
      }
   }

   @Test
   void rejectsNonFiniteCoordinates() {
      assertThrows(IllegalArgumentException.class, () ->
         new WorldCutGeometry(Double.NaN, 100, .5, 0, 1, 1, 200, 17, 45, 50, 20));
      assertThrows(IllegalArgumentException.class, () ->
         new WorldCutGeometry(.5, 100, .5, Double.POSITIVE_INFINITY, 1, 1, 200, 17, 45, 50, 20));
   }

   @Test
   void waveCountsClippedWork() {
      var wave = shape(9).wave(80, 90, along -> 100, p -> p.z() < 12);
      int expected=wave.remainingWork(); assertTrue(expected>0 && expected<=1700);
      assertEquals(0, wave.frontAlong());
      int used = 0;
      while (wave.hasNext()) {
         wave.next(); used++;
         assertEquals(expected - used, wave.remainingWork());
      }
      assertEquals(0, wave.remainingWork());
      assertEquals(10, wave.frontAlong());
   }
}
