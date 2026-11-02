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
