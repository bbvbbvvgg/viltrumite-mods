package dev.baranhan.viltrumitecore.hero.thor.scene;

import net.minecraft.world.phys.Vec3;

/**
 * V teleport point selection (decisions 14, 29, 41-48, 71, 72, 120): Mjölnir in the hand,
 * not in full flight; the camera rises ~80 blocks and then stays; the point
 * is picked with the crosshair inside the area the camera sees and must be a
 * safe solid surface; LMB confirms, Escape cancels, 10 s limit. Thor stands
 * still and takes no damage; the world goes on. Pure.
 */
public final class TeleportRules {
   public enum Refusal {
      NONE,
      NO_HAMMER,
      FULL_FLIGHT,
      BUSY
   }

   public enum Target {
      OK,
      /** Not a solid top face. */
      NOT_SOLID,
      /** The body would not fit or there is no floor (SafeSpot). */
      UNSAFE,
      /** Outside the area the camera shows. */
      OUT_OF_VIEW
   }

   private TeleportRules() {
   }

   public static Refusal gate(boolean hammerInHand, boolean fullFlight, boolean sceneLocked) {
      if (sceneLocked) {
         return Refusal.BUSY;
      } else if (!hammerInHand) {
         return Refusal.NO_HAMMER;
      }

      return fullFlight ? Refusal.FULL_FLIGHT : Refusal.NONE;
   }

   /** The still camera point: straight above the eyes, under the build limit. */
   public static Vec3 camera(Vec3 eye, int height, int maxBuildHeight) {
      return new Vec3(eye.x, Math.min(eye.y + height, maxBuildHeight - 1.0), eye.z);
   }

   /** The camera on its way up ({@code tick} may be fractional); still once risen. */
   public static Vec3 rising(Vec3 eye, Vec3 camera, double tick, int riseTicks) {
      double s = SceneFrame.smooth(tick / Math.max(1, riseTicks));
      return eye.add(camera.subtract(eye).scale(s));
   }

   /** Inside the selectable area: within the horizontal radius of the camera column and below the camera. */
   public static boolean inView(Vec3 camera, Vec3 point, double radius) {
      double dx = point.x - camera.x;
      double dz = point.z - camera.z;
      return dx * dx + dz * dz <= radius * radius && point.y < camera.y;
   }

   public static Target judge(boolean solidTop, boolean safe, boolean inView, boolean visibleFromCamera) {
      if (!inView || !visibleFromCamera) {
         return Target.OUT_OF_VIEW;
      } else if (!solidTop) {
         return Target.NOT_SOLID;
      }

      return safe ? Target.OK : Target.UNSAFE;
   }

   /** Selection is over at the limit. */
   public static boolean expired(int ticks, int limit) {
      return ticks >= limit;
   }
}
