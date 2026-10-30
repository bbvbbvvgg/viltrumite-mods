package dev.baranhan.viltrumitecore.client.captain;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Owner-side motion of the server (shield rush, slam leap/dive, tactical roll): the velocity is re-applied every
 * client tick for its ticks; with gravity kept only the horizontal part is forced. A wall stops a horizontal
 * dash, the ground stops a dive. Client only.
 */
public final class CaptainMotion {
   private static Vec3 velocity = Vec3.ZERO;
   private static int left;
   private static boolean gravity;

   private CaptainMotion() {
   }

   public static void start(Vec3 v, int ticks, boolean keepGravity) {
      velocity = v;
      left = Math.max(0, ticks);
      gravity = keepGravity;
      if (left > 0) {
         apply();
      }
   }

   public static void tick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null || left <= 0) {
         left = 0;
         return;
      }
      left--;
      if (velocity.y < -0.5 && player.onGround()) {
         left = 0;
         return;
      }
      if (velocity.horizontalDistanceSqr() > 0.01 && player.horizontalCollision) {
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
      player.setDeltaMovement(gravity ? new Vec3(velocity.x, cur.y, velocity.z) : velocity);
      player.fallDistance = 0.0F;
   }

   public static boolean active() {
      return left > 0;
   }

   public static void clear() {
      left = 0;
   }
}
