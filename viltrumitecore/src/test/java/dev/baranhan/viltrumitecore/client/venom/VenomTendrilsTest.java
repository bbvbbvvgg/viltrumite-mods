package dev.baranhan.viltrumitecore.client.venom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** The living tendrils (spec §5.2): rooted, alive, bounded; the shot line reaches, sags and stays finite. */
class VenomTendrilsTest {
   private static boolean finite(Vector3f v) {
      return Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z);
   }

   @Test
   void aWaveStartsAtTheRootAndStaysWithinItsAmplitude() {
      Vector3f root = new Vector3f(1, 2, 3);
      Vector3f dir = new Vector3f(0, 0, 1);
      Vector3f side = new Vector3f(1, 0, 0);
      float amp = 0.3F;
      for (double s = 0; s < 4; s += 0.25) {
         Vector3f[] pts = VenomTendrils.wave(root, dir, side, 2.0F, s, 0.4F, amp, 12);
         assertEquals(12, pts.length);
         assertEquals(0.0F, pts[0].distance(root), 1.0E-5F, "rooted");
         assertEquals(root.z + 2.0F, pts[11].z, 1.0E-4F, "full length along the direction");
         for (Vector3f p : pts) {
            assertTrue(finite(p));
            float off = (float)Math.hypot(p.x - root.x, p.y - root.y);
            assertTrue(off <= amp * 1.25F, "bounded sway " + off);
         }
      }
   }

   @Test
   void neighboursNeverMoveInStep() {
      Vector3f root = new Vector3f();
      Vector3f[] a = VenomTendrils.wave(root, new Vector3f(0, 1, 0), new Vector3f(1, 0, 0), 1.5F, 1.0, 0.0F, 0.3F, 8);
      Vector3f[] b = VenomTendrils.wave(root, new Vector3f(0, 1, 0), new Vector3f(1, 0, 0), 1.5F, 1.0, 1.0F, 0.3F, 8);
      assertTrue(a[7].distance(b[7]) > 0.01F);
      Vector3f[] later = VenomTendrils.wave(root, new Vector3f(0, 1, 0), new Vector3f(1, 0, 0), 1.5F, 1.2, 0.0F, 0.3F, 8);
      assertTrue(a[7].distance(later[7]) > 0.001F, "alive: it moves with time");
   }

   @Test
   void theShotLineReachesItsTargetAndSags() {
      Vector3f from = new Vector3f(0, 10, 0);
      Vector3f to = new Vector3f(10, 10, 0);
      Vector3f[] none = VenomTendrils.line(from, to, 0.0F, 0.0, 0.1F, 9);
      for (Vector3f p : none) {
         assertEquals(0.0F, p.distance(from), 1.0E-4F, "not shot yet");
      }
      Vector3f[] full = VenomTendrils.line(from, to, 1.0F, 0.0, 0.1F, 9);
      assertEquals(0.0F, full[8].distance(to), 1.0E-3F, "the tip on the target");
      assertTrue(full[4].y < from.y - 0.5F, "the middle sags");
      Vector3f[] half = VenomTendrils.line(from, to, 0.5F, 0.0, 0.1F, 9);
      assertEquals(5.0F, half[8].x, 0.05F, "half way out");
   }

   @Test
   void aDegenerateLineStaysFinite() {
      Vector3f p = new Vector3f(3, 4, 5);
      for (Vector3f q : VenomTendrils.line(p, new Vector3f(p), 1.0F, 2.0, 0.2F, 6)) {
         assertTrue(finite(q));
      }
      for (Vector3f q : VenomTendrils.line(p, new Vector3f(3, 9, 5), 1.0F, 2.0, 0.2F, 6)) {
         assertTrue(finite(q), "a vertical line has a side too");
      }
   }
}
