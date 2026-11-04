package dev.baranhan.viltrumitecore.client.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class TrimGeometryTest {
   private static double area(float[] p, int n) {
      double a = 0.0;
      for (int i = 0; i < n; i++) {
         int j = (i + 1) % n;
         a += (double)p[2 * i] * p[2 * j + 1] - (double)p[2 * j] * p[2 * i + 1];
      }
      return a / 2.0;
   }

   @Test
   void halvesTileTheRectangleWithVanillaWinding() {
      Random r = new Random(7);
      float[] up = new float[10], lo = new float[10];
      for (int k = 0; k < 5000; k++) {
         float x0 = -1.0F + r.nextFloat() * 1.2F, x1 = x0 + 0.05F + r.nextFloat() * (1.0F - x0 - 0.05F);
         float y0 = -1.0F + r.nextFloat() * 1.2F, y1 = y0 + 0.05F + r.nextFloat() * (1.0F - y0 - 0.05F);
         int nu = TrimGeometry.clip(x0, y0, x1, y1, true, up);
         int nl = TrimGeometry.clip(x0, y0, x1, y1, false, lo);
         double rect = (double)(x1 - x0) * (y1 - y0);
         double au = nu >= 3 ? area(up, nu) : 0.0, al = nl >= 3 ? area(lo, nl) : 0.0;
         // vanilla corner order (x0,y0) (x0,y1) (x1,y1) (x1,y0) is clockwise in (x, y): negative signed area
         assertTrue(au <= 1e-9 && al <= 1e-9, "winding flipped");
         assertEquals(rect, -(au + al), 1e-5, "halves must cover the rectangle exactly once");
         for (int i = 0; i < nu; i++) {
            assertTrue(up[2 * i + 1] >= up[2 * i] - 1e-6F, "upper half point below the diagonal");
         }
         for (int i = 0; i < nl; i++) {
            assertTrue(lo[2 * i + 1] <= lo[2 * i] + 1e-6F, "lower half point above the diagonal");
         }
         assertTrue(nu <= 5 && nl <= 5);
      }
   }

   @Test
   void fogMatchesTheOriginalTrianglesAndIsContinuous() {
      float d0 = 20.0F, d1 = 35.8F, d2 = 45.7F, d3 = 30.1F;
      assertEquals(d0, TrimGeometry.fogAt(-1, -1, d0, d1, d2, d3), 1e-5);
      assertEquals(d1, TrimGeometry.fogAt(-1, 1, d0, d1, d2, d3), 1e-5);
      assertEquals(d2, TrimGeometry.fogAt(1, 1, d0, d1, d2, d3), 1e-5);
      assertEquals(d3, TrimGeometry.fogAt(1, -1, d0, d1, d2, d3), 1e-5);
      for (float t = -1.0F; t <= 1.0F; t += 0.125F) {
         float upper = d0 + (d2 - d1) * (t + 1) / 2 + (d1 - d0) * (t + 1) / 2;
         float lower = d0 + (d3 - d0) * (t + 1) / 2 + (d2 - d3) * (t + 1) / 2;
         assertEquals(upper, lower, 1e-4, "the two triangles must agree on the diagonal");
         assertEquals(upper, TrimGeometry.fogAt(t, t, d0, d1, d2, d3), 1e-4);
      }
      // centre of the upper triangle (-1/3, 1/3) = average of its corners
      assertEquals((d0 + d1 + d2) / 3.0F, TrimGeometry.fogAt(-1.0F / 3, 1.0F / 3, d0, d1, d2, d3), 1e-4);
      assertEquals((d0 + d2 + d3) / 3.0F, TrimGeometry.fogAt(1.0F / 3, -1.0F / 3, d0, d1, d2, d3), 1e-4);
   }

   @Test
   void packedLightKeepsTheLightmapAndTheDistance() {
      int[] lights = {0, 0xF000F0, 15 << 4 | 0 << 20, 7 << 4 | 12 << 20};
      for (int light : lights) {
         for (float d = 0.0F; d < TrimGeometry.MAX_FOG_DISTANCE; d += 3.37F) {
            int p = TrimGeometry.packLight(light, d);
            assertEquals(light & 0xFF, p & 0xFF, "block light lost");
            assertEquals(light >>> 16 & 0xFF, p >>> 16 & 0xFF, "sky light lost");
            assertTrue((p & 0xFFFF) < 0x8000 && (p >>> 16) < 0x8000, "must stay a positive short");
            assertEquals(d, TrimGeometry.unpackFog(p), 0.5F / TrimGeometry.FOG_STEP + 1e-4F);
         }
      }
      assertEquals(-1.0F, TrimGeometry.unpackFog(0xF000F0), "plain light must decode as 'no fog distance'");
   }
}
