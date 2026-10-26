package dev.baranhan.viltrumitecore.hero.reinhard.saw;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Saw Sphere's barrier: the 12-block sphere voxelised into half-block cells aligned to the
 * block grid. A <em>surface cell</em> is a cell the sphere surface passes through above Y0; the
 * union of surface cells separates the inside from the outside (any path through the surface
 * crosses one). The cell list is a pure function of the centre, so client and server enumerate
 * the same cells and the start packet carries only the activity bits.
 *
 * <p>Activity is decided once, at activation ({@link #build}): a flood fill from the centre over
 * empty cells inside the ball and above Y0 reaches the shell exactly where open air connects it
 * to the caster; cells behind terrain, walls or under the ground stay inactive, so the shell
 * ends where it meets real blocks. Cells a block only partly fills next to the reached air stay
 * active too (no gaps along slabs, snow or fences). The mask never changes afterwards: a cut
 * boundary block leaves a real hole, a cell is never healed or grown. Visual = collision.
 */
public final class SawShell {
   public static final double CELL = 0.5;
   public static final int FREE = 0;
   public static final int PARTIAL = 1;
   public static final int FULL = 2;
   private static final int MAX_SCAN = 20000;

   /** Cell occupancy by real block collision: {@link #FREE}, {@link #PARTIAL} or {@link #FULL}. */
   @FunctionalInterface
   public interface Occupancy {
      int at(int ix, int iy, int iz);
   }

   public final double cx;
   public final double y0;
   public final double cz;
   public final double radius;
   private final long[] keys;
   private final Long2IntOpenHashMap index;
   private final BitSet active;
   private final LongOpenHashSet activeKeys = new LongOpenHashSet();
   private final AABB bounds;
   /** Keys are relative to this cell (the centre's), so any world coordinate packs into 21 bits. */
   private final int ox, oy, oz;

   private SawShell(double cx, double y0, double cz, double radius, BitSet active) {
      this.cx = cx;
      this.y0 = y0;
      this.cz = cz;
      this.radius = radius;
      this.ox = cellOf(cx);
      this.oy = cellOf(y0);
      this.oz = cellOf(cz);
      this.keys = enumerate(cx, y0, cz, radius);
      this.index = new Long2IntOpenHashMap(this.keys.length * 2);
      this.index.defaultReturnValue(-1);
      for (int i = 0; i < this.keys.length; i++) this.index.put(this.keys[i], i);
      this.active = active == null ? new BitSet(this.keys.length) : (BitSet) active.clone();
      if (this.active.length() > this.keys.length) this.active.clear(this.keys.length, this.active.length());
      for (int i = this.active.nextSetBit(0); i >= 0; i = this.active.nextSetBit(i + 1)) this.activeKeys.add(this.keys[i]);
      this.bounds = new AABB(cx - radius - CELL, y0, cz - radius - CELL, cx + radius + CELL, y0 + radius + CELL, cz + radius + CELL);
   }

   /** A shell rebuilt from transmitted bits (client); bits past the cell count are ignored. */
   public static SawShell fromMask(double cx, double y0, double cz, double radius, long[] mask) {
      return new SawShell(cx, y0, cz, radius, BitSet.valueOf(mask));
   }

   public static long key(int ix, int iy, int iz) {
      return ((long) (ix & 0x1FFFFF) << 42) | ((long) (iy & 0x1FFFFF) << 21) | (long) (iz & 0x1FFFFF);
   }

   static int kx(long key) {
      return (int) (key << 1 >> 43);
   }

   static int ky(long key) {
      return (int) (key << 22 >> 43);
   }

   static int kz(long key) {
      return (int) (key << 43 >> 43);
   }

   public static int cellOf(double v) {
      return (int) Math.floor(v / CELL);
   }

   /** Squared distance from the centre to the nearest and farthest points of a cell. */
   private static double near(double c, double lo, double hi) {
      double d = c < lo ? lo - c : c > hi ? c - hi : 0.0;
      return d * d;
   }

   private static double far(double c, double lo, double hi) {
      double d = Math.max(Math.abs(c - lo), Math.abs(c - hi));
      return d * d;
   }

   static boolean above(double y0, int iy) {
      return (iy + 1) * CELL > y0 + 1.0E-6;
   }

   /** Every surface cell above Y0, in a fixed order (iy, ix, iz ascending). */
   public static long[] enumerate(double cx, double y0, double cz, double radius) {
      double r2 = radius * radius;
      int x0 = cellOf(cx - radius) - 1, x1 = cellOf(cx + radius) + 1;
      int z0 = cellOf(cz - radius) - 1, z1 = cellOf(cz + radius) + 1;
      int ya = cellOf(y0) - 1, yb = cellOf(y0 + radius) + 1;
      int ox = cellOf(cx), oy = cellOf(y0), oz = cellOf(cz);
      long[] out = new long[4096];
      int n = 0;
      for (int iy = ya; iy <= yb; iy++) {
         if (!above(y0, iy)) continue;
         double ny = near(y0, iy * CELL, (iy + 1) * CELL), fy = far(y0, iy * CELL, (iy + 1) * CELL);
         for (int ix = x0; ix <= x1; ix++) {
            double nx = near(cx, ix * CELL, (ix + 1) * CELL), fx = far(cx, ix * CELL, (ix + 1) * CELL);
            for (int iz = z0; iz <= z1; iz++) {
               double nz = near(cz, iz * CELL, (iz + 1) * CELL), fz = far(cz, iz * CELL, (iz + 1) * CELL);
               if (nx + ny + nz <= r2 && fx + fy + fz >= r2) {
                  if (n == out.length) out = java.util.Arrays.copyOf(out, n * 2);
                  out[n++] = key(ix - ox, iy - oy, iz - oz);
               }
            }
         }
      }
      return java.util.Arrays.copyOf(out, n);
   }

   /**
    * The activation mask: flood fill from the centre (first free cell up to 2 blocks above Y0)
    * over FREE cells of the ball above Y0; a surface cell is active when the fill reached it, or
    * when it is only PARTIAL and touches a reached cell. Null when the centre itself is buried.
    */
   @Nullable
   public static SawShell build(double cx, double y0, double cz, double radius, Occupancy occupancy) {
      double r2 = radius * radius;
      int sx = cellOf(cx), sz = cellOf(cz);
      int sy = Integer.MIN_VALUE;
      for (int k = 0; k <= 4; k++) {
         int iy = cellOf(y0 + 0.25 + k * CELL);
         if (occupancy.at(sx, iy, sz) == FREE) {
            sy = iy;
            break;
         }
      }
      if (sy == Integer.MIN_VALUE) return null;

      LongOpenHashSet reached = new LongOpenHashSet(65536);
      LongArrayFIFOQueue queue = new LongArrayFIFOQueue(8192);
      int ox = sx, oy = cellOf(y0), oz = sz;
      long seed = key(sx - ox, sy - oy, sz - oz);
      reached.add(seed);
      queue.enqueue(seed);
      int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
      while (!queue.isEmpty()) {
         long k = queue.dequeueLong();
         int x = kx(k) + ox, y = ky(k) + oy, z = kz(k) + oz;
         for (int[] s : steps) {
            int nx = x + s[0], ny = y + s[1], nz = z + s[2];
            if (!above(y0, ny)) continue;
            double d = near(cx, nx * CELL, (nx + 1) * CELL) + near(y0, ny * CELL, (ny + 1) * CELL) + near(cz, nz * CELL, (nz + 1) * CELL);
            if (d > r2) continue;
            long nk = key(nx - ox, ny - oy, nz - oz);
            if (reached.contains(nk) || occupancy.at(nx, ny, nz) != FREE) continue;
            reached.add(nk);
            queue.enqueue(nk);
         }
      }

      long[] cells = enumerate(cx, y0, cz, radius);
      BitSet mask = new BitSet(cells.length);
      for (int i = 0; i < cells.length; i++) {
         long k = cells[i];
         if (reached.contains(k)) {
            mask.set(i);
            continue;
         }
         int x = kx(k) + ox, y = ky(k) + oy, z = kz(k) + oz;
         if (occupancy.at(x, y, z) != PARTIAL) continue;
         for (int[] s : steps) {
            if (reached.contains(key(x + s[0] - ox, y + s[1] - oy, z + s[2] - oz))) {
               mask.set(i);
               break;
            }
         }
      }
      return new SawShell(cx, y0, cz, radius, mask);
   }

   public int cellCount() {
      return this.keys.length;
   }

   public int activeCount() {
      return this.activeKeys.size();
   }

   public long[] mask() {
      return this.active.toLongArray();
   }

   /** Absolute cell coordinates of surface cell {@code i}. */
   public int cellX(int i) {
      return kx(this.keys[i]) + this.ox;
   }

   public int cellY(int i) {
      return ky(this.keys[i]) + this.oy;
   }

   public int cellZ(int i) {
      return kz(this.keys[i]) + this.oz;
   }

   public boolean activeIndex(int i) {
      return this.active.get(i);
   }

   public boolean activeCell(int ix, int iy, int iz) {
      return this.activeKeys.contains(key(ix - this.ox, iy - this.oy, iz - this.oz));
   }

   /** The active surface cell under a point (a point on the sphere always lies in a surface cell). */
   public boolean activeAt(double x, double y, double z) {
      return this.activeCell(cellOf(x), cellOf(y), cellOf(z));
   }

   /** Surface cell index under a point, -1 when it is not a surface cell. */
   public int indexAt(double x, double y, double z) {
      return this.index.get(key(cellOf(x) - this.ox, cellOf(y) - this.oy, cellOf(z) - this.oz));
   }

   public AABB bounds() {
      return this.bounds;
   }

   public static AABB cellBox(int ix, int iy, int iz) {
      return new AABB(ix * CELL, iy * CELL, iz * CELL, (ix + 1) * CELL, (iy + 1) * CELL, (iz + 1) * CELL);
   }

   /** Adds the active cells touching {@code box} as collision shapes; returns the (possibly new) list. */
   public List<VoxelShape> collect(AABB box, List<VoxelShape> shapes, boolean[] copied) {
      if (!box.intersects(this.bounds) || this.activeKeys.isEmpty()) return shapes;
      int x0 = cellOf(box.minX), x1 = cellOf(box.maxX), y0c = cellOf(box.minY), y1 = cellOf(box.maxY), z0 = cellOf(box.minZ), z1 = cellOf(box.maxZ);
      long volume = (long) (x1 - x0 + 1) * (y1 - y0c + 1) * (z1 - z0 + 1);
      List<VoxelShape> out = shapes;
      if (volume <= MAX_SCAN) {
         for (int iy = y0c; iy <= y1; iy++) for (int ix = x0; ix <= x1; ix++) for (int iz = z0; iz <= z1; iz++) {
            if (this.activeKeys.contains(key(ix - this.ox, iy - this.oy, iz - this.oz))) out = add(out, copied, ix, iy, iz);
         }
      } else {
         for (int i = this.active.nextSetBit(0); i >= 0; i = this.active.nextSetBit(i + 1)) {
            long k = this.keys[i];
            int ix = kx(k) + this.ox, iy = ky(k) + this.oy, iz = kz(k) + this.oz;
            if (ix >= x0 && ix <= x1 && iy >= y0c && iy <= y1 && iz >= z0 && iz <= z1) out = add(out, copied, ix, iy, iz);
         }
      }
      return out;
   }

   private static List<VoxelShape> add(List<VoxelShape> list, boolean[] copied, int ix, int iy, int iz) {
      List<VoxelShape> out = list;
      if (!copied[0]) {
         out = new ArrayList<>(list.size() + 16);
         out.addAll(list);
         copied[0] = true;
      }
      out.add(Shapes.create(cellBox(ix, iy, iz)));
      return out;
   }

   /** True when the straight segment passes through an active cell (teleports may not jump the shell). */
   public boolean crosses(double ax, double ay, double az, double bx, double by, double bz) {
      double dx = bx - ax, dy = by - ay, dz = bz - az;
      double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
      int steps = (int) Math.min(8000, Math.ceil(length / 0.2));
      for (int s = 0; s <= steps; s++) {
         double u = steps == 0 ? 0.0 : (double) s / steps;
         if (this.activeAt(ax + dx * u, ay + dy * u, az + dz * u)) return true;
      }
      return false;
   }
}
