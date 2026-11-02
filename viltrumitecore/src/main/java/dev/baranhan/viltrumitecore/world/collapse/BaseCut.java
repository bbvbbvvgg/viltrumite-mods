package dev.baranhan.viltrumitecore.world.collapse;

import java.util.BitSet;
import javax.annotation.Nullable;

/**
 * The footprint of a base cut (pure, unit tested). Atheria buildings touch their neighbours (shared walls, sky
 * bridges, a wing on the next block), so after a cut through every wall the plain {@link SupportProbe} still finds
 * the ground through the building next door and nothing falls (#135). The footprint is every column with a removed
 * block plus what those columns enclose (the rooms behind a ring of cut walls); a {@link SupportProbe} restricted to
 * it ({@link SupportProbe#within}) tells whether the building inside stands on anything of its own. Open
 * entrances (an arcade between pillars, a glass door that was air already) up to {@code 2 * GAP} blocks wide are
 * closed first (a morphological closing), so a lobby with an open front still encloses its rooms. A strip of wall,
 * a doorway, an L of two facades or a window band has no inside (too thin) and never qualifies.
 *
 * <p>Upper floors are often wider than the lobby (the Atheria tower at 1215 -200 overhangs the street by one to
 * eight blocks): {@link #over} also lets the probe into columns near the footprint that are empty at the height of
 * the cut. A column that is solid there stands on the street itself: a small free-standing pillar is the
 * building's own support (it still holds the building until it is cut too), anything longer is the neighbour's wall
 * and the probe only counts how much the building leans on it.
 */
public final class BaseCut implements SupportProbe.Columns {
   /** Columns with a removed block: fewer is a doorway or a pillar, not a cut. */
   public static final int MIN_COLUMNS = 16;
   /** Footprint columns whose four neighbours are footprint too: a wall strip or an L of walls has none. */
   public static final int MIN_CORE = 9;
   /** Narrowest footprint side. */
   public static final int MIN_SIDE = 5;
   /** Largest footprint side (a probe stays bounded). */
   public static final int MAX_SIDE = 160;
   /**
    * Share of the severed structure's outer faces that touch something outside the footprint above which the
    * neighbours carry it (a building wedged between others on three sides stays).
    */
   public static final float MAX_CONTACT = 0.6F;
   /** Closing radius: gaps in the cut ring up to twice this wide count as closed (Atheria arcades are 4 wide). */
   public static final int GAP = 2;
   /** How far upper floors may overhang the cut footprint and still fall with it. */
   public static final int OVERHANG = 8;
   /**
    * A free-standing pillar next to the footprint (the Atheria tower stands on a row of them outside its lobby)
    * has at most this many columns at the cut height; a neighbour's wall is longer. The building may stand on it.
    */
   public static final int PILLAR_MAX = 9;
   /**
    * A wall the falling building leaves leaning on its neighbour (the tower's facade standing on the podium wall)
    * is at most this many columns thick outside the falling columns.
    */
   public static final int SHEET_DEPTH = 2;
   /** At most this many remnant wall blocks join one collapse; the rest stays standing. */
   public static final int SHEET_MAX = 40000;
   /** A remnant wall rises at least this many rows above the neighbour (a parapet on the next roof is no sheet). */
   public static final int SHEET_MIN_HEIGHT = 8;

   public final int minX, minZ, width, depth, columns, footprint, core;
   private final BitSet cells;

   private BaseCut(int minX, int minZ, int width, int depth, BitSet cells, int columns, int footprint, int core) {
      this.minX = minX;
      this.minZ = minZ;
      this.width = width;
      this.depth = depth;
      this.cells = cells;
      this.columns = columns;
      this.footprint = footprint;
      this.core = core;
   }

   @Override
   public boolean contains(int x, int z) {
      int i = x - this.minX, j = z - this.minZ;
      return i >= 0 && j >= 0 && i < this.width && j < this.depth && this.cells.get(j * this.width + i);
   }

