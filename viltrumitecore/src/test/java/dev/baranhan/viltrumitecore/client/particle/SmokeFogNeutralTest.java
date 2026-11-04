package dev.baranhan.viltrumitecore.client.particle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The trimmed smoke quad may only be drawn when vanilla per-vertex fog is identical for every point of the full
 * quad (all unfogged or all fully fogged). Brute force over random camera-facing quads: whenever
 * {@code fogNeutral} says yes, the fog factor of every corner and interior sample must be the same 0 or 1.
 */
class SmokeFogNeutralTest {
   /** vanilla fog.glsl linear_fog: 0 at or before start, smoothstep up to end, 1 beyond. */
   private static float fog(float d, float start, float end) {
      if (d <= start) {
         return 0.0F;
      }
      if (d >= end) {
         return 1.0F;
      }
      float t = (d - start) / (end - start);
      return t * t * (3.0F - 2.0F * t);
   }

   private static float dist(float x, float y, float z, boolean cylinder) {
      return cylinder ? Math.max((float)Math.sqrt(x * x + z * z), Math.abs(y)) : (float)Math.sqrt(x * x + y * y + z * z);
   }

   @Test
   void neutralQuadsHaveUniformFog() {
      Random r = new Random(150);
      int neutral = 0;
      for (int i = 0; i < 20000; i++) {
         boolean cyl = r.nextBoolean();
         float start = 10.0F + r.nextFloat() * 180.0F;
         float end = start + 1.0F + r.nextFloat() * 60.0F;
         float px = (r.nextFloat() - 0.5F) * 2.0F * (end + 60.0F);
         float py = (r.nextFloat() - 0.5F) * 2.0F * 60.0F;
         float pz = (r.nextFloat() - 0.5F) * 2.0F * (end + 60.0F);
         float size = 0.2F + r.nextFloat() * 25.0F;
         if (!MeteorSmokeParticle.fogNeutral(px, py, pz, size, start, end, cyl)) {
            continue;
         }
         neutral++;
         // random orthonormal billboard axes
         float ax = r.nextFloat() - 0.5F, ay = r.nextFloat() - 0.5F, az = r.nextFloat() - 0.5F;
         float al = (float)Math.sqrt(ax * ax + ay * ay + az * az);
         ax /= al; ay /= al; az /= al;
         float bx = ay * 0.3F - az * 0.9F, by = az * 0.1F - ax * 0.3F, bz = ax * 0.9F - ay * 0.1F;
         float bl = (float)Math.sqrt(bx * bx + by * by + bz * bz);
         bx /= bl; by /= bl; bz /= bl;
         Float first = null;
         for (int a = -4; a <= 4; a++) {
            for (int b = -4; b <= 4; b++) {
               float s = a / 4.0F * size, t = b / 4.0F * size;
               float f = fog(dist(px + s * ax + t * bx, py + s * ay + t * by, pz + s * az + t * bz, cyl), start, end);
               assertTrue(f == 0.0F || f == 1.0F, "partial fog on a neutral quad");
               if (first == null) {
                  first = f;
               }
               assertTrue(first == f, "mixed fog on a neutral quad");
            }
         }
      }
      assertTrue(neutral > 2000, "too few neutral samples: " + neutral);
   }

   @Test
   void quadCrossingFogStartIsNotNeutral() {
      assertFalse(MeteorSmokeParticle.fogNeutral(0.0F, 0.0F, 50.0F, 10.0F, 55.0F, 70.0F, false));
      assertTrue(MeteorSmokeParticle.fogNeutral(0.0F, 0.0F, 20.0F, 10.0F, 55.0F, 70.0F, false));
      assertTrue(MeteorSmokeParticle.fogNeutral(0.0F, 0.0F, 120.0F, 10.0F, 55.0F, 70.0F, false));
   }
}
