package dev.baranhan.viltrumitecore.world.collapse;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

/**
 * Where structural blocks were removed recently, by anything (pure, unit tested): player mining, commands, fire,
 * explosions and hero powers alike. Unlike the {@link DamageLedger} it has no weights and no score: it only says
 * where a building may have lost support, so {@link SupportWatch} can check the structure around it (#135).
 * Same 8^3 cells and caps as the damage ledger; a longer window ({@link CollapseRules#SUPPORT_WINDOW_TICKS}): cutting
 * through every wall of a building by hand takes minutes, and the {@link BaseCut} footprint needs all of it.
 */
public final class SupportLedger {
   private static final class Cell {
      final LongArrayList keys = new LongArrayList();
      final LongArrayList ticks = new LongArrayList();
      long last;
   }

   private final Long2ObjectOpenHashMap<Cell> cells = new Long2ObjectOpenHashMap<>();
   /** cell key -> tick of the latest removal, cells waiting for a probe. */
   private final Long2LongOpenHashMap dirty = new Long2LongOpenHashMap();
   private int size;

   public int size() {
      return this.size;
   }

   public int pending() {
      return this.dirty.size();
   }

   public void record(int x, int y, int z, long now) {
      long ck = DamageLedger.cellKey(x, y, z);
      Cell cell = this.cells.get(ck);
      if (cell == null) {
         cell = new Cell();
         this.cells.put(ck, cell);
      }
      long key = BlockKey.of(x, y, z);
      if (cell.keys.size() >= CollapseRules.CELL_CAP) {
         cell.keys.removeLong(0);
         cell.ticks.removeLong(0);
         this.size--;
      }
      cell.keys.add(key);
      cell.ticks.add(now);
      cell.last = now;
      this.size++;
      this.dirty.put(ck, now);
      if (this.size > CollapseRules.LEVEL_CAP) {
         this.evictOldest();
      }
   }

   private void evictOldest() {
      long oldestKey = 0L;
      long oldest = Long.MAX_VALUE;
      for (Long2ObjectMap.Entry<Cell> e : this.cells.long2ObjectEntrySet()) {
         if (e.getValue().last < oldest) {
            oldest = e.getValue().last;
            oldestKey = e.getLongKey();
         }
      }
      Cell c = this.cells.remove(oldestKey);
      if (c != null) {
         this.size -= c.keys.size();
         this.dirty.remove(oldestKey);
      }
   }

   /** Forgets removals older than the window. */
   public void prune(long now) {
      long cutoff = now - CollapseRules.SUPPORT_WINDOW_TICKS;
      ObjectIterator<Long2ObjectMap.Entry<Cell>> it = this.cells.long2ObjectEntrySet().iterator();
      while (it.hasNext()) {
         Long2ObjectMap.Entry<Cell> e = it.next();
         Cell c = e.getValue();
         int drop = 0;
         while (drop < c.ticks.size() && c.ticks.getLong(drop) < cutoff) {
            drop++;
         }
         if (drop > 0) {
            c.keys.removeElements(0, drop);
            c.ticks.removeElements(0, drop);
            this.size -= drop;
         }
         if (c.keys.isEmpty()) {
            this.dirty.remove(e.getLongKey());
            it.remove();
         }
      }
   }

   /**
    * The cell key of one settled dirty cell ({@link CollapseRules#SETTLE_TICKS} without a new removal), the oldest
    * first, or {@link Long#MIN_VALUE}. A cluster with a neighbour cell that is still being damaged stays pending and the
    * next settled cluster is tried (a busy site never blocks a distant one). The chosen cell leaves the dirty set
    * together with the dirty cells the probe around its centre covers completely ({@link #covered}); a cell the probe
    * only grazes keeps its removals pending for a probe of its own.
    */
   public long settled(long now) {
      LongArrayList candidates = new LongArrayList();
      for (Long2LongOpenHashMap.Entry e : this.dirty.long2LongEntrySet()) {
         if (now - e.getLongValue() >= CollapseRules.SETTLE_TICKS) {
            candidates.add(e.getLongKey());
         }
      }
      if (candidates.isEmpty()) {
         return Long.MIN_VALUE;
      }
      candidates.sort((a, b) -> {
         int c = Long.compare(this.dirty.get(a), this.dirty.get(b));
         return c != 0 ? c : Long.compare(a, b);
      });
      int r = (CollapseRules.SUPPORT_CLUSTER_RADIUS >> CollapseRules.CELL_SHIFT) + 1, ry = (CollapseRules.SUPPORT_CLUSTER_BAND >> CollapseRules.CELL_SHIFT) + 1;
      for (int c = 0; c < candidates.size(); c++) {
         long best = candidates.getLong(c);
         int bx = BlockKey.x(best), by = BlockKey.y(best), bz = BlockKey.z(best);
         // a neighbour cell that is still being damaged waits: probing half a cut would answer "grounded"
         boolean busy = false;
         LongArrayList cover = new LongArrayList();
         for (Long2LongOpenHashMap.Entry e : this.dirty.long2LongEntrySet()) {
            long k = e.getLongKey();
            if (Math.abs(BlockKey.x(k) - bx) <= r && Math.abs(BlockKey.y(k) - by) <= ry && Math.abs(BlockKey.z(k) - bz) <= r) {
               if (now - e.getLongValue() < CollapseRules.SETTLE_TICKS) {
                  busy = true;
                  break;
               }
               if (covered(best, k, CollapseRules.SUPPORT_CLUSTER_RADIUS, CollapseRules.SUPPORT_CLUSTER_BAND)) {
                  cover.add(k);
               }
            }
         }
         if (busy) {
            continue;
         }
         for (int i = 0; i < cover.size(); i++) {
            this.dirty.remove(cover.getLong(i));
         }
         this.dirty.remove(best);
         return best;
      }
      return Long.MIN_VALUE;
   }

