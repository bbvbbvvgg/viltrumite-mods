package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Swept hits of the war's projectiles (Barrage and Time Volley shards, Maw's thrown debris): the block trace runs first and
 * cuts the path, and only a body the cut segment actually passes through is hit, the nearest along the path. A hero right
 * behind a wall is inside the movement box but past the wall, so cover stops the shot.
 */
public final class SweptHits {
   private SweptHits() {
   }

   /**
    * Pure: index of the first box the segment {@code from -> to} passes through (each box inflated by {@code radius}), or
    * -1. A box the segment starts inside is hit at distance 0. {@code to} is already cut at the first block.
    */
   public static int firstAlong(Vec3 from, Vec3 to, List<AABB> boxes, double radius) {
      int best = -1;
      double bestD = Double.MAX_VALUE;
      for (int i = 0; i < boxes.size(); i++) {
         AABB b = boxes.get(i).inflate(radius);
         double d;
         if (b.contains(from)) {
            d = 0.0;
         } else {
            Optional<Vec3> at = b.clip(from, to);
            if (at.isEmpty()) {
               continue;
            }
            d = at.get().distanceToSqr(from);
         }
         if (d < bestD) {
            bestD = d;
            best = i;
         }
      }
      return best;
   }
}
