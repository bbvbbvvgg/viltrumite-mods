package dev.baranhan.viltrumitecore.client.reinhard;

import org.junit.jupiter.api.Test;

/** REINHARD_FP_PROBE=...: prints first-person blade NDC for authoring (not an assertion). */
class FirstPersonBladeProbeTest {
   static float fitCost(dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel arms, float[] c, float hx, float hy, float tx, float ty) {
      var f = FirstPersonBlade.frame(arms, new ReinhardFirstPersonPose.Offset(c[3], c[4], c[5], c[0], c[1], c[2]));
      float[] h = FirstPersonBlade.ndc(FirstPersonBlade.point(f, FirstPersonBlade.GUARD));
      float[] p = FirstPersonBlade.ndc(FirstPersonBlade.point(f, FirstPersonBlade.TIP));
      if (h[2] < .25F || p[2] < .25F) return 1e6F;
      return (h[0]-hx)*(h[0]-hx)+(h[1]-hy)*(h[1]-hy)+.6F*((p[0]-tx)*(p[0]-tx)+(p[1]-ty)*(p[1]-ty))
         + 2*Math.max(0, h[2] + .15F - p[2]) + .00002F*(c[0]*c[0]+c[1]*c[1]+c[2]*c[2]) + .3F*(c[3]*c[3]+c[4]*c[4]+c[5]*c[5]);
   }

   @Test
   void probe() throws Exception {
      String spec = System.getenv("REINHARD_FP_PROBE");
      if (spec == null) return;
      var arms = FirstPersonBlade.arms();
      var clips = ReinhardSecondPassTest.clips();
      FirstPersonBlade.poseElbows(arms, clips, "idle_holy_drawn", 0);
      System.out.println("PROBE drawn-idle none" + FirstPersonBlade.describe(FirstPersonBlade.frame(arms, ReinhardFirstPersonPose.Offset.NONE)));
      if (spec.startsWith("fit:")) {
         // fit:name=hx,hy,tx,ty|name=...  coarse rotation grid, then coordinate refinement
         for (String target : spec.substring(4).split("\\|")) {
            String name = target.substring(0, target.indexOf('='));
            String[] t = target.substring(target.indexOf('=') + 1).split(",");
            float hx = Float.parseFloat(t[0]), hy = Float.parseFloat(t[1]), tx = Float.parseFloat(t[2]), ty = Float.parseFloat(t[3]);
            float[] best = null;
            for (float pitch = -130; pitch <= 50; pitch += 10)
               for (float yaw = -70; yaw <= 70; yaw += 10)
                  for (float roll = -100; roll <= 100; roll += 10)
                     for (float x = -.2F; x <= .41F; x += .2F)
                        for (float y = -.2F; y <= .41F; y += .2F) {
                           float[] c = {pitch, yaw, roll, x, y, 0};
                           float cost = fitCost(arms, c, hx, hy, tx, ty);
                           if (best == null || cost < best[6]) best = new float[]{pitch, yaw, roll, x, y, 0, cost};
                        }
            float[] steps = {5, 5, 5, .05F, .05F, .05F};
            for (int round = 0; round < 6; round++) {
               for (int pass = 0; pass < 40; pass++) {
                  boolean improved = false;
                  for (int k = 0; k < 6; k++)
                     for (float sgn : new float[]{-1, 1}) {
                        float[] c = java.util.Arrays.copyOf(best, 6);
                        c[k] += sgn * steps[k];
                        if (k >= 3 && Math.abs(c[k]) > .5F) continue;
                        if (c[0] < -140 || c[0] > 60 || Math.abs(c[1]) > 80 || Math.abs(c[2]) > 130) continue;
                        float cost = fitCost(arms, c, hx, hy, tx, ty);
                        if (cost < best[6] - 1e-6F) { System.arraycopy(c, 0, best, 0, 6); best[6] = cost; improved = true; }
                     }
                  if (!improved) break;
               }
               for (int k = 0; k < 6; k++) steps[k] *= .5F;
            }
            var o = new ReinhardFirstPersonPose.Offset(best[3], best[4], best[5], best[0], best[1], best[2]);
            System.out.printf("PROBE fit %s cost=%.4f key(T,%.1fF,%.1fF,%.1fF,%.3fF,%.3fF,%.3fF)%s%n", name, best[6], best[0], best[1], best[2], best[3], best[4], best[5],
               FirstPersonBlade.describe(FirstPersonBlade.frame(arms, o)));
         }
         return;
      }
      if (spec.startsWith("search:")) {
         String[] t = spec.substring(7).split(",");
         float hx = Float.parseFloat(t[0]), hy = Float.parseFloat(t[1]), tx = Float.parseFloat(t[2]), ty = Float.parseFloat(t[3]);
         java.util.List<float[]> best = new java.util.ArrayList<>();
         for (float pitch = -45; pitch <= 10; pitch += 5)
            for (float yaw = -45; yaw <= 20; yaw += 5)
               for (float roll = -70; roll <= 0; roll += 5)
                  for (float x = -.1F; x <= .31F; x += .1F)
                     for (float y = 0F; y <= .31F; y += .1F)
                      for (float z = -.1F; z <= .11F; z += .1F) {
                        var o = new ReinhardFirstPersonPose.Offset(x, y, z, pitch, yaw, roll);
                        var f = FirstPersonBlade.frame(arms, o);
                        float[] h = FirstPersonBlade.ndc(FirstPersonBlade.point(f, FirstPersonBlade.GUARD));
                        float[] p = FirstPersonBlade.ndc(FirstPersonBlade.point(f, FirstPersonBlade.TIP));
                        float[] m = FirstPersonBlade.ndc(FirstPersonBlade.point(f, FirstPersonBlade.TIP * .5F));
                        if (h[2] < .3F || p[2] < .3F) continue;
                        float cost = (h[0]-hx)*(h[0]-hx)+(h[1]-hy)*(h[1]-hy)+(p[0]-tx)*(p[0]-tx)+(p[1]-ty)*(p[1]-ty)
                           + Math.max(0, h[2] + .3F - p[2]) + .002F*(pitch*pitch+yaw*yaw+roll*roll)/100
                           + (m[0] < .05F ? 1 : 0) + 4*Math.max(0, .15F - p[0]);
                        best.add(new float[]{cost, pitch, yaw, roll, x, y, z});
                     }
         best.sort((a, b) -> Float.compare(a[0], b[0]));
         for (int i = 0; i < 8 && i < best.size(); i++) {
            float[] b = best.get(i);
            var o = new ReinhardFirstPersonPose.Offset(b[4], b[5], b[6], b[1], b[2], b[3]);
            System.out.printf("PROBE best cost=%.3f p=%.0f y=%.0f r=%.0f x=%.2f y=%.2f z=%.2f%s%n", b[0], b[1], b[2], b[3], b[4], b[5], b[6],
               FirstPersonBlade.describe(FirstPersonBlade.frame(arms, o)));
         }
         return;
      }
      for (String row : spec.split(";")) {
         if (row.isBlank() || row.equals("1")) continue;
         String[] v = row.split(",");
         var o = new ReinhardFirstPersonPose.Offset(Float.parseFloat(v[3]), Float.parseFloat(v[4]), Float.parseFloat(v[5]),
            Float.parseFloat(v[0]), Float.parseFloat(v[1]), Float.parseFloat(v[2]));
         System.out.println("PROBE " + row + FirstPersonBlade.describe(FirstPersonBlade.frame(arms, o)));
      }
   }
}