   /** The footprint of the removed positions, or null when they do not cut out an area (a strip, a doorway). */
   @Nullable
   public static BaseCut of(long[] removed) {
      if (removed.length < MIN_COLUMNS) {
         return null;
      }
      int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
      for (long k : removed) {
         int x = BlockKey.x(k), z = BlockKey.z(k);
         x0 = Math.min(x0, x);
         z0 = Math.min(z0, z);
         x1 = Math.max(x1, x);
         z1 = Math.max(z1, z);
      }
      int w = x1 - x0 + 1, d = z1 - z0 + 1;
      if (w < MIN_SIDE || d < MIN_SIDE || w > MAX_SIDE || d > MAX_SIDE) {
         return null;
      }
      // a border around the bounding box: the outside flood starts there, the closing never touches the edge
      int pad = GAP + 1;
      int gw = w + 2 * pad, gd = d + 2 * pad;
      BitSet cut = new BitSet(gw * gd);
      int columns = 0;
      for (long k : removed) {
         int i = BlockKey.x(k) - x0 + pad, j = BlockKey.z(k) - z0 + pad;
         int c = j * gw + i;
         if (!cut.get(c)) {
            cut.set(c);
            columns++;
         }
      }
      if (columns < MIN_COLUMNS) {
         return null;
      }
      BitSet closed = square(square(cut, gw, gd, true), gw, gd, false);
      closed.or(cut);
      // outside = reachable from the border without crossing the (closed) cut ring (4-connected)
      BitSet outside = new BitSet(gw * gd);
      int[] queue = new int[gw * gd];
      int head = 0, tail = 0;
      queue[tail++] = 0;
      outside.set(0);
      while (head < tail) {
         int c = queue[head++];
         int i = c % gw, j = c / gw;
         if (i > 0) {
            tail = flood(c - 1, closed, outside, queue, tail);
         }
         if (i < gw - 1) {
            tail = flood(c + 1, closed, outside, queue, tail);
         }
         if (j > 0) {
            tail = flood(c - gw, closed, outside, queue, tail);
         }
         if (j < gd - 1) {
            tail = flood(c + gw, closed, outside, queue, tail);
         }
      }
      BitSet cells = new BitSet(w * d);
      int footprint = 0;
      for (int j = 0; j < d; j++) {
         for (int i = 0; i < w; i++) {
            if (!outside.get((j + pad) * gw + i + pad)) {
               cells.set(j * w + i);
               footprint++;
            }
         }
      }
      int core = 0;
      for (int j = 1; j < d - 1; j++) {
         for (int i = 1; i < w - 1; i++) {
            int c = j * w + i;
            if (cells.get(c) && cells.get(c - 1) && cells.get(c + 1) && cells.get(c - w) && cells.get(c + w)) {
               core++;
            }
         }
      }
      if (core < MIN_CORE) {
         return null;
      }
      return new BaseCut(x0, z0, w, d, cells, columns, footprint, core);
   }

   /** Dilation ({@code grow}) or erosion of a grid by a (2 GAP + 1) square; cells off the grid are empty. */
   private static BitSet square(BitSet in, int gw, int gd, boolean grow) {
      BitSet out = new BitSet(gw * gd);
      for (int j = 0; j < gd; j++) {
         for (int i = 0; i < gw; i++) {
            boolean any = false, all = true;
            for (int dj = -GAP; dj <= GAP && (grow ? !any : all); dj++) {
               for (int di = -GAP; di <= GAP; di++) {
                  int a = i + di, b = j + dj;
                  boolean v = a >= 0 && b >= 0 && a < gw && b < gd && in.get(b * gw + a);
                  any |= v;
                  all &= v;
               }
            }
            if (grow ? any : all) {
               out.set(j * gw + i);
            }
         }
      }
      return out;
   }

   /**
    * The columns a probe of this cut may enter: the footprint, plus columns within {@link #OVERHANG} of it that
    * hold nothing solid between {@code cutY} and {@code cutTop} (overhanging upper floors, a sky bridge over the
    * street). Answers are cached per column.
    */
   public SupportProbe.Columns over(CollapseAnalyzer.Volume volume, int cutY, int cutTop) {
      int r = OVERHANG;
      int gw = this.width + 2 * r, gd = this.depth + 2 * r;
      // Chebyshev dilation of the footprint by r, separable: along x, then along z
      BitSet row = new BitSet(gw * gd);
      for (int j = 0; j < this.depth; j++) {
         for (int i = 0; i < this.width; i++) {
            if (this.cells.get(j * this.width + i)) {
               int gj = j + r;
               row.set(gj * gw + i, gj * gw + i + 2 * r + 1);
            }
         }
      }
      BitSet near = new BitSet(gw * gd);
      for (int c = row.nextSetBit(0); c >= 0; c = row.nextSetBit(c + 1)) {
         int i = c % gw, j = c / gw;
         for (int dj = Math.max(0, j - r); dj <= Math.min(gd - 1, j + r); dj++) {
            near.set(dj * gw + i);
         }
      }
      byte[] seen = new byte[gw * gd];
      int ox = this.minX - r, oz = this.minZ - r;
      BaseCut cut = this;
      return new SupportProbe.Columns() {
         @Override
         public boolean contains(int x, int z) {
            if (cut.contains(x, z)) {
               return true;
            }
            int i = x - ox, j = z - oz;
            if (i < 0 || j < 0 || i >= gw || j >= gd || !near.get(j * gw + i)) {
               return false;
            }
            int c = j * gw + i;
            if (seen[c] == 0) {
               if (clear(volume, x, z, cutY, cutTop)) {
                  seen[c] = 1;
               } else {
                  // solid at the cut height: a pillar of this building (small) or the neighbour (long)
                  it.unimi.dsi.fastutil.longs.LongArrayList pillar = new it.unimi.dsi.fastutil.longs.LongArrayList();
                  boolean small = standing(volume, x, z, cutY, cutTop, pillar);
                  for (int n = 0; n < pillar.size(); n++) {
                     long k = pillar.getLong(n);
                     int pi = BlockKey.x(k) - ox, pj = BlockKey.z(k) - oz;
                     if (pi >= 0 && pj >= 0 && pi < gw && pj < gd) {
                        seen[pj * gw + pi] = (byte)(small ? 1 : 2);
                     }
                  }
                  seen[c] = (byte)(small ? 1 : 2);
               }
            }
            return seen[c] == 1;
         }

         @Override
         public boolean inside(int x, int z) {
            return cut.contains(x, z);
         }
      };
   }

