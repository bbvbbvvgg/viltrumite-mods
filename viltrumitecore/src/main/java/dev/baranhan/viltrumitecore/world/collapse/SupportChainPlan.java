package dev.baranhan.viltrumitecore.world.collapse;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import javax.annotation.Nullable;

/**
 * A severed structure split into collapse stages (pure, unit tested). A section that fits one collapse falls as
 * one; a bigger one (a skyscraper top) comes down as a chain, top floors first, each stage within the per-stage
 * block limit and the packet box limits: the roof line sinks floor by floor while the cut at the base stays as
 * high as it was cut (lowest first would open a growing gap under a hovering tower). Every stage lands on the
 * ground under the structure, never on the structure's own lower floors. Nothing of it is left hanging: the chain
 * runs until every still-solid block has fallen (#135: truncation used to leave the rest in the air).
 */
public final class SupportChainPlan {
   private long[] order;
   private int cursor;
   private final LongArrayList deferred = new LongArrayList();
   private final int total;
   private final LongOpenHashSet all;
   private int stages;
   public final int minX, minY, minZ, maxX, maxY, maxZ;
   public final double centerX, centerZ;

   public SupportChainPlan(long[] blocks, long seed) {
      this.total = blocks.length;
      this.order = sortByHeight(blocks, seed);
      this.all = new LongOpenHashSet(blocks);
      int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
      double sx = 0, sz = 0;
      for (long k : blocks) {
         int x = BlockKey.x(k), y = BlockKey.y(k), z = BlockKey.z(k);
         x0 = Math.min(x0, x);
         y0 = Math.min(y0, y);
         z0 = Math.min(z0, z);
         x1 = Math.max(x1, x);
         y1 = Math.max(y1, y);
         z1 = Math.max(z1, z);
         sx += x + 0.5;
         sz += z + 0.5;
      }
      this.minX = x0;
      this.minY = y0;
      this.minZ = z0;
      this.maxX = x1;
      this.maxY = y1;
      this.maxZ = z1;
      this.centerX = blocks.length == 0 ? 0 : sx / blocks.length;
      this.centerZ = blocks.length == 0 ? 0 : sz / blocks.length;
   }

   public int total() {
      return this.total;
   }

   public int stages() {
      return this.stages;
   }

   /** Blocks not yet handed to a stage. */
   public int remaining() {
      return this.order.length - this.cursor + this.deferred.size();
   }

   public boolean done() {
      return this.remaining() == 0;
   }

   /** Highest layer first; inside a layer a seeded order, so a stage edge is jagged, not a ruler line. */
   static long[] sortByHeight(long[] blocks, long seed) {
      if (blocks.length == 0) {
         return blocks;
      }
      // one primitive sort: depth below the top (12 bits) | seeded jitter (20 bits) | index (up to 2^31)
      int maxY = Integer.MIN_VALUE;
      for (long k : blocks) {
         maxY = Math.max(maxY, BlockKey.y(k));
      }
      long[] keyed = new long[blocks.length];
      for (int i = 0; i < blocks.length; i++) {
         long y = Math.min(4095, maxY - BlockKey.y(blocks[i]));
         long jitter = CollapseAnalyzer.hash(seed, BlockKey.x(blocks[i]), 3, BlockKey.z(blocks[i])) & 0xFFFFFL;
         keyed[i] = y << 51 | jitter << 31 | i;
      }
      Arrays.sort(keyed);
      long[] out = new long[blocks.length];
      for (int i = 0; i < out.length; i++) {
         out[i] = blocks[(int)(keyed[i] & 0x7FFFFFFFL)];
      }
      return out;
   }

