package dev.baranhan.viltrumitecore.hero.reinhard;

import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiPredicate;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Spec §3.1: falling below the world never kills Reinhard; an air step lifts
 * him onto the surface above the fall point (under a roof such as the Nether's:
 * the highest floor below it, never the roof), else the nearest loaded column
 * with ground, else the level spawn (End: the obsidian platform).
 */
public final class VoidRescue {
   private static final int SEARCH_STEP = 4;

   private VoidRescue() {
   }

   /** Below the rescue depth under the world. */
   public static boolean needsRescue(double y, int minBuildHeight) {
      return y < minBuildHeight - ReinhardRules.VOID_RESCUE_DEPTH;
   }

   /** Top-down scan of one column: the highest y in [minY, maxY] where {@code solidAt} holds. */
   public static OptionalInt surfaceY(int minY, int maxY, IntPredicate solidAt) {
      for (int y = maxY; y >= minY; y--) {
         if (solidAt.test(y)) {
            return OptionalInt.of(y);
         }
      }

      return OptionalInt.empty();
   }

   /**
    * A roofed column (the Nether): the highest floor under the ceiling, never the roof.
    * Below {@code maxY} the ceiling's solid run is skipped, then the first solid y with
    * two free blocks above it is the floor; empty when the column has none.
    */
   public static OptionalInt floorBelowCeiling(int minY, int maxY, IntPredicate solidAt) {
      int y = maxY;
      while (y >= minY && solidAt.test(y)) {
         y--;
      }

      for (; y >= minY; y--) {
         if (solidAt.test(y) && !solidAt.test(y + 1) && !solidAt.test(y + 2)) {
            return OptionalInt.of(y);
         }
      }

      return OptionalInt.empty();
   }

   /** The column's top reaches the logical ceiling of a dimension that has one: the top is a roof. */
   public static boolean roofed(boolean hasCeiling, int top, int minBuildHeight, int logicalHeight) {
      return hasCeiling && top >= minBuildHeight + logicalHeight - 1;
   }

   /**
    * Nearest column offset (dx, dz) on a {@code step} grid within {@code radius}
    * (Euclidean, centre excluded) for which {@code hasTop} holds.
    */
   public static Optional<int[]> nearestColumn(int radius, int step, BiPredicate<Integer, Integer> hasTop) {
      List<int[]> grid = new ArrayList<>();
      int cells = radius / step;
      for (int i = -cells; i <= cells; i++) {
         for (int j = -cells; j <= cells; j++) {
            int dx = i * step;
            int dz = j * step;
            if ((dx != 0 || dz != 0) && dx * dx + dz * dz <= radius * radius) {
               grid.add(new int[]{dx, dz});
            }
         }
      }

      grid.sort(Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1]).thenComparingInt(o -> o[0]).thenComparingInt(o -> o[1]));
      for (int[] offset : grid) {
         if (hasTop.test(offset[0], offset[1])) {
            return Optional.of(offset);
         }
      }

      return Optional.empty();
   }

   /** Server tick: lift the player when he fell below the world. */
   public static boolean tick(ServerPlayer player) {
      if (player.isSpectator() || !player.isAlive() || !needsRescue(player.getY(), player.level().getMinBuildHeight())) {
         return false;
      }

      rescue(player);
      return true;
   }

   static void rescue(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      if (player.isPassenger()) {
         player.stopRiding();
      }

      BlockPos to = target(level, player.blockPosition());
      player.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
      player.setDeltaMovement(Vec3.ZERO);
      player.hurtMarked = true;
      player.fallDistance = 0.0F;
      BlockState ground = level.getBlockState(to.below());
      HeroFx.launch(player, player.position(), 1.0F, ground.isAir() ? null : ground);
      ReinhardSounds.play(player, ReinhardSounds.AIR_STEP.get(), 1.0F, 1.2F);
   }

   /** Feet position of the rescue: one block above the found surface. */
   static BlockPos target(ServerLevel level, BlockPos fall) {
      int x = fall.getX();
      int z = fall.getZ();
      OptionalInt above = columnTop(level, x, z);
      if (above.isPresent()) {
         return new BlockPos(x, above.getAsInt() + 1, z);
      }

      Optional<int[]> near = nearestColumn(ReinhardRules.VOID_SEARCH_RADIUS, SEARCH_STEP, (dx, dz) -> columnTop(level, x + dx, z + dz).isPresent());
      if (near.isPresent()) {
         int nx = x + near.get()[0];
         int nz = z + near.get()[1];
         return new BlockPos(nx, columnTop(level, nx, nz).getAsInt() + 1, nz);
      }

      if (level.dimension() == Level.END) {
         ServerLevel.makeObsidianPlatform(level);
         return ServerLevel.END_SPAWN_POINT;
      }

      BlockPos spawn = level.getSharedSpawnPos();
      OptionalInt spawnTop = columnTop(level, spawn.getX(), spawn.getZ());
      return spawnTop.isPresent() ? new BlockPos(spawn.getX(), spawnTop.getAsInt() + 1, spawn.getZ()) : spawn;
   }

   /** Highest standable block of a loaded column (motion-blocking or fluid); under a roof, the floor below it. */
   private static OptionalInt columnTop(ServerLevel level, int x, int z) {
      if (!level.hasChunk(x >> 4, z >> 4)) {
         return OptionalInt.empty();
      }

      int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      IntPredicate solid = y -> {
         BlockState state = level.getBlockState(pos.set(x, y, z));
         return state.blocksMotion() || !state.getFluidState().isEmpty();
      };
      if (roofed(level.dimensionType().hasCeiling(), top, level.getMinBuildHeight(), level.getLogicalHeight())) {
         return floorBelowCeiling(level.getMinBuildHeight(), top, solid);
      }

      return surfaceY(level.getMinBuildHeight(), top, solid);
   }
}
