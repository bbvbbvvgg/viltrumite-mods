package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutSweep;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Item 7: the giant VFX copy of Reid swings ahead of Reinhard and never covers him, from the ground and from the air,
 * for both World Cut shoulders and any cut direction. Geometry only: the real mesh cubes through the production
 * placement. It is no substitute for looking at it in game.
 */
class WorldCutTitanBladeTest {
   private record Cast(String name, Vec3 origin, double aimY, Vec3 dir) {
   }

   private static final List<Cast> CASTS = List.of(
      new Cast("ground south", new Vec3(.5, 64, .5), 64, new Vec3(0, 0, 1)),
      new Cast("ground diagonal", new Vec3(-120.5, 70, 33.2), 70, new Vec3(.6, 0, .8)),
      new Cast("air 30 above the trench", new Vec3(.5, 94, .5), 64, new Vec3(0, 0, -1)),
      new Cast("air 80 above the trench", new Vec3(10, 150, 10), 70, new Vec3(-.8, 0, .6)),
      new Cast("trench above the caster", new Vec3(.5, 64, .5), 72, new Vec3(1, 0, 0)));

   /** Every mesh cube of the blade, as its corners in native blade space (a true box there). */
   private static List<Vector3f[]> cubes(BakedGeoModel blade) {
      List<Vector3f[]> cubes = new ArrayList<>();
      for (var bone : blade.allBones()) for (var cube : bone.cubes)
         cubes.add(ReinhardGeometry.vertices(cube, ReinhardGeometry.frame(bone)));
      return cubes;
   }

   /** A mesh cube carried by the placement into the world: corner + three edges, an exact parallelepiped. */
   private record Solid(Vec3 corner, Vec3[] edges) {
      static Solid of(Vector3f[] v, ReinhardTitanBlade.Placement placement) {
         List<Vector3f> differences = new ArrayList<>();
         for (int i = 1; i < v.length; i++) differences.add(new Vector3f(v[i]).sub(v[0]));
         differences.sort(java.util.Comparator.comparingDouble(Vector3f::lengthSquared));
         List<Vector3f> edges = new ArrayList<>();
         for (var d : differences) {
            if (d.lengthSquared() < 1E-12) continue;
            Vector3f n = new Vector3f(d).normalize();
            if (edges.stream().allMatch(e -> Math.abs(new Vector3f(e).normalize().dot(n)) < .01)) edges.add(d);
            if (edges.size() == 3) break;
         }
         assertEquals(3, edges.size(), "box edges");
         Vec3 corner = placement.point(v[0]);
         Vec3[] world = new Vec3[3];
         for (int i = 0; i < 3; i++) world[i] = placement.point(new Vector3f(v[0]).add(edges.get(i))).subtract(corner);
         return new Solid(corner, world);
      }

      private double[][] inverse() {
         Vec3 a = edges[0], b = edges[1], c = edges[2];
         Vec3 bc = b.cross(c), ca = c.cross(a), ab = a.cross(b);
         double det = a.dot(bc);
         return new double[][]{{bc.x / det, bc.y / det, bc.z / det}, {ca.x / det, ca.y / det, ca.z / det},
            {ab.x / det, ab.y / det, ab.z / det}};
      }

      /** Exact: does the segment {@code a}-{@code b} pass through this solid? */
      boolean hits(Vec3 a, Vec3 b) {
         double[][] inv = inverse();
         Vec3 r = a.subtract(corner), d = b.subtract(a);
         double lo = 0, hi = 1;
         for (double[] row : inv) {
            double u0 = row[0] * r.x + row[1] * r.y + row[2] * r.z, u1 = row[0] * d.x + row[1] * d.y + row[2] * d.z;
            if (Math.abs(u1) < 1E-12) {
               if (u0 < 0 || u0 > 1) return false;
               continue;
            }
            double s1 = -u0 / u1, s2 = (1 - u0) / u1;
            lo = Math.max(lo, Math.min(s1, s2));
            hi = Math.min(hi, Math.max(s1, s2));
            if (lo > hi) return false;
         }
         return true;
      }

