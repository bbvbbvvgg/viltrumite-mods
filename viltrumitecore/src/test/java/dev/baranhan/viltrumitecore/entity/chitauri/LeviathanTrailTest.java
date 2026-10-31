package dev.baranhan.viltrumitecore.entity.chitauri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Leviathan body follows the head's own path by arc length (server hitboxes and client segments share it). */
class LeviathanTrailTest {
   @Test
   void straightTrailPlacesSegmentsBehindTheHead() {
      LeviathanTrail trail = new LeviathanTrail(480);
      trail.prefill(10, 70, 5, 1, 0, 0, 60);
      double[] out = new double[3];
      for (int i = 0; i <= LeviathanTrail.SEGMENTS; i++) {
         double d = LeviathanTrail.offset(i);
         trail.sample(10, 70, 5, d, out);
         assertEquals(10 - d, out[0], 1.0E-9);
         assertEquals(70, out[1], 1.0E-9);
         assertEquals(5, out[2], 1.0E-9);
      }
   }

   @Test
   void bodyFollowsTheCurveTheHeadFlew() {
      // head flew a circle of radius 30 around the origin: every body point must lie on it
      LeviathanTrail trail = new LeviathanTrail(480);
      double r = 30.0;
      double hx = 0, hz = 0;
      for (int k = 0; k <= 400; k++) {
         double a = k * 0.01;
         hx = Math.cos(a) * r;
         hz = Math.sin(a) * r;
         trail.push(hx, 64, hz);
      }
      double[] out = new double[3];
      for (int i = 0; i <= LeviathanTrail.SEGMENTS; i++) {
         trail.sample(hx, 64, hz, LeviathanTrail.offset(i), out);
         double rr = Math.sqrt(out[0] * out[0] + out[2] * out[2]);
         assertEquals(r, rr, 0.05, "piece " + i + " stays on the head path");
         // chord shorter than the arc but close for the small curvature
         double chord = Math.sqrt((out[0] - hx) * (out[0] - hx) + (out[2] - hz) * (out[2] - hz));
         assertTrue(chord <= LeviathanTrail.offset(i) + 1.0E-6);
         assertTrue(chord > LeviathanTrail.offset(i) * 0.95);
      }
   }

   @Test
   void verticalUndulationBecomesATravellingWave() {
      // head moves along +x with y = 4 sin(x / 6): a body point at x must have the height the head had at x
      LeviathanTrail trail = new LeviathanTrail(480);
      double hx = 0, hy = 0;
      for (int k = 0; k <= 600; k++) {
         hx = k * 0.2;
         hy = 4.0 * Math.sin(hx / 6.0);
         trail.push(hx, hy, 0);
      }
      double[] out = new double[3];
      for (int i = 0; i <= LeviathanTrail.SEGMENTS; i++) {
         trail.sample(hx, hy, 0, LeviathanTrail.offset(i), out);
         assertEquals(4.0 * Math.sin(out[0] / 6.0), out[1], 0.05, "piece " + i);
         assertTrue(out[0] < hx);
      }
   }

   @Test
   void ringBufferKeepsTheNewestPointsAndExtendsPastTheOldest() {
      LeviathanTrail trail = new LeviathanTrail(8);
      for (int k = 0; k < 50; k++) {
         trail.push(k, 0, 0);
      }
      assertEquals(8, trail.size());
      double[] out = new double[3];
      // history covers x 42..49; 20 blocks back runs past it and continues straight along -x
      trail.sample(50, 0, 0, 20, out);
      assertEquals(30, out[0], 1.0E-9);
      assertEquals(0, out[1], 1.0E-9);
   }

   @Test
   void tinyMovesAreNotStored() {
      LeviathanTrail trail = new LeviathanTrail(16);
      trail.push(0, 0, 0);
      trail.push(LeviathanTrail.MIN_STEP * 0.5, 0, 0);
      assertEquals(1, trail.size());
      trail.push(LeviathanTrail.MIN_STEP * 1.01, 0, 0);
      assertEquals(2, trail.size());
      trail.clear();
      assertEquals(0, trail.size());
      double[] out = new double[3];
      trail.sample(1, 2, 3, 4, out);
      assertEquals(1, out[0], 1.0E-9);
      assertEquals(-1, out[2], 1.0E-9);
   }

   @Test
   void segmentsTaperAndSpaceOut() {
      double last = 0.0;
      for (int i = 0; i <= LeviathanTrail.SEGMENTS; i++) {
         double d = LeviathanTrail.offset(i);
         assertTrue(d > last, "piece " + i + " behind the previous one");
         last = d;
         if (i > 0) {
            assertTrue(LeviathanTrail.scale(i) <= LeviathanTrail.scale(i - 1), "tapers at " + i);
         }
      }
      assertEquals(LeviathanTrail.HEAD_GAP, LeviathanTrail.offset(0), 1.0E-9);
      assertEquals(LeviathanTrail.TAIL_SCALE, LeviathanTrail.scale(LeviathanTrail.SEGMENTS));
      assertEquals(LeviathanTrail.SEGMENTS, LeviathanTrail.SCALE.length);
      // plan: about 28 blocks with the head (~5 blocks of skull in front of the centre)
      double length = LeviathanTrail.bodyLength() + 5.0;
      assertTrue(length > 25.0 && length < 35.0, "length " + length);
   }
}
