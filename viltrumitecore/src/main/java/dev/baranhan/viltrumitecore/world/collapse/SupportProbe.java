package dev.baranhan.viltrumitecore.world.collapse;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import javax.annotation.Nullable;

/**
 * Global support check (pure, unit tested). The box analysis ({@link CollapseAnalyzer}) only sees 25 x 25 x ~34
 * blocks and counts the box top and sides as support, so a skyscraper cut at its base kept hanging (#135). This
 * probe follows the real structure instead: from the solid neighbours of removed blocks it floods through solid
 * blocks, lowest layer first, until it reaches the ground or runs out of structure.
 *
 * <p><b>Grounded</b> = the search reaches a block below the cut ({@code y < cutY}, the lowest removed block of the
 * probed cluster), an unbreakable block, the world floor, an unloaded chunk or the search radius (the last two are
 * "cannot prove it floats": nothing falls). Everything else a search exhausts is <b>floating</b>: it lost its last
 * connection to the ground. The search walks down first, so a wall that still stands on the street answers after a
 * few dozen blocks; only a really severed section costs its full size ({@code maxNodes} bounds it).
 *
 * <p>With {@code assumed} positions (removed blocks treated as solid again) the same search answers "was it
 * standing before?": a sky platform that was already floating does not come down when a player edits it.
 * Incremental: {@link #step(int)} processes a node budget, so a large building is checked over a few ticks.
 */
public final class SupportProbe {
   public enum Status {
      RUNNING,
      /** Every search reached the ground: nothing hangs. */
      GROUNDED,
      /** At least one search exhausted a structure that does not touch the ground. */
      FLOATING,
      /** Too big to decide within the node budget: treated as supported. */
      UNDECIDED
   }

   private static final int[] DX = {0, 1, -1, 0, 0, 0};
   private static final int[] DY = {-1, 0, 0, 0, 0, 1};
   private static final int[] DZ = {0, 0, 0, 1, -1, 0};

   private final CollapseAnalyzer.Volume volume;
   private final long[] seeds;
   @Nullable
   private final LongOpenHashSet assumed;
   private final boolean singleSearch;
   private final int cutY, minY, maxY, maxNodes, radius, ox, oz;
   /** Node -> id of the search that reached it. */
   private final Long2IntOpenHashMap visited = new Long2IntOpenHashMap();
   private final LongArrayList[] buckets;
   private int low = Integer.MAX_VALUE;
   private int queued;
   private int seedIndex;
   private int search;
   private boolean inSearch;
   private LongArrayList comp = new LongArrayList();
   private int compStructure, compNatural;
   private final LongArrayList floating = new LongArrayList();
   private int floatingStructure, floatingNatural, floatingComponents, groundedSearches;
   private int nodes;
   private Status status = Status.RUNNING;
   private String reason = "running";

   /**
    * @param seeds removed positions (their solid neighbours are searched) or, with {@code singleSearch}, the
    *              start nodes of one search
    * @param assumed air positions that count as solid (the state before the damage), or null
    * @param cutY a search that reaches {@code y < cutY} is grounded
    */
   public SupportProbe(CollapseAnalyzer.Volume volume, long[] seeds, @Nullable LongOpenHashSet assumed, boolean singleSearch, int cutY, int minY, int maxY,
      int maxNodes, int radius, int originX, int originZ) {
      this.volume = volume;
      this.seeds = seeds;
      this.assumed = assumed;
      this.singleSearch = singleSearch;
      this.cutY = cutY;
      this.minY = minY;
      this.maxY = maxY;
      this.maxNodes = maxNodes;
      this.radius = radius;
      this.ox = originX;
      this.oz = originZ;
      this.buckets = new LongArrayList[Math.max(1, maxY - minY + 1)];
      this.visited.defaultReturnValue(0);
   }

   public Status status() {
      return this.status;
   }

   public String reason() {
      return this.reason;
   }

   public int nodes() {
      return this.nodes;
   }

   /** Solid blocks of every floating search (never an assumed position). */
   public long[] floatingBlocks() {
      return this.floating.toLongArray();
   }

   public int floatingStructure() {
      return this.floatingStructure;
   }

   public int floatingNatural() {
      return this.floatingNatural;
   }

   public int floatingComponents() {
      return this.floatingComponents;
   }

   public int groundedSearches() {
      return this.groundedSearches;
   }

   /** Runs the whole probe (tests, commands). */
   public Status run() {
      while (this.step(1 << 20) == Status.RUNNING) {
         // keep going
      }
      return this.status;
   }

