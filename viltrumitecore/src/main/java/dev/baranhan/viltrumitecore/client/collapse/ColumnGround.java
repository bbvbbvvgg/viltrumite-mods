package dev.baranhan.viltrumitecore.client.collapse;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * The real surfaces under falling fragments (no Minecraft client classes, unit tested): per column, the solid
 * blocks found so far and the scanned height range, filled lazily downward from where pieces actually are. A
 * fragment lands on the first solid block under it (a neighbour's roof, a floor slab, a ledge or the street)
 * instead of a heightmap clamped around the section's ground. A column in an unloaded chunk is
 * {@link CollapseMotion.Ground#UNKNOWN}; a column with nothing down to {@code bottom} is the void.
 */
final class ColumnGround implements CollapseMotion.Ground {
   static final int AIR = 0;
   static final int SOLID = 1;
   static final int UNLOADED = 2;

   /** One block of the world: {@link #AIR} (or passable, or excluded), {@link #SOLID}, {@link #UNLOADED}. */
   @FunctionalInterface
   interface Probe {
      int at(int x, int y, int z);
   }

   private static final class Column {
      /** Scanned range [lo, hi]; empty while hi < lo. */
      int lo, hi;
      /** Solid heights inside [lo, hi], ascending. */
      final IntArrayList solids = new IntArrayList();
      boolean unknown;
   }

   private final Probe probe;
   private final int bottom;
   private final int fallback;
   private final Long2ObjectOpenHashMap<Column> columns = new Long2ObjectOpenHashMap<>();

   /** {@code bottom}: lowest y that exists; {@code fallback}: legacy 2D query (the section's ground). */
   ColumnGround(Probe probe, int bottom, int fallback) {
      this.probe = probe;
      this.bottom = bottom;
      this.fallback = fallback;
   }

   @Override
   public int surface(int x, int z) {
      return this.fallback;
   }

   @Override
   public int surface(int x, int z, int fromY) {
      long key = ((long)x << 32) ^ (z & 0xFFFFFFFFL);
      Column c = this.columns.get(key);
      if (c == null) {
         c = new Column();
         c.hi = fromY;
         c.lo = fromY + 1;
         this.columns.put(key, c);
      }
      if (c.unknown) {
         return UNKNOWN;
      }
      if (fromY > c.hi) {
         // extend upward: every new solid is above all known ones, the list stays ascending
         for (int y = c.hi + 1; y <= fromY; y++) {
            int b = this.probe.at(x, y, z);
            if (b == UNLOADED) {
               c.unknown = true;
               return UNKNOWN;
            }
            if (b == SOLID) {
               c.solids.add(y);
            }
         }
         c.hi = fromY;
      }
      int idx = highestAtOrBelow(c.solids, fromY);
      if (idx >= 0) {
         return c.solids.getInt(idx) + 1;
      }
      // nothing known below fromY: scan on down from the bottom of the known range
      // (a gap between fromY and the known range is scanned too, so the range never has holes)
      for (int y = c.lo - 1; y >= this.bottom; y--) {
         int b = this.probe.at(x, y, z);
         if (b == UNLOADED) {
            c.unknown = true;
            return UNKNOWN;
         }
         c.lo = y;
         if (b == SOLID) {
            c.solids.add(0, y);
            if (y <= fromY) {
               return y + 1;
            }
         }
      }
      c.lo = this.bottom;
      return this.bottom - 8;
   }

   @Override
   public boolean solid(int x, int y, int z) {
      Column c = this.columns.get(((long)x << 32) ^ (z & 0xFFFFFFFFL));
      if (c != null && !c.unknown && y >= c.lo && y <= c.hi) {
         int idx = highestAtOrBelow(c.solids, y);
         return idx >= 0 && c.solids.getInt(idx) == y;
      }
      return this.probe.at(x, y, z) == SOLID;
   }

   /** Index of the highest value at or below {@code y} in an ascending list, or -1. */
   static int highestAtOrBelow(IntArrayList ys, int y) {
      int lo = 0, hi = ys.size() - 1, best = -1;
      while (lo <= hi) {
         int mid = (lo + hi) >>> 1;
         if (ys.getInt(mid) <= y) {
            best = mid;
            lo = mid + 1;
         } else {
            hi = mid - 1;
         }
      }
      return best;
   }
}
