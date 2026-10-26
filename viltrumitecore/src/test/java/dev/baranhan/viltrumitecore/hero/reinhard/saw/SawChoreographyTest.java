package dev.baranhan.viltrumitecore.hero.reinhard.saw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Item 19: the pure Saw Sphere score both sides play from. */
class SawChoreographyTest {
   /** Anchor yaw and the caster's position inside his block: centred, rotated and near the edges. */
   private static final double[][] CASES = {{0, .5, .5}, {37, .5, .5}, {90, .2, .9}, {200, .73, .11}, {13, .99, .01}, {300, .05, .5}};

   @Test void thirtyFiveDiscsOneLaunchEachInnerRingsFirst() {
      assertEquals(35, SawChoreography.COUNT);
      assertEquals(46, SawChoreography.swingCount());
      Set<Integer> ticks = new HashSet<>();
      int lastRing = 0;
      for (int k = 0; k < SawChoreography.COUNT; k++) {
         int saw = SawChoreography.sawOfSwing(k);
         assertEquals(SawChoreography.LAUNCH_START + k * SawChoreography.SWING_PERIOD, SawChoreography.launchTick(saw), "swing " + k);
         assertTrue(ticks.add(SawChoreography.launchTick(saw)), "one disc per swing");
         assertTrue(SawChoreography.ring(saw) >= lastRing, "inner rings launch first");
         lastRing = SawChoreography.ring(saw);
      }
      for (int k = 0; k < SawChoreography.swingCount(); k++) {
         assertEquals(k, SawChoreography.swingAt(SawChoreography.LAUNCH_START + k * SawChoreography.SWING_PERIOD));
      }
      assertEquals(-1, SawChoreography.swingAt(SawChoreography.LAUNCH_START + 1));
      assertEquals(-1, SawChoreography.swingAt(SawChoreography.LAST_SWING + SawChoreography.SWING_PERIOD));
      assertEquals(SawChoreography.LAST_SWING, SawChoreography.LAUNCH_START + (SawChoreography.swingCount() - 1) * SawChoreography.SWING_PERIOD);
   }

   @Test void launchFacesTheDiscNearestTheBlade() {
      // Each swing flings the disc of its ring that the spinning caster faces most squarely.
      for (int k = 0; k < SawChoreography.COUNT; k++) {
         int saw = SawChoreography.sawOfSwing(k);
         int tick = SawChoreography.launchTick(saw);
         double gap = Math.abs(SawChoreography.wrap(SawChoreography.angle(saw, tick) - SawChoreography.spin(tick)));
         for (int other = 0; other < SawChoreography.COUNT; other++) {
            if (SawChoreography.ring(other) != SawChoreography.ring(saw) || SawChoreography.launchTick(other) < tick) continue;
            assertTrue(gap <= Math.abs(SawChoreography.wrap(SawChoreography.angle(other, tick) - SawChoreography.spin(tick))) + 1E-9,
               "swing " + k + " picks the facing disc");
         }
      }
   }

   @Test void spinIsSmoothAndItsRateIsItsDerivative() {
      assertEquals(0, SawChoreography.spin(0), 0);
      assertEquals(0, SawChoreography.spinRate(SawChoreography.SPIN_UP_START), 0);
      assertEquals(SawChoreography.SPIN_MAX, SawChoreography.spinRate(100), 1E-12);
      assertEquals(2.5 * 2 * Math.PI / 20, SawChoreography.SPIN_MAX, 1E-12, "2.5 revolutions per second");
      double previous = 0;
      for (double t = 0; t <= SawChoreography.LENGTH; t += .25) {
         double s = SawChoreography.spin(t);
         assertTrue(s >= previous - 1E-12, "never turns back " + t);
         double numeric = (SawChoreography.spin(t + 1E-4) - SawChoreography.spin(t - 1E-4)) / 2E-4;
         if (t > 0) assertEquals(SawChoreography.spinRate(t), numeric, 1E-5, "rate at " + t);
         previous = s;
      }
      assertEquals(0, SawChoreography.spinRate(SawChoreography.SPIN_DOWN_END), 0, "still before the final pass ends");
   }