   /** Processes up to {@code budget} nodes; returns the status (RUNNING while not finished). */
   public Status step(int budget) {
      int left = budget;
      while (this.status == Status.RUNNING && left > 0) {
         if (!this.inSearch) {
            if (!this.nextSearch()) {
               this.status = this.floating.isEmpty() ? Status.GROUNDED : Status.FLOATING;
               this.reason = this.floating.isEmpty() ? "grounded" : "floating";
               break;
            }
            continue;
         }
         long n = this.pop();
         if (n == Long.MIN_VALUE) {
            // exhausted without touching the ground: this structure hangs in the air
            this.floating.addAll(this.comp);
            this.floatingStructure += this.compStructure;
            this.floatingNatural += this.compNatural;
            this.floatingComponents++;
            this.endSearch();
            continue;
         }
         left--;
         if (++this.nodes > this.maxNodes) {
            this.status = Status.UNDECIDED;
            this.reason = "too big (" + this.maxNodes + " blocks searched)";
            break;
         }
         int x = BlockKey.x(n), y = BlockKey.y(n), z = BlockKey.z(n);
         if (y < this.cutY || y <= this.minY) {
            this.grounded();
            continue;
         }
         for (int d = 0; d < 6; d++) {
            int nx = x + DX[d], ny = y + DY[d], nz = z + DZ[d];
            if (ny < this.minY || ny > this.maxY) {
               continue;
            }
            long k = BlockKey.of(nx, ny, nz);
            int seen = this.visited.get(k);
            if (seen == this.search) {
               continue;
            }
            byte m = this.classify(k, nx, ny, nz);
            if (m == CollapseAnalyzer.UNKNOWN || m == CollapseAnalyzer.FIXED) {
               this.grounded();
               break;
            }
            if (!solid(m)) {
               continue;
            }
            if (seen != 0) {
               // a node of an earlier search: that search touched the ground (a floating one would contain us)
               this.grounded();
               break;
            }
            if (Math.abs(nx - this.ox) > this.radius || Math.abs(nz - this.oz) > this.radius) {
               this.grounded();
               break;
            }
            this.add(k, m, ny);
         }
      }
      return this.status;
   }

   private boolean nextSearch() {
      if (this.singleSearch) {
         if (this.search > 0) {
            return false;
         }
         this.startSearch();
         for (long s : this.seeds) {
            int x = BlockKey.x(s), y = BlockKey.y(s), z = BlockKey.z(s);
            byte m = this.classify(s, x, y, z);
            if (m == CollapseAnalyzer.FIXED || m == CollapseAnalyzer.UNKNOWN) {
               this.grounded();
               return true;
            }
            if (solid(m) && this.visited.get(s) == 0) {
               this.add(s, m, y);
            }
         }
         return true;
      }
      while (this.seedIndex < this.seeds.length) {
         long s = this.seeds[this.seedIndex];
         int sx = BlockKey.x(s), sy = BlockKey.y(s), sz = BlockKey.z(s);
         // the six neighbours of a removed position, one search each (a shared structure is searched once)
         int d = this.neighbour++;
         if (this.neighbour >= 6) {
            this.neighbour = 0;
            this.seedIndex++;
         }
         int nx = sx + DX[d], ny = sy + DY[d], nz = sz + DZ[d];
         if (ny < this.minY || ny > this.maxY) {
            continue;
         }
         long k = BlockKey.of(nx, ny, nz);
         if (this.visited.get(k) != 0) {
            continue;
         }
         byte m = this.classify(k, nx, ny, nz);
         if (!solid(m) || m == CollapseAnalyzer.FIXED) {
            continue;
         }
         this.startSearch();
         this.add(k, m, ny);
         return true;
      }
      return false;
   }

   private int neighbour;

   private void startSearch() {
      this.search++;
      this.inSearch = true;
      this.comp = new LongArrayList();
      this.compStructure = 0;
      this.compNatural = 0;
   }

   private void endSearch() {
      this.inSearch = false;
      if (this.queued > 0) {
         for (int i = Math.max(0, this.low); i < this.buckets.length; i++) {
            if (this.buckets[i] != null) {
               this.buckets[i].clear();
            }
         }
      }
      this.queued = 0;
      this.low = Integer.MAX_VALUE;
   }

   private void grounded() {
      this.groundedSearches++;
      this.endSearch();
   }

   private void add(long k, byte m, int y) {
      this.visited.put(k, this.search);
      boolean assumedAir = this.assumed != null && m == CollapseAnalyzer.STRUCTURE && this.assumed.contains(k) && this.volume.at(BlockKey.x(k), y, BlockKey.z(k)) == CollapseAnalyzer.AIR;
      if (!assumedAir) {
         this.comp.add(k);
         if (m == CollapseAnalyzer.NATURAL) {
            this.compNatural++;
         } else {
            this.compStructure++;
         }
      }
      int b = y - this.minY;
      LongArrayList list = this.buckets[b];
      if (list == null) {
         list = this.buckets[b] = new LongArrayList();
      }
      list.add(k);
      this.queued++;
      if (b < this.low) {
         this.low = b;
      }
   }

   private long pop() {
      if (this.queued == 0) {
         return Long.MIN_VALUE;
      }
      while (this.buckets[this.low] == null || this.buckets[this.low].isEmpty()) {
         this.low++;
      }
      LongArrayList list = this.buckets[this.low];
      this.queued--;
      return list.removeLong(list.size() - 1);
   }

   private byte classify(long k, int x, int y, int z) {
      byte m = this.volume.at(x, y, z);
      if (m == CollapseAnalyzer.AIR && this.assumed != null && this.assumed.contains(k)) {
         return CollapseAnalyzer.STRUCTURE;
      }
      return m;
   }

   private static boolean solid(byte m) {
      return m == CollapseAnalyzer.STRUCTURE || m == CollapseAnalyzer.NATURAL || m == CollapseAnalyzer.FIXED;
   }
}
