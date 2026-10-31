package dev.baranhan.viltrumitecore.client.reinhard.finalpunch;

import net.minecraft.world.phys.Vec3;

/**
 * Where the right fist is on the {@code final_punch} clip when the body was not rendered this
 * frame (first-person ends, culled caster): the clip's fist centre sampled by
 * tools/assets/make_reinhard_final_punch.py (forward, up, right of the feet, blocks, scene yaw).
 * The rendered hand ({@link FinalPunchClient#recordFist}) always wins when it is fresh.
 */
public final class FinalPunchFist {
   // GENERATED-FIST-TABLE-BEGIN
   static final double[][] KEYS = {
      {0, 0.05, 0.76, 0.33},
      {480, 0.05, 0.76, 0.33},
   };
   // GENERATED-FIST-TABLE-END

   private FinalPunchFist() {
   }

   /** Pure: the clip fist at scene tick {@code t} (forward, up, right). */
   public static double[] local(double t) {
      if (t <= KEYS[0][0]) return new double[]{KEYS[0][1], KEYS[0][2], KEYS[0][3]};
      for (int i = 1; i < KEYS.length; i++) {
         if (t <= KEYS[i][0]) {
            double[] a = KEYS[i - 1];
            double[] b = KEYS[i];
            double w = (t - a[0]) / Math.max(1.0E-6, b[0] - a[0]);
            return new double[]{a[1] + (b[1] - a[1]) * w, a[2] + (b[2] - a[2]) * w, a[3] + (b[3] - a[3]) * w};
         }
      }
      double[] last = KEYS[KEYS.length - 1];
      return new double[]{last[1], last[2], last[3]};
   }

   public static Vec3 world(Vec3 feet, float yaw, double t) {
      double[] l = local(t);
      Vec3 fw = Vec3.directionFromRotation(0, yaw);
      Vec3 right = new Vec3(-fw.z, 0, fw.x);
      return feet.add(fw.scale(l[0])).add(0, l[1], 0).add(right.scale(l[2]));
   }

   static Vec3 estimate(FinalPunchClient.Scene scene, double t) {
      return world(scene.feet(), scene.yaw, t);
   }
}
