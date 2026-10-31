package dev.baranhan.viltrumitecore.worldevent.chitauri;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Chunk-safe terrain queries for Chitauri AI and destruction. Nothing here loads or generates a
 * chunk: an unloaded column reads as "unknown" (no ground, solid for rays), so the event avoids it.
 */
public final class ChitauriTerrain {
   public static final int UNKNOWN = Integer.MIN_VALUE;

   private ChitauriTerrain() {
   }

   public static boolean loaded(Level level, double x, double z) {
      return level.hasChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
   }

   /** Entity-ticking (safe to spawn into or fly through). */
   public static boolean ticking(ServerLevel level, double x, double y, double z) {
      BlockPos pos = BlockPos.containing(x, Mth.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1), z);
      return level.hasChunkAt(pos) && level.isPositionEntityTicking(pos);
   }

   /** Top of the motion-blocking surface (buildings included), or {@link #UNKNOWN} when not loaded. */
   public static int surface(Level level, double x, double z) {
      int bx = Mth.floor(x);
      int bz = Mth.floor(z);
      if (!level.hasChunk(bx >> 4, bz >> 4)) {
         return UNKNOWN;
      }
      return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
   }

   /** Highest surface over a small square (a chariot's clearance), {@link #UNKNOWN} if any column is unloaded. */
   public static int surfaceMax(Level level, double x, double z, int radius) {
      int best = UNKNOWN;
      for (int dx = -radius; dx <= radius; dx += Math.max(1, radius)) {
         for (int dz = -radius; dz <= radius; dz += Math.max(1, radius)) {
            int s = surface(level, x + dx, z + dz);
            if (s == UNKNOWN) {
               return UNKNOWN;
            }
            best = Math.max(best, s);
         }
      }
      return best;
   }

   public static boolean solid(Level level, BlockPos pos) {
      if (!level.hasChunkAt(pos)) {
         return true;
      }
      BlockState state = level.getBlockState(pos);
      return !state.isAir() && state.blocksMotion();
   }

   /**
    * Distance along {@code from -> to} to the first solid (or unloaded) block, or the full length
    * when the way is clear. Voxel walk, so it never loads a chunk (unlike Level.clip).
    */
   public static double clearDistance(Level level, Vec3 from, Vec3 to) {
      Vec3 d = to.subtract(from);
      double len = d.length();
      if (len < 1.0E-4) {
         return 0.0;
      }
      double step = 0.5;
      int n = (int)Math.ceil(len / step);
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      int lx = Integer.MIN_VALUE, ly = 0, lz = 0;
      for (int i = 1; i <= n; i++) {
         double f = Math.min(1.0, i * step / len);
         int x = Mth.floor(from.x + d.x * f);
         int y = Mth.floor(from.y + d.y * f);
         int z = Mth.floor(from.z + d.z * f);
         if (x == lx && y == ly && z == lz) {
            continue;
         }
         lx = x;
         ly = y;
         lz = z;
         pos.set(x, y, z);
         if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            if (y < level.getMinBuildHeight()) {
               return f * len;
            }
            continue;
         }
         if (solid(level, pos)) {
            return Math.max(0.0, (f * len) - step);
         }
      }
      return len;
   }

   /** True when nothing solid lies between the two points. */
   public static boolean clear(Level level, Vec3 from, Vec3 to) {
      return clearDistance(level, from, to) >= from.distanceTo(to) - 1.0E-3;
   }
}
