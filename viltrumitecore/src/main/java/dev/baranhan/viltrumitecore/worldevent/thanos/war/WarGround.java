package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Ground lookups for the war's strikes, portals and drops. */
public final class WarGround {
   private WarGround() {
   }

   /** The top surface at (x, z) (roofs count: a lance hits the roof first). */
   public static Vec3 top(ServerLevel level, double x, double z) {
      int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int)Math.floor(x), (int)Math.floor(z));
      return new Vec3(x, y, z);
   }

   /** The first solid floor at or below {@code at} within {@code down} blocks (else the top surface). */
   public static Vec3 below(ServerLevel level, Vec3 at, int down) {
      BlockPos.MutableBlockPos pos = BlockPos.containing(at).mutable();
      for (int i = 0; i <= down; i++) {
         BlockPos under = pos.below();
         if (!level.getBlockState(under).getCollisionShape(level, under).isEmpty() && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return new Vec3(at.x, pos.getY(), at.z);
         }

         pos.move(0, -1, 0);
         if (pos.getY() <= level.getMinBuildHeight()) {
            break;
         }
      }

      return top(level, at.x, at.z);
   }
}
