package dev.baranhan.viltrumitecore.hero.reinhard.melee;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MeleeArcTest {
   private static final MeleeArc.Swing DIAGONAL = new MeleeArc.Swing(58, 42, -52, -34, 4.9, 9, 4, 6, 4);

   private static double dot(double[] a, double[] b) {
      return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
   }

   @Test
   void basisIsOrthonormalForAnyView() {
      for (float yaw : new float[]{0, 37, -120, 179}) {
         for (float pitch : new float[]{-90, -45, 0, 30, 90}) {
            double[][] b = MeleeArc.basis(yaw, pitch);
            for (int i = 0; i < 3; i++) {
               assertEquals(1.0, dot(b[i], b[i]), 1.0E-9, "unit " + i);
               for (int j = i + 1; j < 3; j++) {
                  assertEquals(0.0, dot(b[i], b[j]), 1.0E-9, "orthogonal " + i + j);
               }
            }
         }
      }
   }

   @Test
   void minecraftViewConventions() {
      double[][] south = MeleeArc.basis(0, 0);
      assertArrayEquals(new double[]{0, 0, 1}, south[0], 1.0E-9, "yaw 0 looks +Z");
      assertArrayEquals(new double[]{-1, 0, 0}, south[1], 1.0E-9, "screen right of +Z is -X");
      assertArrayEquals(new double[]{0, 1, 0}, south[2], 1.0E-9);
      assertEquals(-1.0, MeleeArc.basis(0, 90)[0][1], 1.0E-9, "positive pitch looks down");
   }

   @Test
   void anglesInvertDirection() {
      double[][] b = MeleeArc.basis(-63, 21);
      for (double[] yp : new double[][]{{0, 0}, {40, 10}, {-75, -30}, {12, 60}}) {
         double[] d = MeleeArc.direction(b, yp[0], yp[1]);
         double[] back = MeleeArc.angles(b, d[0] * 3, d[1] * 3, d[2] * 3);
         assertEquals(yp[0], back[0], 1.0E-6);
         assertEquals(yp[1], back[1], 1.0E-6);
         assertEquals(3 * Math.cos(Math.toRadians(yp[1])) * Math.cos(Math.toRadians(yp[0])), back[2], 1.0E-6);
      }
   }

   @Test
   void progressCoversTheContactWindowOnly() {
      assertEquals(0, DIAGONAL.progress(3), 1.0E-9);
      assertEquals(1 / 3.0, DIAGONAL.progress(4), 1.0E-9);
      assertEquals(1, DIAGONAL.progress(6), 1.0E-9);
      assertEquals(1, DIAGONAL.progress(40), 1.0E-9);
      double last = -1;
      for (double t = 2; t <= 8; t += 0.25) {
         double p = DIAGONAL.progress(t);
         assertTrue(p >= last && p >= 0 && p <= 1);
         last = p;
      }
   }

   @Test
   void easedPathEndsOnItsKeysAndIsFastestMidArc() {
      assertArrayEquals(new double[]{58, 42}, DIAGONAL.at(0), 1.0E-9);
      assertArrayEquals(new double[]{-52, -34}, DIAGONAL.at(1), 1.0E-9);
      assertArrayEquals(new double[]{-52, -34}, DIAGONAL.at(3), 1.0E-9, "clamped");
      double early = Math.abs(DIAGONAL.at(0.1)[0] - DIAGONAL.at(0)[0]);
      double mid = Math.abs(DIAGONAL.at(0.55)[0] - DIAGONAL.at(0.45)[0]);
      assertTrue(mid > 2 * early);
   }

   @Test
   void sideAndMirror() {
      assertEquals(-1, DIAGONAL.side());
      assertEquals(1, DIAGONAL.mirrored().side());
      assertEquals(0, MeleeMoves.reid(MeleeMoves.SLASH_FINISH).swing().side(), "the finisher is a straight chop");
      MeleeArc.Swing m = DIAGONAL.mirrored();
      assertEquals(-58, m.yawFrom());
      assertEquals(DIAGONAL.pitchFrom(), m.pitchFrom());
      assertEquals(DIAGONAL.reach(), m.reach());
   }

   @Test
   void touchesFollowsTheCurvedPathNotItsChord() {
      // A body on the path midpoint is touched, the same body far off the path is not.
      double[] mid = DIAGONAL.at(0.5);
      assertTrue(MeleeArc.touches(DIAGONAL, 0, 1, mid[0], mid[1], 1));
      assertFalse(MeleeArc.touches(DIAGONAL, 0, 1, mid[0] + 40, mid[1] - 40, 1));
      // Only the part swept this tick counts.
      double[] end = DIAGONAL.at(1);
      assertFalse(MeleeArc.touches(DIAGONAL, 0, 0.3, end[0], end[1], 1));
      assertTrue(MeleeArc.touches(DIAGONAL, 0.7, 1, end[0], end[1], 1));
      // A bigger body (closer) is touched from further away: 25 degrees off the path, perpendicular to it.
      double[] start = DIAGONAL.at(0);
      double len = Math.hypot(end[0] - start[0], end[1] - start[1]);
      double py = -(end[1] - start[1]) / len * 25, pp = (end[0] - start[0]) / len * 25;
      assertTrue(MeleeArc.touches(DIAGONAL, 0, 1, mid[0] + py, mid[1] + pp, MeleeArc.angularRadius(0.6, 1)));
      assertFalse(MeleeArc.touches(DIAGONAL, 0, 1, mid[0] + py, mid[1] + pp, MeleeArc.angularRadius(0.6, 6)));
   }

   @Test
   void segmentDistanceClampsToTheSegment() {
      double[] a = {0, 0}, b = {10, 0};
      assertEquals(3, MeleeArc.segmentDistance(5, 3, a, b), 1.0E-9);
      assertEquals(5, MeleeArc.segmentDistance(-3, 4, a, b), 1.0E-9);
      assertEquals(2, MeleeArc.segmentDistance(12, 0, a, b), 1.0E-9);
      assertEquals(5, MeleeArc.segmentDistance(3, 4, a, a), 1.0E-9, "degenerate segment is a point");
   }
}