   private static boolean clear(CollapseAnalyzer.Volume volume, int x, int z, int cutY, int cutTop) {
      for (int y = cutY; y <= cutTop; y++) {
         byte m = volume.at(x, y, z);
         if (m == CollapseAnalyzer.STRUCTURE || m == CollapseAnalyzer.NATURAL || m == CollapseAnalyzer.FIXED || m == CollapseAnalyzer.UNKNOWN) {
            return false;
         }
      }
      return true;
   }

   /**
    * The columns that stand at the cut height together with (x, z) (8-connected), into {@code out}; true when they
    * are a small structural pillar ({@link #PILLAR_MAX} columns or fewer, no terrain).
    */
   private static boolean standing(CollapseAnalyzer.Volume volume, int x, int z, int cutY, int cutTop, it.unimi.dsi.fastutil.longs.LongArrayList out) {
      it.unimi.dsi.fastutil.longs.LongOpenHashSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
      long start = BlockKey.of(x, 0, z);
      seen.add(start);
      out.add(start);
      for (int n = 0; n < out.size(); n++) {
         long k = out.getLong(n);
         int cx = BlockKey.x(k), cz = BlockKey.z(k);
         for (int y = cutY; y <= cutTop; y++) {
            byte m = volume.at(cx, y, cz);
            if (m == CollapseAnalyzer.NATURAL || m == CollapseAnalyzer.FIXED || m == CollapseAnalyzer.UNKNOWN) {
               return false; // terrain, bedrock, the edge of the loaded world: not a pillar
            }
         }
         for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
               long nk = BlockKey.of(cx + dx, 0, cz + dz);
               if ((dx != 0 || dz != 0) && !seen.contains(nk) && !clear(volume, cx + dx, cz + dz, cutY, cutTop)) {
                  seen.add(nk);
                  out.add(nk);
                  if (out.size() > PILLAR_MAX) {
                     return false;
                  }
               }
            }
         }
      }
      return true;
   }

   /**
    * The falling blocks plus the walls they leave standing on the neighbour. A wall solid at the cut height that
    * is long (the shared wall of the podium or the next house) only counts as contact for the probe, so after the
    * tower falls its facade would stay as a one block thick sheet two hundred blocks tall. Structure in the
    * {@link #SHEET_DEPTH} columns around the falling ones that rises above everything further out (the podium roof,
    * the next house, the street) comes down with the building; below that height it is the neighbour's wall and
    * stays.
    */
   public static long[] withSheets(CollapseAnalyzer.Volume volume, long[] blocks, int cutTop, int maxY) {
      return new Sheets(volume, blocks, cutTop, maxY).run();
   }

   private static final class Sheets {
      private static final int[] DX = {1, -1, 0, 0, 0, 0}, DY = {0, 0, 0, 0, 1, -1}, DZ = {0, 0, 1, -1, 0, 0};
      private final CollapseAnalyzer.Volume volume;
      private final long[] blocks;
      private final int cutTop, maxY;
      private final it.unimi.dsi.fastutil.longs.LongOpenHashSet columns = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
      private final it.unimi.dsi.fastutil.longs.LongOpenHashSet in;
      private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap dist = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
      private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap from = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
      private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap tops = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
      private final it.unimi.dsi.fastutil.longs.LongArrayList added = new it.unimi.dsi.fastutil.longs.LongArrayList();

      Sheets(CollapseAnalyzer.Volume volume, long[] blocks, int cutTop, int maxY) {
         this.volume = volume;
         this.blocks = blocks;
         this.cutTop = cutTop;
         this.maxY = maxY;
         this.in = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(blocks);
         for (long k : blocks) {
            this.columns.add(BlockKey.of(BlockKey.x(k), 0, BlockKey.z(k)));
         }
         this.dist.defaultReturnValue(-1);
         this.from.defaultReturnValue(Integer.MIN_VALUE);
         this.tops.defaultReturnValue(Integer.MIN_VALUE);
      }

      long[] run() {
         for (int i = 0; i < this.blocks.length && this.added.size() < SHEET_MAX; i++) {
            long k = this.blocks[i];
            int x = BlockKey.x(k), y = BlockKey.y(k), z = BlockKey.z(k);
            for (int d = 0; d < 4; d++) {
               this.offer(x + DX[d], y, z + DZ[d]);
            }
         }
         for (int n = 0; n < this.added.size() && this.added.size() < SHEET_MAX; n++) {
            long k = this.added.getLong(n);
            int x = BlockKey.x(k), y = BlockKey.y(k), z = BlockKey.z(k);
            for (int d = 0; d < 6; d++) {
               this.offer(x + DX[d], y + DY[d], z + DZ[d]);
            }
         }
         if (this.added.isEmpty()) {
            return this.blocks;
         }
         long[] out = java.util.Arrays.copyOf(this.blocks, this.blocks.length + this.added.size());
         this.added.getElements(0, out, this.blocks.length, this.added.size());
         return out;
      }

      private void offer(int x, int y, int z) {
         if (y <= this.cutTop || y > this.maxY || this.added.size() >= SHEET_MAX) {
            return;
         }
         long k = BlockKey.of(x, y, z);
         if (this.in.contains(k)) {
            return;
         }
         int d = this.dist(x, z);
         if (d == 0 || d > SHEET_DEPTH || y < this.from(x, z) || this.volume.at(x, y, z) != CollapseAnalyzer.STRUCTURE) {
            return;
         }
         this.in.add(k);
         this.added.add(k);
      }

      /** Chebyshev distance to the nearest falling column, {@code SHEET_DEPTH + 1} when farther. */
      private int dist(int x, int z) {
         long c = BlockKey.of(x, 0, z);
         int d = this.dist.get(c);
         if (d < 0) {
            d = this.columns.contains(c) ? 0 : SHEET_DEPTH + 1;
            for (int r = 1; r <= SHEET_DEPTH && d > r; r++) {
               for (int dz = -r; dz <= r && d > r; dz++) {
                  for (int dx = -r; dx <= r; dx++) {
                     if ((Math.abs(dx) == r || Math.abs(dz) == r) && this.columns.contains(BlockKey.of(x + dx, 0, z + dz))) {
                        d = r;
                        break;
                     }
                  }
               }
            }
            this.dist.put(c, d);
         }
         return d;
      }

      /** Lowest row of (x, z) that belongs to the falling building: above everything further out. */
      private int from(int x, int z) {
         long c = BlockKey.of(x, 0, z);
         int f = this.from.get(c);
         if (f == Integer.MIN_VALUE) {
            f = this.cutTop + 1;
            for (int dz = -SHEET_DEPTH; dz <= SHEET_DEPTH; dz++) {
               for (int dx = -SHEET_DEPTH; dx <= SHEET_DEPTH; dx++) {
                  if (this.dist(x + dx, z + dz) > SHEET_DEPTH) {
                     f = Math.max(f, this.top(x + dx, z + dz) + 1);
                  }
               }
            }
            if (this.top(x, z) - f + 1 < SHEET_MIN_HEIGHT) {
               f = Integer.MAX_VALUE;
            }
            this.from.put(c, f);
         }
         return f;
      }

      /** Highest solid row of a column (anything unknown counts as solid all the way up). */
      private int top(int x, int z) {
         long c = BlockKey.of(x, 0, z);
         int t = this.tops.get(c);
         if (t == Integer.MIN_VALUE) {
            t = this.cutTop;
            for (int y = this.maxY; y > this.cutTop; y--) {
               byte m = this.volume.at(x, y, z);
               if (m == CollapseAnalyzer.STRUCTURE || m == CollapseAnalyzer.NATURAL || m == CollapseAnalyzer.FIXED || m == CollapseAnalyzer.UNKNOWN) {
                  t = y;
                  break;
               }
            }
            this.tops.put(c, t);
         }
         return t;
      }
   }

   private static int flood(int c, BitSet cut, BitSet outside, int[] queue, int tail) {
      if (!cut.get(c) && !outside.get(c)) {
         outside.set(c);
         queue[tail++] = c;
      }
      return tail;
   }

   @Override
   public String toString() {
      return String.format("%d columns cut, footprint %d (%dx%d at %d %d), core %d", this.columns, this.footprint, this.width, this.depth, this.minX,
         this.minZ, this.core);
   }
}
