package dev.baranhan.viltrumitecore.scp.scp096;

import it.unimi.dsi.fastutil.longs.LongSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * World probes of the vertical pursuit (issue #137): the outer wall column to climb under the nearest edge of the
 * target's floor, the slab above the head or under the feet to punch through, free space to pull itself into. Every
 * probe is bounded (a few thousand block reads at most, only in loaded chunks) and runs at a re-plan, not every tick.
 */
final class Scp096Probe {
   /** Farthest the edge search walks out from the target. */
   static final double EDGE_REACH = 40.0;
   static final int DIRECTIONS = 16;
   /** Lowest the climb column is followed down when counting the ledges that jut into it. */
   static final int COLUMN_DEPTH = 320;

   private Scp096Probe() {
   }

   /**
    * One outer wall column: where to stand (block x, z), the wall direction (one horizontal axis, from the column toward
    * the building), the feet height on the target's floor, the blocks jutting into the column below that floor.
    */
   record Column(int x, int z, int wallX, int wallZ, int landingY, int overhangs, double score) {
      Vec3 center(double y) {
         return new Vec3(this.x + 0.5, y, this.z + 0.5);
      }

      long key() {
         return BlockPos.asLong(this.x, 0, this.z);
      }
   }

   /** A slab to punch: its lowest (for a ceiling) or highest (for a floor) layer, thickness, whether every block may go. */
   record Slab(int near, int thickness, boolean breakable) {
   }

   static boolean solid(ServerLevel level, BlockPos p) {
      if (!level.isLoaded(p)) {
         return true;
      }
      BlockState s = level.getBlockState(p);
      return !s.getCollisionShape(level, p).isEmpty();
   }

   private static boolean solid(ServerLevel level, BlockPos.MutableBlockPos m, int x, int y, int z) {
      return solid(level, m.set(x, y, z));
   }

   /** Block columns under the box (inflated by pad), as {x0, x1, z0, z1}. */
   static int[] footprint(AABB bb, double pad) {
      return new int[]{Mth.floor(bb.minX - pad), Mth.floor(bb.maxX + pad - 1.0E-4), Mth.floor(bb.minZ - pad), Mth.floor(bb.maxZ + pad - 1.0E-4)};
   }

   /** The 2x2 block columns around the block corner nearest to the body: the hole the bore punches. */
   static int[] hole(Entity mob) {
      int cx = (int)Math.round(mob.getX());
      int cz = (int)Math.round(mob.getZ());
      return new int[]{cx - 1, cx, cz - 1, cz};
   }

   /**
    * The slab right above the head over the columns {@code f}: the first layer within {@code reach} with any solid block,
    * how thick it is (layers in a row with something solid, up to {@link Scp096Route#MAX_SLAB} + 1) and whether every
    * block of it may be broken (unbreakable or too hard ones make the spot useless). Null when nothing is above.
    */
   @Nullable
   static Slab ceiling(ServerLevel level, Scp096Entity mob, int[] f, int reach) {
      return slab(level, mob, f, Mth.floor(mob.getBoundingBox().maxY + 0.02), 1, reach);
   }

   /** The floor under the feet over the columns {@code f} with open space below it: null for the ground or nothing. */
   @Nullable
   static Slab floor(ServerLevel level, Scp096Entity mob, int[] f) {
      Slab s = slab(level, mob, f, Mth.floor(mob.getY() - 0.02), -1, 1);
      if (s == null) {
         return null;
      }
      // a floor, not the ground: three open layers right under it
      int below = s.near() - s.thickness();
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int dy = 0; dy < 3; dy++) {
         for (int x = f[0]; x <= f[1]; x++) {
            for (int z = f[2]; z <= f[3]; z++) {
               if (solid(level, m, x, below - dy, z)) {
                  return null;
               }
            }
         }
      }
      return s;
   }

   @Nullable
   private static Slab slab(ServerLevel level, Scp096Entity mob, int[] f, int y0, int step, int reach) {
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      int first = Integer.MIN_VALUE;
      for (int i = 0; i < reach; i++) {
         if (layerSolid(level, m, f, y0 + i * step)) {
            first = y0 + i * step;
            break;
         }
      }
      if (first == Integer.MIN_VALUE) {
         return null;
      }
      int thick = 0;
      boolean ok = true;
      while (thick <= Scp096Route.MAX_SLAB && layerSolid(level, m, f, first + thick * step)) {
         int y = first + thick * step;
         for (int x = f[0]; x <= f[1]; x++) {
            for (int z = f[2]; z <= f[3]; z++) {
               m.set(x, y, z);
               BlockState s = level.getBlockState(m);
               if (!s.getCollisionShape(level, m).isEmpty() && !Scp096Destruction.breakable(level, m.immutable(), s, mob, false)) {
                  ok = false;
               }
            }
         }
         thick++;
      }
      return new Slab(first, thick, ok && thick <= Scp096Route.MAX_SLAB);
   }

   private static boolean layerSolid(ServerLevel level, BlockPos.MutableBlockPos m, int[] f, int y) {
      for (int x = f[0]; x <= f[1]; x++) {
         for (int z = f[2]; z <= f[3]; z++) {
            if (solid(level, m, x, y, z)) {
               return true;
            }
         }
      }
      return false;
   }

   /** The body box shifted by (dx, dy, dz) is free of colliders (blocks only). */
   static boolean free(ServerLevel level, Scp096Entity mob, double dx, double dy, double dz) {
      return level.noCollision(mob, mob.getBoundingBox().move(dx, dy, dz).deflate(0.02));
   }

   /** Nothing solid in the block column (x, z) from y up {@code n} layers: open sky over a wall top, not a window. */
   static boolean openAbove(ServerLevel level, int x, int y, int z, int n) {
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int i = 0; i < n; i++) {
         if (solid(level, m, x, y + i, z)) {
            return false;
         }
      }
      return true;
   }

   /**
    * Feet height of the floor the target stands on (or hovers over, within 30 blocks): the block under its feet plus
    * one. {@link Integer#MIN_VALUE} when it hovers over nothing nearby.
    */
   static int landing(ServerLevel level, Entity target) {
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      int x = Mth.floor(target.getX());
      int z = Mth.floor(target.getZ());
      int feet = Mth.floor(target.getY() + 0.01);
      for (int dy = 1; dy <= 30; dy++) {
         if (solid(level, m, x, feet - dy, z)) {
            return feet - dy + 1;
         }
      }
      return Integer.MIN_VALUE;
   }

   /**
    * The best outer wall column to climb to the target: walks out from the target in {@link #DIRECTIONS} directions on
    * its floor until the floor ends over a real drop, steps back onto a thin ledge column if the floor ended on one
    * (cornices), counts the blocks that jut into the column below and scores run, walk and ledges
    * ({@link Scp096Route#facadeScore}). For a target hovering over nothing nearby the search starts from the closest
    * building top within leap reach below it (a vantage point to pounce from). Null when no edge is found.
    */
   @Nullable
   static Column facade(ServerLevel level, Scp096Entity mob, Entity target, boolean griefing, LongSet failed) {
      double tx = target.getX();
      double tz = target.getZ();
      int landingY = landing(level, target);
      if (landingY == Integer.MIN_VALUE) {
         Vec3 v = vantage(level, target);
         if (v == null) {
            return null;
         }
         tx = v.x;
         tz = v.z;
         landingY = Mth.floor(v.y);
      }
      int floorY = landingY - 1;
      int mobY = Mth.floor(mob.getY());
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      Column best = null;
      for (int k = 0; k < DIRECTIONS; k++) {
         double a = Math.PI * 2.0 * k / DIRECTIONS;
         double cx = Math.cos(a);
         double cz = Math.sin(a);
         int px = Mth.floor(tx);
         int pz = Mth.floor(tz);
         int ex = Integer.MIN_VALUE;
         int ez = 0;
         int ix = px;
         int iz = pz;
         for (double s = 0.5; s <= EDGE_REACH; s += 0.5) {
            int x = Mth.floor(tx + cx * s);
            int z = Mth.floor(tz + cz * s);
            if (x == px && z == pz) {
               continue;
            }
            if (!level.isLoaded(m.set(x, floorY, z))) {
               break;
            }
            px = x;
            pz = z;
            if (solid(level, m, x, floorY, z)) {
               ix = x;
               iz = z;
               continue;
            }
            // the floor ends: a real drop (two more open layers below), not a gap in the slab
            if (!solid(level, m, x, floorY - 1, z) && !solid(level, m, x, floorY - 2, z)) {
               ex = x;
               ez = z;
            }
            break;
         }
         if (ex == Integer.MIN_VALUE) {
            continue;
         }
         // the wall axis: from the open column toward the floor cell beside it that faces back along the march (a
         // diagonal ray crosses the edge on one axis only: the other step was still on the floor)
         int wx = Integer.signum(ix - ex);
         int wz = Integer.signum(iz - ez);
         if (wx != 0 && wz != 0) {
            boolean xFloor = solid(level, m, ex + wx, floorY, ez);
            boolean zFloor = solid(level, m, ex, floorY, ez + wz);
            if (xFloor != zFloor) {
               if (xFloor) {
                  wz = 0;
               } else {
                  wx = 0;
               }
            } else if (Math.abs(cx) >= Math.abs(cz)) {
               wz = 0;
            } else {
               wx = 0;
            }
         }
         if (wx == 0 && wz == 0) {
            continue;
         }
         // a floor that ended on a thin ledge (a cornice): climb right against the wall under it instead
         int colX = ex;
         int colZ = ez;
         for (int step = 0; step < 2; step++) {
            int inX = colX + wx;
            int inZ = colZ + wz;
            if (mostlyOpenBelow(level, m, inX, floorY - 1, inZ, Math.min(24, Math.max(4, floorY - mobY)))) {
               colX = inX;
               colZ = inZ;
            } else {
               break;
            }
         }
         int overhangs = 0;
         int bottom = Math.max(level.getMinBuildHeight(), Math.min(floorY - 1, mobY) - 2);
         for (int y = floorY; y >= bottom && floorY - y <= COLUMN_DEPTH; y--) {
            if (solid(level, m, colX, y, colZ)) {
               overhangs++;
            }
         }
         double run = Math.hypot(colX + 0.5 - mob.getX(), colZ + 0.5 - mob.getZ());
         double walk = Math.hypot(colX + 0.5 - target.getX(), colZ + 0.5 - target.getZ());
         boolean bad = failed.contains(BlockPos.asLong(colX, 0, colZ));
         double score = Scp096Route.facadeScore(run, walk, overhangs, griefing, bad);
         if (best == null || score < best.score()) {
            best = new Column(colX, colZ, wx, wz, landingY, overhangs, score);
         }
      }
      return best;
   }

   /** At least three quarters of the n layers from y down in the column are open (a ledge over a drop). */
   private static boolean mostlyOpenBelow(ServerLevel level, BlockPos.MutableBlockPos m, int x, int y, int z, int n) {
      int open = 0;
      for (int i = 0; i < n; i++) {
         if (!solid(level, m, x, y - i, z)) {
            open++;
         }
      }
      return open * 4 >= n * 3;
   }

   /**
    * For a target hovering over nothing: the top of the closest building within 24 blocks whose roof is at most
    * {@link Scp096Rules#LEAP_MAX_UP} - 4 below it (feet position on that roof), or null.
    */
   @Nullable
   static Vec3 vantage(ServerLevel level, Entity target) {
      int ty = Mth.floor(target.getY());
      int bx = Mth.floor(target.getX());
      int bz = Mth.floor(target.getZ());
      for (int r = 3; r <= 24; r += 3) {
         Vec3 best = null;
         int bestTop = Integer.MIN_VALUE;
         for (int k = 0; k < DIRECTIONS; k++) {
            double a = Math.PI * 2.0 * k / DIRECTIONS;
            int x = bx + (int)Math.round(Math.cos(a) * r);
            int z = bz + (int)Math.round(Math.sin(a) * r);
            if (!level.isLoaded(new BlockPos(x, ty, z))) {
               continue;
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (top <= ty + 1 && top >= ty - (int)Scp096Rules.LEAP_MAX_UP + 4 && top > bestTop) {
               bestTop = top;
               best = new Vec3(x + 0.5, top, z + 0.5);
            }
         }
         if (best != null) {
            return best;
         }
      }
      return null;
   }
}
