package dev.baranhan.viltrumitecore.client.chitauri;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Soft additive shapes for POSITION_COLOR quad buffers (RenderType.lightning or a QUADS BufferBuilder):
 * radial-gradient discs, rings and tapered ribbons. Triangles are degenerate quads. Render thread only.
 */
final class GlowDraw {
   private static final float[] COS = new float[65];
   private static final float[] SIN = new float[65];

   static {
      for (int i = 0; i <= 64; i++) {
         double a = i / 64.0 * Math.PI * 2.0;
         COS[i] = (float)Math.cos(a);
         SIN[i] = (float)Math.sin(a);
      }
   }

   private GlowDraw() {
   }

   /**
    * Disc in the plane of the unit axes (ax, bx) around {@code c}: solid core (alpha {@code a}) out to {@code core} of the
    * radius, fading to zero at the rim. 16 sectors.
    */
   static void disc(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f ax, Vector3f bx, float radius, float core, int r, int g, int b, int a) {
      disc(vc, m, c, ax, bx, radius, core, 16, r, g, b, a);
   }

   /** {@link #disc} with 8, 16, 32 or 64 sectors (cheap far shapes such as cloud puffs use 8). */
   static void disc(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f ax, Vector3f bx, float radius, float core, int sectors, int r, int g, int b, int a) {
      if (a <= 0 || radius <= 0.0F) {
         return;
      }
      int n = sectors <= 8 ? 8 : sectors <= 16 ? 16 : sectors <= 32 ? 32 : 64;
      int step = 64 / n;
      float rc = radius * Mth.clamp(core, 0.05F, 0.95F);
      for (int i = 0; i < n; i++) {
         int i0 = i * step;
         int i1 = i0 + step;
         float c0 = COS[i0], s0 = SIN[i0], c1 = COS[i1], s1 = SIN[i1];
         // core fan (degenerate quad)
         put(vc, m, c, ax, bx, 0, 0, r, g, b, a);
         put(vc, m, c, ax, bx, c0 * rc, s0 * rc, r, g, b, a);
         put(vc, m, c, ax, bx, c1 * rc, s1 * rc, r, g, b, a);
         put(vc, m, c, ax, bx, 0, 0, r, g, b, a);
         // falloff band
         put(vc, m, c, ax, bx, c0 * rc, s0 * rc, r, g, b, a);
         put(vc, m, c, ax, bx, c0 * radius, s0 * radius, r, g, b, 0);
         put(vc, m, c, ax, bx, c1 * radius, s1 * radius, r, g, b, 0);
         put(vc, m, c, ax, bx, c1 * rc, s1 * rc, r, g, b, a);
      }
   }

   /** Ring band between {@code inner} and {@code outer} radii, brightest at {@code peak} (0 inner .. 1 outer). */
   static void ring(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f ax, Vector3f bx, float inner, float outer, float peak, int segments,
      int r, int g, int b, int a) {
      if (a <= 0 || outer <= inner) {
         return;
      }
      float mid = inner + (outer - inner) * Mth.clamp(peak, 0.0F, 1.0F);
      int segs = Math.max(8, Math.min(64, segments));
      for (int i = 0; i < segs; i++) {
         double a0 = i / (double)segs * Math.PI * 2.0;
         double a1 = (i + 1) / (double)segs * Math.PI * 2.0;
         float c0 = (float)Math.cos(a0), s0 = (float)Math.sin(a0), c1 = (float)Math.cos(a1), s1 = (float)Math.sin(a1);
         put(vc, m, c, ax, bx, c0 * inner, s0 * inner, r, g, b, 0);
         put(vc, m, c, ax, bx, c0 * mid, s0 * mid, r, g, b, a);
         put(vc, m, c, ax, bx, c1 * mid, s1 * mid, r, g, b, a);
         put(vc, m, c, ax, bx, c1 * inner, s1 * inner, r, g, b, 0);
         put(vc, m, c, ax, bx, c0 * mid, s0 * mid, r, g, b, a);
         put(vc, m, c, ax, bx, c0 * outer, s0 * outer, r, g, b, 0);
         put(vc, m, c, ax, bx, c1 * outer, s1 * outer, r, g, b, 0);
         put(vc, m, c, ax, bx, c1 * mid, s1 * mid, r, g, b, a);
      }
   }

   /**
    * Camera-facing ribbon from {@code p0} to {@code p1} (camera-relative), half-width {@code w0} → {@code w1}, alpha
    * {@code a0} → {@code a1}; {@code toCam} is any vector towards the eye. Bright core plus a soft edge.
    */
   static void ribbon(VertexConsumer vc, Matrix4f m, Vector3f p0, Vector3f p1, Vector3f toCam, float w0, float w1, int r, int g, int b, int a0, int a1) {
      float dx = p1.x - p0.x, dy = p1.y - p0.y, dz = p1.z - p0.z;
      float sx = dy * toCam.z - dz * toCam.y;
      float sy = dz * toCam.x - dx * toCam.z;
      float sz = dx * toCam.y - dy * toCam.x;
      float len = Mth.sqrt(sx * sx + sy * sy + sz * sz);
      if (len < 1.0E-6F) {
         return;
      }
      sx /= len;
      sy /= len;
      sz /= len;
      // soft edges (alpha 0 outside), solid centre line
      vertex(vc, m, p0.x - sx * w0, p0.y - sy * w0, p0.z - sz * w0, r, g, b, 0);
      vertex(vc, m, p0.x, p0.y, p0.z, r, g, b, a0);
      vertex(vc, m, p1.x, p1.y, p1.z, r, g, b, a1);
      vertex(vc, m, p1.x - sx * w1, p1.y - sy * w1, p1.z - sz * w1, r, g, b, 0);
      vertex(vc, m, p0.x, p0.y, p0.z, r, g, b, a0);
      vertex(vc, m, p0.x + sx * w0, p0.y + sy * w0, p0.z + sz * w0, r, g, b, 0);
      vertex(vc, m, p1.x + sx * w1, p1.y + sy * w1, p1.z + sz * w1, r, g, b, 0);
      vertex(vc, m, p1.x, p1.y, p1.z, r, g, b, a1);
   }

   /** Camera-facing soft disc: {@code left}/{@code up} are the camera axes. */
   static void billboard(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f left, Vector3f up, float radius, float core, int r, int g, int b, int a) {
      disc(vc, m, c, left, up, radius, core, r, g, b, a);
   }

   private static void put(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f ax, Vector3f bx, float u, float v, int r, int g, int b, int a) {
      vertex(vc, m, c.x + ax.x * u + bx.x * v, c.y + ax.y * u + bx.y * v, c.z + ax.z * u + bx.z * v, r, g, b, a);
   }

   static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, int r, int g, int b, int a) {
      vc.vertex(m, x, y, z).color(clamp(r), clamp(g), clamp(b), clamp(a)).endVertex();
   }

   static int clamp(int v) {
      return v < 0 ? 0 : Math.min(255, v);
   }
}
