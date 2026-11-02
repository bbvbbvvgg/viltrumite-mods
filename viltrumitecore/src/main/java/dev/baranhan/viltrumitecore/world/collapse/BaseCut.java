package dev.baranhan.viltrumitecore.world.collapse;

import java.util.BitSet;
import javax.annotation.Nullable;

/**
 * The footprint of a base cut (pure, unit tested). Atheria buildings touch their neighbours (shared walls, sky
 * bridges, a wing on the next block), so after a cut through every wall the plain {@link SupportProbe} still finds
 * the ground through the building next door and nothing falls (#135). The footprint is every column with a removed
 * block plus what those columns enclose (the rooms behind a ring of cut walls); a {@link SupportProbe} restricted to
 * it ({@link SupportProbe#within}) tells whether the building inside stands on anything of its own. A strip of
 * wall, a doorway or a window band has no inside (too thin) and never qualifies.
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
      // a one-column border around the bounding box: the outside flood starts there
      int gw = w + 2, gd = d + 2;
      BitSet cut = new BitSet(gw * gd);
      int columns = 0;
      for (long k : removed) {
         int i = BlockKey.x(k) - x0 + 1, j = BlockKey.z(k) - z0 + 1;
         int c = j * gw + i;
         if (!cut.get(c)) {
            cut.set(c);
            columns++;
         }
      }
      if (columns < MIN_COLUMNS) {
         return null;
      }
      // outside = reachable from the border without crossing a cut column (4-connected)
      BitSet outside = new BitSet(gw * gd);
      int[] queue = new int[gw * gd];
      int head = 0, tail = 0;
      queue[tail++] = 0;
      outside.set(0);
      while (head < tail) {
         int c = queue[head++];
         int i = c % gw, j = c / gw;
         if (i > 0) {
            tail = flood(c - 1, cut, outside, queue, tail);
         }
         if (i < gw - 1) {
            tail = flood(c + 1, cut, outside, queue, tail);
         }
         if (j > 0) {
            tail = flood(c - gw, cut, outside, queue, tail);
         }
         if (j < gd - 1) {
            tail = flood(c + gw, cut, outside, queue, tail);
         }
      }
      BitSet cells = new BitSet(w * d);
      int footprint = 0;
      for (int j = 0; j < d; j++) {
         for (int i = 0; i < w; i++) {
            if (!outside.get((j + 1) * gw + i + 1)) {
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
