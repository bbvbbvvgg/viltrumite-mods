package dev.baranhan.viltrumitecore.client.venom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Procedural symbiote tendrils: a tapering hexagonal tube along a polyline, with a hooked tip, textured with the
 * tendril strip (v along the length so the strands run down it). Points are in the space of the pose handed in
 * (model space on a bone, or a body's world offset). The paths are pure (VenomTendrilsTest). Client only.
 */
public final class VenomTendrils {
   static final int SIDES = 6;

   private VenomTendrils() {
   }

   /**
    * Pure: a living tendril from {@code root} along {@code dir} (normalized), {@code length} long, curling with a
    * travelling wave; {@code seed} offsets the phase so neighbours never move in step. {@code n} points.
    */
   public static Vector3f[] wave(Vector3f root, Vector3f dir, Vector3f side, float length, double seconds, float seed, float amp, int n) {
      Vector3f up = new Vector3f(dir).cross(side).normalize();
      Vector3f[] out = new Vector3f[n];
      for (int i = 0; i < n; i++) {
         float t = i / (float)(n - 1);
         double ph = seconds * 3.1 + seed * 2.7 - t * 4.2;
         float a = amp * t * (0.4F + 0.6F * t);
         float s = (float)Math.sin(ph) * a;
         float u = (float)Math.cos(ph * 0.8 + seed) * a * 0.7F;
         out[i] = new Vector3f(root).add(new Vector3f(dir).mul(length * t)).add(new Vector3f(side).mul(s)).add(new Vector3f(up).mul(u));
      }
      return out;
   }

   /**
    * Pure: a line shot from {@code from} to {@code to}, {@code reach} 0-1 of the way (the shot extends), sagging a
    * little and rippling while taut ({@code seconds}); n points.
    */
   public static Vector3f[] line(Vector3f from, Vector3f to, float reach, double seconds, float sag, int n) {
      Vector3f d = new Vector3f(to).sub(from);
      float len = d.length();
      Vector3f dir = len > 1.0E-4F ? new Vector3f(d).div(len) : new Vector3f(0, 1, 0);
      Vector3f side = Math.abs(dir.y) < 0.95F ? new Vector3f(dir).cross(0, 1, 0).normalize() : new Vector3f(1, 0, 0);
      Vector3f[] out = new Vector3f[n];
      for (int i = 0; i < n; i++) {
         float t = i / (float)(n - 1) * reach;
         float bow = (float)Math.sin(Math.PI * t) * sag * len;
         float ripple = (float)Math.sin(seconds * 14.0 - t * 18.0) * 0.04F * (float)Math.sin(Math.PI * t);
         out[i] = new Vector3f(from).add(new Vector3f(dir).mul(len * t)).add(0, -bow, 0).add(new Vector3f(side).mul(ripple));
      }
      return out;
   }

   /** Draws a tube along the points; radius r0 at the root, r1 at the tip; the last segment closes to a hooked point. */
   public static void draw(PoseStack.Pose pose, VertexConsumer vc, Vector3f[] pts, float r0, float r1, int light, int overlay, float r, float g,
      float b, float a) {
      if (pts.length < 2) {
         return;
      }
      Matrix4f m = pose.pose();
      Matrix3f nm = pose.normal();
      Vector3f[][] rings = new Vector3f[pts.length][];
      Vector3f prevSide = null;
      for (int i = 0; i < pts.length; i++) {
         Vector3f t = i == pts.length - 1 ? new Vector3f(pts[i]).sub(pts[i - 1]) : new Vector3f(pts[i + 1]).sub(pts[i]);
         if (t.lengthSquared() < 1.0E-10F) {
            t.set(0, 1, 0);
         }
         t.normalize();
         Vector3f side = prevSide != null ? new Vector3f(prevSide).sub(new Vector3f(t).mul(prevSide.dot(t))) : null;
         if (side == null || side.lengthSquared() < 1.0E-6F) {
            side = Math.abs(t.y) < 0.9F ? new Vector3f(t).cross(0, 1, 0) : new Vector3f(t).cross(1, 0, 0);
         }
         side.normalize();
         prevSide = side;
         Vector3f up = new Vector3f(t).cross(side).normalize();
         float f = i / (float)(pts.length - 1);
         float rad = i == pts.length - 1 ? 0.0F : r0 + (r1 - r0) * f;
         Vector3f[] ring = new Vector3f[SIDES];
         for (int k = 0; k < SIDES; k++) {
            double ang = Math.PI * 2 * k / SIDES;
            ring[k] = new Vector3f(pts[i]).add(new Vector3f(side).mul((float)Math.cos(ang) * rad)).add(new Vector3f(up).mul((float)Math.sin(ang) * rad));
         }
         rings[i] = ring;
      }
      for (int i = 0; i < pts.length - 1; i++) {
         // one strip repeat per segment (the strip tiles vertically; no wrap mode needed)
         float v0 = 0.0F, v1 = 1.0F;
         for (int k = 0; k < SIDES; k++) {
            int k2 = (k + 1) % SIDES;
            Vector3f a0 = rings[i][k], a1 = rings[i][k2], b1 = rings[i + 1][k2], b0 = rings[i + 1][k];
            Vector3f n = new Vector3f(a0).add(a1).mul(0.5F).sub(pts[i]);
            if (n.lengthSquared() < 1.0E-10F) {
               n.set(0, 1, 0);
            }
            n.normalize();
            float u0 = k / (float)SIDES, u1 = (k + 1) / (float)SIDES;
            vertex(vc, m, nm, a0, u0, v0, light, overlay, n, r, g, b, a);
            vertex(vc, m, nm, b0, u0, v1, light, overlay, n, r, g, b, a);
            vertex(vc, m, nm, b1, u1, v1, light, overlay, n, r, g, b, a);
            vertex(vc, m, nm, a1, u1, v0, light, overlay, n, r, g, b, a);
         }
      }
   }

   private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vector3f p, float u, float v, int light, int overlay, Vector3f n, float r,
      float g, float b, float a) {
      vc.vertex(m, p.x, p.y, p.z).color(r, g, b, a).uv(u, v).overlayCoords(overlay).uv2(light).normal(nm, n.x, n.y, n.z).endVertex();
   }
}
