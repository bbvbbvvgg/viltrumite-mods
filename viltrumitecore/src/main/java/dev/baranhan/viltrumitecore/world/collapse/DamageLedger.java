package dev.baranhan.viltrumitecore.world.collapse;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * Recent structural damage of one level (pure, unit tested): every solid block that a destructive cause
 * removed, bucketed into 8x8x8 cells, weighted by its cause and forgotten after
 * {@link CollapseRules#WINDOW_TICKS}. The ledger only says where real damage happened; the analyzer later
 * checks that those positions are still air. Bounded: {@link CollapseRules#CELL_CAP} per cell,
 * {@link CollapseRules#LEVEL_CAP} per level (oldest cells first).
 */
public final class DamageLedger {
   /** One damaged spot ready for analysis. */
   public record Candidate(int x, int y, int z, float score, boolean impact) {
   }

   private static final class Cell {
      final LongArrayList keys = new LongArrayList();
      float[] weights = new float[16];
      long[] ticks = new long[16];
      float score;
      long last;

      void add(long key, float weight, long now) {
         int n = this.keys.size();
         if (n >= CollapseRules.CELL_CAP) {
            // drop the oldest entry
            this.score -= this.weights[0];
            this.keys.removeLong(0);
            System.arraycopy(this.weights, 1, this.weights, 0, n - 1);
            System.arraycopy(this.ticks, 1, this.ticks, 0, n - 1);
            n--;
         }
         if (n >= this.weights.length) {
            this.weights = java.util.Arrays.copyOf(this.weights, Math.min(CollapseRules.CELL_CAP, n * 2));
            this.ticks = java.util.Arrays.copyOf(this.ticks, this.weights.length);
         }
         this.keys.add(key);
         this.weights[n] = weight;
         this.ticks[n] = now;
         this.score += weight;
         this.last = now;
      }

      /** Removes entries older than {@code cutoff}; returns the number removed. */
      int prune(long cutoff) {
         int n = this.keys.size();
         int drop = 0;
         while (drop < n && this.ticks[drop] < cutoff) {
            this.score -= this.weights[drop];
            drop++;
         }
         if (drop > 0) {
            this.keys.removeElements(0, drop);
            System.arraycopy(this.weights, drop, this.weights, 0, n - drop);
            System.arraycopy(this.ticks, drop, this.ticks, 0, n - drop);
         }
         if (this.keys.isEmpty()) {
            this.score = 0.0F;
         }
         return drop;
      }
   }

   private final Long2ObjectOpenHashMap<Cell> cells = new Long2ObjectOpenHashMap<>();
   /** cell key -> tick of the latest damage, cells waiting for evaluation. */
   private final Long2LongOpenHashMap dirty = new Long2LongOpenHashMap();
   /** cell key -> impact flag tick (meteor, Leviathan crash): settles faster. */
   private final Long2LongOpenHashMap impacts = new Long2LongOpenHashMap();
   private int size;

   public static long cellKey(int x, int y, int z) {
      return BlockKey.of(x >> CollapseRules.CELL_SHIFT, y >> CollapseRules.CELL_SHIFT, z >> CollapseRules.CELL_SHIFT);
   }

   public int size() {
      return this.size;
   }

   public int cellCount() {
      return this.cells.size();
   }

   /** Records one removed block. A weight of 0 (secondary damage) is ignored. */
   public void record(int x, int y, int z, float weight, long now) {
      if (!(weight > 0.0F)) {
         return;
      }
      long ck = cellKey(x, y, z);
      Cell cell = this.cells.get(ck);
      if (cell == null) {
         cell = new Cell();
         this.cells.put(ck, cell);
      }
      int before = cell.keys.size();
      cell.add(BlockKey.of(x, y, z), weight, now);
      this.size += cell.keys.size() - before;
      this.dirty.put(ck, now);
      if (this.size > CollapseRules.LEVEL_CAP) {
         this.evictOldest();
      }
   }

   /** Marks an explicit impact (meteor hit, Leviathan crash) at a position: its cluster settles faster. */
   public void impact(int x, int y, int z, long now) {
      long ck = cellKey(x, y, z);
      this.impacts.put(ck, now);
      if (this.cells.containsKey(ck)) {
         this.dirty.put(ck, now);
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

   /** Forgets damage older than the window. */
   public void prune(long now) {
      long cutoff = now - CollapseRules.WINDOW_TICKS;
      ObjectIterator<Long2ObjectMap.Entry<Cell>> it = this.cells.long2ObjectEntrySet().iterator();
      while (it.hasNext()) {
         Long2ObjectMap.Entry<Cell> e = it.next();
         long key = e.getLongKey();
         Cell cell = e.getValue();
         this.size -= cell.prune(cutoff);
         if (cell.keys.isEmpty()) {
            it.remove();
            this.dirty.remove(key);
         }
      }
      this.impacts.long2LongEntrySet().removeIf(e -> e.getLongValue() < cutoff);
   }

   /** Weighted damage in the 3x3x3 cells around a cell. */
   public float clusterScore(long cellKey) {
      int cx = BlockKey.x(cellKey), cy = BlockKey.y(cellKey), cz = BlockKey.z(cellKey);
      float s = 0.0F;
      for (int dx = -1; dx <= 1; dx++) {
         for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
               Cell c = this.cells.get(BlockKey.of(cx + dx, cy + dy, cz + dz));
               if (c != null) {
                  s += c.score;
               }
            }
         }
      }
      return s;
   }

   /**
    * Settled dirty clusters whose score passed the threshold, strongest first, at most {@code max}.
    * Returned cells leave the dirty set; weaker settled cells leave it too (they come back on new damage).
    */
   public List<Candidate> settled(long now, int max) {
      List<Candidate> out = new ArrayList<>();
      if (this.dirty.isEmpty()) {
         return out;
      }
      LongArrayList done = new LongArrayList();
      for (Long2LongOpenHashMap.Entry e : this.dirty.long2LongEntrySet()) {
         long ck = e.getLongKey();
         boolean impact = this.impactNear(ck, now);
         int settle = impact ? CollapseRules.IMPACT_SETTLE_TICKS : CollapseRules.SETTLE_TICKS;
         if (now - e.getLongValue() < settle) {
            continue;
         }
         done.add(ck);
         float score = this.clusterScore(ck);
         if (score >= CollapseRules.SCORE_THRESHOLD) {
            double[] c = this.centroid(ck);
            out.add(new Candidate((int)Math.floor(c[0]), (int)Math.floor(c[1]), (int)Math.floor(c[2]), score, impact));
         }
      }
      for (int i = 0; i < done.size(); i++) {
         this.dirty.remove(done.getLong(i));
      }
      out.sort((a, b) -> Float.compare(b.score(), a.score()));
      return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
   }

   private boolean impactNear(long ck, long now) {
      int cx = BlockKey.x(ck), cy = BlockKey.y(ck), cz = BlockKey.z(ck);
      for (int dx = -1; dx <= 1; dx++) {
         for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
               long t = this.impacts.getOrDefault(BlockKey.of(cx + dx, cy + dy, cz + dz), Long.MIN_VALUE);
               if (t != Long.MIN_VALUE && now - t <= 40) {
                  return true;
               }
            }
         }
      }
      return false;
   }

   /** Weighted centroid of the damage in the 3x3x3 cells around a cell. */
   private double[] centroid(long ck) {
      int cx = BlockKey.x(ck), cy = BlockKey.y(ck), cz = BlockKey.z(ck);
      double sx = 0, sy = 0, sz = 0, sw = 0;
      for (int dx = -1; dx <= 1; dx++) {
         for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
               Cell c = this.cells.get(BlockKey.of(cx + dx, cy + dy, cz + dz));
               if (c == null) {
                  continue;
               }
               for (int i = 0; i < c.keys.size(); i++) {
                  long k = c.keys.getLong(i);
                  float w = c.weights[i];
                  sx += (BlockKey.x(k) + 0.5) * w;
                  sy += (BlockKey.y(k) + 0.5) * w;
                  sz += (BlockKey.z(k) + 0.5) * w;
                  sw += w;
               }
            }
         }
      }
      return sw <= 0 ? new double[]{(cx << 3) + 4, (cy << 3) + 4, (cz << 3) + 4} : new double[]{sx / sw, sy / sw, sz / sw};
   }

   /** Damaged positions within a horizontal radius / vertical band of a point (for the analyzer). */
   public LongArrayList around(int x, int y, int z, int radius, int below, int above) {
      LongArrayList out = new LongArrayList();
      int r = radius >> CollapseRules.CELL_SHIFT;
      int x0 = (x >> CollapseRules.CELL_SHIFT) - r - 1, x1 = (x >> CollapseRules.CELL_SHIFT) + r + 1;
      int z0 = (z >> CollapseRules.CELL_SHIFT) - r - 1, z1 = (z >> CollapseRules.CELL_SHIFT) + r + 1;
      int y0 = (y - below) >> CollapseRules.CELL_SHIFT, y1 = (y + above) >> CollapseRules.CELL_SHIFT;
      for (int cx = x0; cx <= x1; cx++) {
         for (int cy = y0; cy <= y1; cy++) {
            for (int cz = z0; cz <= z1; cz++) {
               Cell c = this.cells.get(BlockKey.of(cx, cy, cz));
               if (c == null) {
                  continue;
               }
               for (int i = 0; i < c.keys.size(); i++) {
                  long k = c.keys.getLong(i);
                  int px = BlockKey.x(k), py = BlockKey.y(k), pz = BlockKey.z(k);
                  if (Math.abs(px - x) <= radius && Math.abs(pz - z) <= radius && py >= y - below && py <= y + above) {
                     out.add(k);
                  }
               }
            }
         }
      }
      return out;
   }

   /** Forgets every damaged position inside a box (a collapse consumed it, so it cannot fire again). */
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
         Cell kept = new Cell();
         for (int i = 0; i < c.keys.size(); i++) {
            long k = c.keys.getLong(i);
            int px = BlockKey.x(k), py = BlockKey.y(k), pz = BlockKey.z(k);
            if (px < minX || px > maxX || py < minY || py > maxY || pz < minZ || pz > maxZ) {
               kept.add(k, c.weights[i], c.ticks[i]);
            }
         }
         this.size -= c.keys.size() - kept.keys.size();
         if (kept.keys.isEmpty()) {
            it.remove();
            this.dirty.remove(ck);
         } else {
            kept.last = c.last;
            e.setValue(kept);
         }
      }
   }

   public void clear() {
      this.cells.clear();
      this.dirty.clear();
      this.impacts.clear();
      this.size = 0;
   }
}
