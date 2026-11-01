package dev.baranhan.viltrumitecore.client.collapse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.world.collapse.CollapseRules;
import dev.baranhan.viltrumitecore.world.collapse.CollapseVariant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CollapseMotionTest {
   static int[][] tower(int w, int y0, int h) {
      int n = w * w * h;
      int[] x = new int[n], y = new int[n], z = new int[n];
      int i = 0;
      for (int dy = 0; dy < h; dy++) {
         for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < w; dz++) {
               x[i] = dx;
               y[i] = y0 + dy;
               z[i] = dz;
               i++;
            }
         }
      }
      return new int[][]{x, y, z};
   }

   static List<CollapseMotion.Piece> build(CollapseVariant v, int[][] t, int impact, int max) {
      return CollapseMotion.build(t[0], t[1], t[2], v, 7L, 1.0F, 0.0F, 64, impact, max, (x, z) -> 64);
   }

   @Test
   void everyBlockBelongsToExactlyOnePiece() {
      int[][] t = tower(5, 68, 20);
      for (CollapseVariant v : CollapseVariant.values()) {
         List<CollapseMotion.Piece> pieces = build(v, t, 30, 140);
         assertTrue(pieces.size() <= 140, v + ": " + pieces.size());
         int[] seen = new int[t[0].length];
         for (CollapseMotion.Piece p : pieces) {
            for (int m : p.members) {
               seen[m]++;
            }
         }
         for (int s : seen) {
            assertEquals(1, s, v.toString());
         }
      }
   }

   @Test
   void piecesLandAroundTheServerImpactTick() {
      int[][] t = tower(5, 68, 22);
      for (CollapseVariant v : CollapseVariant.values()) {
         int impact = CollapseRules.impactTicks(v, 68 - 64, 22);
         List<CollapseMotion.Piece> pieces = build(v, t, impact, 140);
         int[] lands = pieces.stream().mapToInt(p -> p.land).sorted().toArray();
         assertTrue(lands[0] >= 0, v + ": a piece never landed");
         int median = lands[lands.length / 2];
         assertTrue(median >= impact * 0.4 && median <= impact * 2.0 + 10, v + " median " + median + " vs impact " + impact + " " + Arrays.toString(lands));
         for (CollapseMotion.Piece p : pieces) {
            assertTrue(p.y[p.at(p.land)] >= 64, "never below the street");
         }
      }
   }

   @Test
   void toppleFallsTowardTheFallDirection() {
      int[][] t = tower(5, 68, 22);
      List<CollapseMotion.Piece> pieces = build(CollapseVariant.TOPPLE, t, CollapseRules.impactTicks(CollapseVariant.TOPPLE, 4, 22), 140);
      double moved = 0;
      int upper = 0;
      for (CollapseMotion.Piece p : pieces) {
         if (p.oy > 80) {
            moved += p.x[p.at(p.land)] - p.ox;
            upper++;
         }
      }
      assertTrue(upper > 0);
      assertTrue(moved / upper > 4.0, "upper pieces land several blocks out along +x: " + moved / upper);
   }

   @Test
   void pancakeStaysOnItsFootprint() {
      int[][] t = tower(6, 68, 12);
      for (CollapseMotion.Piece p : build(CollapseVariant.PANCAKE, t, CollapseRules.impactTicks(CollapseVariant.PANCAKE, 4, 12), 140)) {
         int j = p.at(p.land);
         assertTrue(Math.abs(p.x[j] - p.ox) < 3.0 && Math.abs(p.z[j] - p.oz) < 3.0);
      }
   }

   @Test
   void tracksAreDeterministicAndRotationsUnit() {
      int[][] t = tower(4, 68, 16);
      List<CollapseMotion.Piece> a = build(CollapseVariant.FACADE_SHEAR, t, 30, 100);
      List<CollapseMotion.Piece> b = build(CollapseVariant.FACADE_SHEAR, t, 30, 100);
      assertEquals(a.size(), b.size());
      for (int i = 0; i < a.size(); i++) {
         assertTrue(Arrays.equals(a.get(i).x, b.get(i).x));
         CollapseMotion.Piece p = a.get(i);
         for (int k = 0; k < p.length; k += 7) {
            double n = p.qx[k] * p.qx[k] + p.qy[k] * p.qy[k] + p.qz[k] * p.qz[k] + p.qw[k] * p.qw[k];
            assertEquals(1.0, n, 1.0E-3);
         }
      }
   }

   @Test
   void piecesRestThenSinkAndFinish() {
      int[][] t = tower(3, 66, 6);
      CollapseMotion.Piece p = build(CollapseVariant.CRUMBLE, t, 12, 40).get(0);
      assertEquals(0.0, p.sink(p.land + 1));
      assertEquals(CollapseMotion.SINK_DEPTH, p.sink(p.land + CollapseMotion.REST_TICKS + CollapseMotion.SINK_TICKS), 1.0E-9);
      assertTrue(p.done(p.land + CollapseMotion.REST_TICKS + CollapseMotion.SINK_TICKS + 1));
   }

   @Test
   void audioMixFadesNearIntoDistant() {
      assertEquals(1.0F, CollapseAudio.gain(CollapseAudio.Layer.NEAR, 10.0));
      assertEquals(0.0F, CollapseAudio.gain(CollapseAudio.Layer.NEAR, 200.0));
      assertEquals(0.0F, CollapseAudio.gain(CollapseAudio.Layer.DISTANT, 20.0));
      assertTrue(CollapseAudio.gain(CollapseAudio.Layer.DISTANT, 250.0) > 0.5F);
      assertTrue(CollapseAudio.gain(CollapseAudio.Layer.RUMBLE, 250.0) > 0.0F);
      assertTrue(CollapseAudio.duck(3) < CollapseAudio.duck(1));
      assertTrue(CollapseAudio.pitch(1.0F, 0.5) < CollapseAudio.pitch(0.0F, 0.5));
   }
}