   @Test void hoverRisesHoldsAndLands() {
      assertEquals(0, SawChoreography.hover(0), 0);
      assertEquals(SawChoreography.HOVER, SawChoreography.hover(SawChoreography.RISE_END), 1E-12);
      assertEquals(SawChoreography.HOVER, SawChoreography.hover(200), 0);
      assertEquals(0, SawChoreography.hover(SawChoreography.DESCEND_END), 1E-12);
      assertEquals(0, SawChoreography.hover(SawChoreography.LENGTH), 1E-12);
      assertEquals(SawChoreography.RADIUS, SawChoreography.shellRadius(SawChoreography.EXPAND_END), 1E-12);
      assertTrue(SawChoreography.shellSolid(SawChoreography.EXPAND_END) && !SawChoreography.shellSolid(SawChoreography.SHATTER));
      assertTrue(!SawChoreography.shellSolid(SawChoreography.EXPAND_END - 1));
   }

   @Test void discsStayInsideTheShellAndAboveTheGround() {
      double[] p = new double[3];
      for (int saw = 0; saw < SawChoreography.COUNT; saw++) {
         for (double t = 0; t <= SawChoreography.LENGTH; t += .5) {
            SawChoreography.position(saw, t, 0, 0, 0, 0F, p);
            double d = Math.hypot(p[0], p[2]);
            assertTrue(p[1] >= SawChoreography.FLOOR - 1E-9, "above the ground " + saw + " " + t);
            assertTrue(d + SawChoreography.SAW_RADIUS <= SawChoreography.chord(p[1]) + 1E-3, "rim inside the shell " + saw + " " + t);
            assertTrue(p[1] - SawChoreography.HOVER - 1.8 > .3 || d > SawChoreography.SAW_RADIUS + .3,
               "the blades never pass through the hovering caster " + saw + " " + t);
         }
      }
   }

   /**
    * Coverage on flat ground: every block whose centre lies in the sphere above Y0 is crossed by a cutting disc
    * slab (the server's own test, {@link SawSphereScene#discTouches} plus the slab overlap), except the columns
    * under the caster's own hitbox up to y = 5 (he hovers there, the crown disc stays above his head). Several
    * anchor yaws and caster positions inside the block: the helical descent must not leave gaps anywhere.
    */
   @Test void discsCutEveryBlockOfTheSphereOnFlatGround() {
      for (double[] o : CASES) {
         double cx = o[1], cz = o[2];
         float yaw = (float) o[0];
         List<int[]> blocks = new ArrayList<>();
         for (int x = -13; x <= 13; x++) for (int z = -13; z <= 13; z++) for (int y = 0; y < 13; y++) {
            double bx = x + .5 - cx, by = y + .5, bz = z + .5 - cz;
            if (bx * bx + by * by + bz * bz < 144 && !(x < cx + .3 && x + 1 > cx - .3 && z < cz + .3 && z + 1 > cz - .3 && y <= 5)) blocks.add(new int[]{x, y, z});
         }
         boolean[] cut = new boolean[blocks.size()];
         double[] p = new double[3];
         for (int t = 0; t <= SawChoreography.LENGTH; t++) {
            for (int saw = 0; saw < SawChoreography.COUNT; saw++) {
               if (!SawChoreography.cutting(saw, t)) continue;
               SawChoreography.position(saw, t, cx, 0, cz, yaw, p);
               double lo = p[1] - SawChoreography.HALF_THICKNESS, hi = p[1] + SawChoreography.HALF_THICKNESS;
               for (int i = 0; i < cut.length; i++) {
                  if (cut[i]) continue;
                  int[] b = blocks.get(i);
                  if (b[1] + 1 <= lo || b[1] >= hi) continue;
                  if (SawSphereScene.discTouches(new net.minecraft.world.phys.AABB(b[0], b[1], b[2], b[0] + 1, b[1] + 1, b[2] + 1), p[0], p[2], SawChoreography.SAW_RADIUS)) cut[i] = true;
               }
            }
         }
         List<String> missed = new ArrayList<>();
         for (int i = 0; i < cut.length; i++) if (!cut[i]) missed.add(java.util.Arrays.toString(blocks.get(i)));
         assertEquals(List.of(), missed, "uncut blocks at anchor yaw " + yaw + ", caster " + cx + "/" + cz + " of " + blocks.size());
      }
   }
}