   /**
    * The next stage: up to {@code cap} blocks that are still standing, highest first, inside the packet box limits.
    * Null when nothing is left. {@code fallX/fallZ} is the chain's fall direction (unit vector).
    */
   @Nullable
   public CollapseAnalyzer.Plan next(CollapseAnalyzer.Volume volume, int cap, CollapseCause cause, long seed, float fallX, float fallZ) {
      if (this.cursor >= this.order.length && !this.deferred.isEmpty()) {
         this.order = this.deferred.toLongArray();
         this.cursor = 0;
         this.deferred.clear();
      }
      LongArrayList stage = new LongArrayList();
      int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
      while (this.cursor < this.order.length && stage.size() < cap) {
         long k = this.order[this.cursor];
         int x = BlockKey.x(k), y = BlockKey.y(k), z = BlockKey.z(k);
         byte m = volume.at(x, y, z);
         if (m != CollapseAnalyzer.STRUCTURE && m != CollapseAnalyzer.NATURAL) {
            this.cursor++; // gone since the probe (or became unbreakable/unloaded): skip
            continue;
         }
         int nx0 = Math.min(x0, x), ny0 = Math.min(y0, y), nz0 = Math.min(z0, z), nx1 = Math.max(x1, x), ny1 = Math.max(y1, y), nz1 = Math.max(z1, z);
         int sx = nx1 - nx0 + 1, sy = ny1 - ny0 + 1, sz = nz1 - nz0 + 1;
         boolean fits = sx <= CollapseRules.PACKET_AXIS_MAX && sy <= CollapseRules.PACKET_AXIS_MAX && sz <= CollapseRules.PACKET_AXIS_MAX
            && (long)sx * sy * sz <= CollapseRules.PACKET_MAX_CELLS;
         if (!fits) {
            if (!stage.isEmpty() && y < y0) {
               break; // the stage is as tall as the box allows: the next layer goes to the next stage
            }
            this.deferred.add(k); // too far sideways for this box: a later stage takes it
            this.cursor++;
            continue;
         }
         x0 = nx0;
         y0 = ny0;
         z0 = nz0;
         x1 = nx1;
         y1 = ny1;
         z1 = nz1;
         stage.add(k);
         this.cursor++;
      }
      if (stage.isEmpty()) {
         return this.done() ? null : this.next(volume, cap, cause, seed, fallX, fallZ);
      }
      long[] blocks = stage.toLongArray();
      Arrays.sort(blocks);
      boolean last = this.done();
      boolean only = last && this.stages == 0;
      this.stages++;
      // the ground under the whole structure: its own lower floors are not where a stage lands
      int ground = ground(volume, this.all, x0, z0, x1, z1, y0);
      int width = Math.max(x1 - x0 + 1, z1 - z0 + 1), thin = Math.min(x1 - x0 + 1, z1 - z0 + 1), height = y1 - y0 + 1;
      CollapseVariant variant;
      if (only && height >= width * 1.4) {
         variant = CollapseVariant.TOPPLE;
      } else if (only && thin <= 2) {
         variant = CollapseVariant.FACADE_SHEAR;
      } else if (blocks.length < CollapseRules.MIN_SECTION * 2) {
         variant = CollapseVariant.CRUMBLE;
      } else {
         variant = CollapseVariant.PANCAKE;
      }
      // the first stage carries the weight of the whole structure, the middle ones stay below the full boom
      CollapseSeverity severity = this.stages == 1 ? CollapseSeverity.of(Math.max(blocks.length, this.total / 4), cause) : CollapseSeverity.of(blocks.length, cause);
      if (!last && this.stages > 1 && severity == CollapseSeverity.CATASTROPHIC) {
         severity = CollapseSeverity.MAJOR;
      }
      return new CollapseAnalyzer.Plan("ok", blocks, CollapseAnalyzer.Rule.SUPPORT, variant, severity, fallX, fallZ, x0, y0, z0, x1, y1, z1, ground, this.total,
         !last, this.total);
   }

   /** Mean surface under a stage footprint (sampled), like the box analysis; at most 320 blocks down. */
   static int ground(CollapseAnalyzer.Volume volume, LongOpenHashSet in, int minX, int minZ, int maxX, int maxZ, int fromY) {
      int stepX = Math.max(1, (maxX - minX) / 4), stepZ = Math.max(1, (maxZ - minZ) / 4);
      int best = Integer.MIN_VALUE, samples = 0;
      long sum = 0;
      for (int x = minX; x <= maxX; x += stepX) {
         for (int z = minZ; z <= maxZ; z += stepZ) {
            int found = fromY - 320;
            for (int y = fromY - 1; y > fromY - 320; y--) {
               byte m = volume.at(x, y, z);
               if (m == CollapseAnalyzer.UNKNOWN) {
                  break;
               }
               if ((m == CollapseAnalyzer.STRUCTURE || m == CollapseAnalyzer.NATURAL || m == CollapseAnalyzer.FIXED) && !in.contains(BlockKey.of(x, y, z))) {
                  found = y;
                  break;
               }
            }
            best = Math.max(best, found);
            sum += found;
            samples++;
         }
      }
      int mean = samples == 0 ? fromY - 1 : (int)Math.floor((double)sum / samples);
      return samples == 0 ? fromY - 1 : (best + mean) / 2;
   }
}
