package dev.baranhan.viltrumitecore.client.jinwoo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Owner-side motion instructions of the server (step, dive, charge, air float):
 * the velocity is re-applied every client tick for its ticks, stopped by a wall
 * or the ground (dive). Client only.
 */
public final class JinwooMotion {
   private static Vec3 velocity = Vec3.ZERO;
   private static int left;
   private static boolean verticalOnly;

   private JinwooMotion() {
   }

   public static void start(Vec3 v, int ticks, boolean onlyY) {
      velocity = v;
      left = ticks;
      verticalOnly = onlyY;
      apply();
   }

   public static void tick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null || left <= 0) {
         left = 0;
         return;
      }
      left--;
      if (!verticalOnly && velocity.y < -0.5 && player.onGround()) {
         left = 0;
         return;
      }
      if (!verticalOnly && velocity.horizontalDistanceSqr() > 0.01 && player.horizontalCollision) {
         left = 0;
         return;
      }
      apply();
   }

   private static void apply() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return;
      }
      Vec3 cur = player.getDeltaMovement();
      player.setDeltaMovement(verticalOnly ? new Vec3(cur.x, velocity.y, cur.z) : velocity);
      player.fallDistance = 0.0F;
   }

   public static boolean active() {
      return left > 0;
   }
}