      /** Exact distance from {@code x}: the box-constrained least squares, every face, edge and corner tried. */
      double distance(Vec3 x) {
         Vec3 r = x.subtract(corner);
         double best = Double.MAX_VALUE;
         for (int code = 0; code < 27; code++) {
            int[] state = {code % 3, code / 3 % 3, code / 9}; // 0 free, 1 at 0, 2 at 1
            Vec3 fixed = Vec3.ZERO;
            List<Vec3> free = new ArrayList<>();
            List<Integer> index = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
               if (state[i] == 2) fixed = fixed.add(edges[i]);
               if (state[i] == 0) { free.add(edges[i]); index.add(i); }
            }
            Vec3 target = r.subtract(fixed);
            double[] u = solve(free, target);
            if (u == null) continue;
            boolean inside = true;
            Vec3 p = fixed;
            for (int k = 0; k < u.length; k++) {
               if (u[k] < -1E-9 || u[k] > 1 + 1E-9) inside = false;
               p = p.add(free.get(k).scale(u[k]));
            }
            if (inside) best = Math.min(best, p.distanceTo(r));
         }
         return best;
      }

      /** Least squares of {@code target} on the span of {@code basis} (normal equations, at most 3x3). */
      private static double[] solve(List<Vec3> basis, Vec3 target) {
         int n = basis.size();
         if (n == 0) return new double[0];
         double[][] m = new double[n][n + 1];
         for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) m[i][j] = basis.get(i).dot(basis.get(j));
            m[i][n] = basis.get(i).dot(target);
         }
         for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) if (Math.abs(m[row][col]) > Math.abs(m[pivot][col])) pivot = row;
            if (Math.abs(m[pivot][col]) < 1E-12) return null;
            double[] swap = m[col]; m[col] = m[pivot]; m[pivot] = swap;
            for (int row = 0; row < n; row++) if (row != col) {
               double f = m[row][col] / m[col][col];
               for (int k = col; k <= n; k++) m[row][k] -= f * m[col][k];
            }
         }
         double[] u = new double[n];
         for (int i = 0; i < n; i++) u[i] = m[i][n] / m[i][i];
         return u;
      }
   }

   private static Vec3 right(Vec3 dir) {
      return new Vec3(-dir.z, 0, dir.x);
   }

   /** Points of Reinhard's model a third-person camera has to see: feet, waist, chest, head, both shoulders. */
   private static List<Vec3> model(Vec3 feet, Vec3 dir) {
      Vec3 side = right(dir).scale(.35);
      return List.of(feet.add(0, .1, 0), feet.add(0, .9, 0), feet.add(0, 1.5, 0), feet.add(0, 1.95, 0),
         feet.add(0, 1.4, 0).add(side), feet.add(0, 1.4, 0).subtract(side));
   }

   @Test
   void solidsAreExact() {
      var placement = new ReinhardTitanBlade.Placement(Vec3.ZERO, new Matrix4f().scale(2, 3, 4));
      Solid box = Solid.of(new Vector3f[]{new Vector3f(0, 0, 0), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0),
         new Vector3f(0, 0, 1), new Vector3f(1, 1, 0), new Vector3f(1, 0, 1), new Vector3f(0, 1, 1), new Vector3f(1, 1, 1)}, placement);
      assertTrue(box.hits(new Vec3(-1, 1, 1), new Vec3(3, 1, 1)));
      assertTrue(!box.hits(new Vec3(-1, 4, 1), new Vec3(3, 4, 1)));
      assertTrue(!box.hits(new Vec3(-3, 1, 1), new Vec3(-1, 1, 1)), "the segment ends before the box");
      assertEquals(0, box.distance(new Vec3(1, 1, 1)), 1E-9);
      assertEquals(2, box.distance(new Vec3(1, 5, 1)), 1E-9, "face");
      assertEquals(Math.sqrt(2), box.distance(new Vec3(3, 4, 2)), 1E-9, "edge");
      assertEquals(Math.sqrt(3), box.distance(new Vec3(-1, -1, -1)), 1E-9, "corner");
   }

   @Test
   void projectionNeverCoversReinhardNorFillsEitherCamera() throws Exception {
      var body = ReinhardSecondPassTest.body();
      var clips = ReinhardSecondPassTest.clips();
      List<Vector3f[]> cubes = cubes(ReinhardEquipmentTest.weapon("blade"));
      assertTrue(cubes.size() > 5, "real blade cubes");
      List<String> bad = new ArrayList<>();
      for (int sweep : new int[]{WorldCutSweep.FROM_RIGHT, WorldCutSweep.FROM_LEFT}) {
         var clip = clips.get(ReinhardClips.worldCut(sweep));
         Matrix4f impact = ReinhardTitanBlade.gripFrame(body, clip, ReinhardRules.WORLD_CUT_BLAST);
         for (Cast cast : CASTS) {
            Vec3 dir = cast.dir().normalize(), feet = cast.origin(), eye = feet.add(0, 1.62, 0);
            // Vanilla third person: four blocks behind him, and the front view four blocks ahead looking back.
            List<Vec3> cameras = List.of(eye.subtract(dir.scale(4)), eye.add(dir.scale(4)), eye.subtract(dir.scale(4)).add(0, 1.5, 0));
            List<Vec3> model = model(feet, dir);
            double minBody = Double.MAX_VALUE, minEye = Double.MAX_VALUE;
            String label = cast.name() + " sweep " + sweep;
            for (double t = ReinhardTitanBlade.VISIBLE_FROM + .5; t <= ReinhardTitanBlade.VISIBLE_TO; t += .5) {
               var placement = ReinhardTitanBlade.placement(feet, dir, cast.aimY(), t, ReinhardTitanBlade.gripFrame(body, clip, t), impact,
                  WorldCutSweep.side(sweep));
               for (Vector3f[] cube : cubes) {
                  Solid solid = Solid.of(cube, placement);
                  for (Vec3 p : model) minBody = Math.min(minBody, solid.distance(p));
                  minEye = Math.min(minEye, solid.distance(eye));
                  for (Vec3 camera : cameras) for (Vec3 p : model)
                     if (solid.hits(camera, p) && bad.size() < 20) bad.add(label + " t=" + t + ": between the camera " + camera + " and his model");
               }
               // The front view looks back at him, away from the projection: only the views that face it must
               // keep it whole.
               for (Vec3 camera : List.of(cameras.get(0), cameras.get(2)))
                  if (ReinhardTitanBlade.cameraFade(placement, camera) < 1 && bad.size() < 20)
                     bad.add(label + " t=" + t + " fades for the caster's own camera " + camera);
               if (ReinhardTitanBlade.cameraFade(placement, eye) < 1 && bad.size() < 20) bad.add(label + " t=" + t + " fades in first person");
            }
            if (minBody < 6) bad.add(label + ": " + minBody + " blocks from his body");
            if (minEye < 6) bad.add(label + ": " + minEye + " blocks from the first-person eye");
         }
      }
      assertTrue(bad.isEmpty(), String.join("\n", bad));
   }

   @Test
   void onTheBlastTheEdgeLandsInTheTrenchFromTheGroundAndFromTheAir() throws Exception {
      var body = ReinhardSecondPassTest.body();
      var clips = ReinhardSecondPassTest.clips();
      for (int sweep : new int[]{WorldCutSweep.FROM_RIGHT, WorldCutSweep.FROM_LEFT}) {
         var clip = clips.get(ReinhardClips.worldCut(sweep));
         Matrix4f impact = ReinhardTitanBlade.gripFrame(body, clip, ReinhardRules.WORLD_CUT_BLAST);
         for (Cast cast : CASTS) {
            Vec3 dir = cast.dir().normalize();
            var placement = ReinhardTitanBlade.placement(cast.origin(), dir, cast.aimY(), ReinhardRules.WORLD_CUT_BLAST, impact, impact,
               WorldCutSweep.side(sweep));
            Vec3 hilt = placement.point(ReinhardGrip.HANDLE), tip = placement.point(ReinhardTitanBlade.TIP);
            Vec3 axis = tip.subtract(hilt).normalize();
            assertEquals(ReinhardTitanBlade.hilt(cast.origin(), dir, cast.aimY()).distanceTo(hilt), 0, 1E-3, "swings about its own hilt");
            assertTrue(tip.y <= cast.aimY() + .5, cast.name() + ": the tip reaches the trench ground, tip y " + tip.y);
            Vec3 aim = cast.origin().add(dir.scale(ReinhardRules.CUT_START_OFFSET + ReinhardTitanBlade.AIM_ALONG))
               .add(0, cast.aimY() - cast.origin().y, 0);
            Vec3 want = aim.subtract(hilt).normalize();
            assertTrue(axis.dot(want) > Math.cos(Math.toRadians(2.5)), cast.name() + ": aimed along the trench, " + axis + " vs " + want);
            assertEquals(0, tip.subtract(cast.origin()).dot(right(dir)), 3, cast.name() + ": on the trench line");
         }
      }
   }

   @Test
   void theProjectionIsRaisedOverTheShoulderOfItsSweep() throws Exception {
      var body = ReinhardSecondPassTest.body();
      var clips = ReinhardSecondPassTest.clips();
      Vec3 origin = new Vec3(.5, 64, .5), dir = new Vec3(0, 0, 1);
      for (int sweep : new int[]{WorldCutSweep.FROM_RIGHT, WorldCutSweep.FROM_LEFT}) {
         var clip = clips.get(ReinhardClips.worldCut(sweep));
         Matrix4f impact = ReinhardTitanBlade.gripFrame(body, clip, ReinhardRules.WORLD_CUT_BLAST);
         var hold = ReinhardTitanBlade.placement(origin, dir, 64, ReinhardRules.WORLD_CUT_BLAST - 14,
            ReinhardTitanBlade.gripFrame(body, clip, ReinhardRules.WORLD_CUT_BLAST - 14), impact, WorldCutSweep.side(sweep));
         double lateral = hold.point(ReinhardTitanBlade.TIP).subtract(origin).dot(right(dir));
         assertTrue(lateral * WorldCutSweep.side(sweep) > 30, "sweep " + sweep + " leans to its shoulder: " + lateral);
         assertTrue(hold.point(ReinhardTitanBlade.TIP).y > origin.y + 70, "raised high over the cut");
      }
   }

   @Test
   void fadesOnlyForACameraInsideTheBlade() throws Exception {
      var body = ReinhardSecondPassTest.body();
      var clip = ReinhardSecondPassTest.clips().get(ReinhardClips.WORLD_CUT);
      Matrix4f impact = ReinhardTitanBlade.gripFrame(body, clip, ReinhardRules.WORLD_CUT_BLAST);
      Vec3 origin = new Vec3(.5, 64, .5), dir = new Vec3(0, 0, 1);
      var placement = ReinhardTitanBlade.placement(origin, dir, 64, ReinhardRules.WORLD_CUT_BLAST, impact, impact, 1);
      Vec3 onBlade = placement.point(new Vector3f(0, 30F / 16, 0));
      assertEquals(0F, ReinhardTitanBlade.cameraFade(placement, onBlade), 1E-6F, "a spectator standing in the blade");
      assertEquals(1F, ReinhardTitanBlade.cameraFade(placement, onBlade.add(0, 40, 0)), 1E-6F);
      assertEquals(0F, ReinhardTitanBlade.alpha(ReinhardTitanBlade.VISIBLE_TO), 1E-6F);
      assertEquals(0F, ReinhardTitanBlade.grow(ReinhardTitanBlade.VISIBLE_FROM), 1E-6F);
   }
}
