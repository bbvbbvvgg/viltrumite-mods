package dev.baranhan.viltrumitecore.worldevent.thanos;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Where Thanos can stand: solid ground below, his whole body free of blocks and liquids, in a
 * loaded chunk. Used for the spawn, the Space stone's steps and the Time rewind.
 */
public final class ThanosPlacement {
   private ThanosPlacement() {
   }

   /** Vertical scan order around a base height: 0, -1, +1, -2, +2 ... within [-down, +up]. */
   public static int[] scan(int up, int down) {
      int[] out = new int[up + down + 1];
      int n = 0;
      out[n++] = 0;
      for (int i = 1; i <= Math.max(up, down); i++) {
         if (i <= down) {
            out[n++] = -i;
         }

         if (i <= up) {
            out[n++] = i;
         }
      }

      return out;
   }

   @Nullable
   public static Vec3 standable(ServerLevel level, @Nullable Entity body, EntityDimensions dims, Vec3 at, int up, int down) {
      int baseY = BlockPos.containing(at).getY();
      for (int dy : scan(up, down)) {
         int y = baseY + dy;
         if (y <= level.getMinBuildHeight() || y + 4 >= level.getMaxBuildHeight()) {
            continue;
         }

         BlockPos feet = BlockPos.containing(at.x, y, at.z);
         if (!level.isLoaded(feet)) {
            return null;
         }

         BlockPos below = feet.below();
         BlockState ground = level.getBlockState(below);
         if (ground.getCollisionShape(level, below).isEmpty() || !ground.getFluidState().isEmpty()) {
            continue;
         }

         Vec3 spot = new Vec3(at.x, y + ground.getCollisionShape(level, below).max(net.minecraft.core.Direction.Axis.Y) - 1.0, at.z);
         if (level.noCollision(body, dims.makeBoundingBox(spot)) && !level.containsAnyLiquid(dims.makeBoundingBox(spot))) {
            return spot;
         }
      }

      return null;
   }

   /** {@link #standable} at {@code at}, then on a ring of {@code radius} around it. */
   @Nullable
   public static Vec3 near(ServerLevel level, @Nullable Entity body, EntityDimensions dims, Vec3 at, double radius, int up, int down) {
      Vec3 spot = standable(level, body, dims, at, up, down);
      if (spot != null) {
         return spot;
      }

      for (int i = 0; i < 8; i++) {
         double a = i * Math.PI / 4.0;
         spot = standable(level, body, dims, at.add(Math.cos(a) * radius, 0.0, Math.sin(a) * radius), up, down);
         if (spot != null) {
            return spot;
         }
      }

      return null;
   }
}