package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutShape;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Production regressions caught: wrong dimensions, clipping, seed or ordering. */
class WorldCutShapeTest {
   private static WorldCutShape shape(long seed) {
      return new WorldCutShape(new Vec3(0.5, 100, 0.5), new Vec3(0, 0, 1), seed);
   }

   @Test
   void naturalVolumeMatchesMask() {
      WorldCutShape shape = shape(123);
      assertTrue(shape.volume() > 60000 && shape.volume() < 153000);
      int count = 0;
      Iterator<BlockPos> wave = shape.wave(-64, 320);
      while (wave.hasNext()) { wave.next(); count++; }
      assertEquals(shape.volume(), count);
   }

   @Test
   void taperedDepthAndSmoothInterior() {
      for (long seed : new long[] {0, 1, 123, Long.MIN_VALUE, Long.MAX_VALUE}) {
         WorldCutShape shape = shape(seed);
         for (int along = 0; along < 200; along++) {
            int depth = shape.depthAt(along);
            assertTrue(depth >= 1 && depth <= 50);
            if (along > 20 && along < 175) assertTrue(Math.abs(depth - shape.depthAt(along - 1)) <= 1, "smooth interior noise");
         }
      }
   }

   @Test
   void waveIsMonotonicAlong() {
      Iterator<BlockPos> wave = shape(12).wave(-64, 320);
      int lastZ = Integer.MIN_VALUE;
      int lastY = Integer.MAX_VALUE;
      Set<BlockPos> unique = new HashSet<>();
      while (wave.hasNext()) {
         BlockPos pos = wave.next();
         assertTrue(pos.getZ() >= lastZ);
         if (pos.getZ() == lastZ) assertTrue(pos.getY() <= lastY, "top-down within each slice");
         assertTrue(unique.add(pos), "duplicate block");
         lastZ = pos.getZ(); lastY = pos.getY();
      }
      assertTrue(unique.stream().allMatch(p -> p.getZ() >= 2 && p.getZ() < 202));
      assertThrows(java.util.NoSuchElementException.class, wave::next);
   }

   @Test
   void widthIs17() {
      WorldCutShape shape = shape(42);
      for (int x = -10; x <= 10; x++) assertEquals(Math.abs(x) <= shape.geometry().halfWidth(0,0), shape.contains(0, x, 0));
      assertFalse(shape.contains(-1, 0, 0));
      assertFalse(shape.contains(200, 0, 0));
      assertFalse(shape.contains(0, 0, -1));
      assertFalse(shape.contains(0, 0, shape.depthAt(0)));
      assertEquals(17, shape.hitBox(0).getXsize(), 1E-8);
   }

   @Test
   void damageFollowsActualTaperAndDepthTerraces() {
      WorldCutShape s = shape(42);
      var centre = s.axis(.5);
      assertTrue(s.intersects(new net.minecraft.world.phys.AABB(centre, centre).inflate(.1), 0, 99));
      assertFalse(s.intersects(new net.minecraft.world.phys.AABB(centre.add(4,0,0), centre.add(4,0,0)).inflate(.1), 0, 99), "outside narrow start");
      int a=80, down=35;
      double bank=s.geometry().halfWidth(a,down)+1;
      var deep=s.axis(a+.5).add(bank,-down,0);
      assertFalse(s.intersects(new net.minecraft.world.phys.AABB(deep,deep).inflate(.1), a, 99), "outside deep terrace");
   }

   @Test
   void clipsToWorld() {
      Iterator<BlockPos> wave = shape(9).wave(80, 90, along -> 100, p -> p.getZ() < 12);
      int count = 0;
      while (wave.hasNext()) {
         BlockPos pos = wave.next();
         assertTrue(pos.getY() >= 80 && pos.getY() < 90);
         assertTrue(pos.getZ() >= 2 && pos.getZ() < 12);
         count++;
      }
      int expected=0;for(int a=0;a<10;a++)for(int y=80;y<90;y++)
         if(100-y<shape(9).depthAt(a))expected+=2*shape(9).geometry().halfWidth(a,100-y)+1;
      assertEquals(expected,count);
      assertFalse(shape(9).wave(110, 120).hasNext());
      assertFalse(shape(9).wave(90, 90).hasNext());
   }

   @Test
   void deterministicForSeed() {
      WorldCutShape a = shape(11), b = shape(11), c = shape(1234);
      boolean differs = false;
      for (int i = 0; i < 200; i++) {
         assertEquals(a.depthAt(i), b.depthAt(i));
         differs |= a.depthAt(i) != c.depthAt(i);
      }
      assertTrue(differs);
   }

   @Test
   void followsSurfaceAndIgnoresPitch() {
      WorldCutShape shape = new WorldCutShape(new Vec3(.5, 100, .5), new Vec3(0, 5, 2), 4);
      assertEquals(new Vec3(0, 0, 1), shape.dirXZ());
      Iterator<BlockPos> wave = shape.wave(-64, 320, along -> 100 + along, p -> true);
      while (wave.hasNext()) {
         BlockPos p = wave.next();
         int along = p.getZ() - 2;
         assertTrue(p.getY() <= 100 + along);
         assertTrue(p.getY() > 100 + along - shape.depthAt(along));
      }
   }
}