   /**
    * Pure: every block of cell {@code cell} lies inside the probe box around the centre of cell {@code centre}
    * ({@code |d| <= radius} horizontally, {@code <= band} vertically, as {@link #around} selects removals).
    */
   static boolean covered(long centre, long cell, int radius, int band) {
      int s = CollapseRules.CELL_SHIFT, size = 1 << s;
      return axisCovered((BlockKey.x(centre) << s) + size / 2, BlockKey.x(cell) << s, size, radius)
         && axisCovered((BlockKey.y(centre) << s) + size / 2, BlockKey.y(cell) << s, size, band)
         && axisCovered((BlockKey.z(centre) << s) + size / 2, BlockKey.z(cell) << s, size, radius);
   }

   private static boolean axisCovered(int centre, int min, int size, int reach) {
      return min >= centre - reach && min + size - 1 <= centre + reach;
   }

   /** Pending (dirty) state of one cell key, for tests. */
   boolean isPending(long cellKey) {
      return this.dirty.containsKey(cellKey);
   }

   /** Removed positions within a horizontal radius and a vertical band of a point. */
   public LongArrayList around(int x, int y, int z, int radius, int band) {
      LongArrayList out = new LongArrayList();
      int s = CollapseRules.CELL_SHIFT;
      for (int cx = (x - radius) >> s; cx <= (x + radius) >> s; cx++) {
         for (int cy = (y - band) >> s; cy <= (y + band) >> s; cy++) {
            for (int cz = (z - radius) >> s; cz <= (z + radius) >> s; cz++) {
               Cell c = this.cells.get(BlockKey.of(cx, cy, cz));
               if (c == null) {
                  continue;
               }
               for (int i = 0; i < c.keys.size(); i++) {
                  long k = c.keys.getLong(i);
                  if (Math.abs(BlockKey.x(k) - x) <= radius && Math.abs(BlockKey.z(k) - z) <= radius && Math.abs(BlockKey.y(k) - y) <= band) {
                     out.add(k);
                  }
               }
            }
         }
      }
      return out;
   }

   /** Forgets every removal inside a box (a collapse consumed it). */
   public void consume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
      ObjectIterator<Long2ObjectMap.Entry<Cell>> it = this.cells.long2ObjectEntrySet().iterator();
      while (it.hasNext()) {
         Long2ObjectMap.Entry<Cell> e = it.next();
         long ck = e.getLongKey();
         int bx = BlockKey.x(ck) << CollapseRules.CELL_SHIFT, by = BlockKey.y(ck) << CollapseRules.CELL_SHIFT, bz = BlockKey.z(ck) << CollapseRules.CELL_SHIFT;
         if (bx > maxX || bx + 7 < minX || by > maxY || by + 7 < minY || bz > maxZ || bz + 7 < minZ) {
            continue;
         }
         Cell c = e.getValue();
         for (int i = c.keys.size() - 1; i >= 0; i--) {
            long k = c.keys.getLong(i);
            int px = BlockKey.x(k), py = BlockKey.y(k), pz = BlockKey.z(k);
            if (px >= minX && px <= maxX && py >= minY && py <= maxY && pz >= minZ && pz <= maxZ) {
               c.keys.removeLong(i);
               c.ticks.removeLong(i);
               this.size--;
            }
         }
         if (c.keys.isEmpty()) {
            it.remove();
            this.dirty.remove(ck);
         }
      }
   }

   public void clear() {
      this.cells.clear();
      this.dirty.clear();
      this.size = 0;
   }
}
