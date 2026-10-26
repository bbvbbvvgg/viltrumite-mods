package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutCarve;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutGeometry;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutShape;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutSurface;
import java.util.ArrayDeque;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * REINHARD 3b item 6: the ordinary World Cut carves a whole, clean trench on uneven, overgrown terrain.
 *
 * <p>A synthetic world (slopes and bumps, plants and snow layers on the ground, trees, protected blocks, a world
 * border across the far end) is carved by the production queue and raster ({@link WorldCutCarve} stepping the
 * {@link WorldCutShape} wave with the server's budget), with the production surface rules ({@link WorldCutSurface}).
 * Then: every destroyable block of the allowed volume is gone, nothing outside it changed, protected blocks and the
 * border are respected, and no block inside the footprint is left floating (6-connected flood fill from the
 * untouched world). The same world carved with the old one-height-per-slice snapshot does leave islands.
 */
class WorldCutIntegrityTest {
   static final byte AIR = 0, STONE = 1, DIRT = 2, GRASS = 3, PLANT = 4, SNOW = 5, LOG = 6, LEAVES = 7, BEDROCK = 8;
   static final int X0 = -128, X1 = 20, Z0 = -14, Z1 = 186, Y0 = 0, Y1 = 112;
   static final int BORDER_X = -100;

   /** A small voxel world; the cut's yaw is diagonal so slice borders and rounding are exercised. */
   static final class World {
      final byte[] blocks = new byte[(X1 - X0) * (Z1 - Z0) * (Y1 - Y0)];

      static int index(int x, int y, int z) { return ((x - X0) * (Z1 - Z0) + (z - Z0)) * (Y1 - Y0) + (y - Y0); }
      static boolean inside(int x, int y, int z) { return x >= X0 && x < X1 && z >= Z0 && z < Z1 && y >= Y0 && y < Y1; }
      byte get(int x, int y, int z) { return inside(x, y, z) ? blocks[index(x, y, z)] : AIR; }
      void set(int x, int y, int z, byte b) { if (inside(x, y, z)) blocks[index(x, y, z)] = b; }

      static int hash(int x, int z) {
         int h = x * 0x45d9f3b ^ z * 0x119de1f3;
         h = (h ^ (h >>> 16)) * 0x45d9f3b;
         return (h ^ (h >>> 16)) & 0x7fffffff;
      }

      static int terrain(int x, int z) {
         return 70 + (int)Math.round(5 * Math.sin(x / 11.0) + 4 * Math.cos(z / 13.0) + 2 * Math.sin((x + z) / 5.0));
      }

      static World generate() {
         World w = new World();
         for (int x = X0; x < X1; x++) for (int z = Z0; z < Z1; z++) {
            int h = terrain(x, z);
            for (int y = Y0; y <= h; y++) w.set(x, y, z, y == Y0 ? BEDROCK : y < h - 3 ? STONE : y < h ? DIRT : GRASS);
            if (hash(x, z) % 173 == 0) w.set(x, h - 12, z, BEDROCK);
         }
         for (int x = X0; x < X1; x++) for (int z = Z0; z < Z1; z++) {
            if (hash(x, z) % 53 != 0) continue;
            int h = terrain(x, z);
            for (int dy = 3; dy <= 5; dy++) {
               int r = dy == 5 ? 1 : 2;
               for (int ax = -r; ax <= r; ax++) for (int az = -r; az <= r; az++)
                  if (w.get(x + ax, h + 1 + dy, z + az) == AIR) w.set(x + ax, h + 1 + dy, z + az, LEAVES);
            }
            for (int y = h + 1; y <= h + 5; y++) w.set(x, y, z, LOG);
         }
         for (int x = X0; x < X1; x++) for (int z = Z0; z < Z1; z++) {
            int h = terrain(x, z), k = hash(x, z);
            if (w.get(x, h + 1, z) != AIR) continue;
            if (k % 5 == 1) w.set(x, h + 1, z, PLANT);
            else if (k % 7 == 2) w.set(x, h + 1, z, SNOW);
         }
         return w;
      }

      World copy() {
         World w = new World();
         System.arraycopy(blocks, 0, w.blocks, 0, blocks.length);
         return w;
      }

      /** Highest block matching, or UNKNOWN outside the loaded area / the border. */
      WorldCutSurface.Heights heights(Predicate<Byte> counts) {
         return (x, z) -> {
            if (x < X0 || x >= X1 || z < Z0 || z >= Z1 || !border(x)) return WorldCutSurface.UNKNOWN;
            for (int y = Y1 - 1; y >= Y0; y--) if (counts.test(get(x, y, z))) return y;
            return WorldCutSurface.UNKNOWN;
         };
      }
   }

   static boolean border(int x) { return x > BORDER_X; }
   static boolean destroyable(byte b) { return b != AIR && b != BEDROCK; }
   static boolean motionBlockingNoLeaves(byte b) { return b == STONE || b == DIRT || b == GRASS || b == LOG || b == BEDROCK; }

   static final class Probe implements WorldCutCarve.Probe {
      final World world;
      Probe(World world) { this.world = world; }
      public boolean loaded(BlockPos p) { return World.inside(p.getX(), p.getY(), p.getZ()) && border(p.getX()); }
      public boolean canDestroy(BlockPos p) { return destroyable(world.get(p.getX(), p.getY(), p.getZ())); }
      public boolean solid(BlockPos p) { byte b = world.get(p.getX(), p.getY(), p.getZ()); return b != AIR && b != PLANT && b != SNOW; }
      public boolean canLaunch(BlockPos p) { return true; }
      public boolean clear(BlockPos p) { world.set(p.getX(), p.getY(), p.getZ(), AIR); return true; }
      public boolean launch(BlockPos p, Vec3 velocity) { world.set(p.getX(), p.getY(), p.getZ(), AIR); return true; }
   }

   static final WorldCutShape SHAPE = new WorldCutShape(new Vec3(.37, 72, .61),
      Vec3.directionFromRotation(0, 33), 0x5EED_1234L);
   static final Predicate<BlockPos> BORDER = p -> border(p.getX());

   /** Carves to completion with the server's per-tick budget; returns the number of steps. */
   static int carve(World world, int[] surface, WorldCutGeometry.ColumnTop tops) {
      WorldCutGeometry.Wave wave = SHAPE.cells(Y0, Y1, a -> surface[a], tops, BORDER);
      int work = wave.remainingWork();
      WorldCutCarve job = new WorldCutCarve(wave, new Probe(world), SHAPE.origin(), SHAPE.dirXZ());
      int steps = 0;
      while (!job.step(null, 2000)) assertTrue(++steps < 10_000, "the carve finishes");
      assertEquals(0, job.remainingWork());
      assertEquals(work, job.visited(), "every queued cell was visited once");
      assertEquals(0, job.failures());
      return steps;
   }

   /** Floating non-air blocks whose column lies inside the trench footprint (protected blocks excluded). */
   static int islandsInFootprint(World world, boolean[] footprint) {
      int nx = X1 - X0, nz = Z1 - Z0, ny = Y1 - Y0;
      boolean[] reached = new boolean[world.blocks.length];
      ArrayDeque<int[]> queue = new ArrayDeque<>();
      for (int x = X0; x < X1; x++) for (int z = Z0; z < Z1; z++) for (int y = Y0; y < Y1; y++) {
         boolean edge = x == X0 || x == X1 - 1 || z == Z0 || z == Z1 - 1 || y == Y0;
         if (edge && world.get(x, y, z) != AIR) { reached[World.index(x, y, z)] = true; queue.add(new int[] {x, y, z}); }
      }
      int[][] steps = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
      while (!queue.isEmpty()) {
         int[] p = queue.poll();
         for (int[] d : steps) {
            int x = p[0] + d[0], y = p[1] + d[1], z = p[2] + d[2];
            if (!World.inside(x, y, z) || world.get(x, y, z) == AIR || world.get(x, y, z) == BEDROCK) continue;
            int i = World.index(x, y, z);
            if (reached[i]) continue;
            reached[i] = true;
            queue.add(new int[] {x, y, z});
         }
      }
      int islands = 0;
      for (int x = X0; x < X1; x++) for (int z = Z0; z < Z1; z++) {
         if (!footprint[(x - X0) * nz + (z - Z0)]) continue;
         for (int y = Y0; y < Y1; y++) {
            byte b = world.get(x, y, z);
            if (b != AIR && b != BEDROCK && !reached[World.index(x, y, z)]) islands++;
         }
      }
      assertEquals(nx * nz * ny, world.blocks.length);
      return islands;
   }

   /** Columns inside the trench's top layer (inside the border). */
   static boolean[] footprint() {
      boolean[] in = new boolean[(X1 - X0) * (Z1 - Z0)];
      WorldCutGeometry g = SHAPE.geometry();
      for (int a = 0; a < g.length(); a++) for (WorldCutGeometry.Column c : g.columns(a))
         if (g.masked(a, c.across(), 0) && border(c.x()) && World.inside(c.x(), Y0, c.z()))
            in[(c.x() - X0) * (Z1 - Z0) + (c.z() - Z0)] = true;
      return in;
   }

   @Test
   void carvesWholeTrenchWithoutIslandsOnOvergrownTerrain() {
      World world = World.generate(), before = world.copy();
      WorldCutGeometry g = SHAPE.geometry();
      int[] surface = WorldCutSurface.references(g, world.heights(WorldCutIntegrityTest::motionBlockingNoLeaves), 71);
      WorldCutGeometry.ColumnTop tops = WorldCutSurface.tops(world.heights(b -> b != AIR), ReinhardRules.CUT_ABOVE);

      // The allowed volume, from the world as it is at the blast.
      boolean[] allowed = new boolean[world.blocks.length];
      int allowedSolid = 0;
      for (int a = 0; a < g.length(); a++) {
         int bottom = Math.max(surface[a] - g.depthAt(a) + 1, Y0);
         for (WorldCutGeometry.Column c : g.columns(a)) {
            if (!border(c.x())) continue;
            int top = Math.min(tops.top(c.x(), c.z(), a, surface[a]), Y1 - 1);
            for (int y = top; y >= bottom; y--) {
               if (!World.inside(c.x(), y, c.z()) || !g.masked(a, c.across(), surface[a] - y)) continue;
               assertFalse(allowed[World.index(c.x(), y, c.z())], "slices partition the footprint");
               allowed[World.index(c.x(), y, c.z())] = true;
               if (destroyable(before.get(c.x(), y, c.z()))) allowedSolid++;
            }
         }
      }
      assertTrue(allowedSolid > 60_000, "a real trench: " + allowedSolid);

      carve(world, surface, tops);

      int left = 0, outside = 0, plantsLeft = 0;
      for (int x = X0; x < X1; x++) for (int z = Z0; z < Z1; z++) for (int y = Y0; y < Y1; y++) {
         int i = World.index(x, y, z);
         byte was = before.blocks[i], now = world.blocks[i];
         if (allowed[i]) {
            if (destroyable(was) && now != AIR) left++;
            if (was == BEDROCK) assertEquals(BEDROCK, now, "protected blocks survive");
         } else if (was != now) {
            outside++;
         }
      }
      assertEquals(0, left, "every destroyable block of the allowed volume is carved");
      assertEquals(0, outside, "nothing outside the allowed volume changes");
      for (int x = X0; x <= BORDER_X; x++) for (int z = Z0; z < Z1; z++) for (int y = Y0; y < Y1; y++)
         assertEquals(before.get(x, y, z), world.get(x, y, z), "the world border is respected");

      boolean[] footprint = footprint();
      for (int x = X0; x < X1; x++) for (int z = Z0; z < Z1; z++) {
         if (!footprint[(x - X0) * (Z1 - Z0) + (z - Z0)]) continue;
         for (int y = Y0; y < Y1; y++) if (world.get(x, y, z) == PLANT || world.get(x, y, z) == SNOW) {
            // A plant or a snow layer may stay only on ground that stayed (outside the mask of a lower terrace).
            if (world.get(x, y - 1, z) == AIR) plantsLeft++;
         }
      }
      assertEquals(0, plantsLeft, "no plant or snow layer hangs over the trench");
      assertEquals(0, islandsInFootprint(world, footprint), "no floating block inside the footprint");
   }

   @Test
   void oldSliceSnapshotLeftIslands() {
      // The regression this test guards: one WORLD_SURFACE sample at the axis per slice, every column from it.
      World world = World.generate();
      int[] surface = new int[ReinhardRules.CUT_LENGTH];
      WorldCutSurface.Heights highest = world.heights(b -> b != AIR);
      for (int a = 0; a < surface.length; a++) {
         BlockPos p = BlockPos.containing(SHAPE.axis(a + .5));
         int h = highest.at(p.getX(), p.getZ());
         surface[a] = h == WorldCutSurface.UNKNOWN ? 71 : h;
      }
      carve(world, surface, WorldCutGeometry.ColumnTop.SLICE);
      assertTrue(islandsInFootprint(world, footprint()) > 50, "the old snapshot leaves floating terrain and plants");
   }

   @Test
   void referenceIgnoresTrunksCanopiesAndPlants() {
      WorldCutGeometry g = SHAPE.geometry();
      // Flat ground at 64 with a trunk column on the axis: the median stays on the ground.
      WorldCutSurface.Heights ground = (x, z) -> x == 0 ? 70 : 64;
      for (int a = 0; a < 40; a++) assertEquals(64, WorldCutSurface.reference(g, a, ground, 0));
      assertEquals(-7, WorldCutSurface.reference(g, 5, (x, z) -> WorldCutSurface.UNKNOWN, -7), "unknown ground falls back");
      WorldCutGeometry.ColumnTop tops = WorldCutSurface.tops((x, z) -> x == 3 ? 200 : x == 4 ? WorldCutSurface.UNKNOWN : 66, 20);
      assertEquals(84, tops.top(3, 0, 0, 64), "never more than CUT_ABOVE over the reference");
      assertEquals(64, tops.top(4, 0, 0, 64), "an unknown column starts at the reference");
      assertEquals(66, tops.top(5, 0, 0, 64), "a bump starts at its own top");
   }

   @Test
   void columnTopsKeepBudgetAccountingAndOrder() {
      WorldCutGeometry g = SHAPE.geometry();
      WorldCutGeometry.ColumnTop bumpy = (x, z, a, s) -> s + Math.floorMod(x * 7 + z * 3, 5) - 1;
      WorldCutGeometry.Wave wave = g.wave(Y0, Y1, a -> 70, bumpy, c -> true);
      int expected = 0;
      for (int a = 0; a < g.length(); a++) {
         int bottom = Math.max(70 - g.depthAt(a) + 1, Y0);
         for (WorldCutGeometry.Column c : g.columns(a)) expected += Math.max(0, bumpy.top(c.x(), c.z(), a, 70) - bottom + 1);
      }
      assertEquals(expected, wave.remainingWork());
      int lastAlong = 0, lastY = Integer.MAX_VALUE, seen = 0;
      java.util.Set<Long> unique = new java.util.HashSet<>();
      while (wave.hasNext()) {
         WorldCutGeometry.Cell c = wave.next();
         seen++;
         assertTrue(c.along() >= lastAlong, "slices in order");
         if (c.along() != lastAlong) lastY = Integer.MAX_VALUE;
         assertTrue(c.y() <= lastY, "top-down within each slice");
         assertTrue(c.y() <= bumpy.top(c.x(), c.z(), c.along(), 70), "a row only visits columns that reach it");
         assertEquals(70 - c.y(), c.down());
         assertTrue(unique.add(BlockPos.asLong(c.x(), c.y(), c.z())), "no duplicate cell");
         lastAlong = c.along(); lastY = c.y();
         assertEquals(expected - seen, wave.remainingWork());
      }
      assertEquals(expected, seen);
   }
}
