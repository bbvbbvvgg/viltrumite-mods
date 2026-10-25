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

   /** maxY is exclusive. Border probes are horizontal and never access a world. */
   public Wave wave(int minY, int maxY, IntUnaryOperator surface, Predicate<Cell> border) {
      List<List<Cell>> columns = new ArrayList<>(length);
      int[] tops = new int[length], bottoms = new int[length], surfaces = new int[length];
      for (int a = 0; a < length; a++) {
         int y = surface.applyAsInt(a);
         surfaces[a] = y;
         tops[a] = Math.min(y, maxY - 1);
         bottoms[a] = Math.max(y - depthAt(a) + 1, minY);
         List<Cell> slice = new ArrayList<>();
         if (minY < maxY && tops[a] >= bottoms[a]) {
            Bounds box = hitBox(a, y);
            // Include both bounding edges. Projection owns the half-open slice;
            // the upper world-axis edge belongs to it when facing backwards.
            int minX = (int) Math.floor(box.minX() - .5 - 1E-8), endX = (int) Math.floor(box.maxX() - .5 + 1E-8) + 1;
            int minZ = (int) Math.floor(box.minZ() - .5 - 1E-8), endZ = (int) Math.floor(box.maxZ() - .5 + 1E-8) + 1;
            for (int x = minX; x < endX; x++) for (int z = minZ; z < endZ; z++) {
               double vx = x + .5 - ox, vz = z + .5 - oz;
               int projectedAlong = (int) Math.floor(vx * dx + vz * dz + 1E-8);
               double across = -vx * dz + vz * dx;
               if (projectedAlong == a && across >= -width / 2.0 - 1E-8 && across < width / 2.0 - 1E-8) {
                  Cell cell = new Cell(x, y, z, a, 0);
                  if (border.test(cell)) slice.add(cell);
               }
            }
         }
         columns.add(slice);
      }
      int total = 0;
      for (int a = 0; a < length; a++) total += columns.get(a).size() * Math.max(0, tops[a] - bottoms[a] + 1);
      final int work = total;
      return new Wave() {
         private int along, y = tops[0], column;
         private int remaining = work, front;
         public int remainingWork() { return remaining; }
         public int frontAlong() { return front; }
         public boolean carve(Cell c) {
            double vx=c.x()+.5-ox, vz=c.z()+.5-oz;
            return Math.abs(-vx*dz+vz*dx) < halfWidth(c.along(),c.down())+.5;
         }
         private void advanceEmpty() {
            while (along < length && (columns.get(along).isEmpty() || y < bottoms[along])) {
               along++;
               column = 0;
               if (along < length) y = tops[along];
            }
         }
         public boolean hasNext() { advanceEmpty(); return along < length; }
         public Cell next() {
            if (!hasNext()) throw new NoSuchElementException();
            Cell c = columns.get(along).get(column++);
            Cell result = new Cell(c.x(), y, c.z(), along, surfaces[along] - y);
            remaining--;
            front = along + 1;
            if (column == columns.get(along).size()) { column = 0; y--; }
            return result;
         }
      };
   }
}
