package dev.baranhan.viltrumitecore.hero.reinhard.cut;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/** Pure raster geometry. The caller supplies the cast's dimensions and start point. */
public record WorldCutGeometry(double ox, double oy, double oz, double dx, double dz,
   long seed, int length, int width, int minDepth, int maxDepth, int above) {
   public record Cell(int x, int y, int z, int along, int down) { }
   public interface Wave extends Iterator<Cell> {
      int remainingWork();
      int frontAlong();
      /** Masking never hides work from the shared visit budget. */
      default boolean carve(Cell cell) { return true; }
   }
   public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      public double widthX() { return maxX - minX; }
   }

   public WorldCutGeometry {
      if (!Double.isFinite(ox) || !Double.isFinite(oy) || !Double.isFinite(oz)
         || !Double.isFinite(dx) || !Double.isFinite(dz) || length < 1 || width < 1
         || (width & 1) == 0 || minDepth < 1 || maxDepth < minDepth || above < 0) {
         throw new IllegalArgumentException("Invalid world cut geometry");
      }
      double norm = Math.hypot(dx, dz);
      if (norm < 1E-8) { dx = 0; dz = 1; }
      else { dx /= norm; dz /= norm; }
   }

   /** Smooth seeded noise, rounded to whole block depths. */
   public int depthAt(int along) {
      int knot = Math.floorDiv(along, 16);
      double t = Math.floorMod(along, 16) / 16.0;
      t = t * t * (3 - 2 * t);
      double n = noise(knot) * (1 - t) + noise(knot + 1) * t;
      double end = Math.min(1,Math.min((along+4)/16.0,(length-along+3)/20.0));
      return Math.max(1,(int)Math.round((minDepth+n*(maxDepth-minDepth))*end));
   }

   private double noise(int knot) {
      long h = seed + knot * 0x9E3779B97F4A7C15L;
      h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
      h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
      h ^= h >>> 31;
      return (h >>> 11) * 0x1.0p-53;
   }

   public boolean contains(int along, int across, int down) {
      return along >= 0 && along < length && Math.abs((long) across) <= halfWidth(along, down)
         && down >= 0 && down < depthAt(along);
   }

   /** Coherent banks, depth terraces and tapered finite endpoints. No isolated holes. */
   public int halfWidth(int along, int down) {
      double end = Math.min(1, Math.min((along + 2) / 16.0, (length - along + 1) / 22.0));
      double depth = Math.max(0, Math.min(1, down / (double) depthAt(along)));
      int k=Math.floorDiv(along,12);double blend=Math.floorMod(along,12)/12.0;
      blend=blend*blend*(3-2*blend);
      double banks = .82+.18*(noise(k)*(1-blend)+noise(k+1)*blend);
      double terraces = 1 - .72 * (Math.floor(depth * 5) / 5);
      return Math.max(0, (int)Math.floor(width * .5 * end * banks * terraces));
   }

   public int volume() {
      int volume = 0;
      for (int a = 0; a < length; a++) for (int d=0; d<depthAt(a); d++) volume += 2*halfWidth(a,d)+1;
      return volume;
   }

   public Bounds hitBox(int along) { return hitBox(along, (int) Math.floor(oy) - 1); }

   public Bounds hitBox(int along, int surface) {
      double half = width / 2.0;
      double x0 = ox + dx * along, z0 = oz + dz * along;
      double x1 = x0 + dx, z1 = z0 + dz;
      double ex = Math.abs(dz) * half, ez = Math.abs(dx) * half;
      return new Bounds(Math.min(x0, x1) - ex, surface - depthAt(along) + 1,
         Math.min(z0, z1) - ez, Math.max(x0, x1) + ex, surface + 1 + above,
         Math.max(z0, z1) + ez);
   }

   public Wave wave(int minY, int maxY) {
      return wave(minY, maxY, a -> (int) Math.floor(oy) - 1, p -> true);
   }

   /** One raster column of a slice: block x, z and the signed distance of its centre from the axis. */
   public record Column(int x, int z, double across) { }

   /**
    * Top of one raster column, inclusive. {@code surface} is the slice's reference ground (depth, terraces and the
    * mask are measured from it); a column may start above it (a bump, a plant, a tree in the trench) or below it.
    */
   @FunctionalInterface
   public interface ColumnTop {
      /** Every column of a slice starts at the slice's reference surface (a flat snapshot). */
      ColumnTop SLICE = (x, z, along, surface) -> surface;
      int top(int x, int z, int along, int surface);
   }

   /**
    * The block columns of slice {@code along}: every column whose centre projects into [along, along + 1) and
    * [-width / 2, width / 2) across. Neighbouring slices partition the footprint: no column twice, no gap.
    */
   public List<Column> columns(int along) {
      List<Column> out = new ArrayList<>();
      if (along < 0 || along >= length) return out;
      Bounds box = hitBox(along, 0);
      // Include both bounding edges. Projection owns the half-open slice;
      // the upper world-axis edge belongs to it when facing backwards.
      int minX = (int) Math.floor(box.minX() - .5 - 1E-8), endX = (int) Math.floor(box.maxX() - .5 + 1E-8) + 1;
      int minZ = (int) Math.floor(box.minZ() - .5 - 1E-8), endZ = (int) Math.floor(box.maxZ() - .5 + 1E-8) + 1;
      for (int x = minX; x < endX; x++) for (int z = minZ; z < endZ; z++) {
         double vx = x + .5 - ox, vz = z + .5 - oz;
         int projectedAlong = (int) Math.floor(vx * dx + vz * dz + 1E-8);
         double across = -vx * dz + vz * dx;
         if (projectedAlong == along && across >= -width / 2.0 - 1E-8 && across < width / 2.0 - 1E-8) {
            out.add(new Column(x, z, across));
         }
      }
      return out;
   }

   /** Inside the carve mask: the trench's coherent banks and terraces at a signed depth below the slice surface. */
   public boolean masked(int along, double across, int down) {
      return Math.abs(across) < halfWidth(along, down) + .5;
   }

   /** maxY is exclusive. Border probes are horizontal and never access a world. Every column starts at the slice
    *  surface ({@link ColumnTop#SLICE}). */
   public Wave wave(int minY, int maxY, IntUnaryOperator surface, Predicate<Cell> border) {
      return wave(minY, maxY, surface, ColumnTop.SLICE, border);
   }

   /**
    * maxY is exclusive. Each column is visited from its own top (clamped to the build height) down to the slice's
    * floor, so terrain, plants and trees standing above the slice's reference surface inside the footprint are part
    * of the work instead of being left floating over the trench. Within a slice the wave still runs row by row from
    * the highest top down; a row only visits the columns that reach it. Every visit is counted work.
    */
   public Wave wave(int minY, int maxY, IntUnaryOperator surface, ColumnTop columnTop, Predicate<Cell> border) {
      int[][] xs = new int[length][], zs = new int[length][], tops = new int[length][];
      int[] bottoms = new int[length], surfaces = new int[length];
      long total = 0;
      for (int a = 0; a < length; a++) {
         int y = surface.applyAsInt(a);
         surfaces[a] = y;
         int bottom = Math.max(y - depthAt(a) + 1, minY);
         bottoms[a] = bottom;
         List<int[]> slice = new ArrayList<>();
         if (minY < maxY) {
            for (Column column : columns(a)) {
               if (!border.test(new Cell(column.x(), y, column.z(), a, 0))) continue;
               int top = Math.min(columnTop.top(column.x(), column.z(), a, y), maxY - 1);
               if (top >= bottom) slice.add(new int[] {column.x(), column.z(), top});
            }
         }
         // Highest tops first (stable): row y visits the prefix of columns that reach it.
         slice.sort((p, q) -> Integer.compare(q[2], p[2]));
         int n = slice.size();
         xs[a] = new int[n]; zs[a] = new int[n]; tops[a] = new int[n];
         for (int i = 0; i < n; i++) {
            int[] c = slice.get(i);
            xs[a][i] = c[0]; zs[a][i] = c[1]; tops[a][i] = c[2];
            total += c[2] - bottom + 1;
         }
      }
      if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("World cut wave too large");
      final int work = (int) total;
      return new Wave() {
         private int along = -1, y, column, reach;
         private int remaining = work, front;
         public int remainingWork() { return remaining; }
         public int frontAlong() { return front; }
         public boolean carve(Cell c) {
            double vx = c.x() + .5 - ox, vz = c.z() + .5 - oz;
            return masked(c.along(), -vx * dz + vz * dx, c.down());
         }
         private void enter(int a) {
            along = a;
            column = 0;
            reach = 0;
            if (a < length && tops[a].length > 0) {
               y = tops[a][0];
               widen();
            }
         }
         private void widen() {
            int[] t = tops[along];
            while (reach < t.length && t[reach] >= y) reach++;
         }
         private void settle() {
            if (along < 0) enter(0);
            while (along < length) {
               if (tops[along].length == 0 || y < bottoms[along]) { enter(along + 1); continue; }
               if (column < reach) return;
               y--;
               column = 0;
               widen();
            }
         }
         public boolean hasNext() { settle(); return along < length; }
         public Cell next() {
            if (!hasNext()) throw new NoSuchElementException();
            int i = column++;
            remaining--;
            front = along + 1;
            return new Cell(xs[along][i], y, zs[along][i], along, surfaces[along] - y);
         }
      };
   }
}
