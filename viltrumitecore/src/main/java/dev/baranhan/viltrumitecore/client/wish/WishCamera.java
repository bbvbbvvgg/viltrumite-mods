package dev.baranhan.viltrumitecore.client.wish;

import dev.baranhan.viltrumitecore.hero.wish.WishTimeline;
import net.minecraft.world.phys.Vec3;

/**
 * Pure camera path of the wisher's cinematic (unit-tested for continuity). Key shots relative to the
 * player's facing at the start: a frontal push-in on the face during the plea, a low heroic swing to the
 * side while the answer builds, a wider reveal at the swap and a slow rise while the title shows. Shots are
 * smoothstep-blended between keys (no cuts) and blended with the player's own eye by
 * {@link WishTimeline#cameraWeight}, so the camera leaves and returns to the first-person view seamlessly.
 */
public final class WishCamera {
   /** {tick, angle from the facing in degrees, distance, height, look height}. */
   static final double[][] KEYS = {
      {0, 28, 3.4, 1.75, 1.55},
      {WishTimeline.CALL_END, 12, 2.6, 1.5, 1.5},
      {30, -50, 3.1, 0.65, 1.3},
      {WishTimeline.SWAP, -78, 3.9, 0.9, 1.2},
      {WishTimeline.FULFIL_END, -94, 4.4, 1.35, 1.25},
      {WishTimeline.LENGTH, -112, 4.9, 1.85, 1.45},
   };

   private WishCamera() {
   }

   /** [camera position, look target] of the shot alone at ritual tick t (feet origin, start yaw in degrees). */
   public static Vec3[] shot(Vec3 origin, float yawDeg, float t) {
      double[] k = sample(t);
      double yaw = Math.toRadians(yawDeg);
      Vec3 front = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
      Vec3 right = new Vec3(-front.z, 0, front.x);
      double a = Math.toRadians(k[1]);
      Vec3 dir = front.scale(Math.cos(a)).add(right.scale(Math.sin(a)));
      return new Vec3[]{origin.add(dir.scale(k[2])).add(0, k[3], 0), origin.add(0, k[4], 0)};
   }

   /** The shot blended with the player's own view (eye, look direction) by the timeline's camera weight. */
   public static Vec3[] blended(Vec3 origin, float yawDeg, float t, Vec3 eye, Vec3 lookDir) {
      Vec3[] shot = shot(origin, yawDeg, t);
      double w = WishTimeline.cameraWeight(t);
      Vec3 ownLook = eye.add(lookDir.normalize().scale(4.0));
      return new Vec3[]{eye.lerp(shot[0], w), ownLook.lerp(shot[1], w)};
   }

   static double[] sample(float t) {
      if (t <= KEYS[0][0]) {
         return KEYS[0];
      }
      for (int i = 1; i < KEYS.length; i++) {
         if (t <= KEYS[i][0]) {
            double[] a = KEYS[i - 1];
            double[] b = KEYS[i];
            double u = (t - a[0]) / (b[0] - a[0]);
            u = u * u * (3 - 2 * u);
            double[] out = new double[a.length];
            for (int j = 0; j < a.length; j++) {
               out[j] = a[j] + (b[j] - a[j]) * u;
            }
            return out;
         }
      }
      return KEYS[KEYS.length - 1];
   }
}
