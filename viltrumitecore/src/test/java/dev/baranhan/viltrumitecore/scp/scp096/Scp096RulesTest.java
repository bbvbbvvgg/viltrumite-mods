package dev.baranhan.viltrumitecore.scp.scp096;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure rules of SCP-096: seeing the face, the leap ballistics, the natural spawn ring and the config clamps. */
class Scp096RulesTest {
   private static final double H = Scp096Rules.PLAYER_HALF_H;
   private static final double V = Scp096Rules.PLAYER_HALF_V;

   /** Face at the origin looking along +Z (its normal), at head height 2. */
   private static boolean seen(double ex, double ey, double ez, double lx, double ly, double lz) {
      return Scp096Sight.faceInView(ex, ey, ez, lx, ly, lz, 0, 2, 0, 0, 0, 1, H, V, Scp096Rules.FACE_RANGE, Scp096Rules.FACE_FRONT);
   }

   @Test
   void theFaceCountsOnlyInFrontOfItAndInsideTheView() {
      // straight in front, looking at it
      assertTrue(seen(0, 2, 10, 0, 0, -1));
      // same spot, looking away: not seen
      assertFalse(seen(0, 2, 10, 0, 0, 1));
      // behind the head (the back of the head is not the face)
      assertFalse(seen(0, 2, -10, 0, 0, 1));
      // beside it at 90 degrees: past FACE_FRONT (75)
      assertFalse(seen(10, 2, 0, -1, 0, 0));
      // at the edge of the view: 45 degrees off the look is inside the 52 degree half angle, 60 is not
      double a45 = Math.toRadians(45);
      double a60 = Math.toRadians(60);
      assertTrue(seen(0, 2, 10, Math.sin(a45), 0, -Math.cos(a45)));
      assertFalse(seen(0, 2, 10, Math.sin(a60), 0, -Math.cos(a60)));
      // out of range
      assertFalse(seen(0, 2, Scp096Rules.FACE_RANGE + 1, 0, 0, -1));
      // looking straight down from above the face does not crash and does not count (the normal points ahead)
      assertFalse(seen(0, 12, 0.01, 0, -1, 0));
   }

   @Test
   void theSpyglassNarrowsTheViewButReachesFarther() {
      double far = 150;
      assertFalse(seen(0, 2, far, 0, 0, -1), "past the naked-eye range");
      assertTrue(Scp096Sight.faceInView(0, 2, far, 0, 0, -1, 0, 2, 0, 0, 0, 1, Scp096Rules.SPYGLASS_HALF, Scp096Rules.SPYGLASS_HALF,
         Scp096Rules.SPYGLASS_RANGE, Scp096Rules.FACE_FRONT));
      double off = Math.toRadians(10);
      assertFalse(Scp096Sight.faceInView(0, 2, far, Math.sin(off), 0, -Math.cos(off), 0, 2, 0, 0, 0, 1, Scp096Rules.SPYGLASS_HALF,
         Scp096Rules.SPYGLASS_HALF, Scp096Rules.SPYGLASS_RANGE, Scp096Rules.FACE_FRONT));
   }

   @Test
   void faceNormalFollowsMinecraftYaw() {
      double[] south = Scp096Sight.normal(0.0F, 0.0F);
      assertEquals(1.0, south[2], 1e-9);
      double[] west = Scp096Sight.normal(90.0F, 0.0F);
      assertEquals(-1.0, west[0], 1e-9);
      double[] down = Scp096Sight.normal(0.0F, 90.0F);
      assertEquals(-1.0, down[1], 1e-9);
      assertEquals(Math.PI, Scp096Sight.horizontalAngle(0, 1, 0, -1), 1e-9);
      assertEquals(0.0, Scp096Sight.horizontalAngle(0, 0, 1, 1), 1e-9, "degenerate look");
   }

   @Test
   void leapsClearTheTargetAndRespectTheLimits() {
      Scp096Leap.Solution s = Scp096Leap.solve(12, 6, 0, 1.0);
      assertNotNull(s);
      assertTrue(s.apex() >= 7.0 - 1e-6, "clears the ledge: apex " + s.apex());
      assertTrue(Math.hypot(s.vx(), s.vz()) <= Scp096Leap.MAX_HSPEED + 1e-9);
      assertTrue(s.vy() <= Scp096Leap.MAX_VY + 1e-9 && s.vy() >= Scp096Leap.MIN_VY);
      // simulate the arc: it comes down onto the target height near the target distance
      double x = 0;
      double y = 0;
      double vy = s.vy();
      for (int i = 0; i < s.ticks(); i++) {
         x += s.vx();
         y += vy;
         vy = (vy - Scp096Leap.GRAVITY) * Scp096Leap.DRAG;
      }
      assertEquals(12.0, x, 0.5);
      assertTrue(y <= 6.0 + 1e-9 && y > 4.0, "lands at the ledge height, y=" + y);
      assertNull(Scp096Leap.solve(4, Scp096Rules.LEAP_MAX_UP + 1, 0, 1.0), "too high");
      assertNull(Scp096Leap.solve(Scp096Rules.LEAP_MAX_H * 2, 0, 0, 1.0), "too far");
      assertNull(Scp096Leap.solve(Double.NaN, 0, 0, 1.0));
      assertTrue(Scp096Leap.arrival(1.0, 0.0) > 10, "a real arc: up first, then down to the start height");
      assertTrue(Scp096Leap.apex(1.0) > Scp096Leap.apex(0.5));
   }

   @Test
   void spawnRingSitsAtTheEdgeOfTheSimulatedArea() {
      double r = Scp096SpawnRules.radius(12, 8);
      assertEquals(128.0, r, "the smaller of view and simulation distance");
      assertEquals(64.0, Scp096SpawnRules.radius(2, 2), "at least 4 chunks");
      assertTrue(Scp096SpawnRules.ringMin(r) >= Scp096SpawnRules.MIN_PLAYER_DISTANCE);
      assertTrue(Scp096SpawnRules.ringMax(r) > Scp096SpawnRules.ringMin(r));
      assertTrue(Scp096SpawnRules.ringMax(r) < r, "inside the simulated area: nothing is loaded for the spawn");
      assertTrue(Scp096SpawnRules.ringMax(64.0) >= Scp096SpawnRules.ringMin(64.0) + 4.0);
   }

   @Test
   void neverSpawnsInFrontOfTheCamera() {
      assertFalse(Scp096SpawnRules.outsideView(0, 1, 0, 50), "straight ahead");
      assertFalse(Scp096SpawnRules.outsideView(0, 1, 30, 50), "inside the view cone");
      assertTrue(Scp096SpawnRules.outsideView(0, 1, 50, 0), "beside");
      assertTrue(Scp096SpawnRules.outsideView(0, 1, 0, -50), "behind");
      // turned away: the yaw faces the opposite way of the player
      float yaw = Scp096SpawnRules.yawAwayFrom(0, 10);
      double[] n = Scp096Sight.normal(yaw, 0);
      assertTrue(n[2] < -0.99, "faces away from a player at +Z, normal " + n[2]);
   }

   @Test
   void rollAndConfigClamps() {
      assertTrue(Scp096SpawnRules.roll(0.005, 0.01));
      assertFalse(Scp096SpawnRules.roll(0.02, 0.01));
      assertFalse(Scp096SpawnRules.roll(0.0, 0.0), "0 % never spawns");
      assertEquals(0.01, Scp096Rules.chance(1.0), 1e-12);
      assertEquals(1.0, Scp096Rules.chance(250.0), 1e-12);
      assertEquals(0.0, Scp096Rules.chance(-3.0), 1e-12);
      assertEquals(0.01, Scp096Rules.chance(Double.NaN), 1e-12);
      assertEquals(6000, Scp096Rules.checkIntervalTicks(300.0));
      assertEquals(200, Scp096Rules.checkIntervalTicks(1.0), "at least 10 s");
      assertEquals(216000L, Scp096Rules.cooldownTicks(180.0));
      assertEquals(0L, Scp096Rules.cooldownTicks(-5.0));
      assertEquals(280, Scp096Rules.hysteriaTicks(14.0));
      assertEquals(60, Scp096Rules.hysteriaTicks(0.5));
      assertEquals(1200, Scp096Rules.hysteriaTicks(600.0));
      assertEquals(Scp096Rules.DEFAULT_HYSTERIA_TICKS, Scp096Rules.hysteriaTicks(Double.POSITIVE_INFINITY));
   }

   @Test
   void hitsAreCappedAndTheChaseKeepsUp() {
      assertEquals(Scp096Rules.MAX_HIT, Scp096Rules.cappedHit(1000.0F));
      assertEquals(0.0F, Scp096Rules.cappedHit(Float.NaN));
      assertEquals(0.0F, Scp096Rules.cappedHit(-4.0F));
      assertEquals(Scp096Rules.CHASE_SPEED, Scp096Rules.chaseSpeed(0.1, 5.0), 1e-12);
      double flyer = Scp096Rules.chaseSpeed(1.5, 40.0);
      assertTrue(flyer > 1.5, "outruns a fast target: " + flyer);
      assertEquals(Scp096Rules.BURST_MAX, Scp096Rules.chaseSpeed(10.0, 100.0), 1e-12);
      assertEquals(0.5, Scp096Rules.approach(0.45, 0.5, 0.12), 1e-12);
      assertEquals(0.12, Scp096Rules.approach(0.0, 0.5, 0.12), 1e-12);
   }

   @Test
   void stateIdsRoundTrip() {
      for (Scp096State s : Scp096State.values()) {
         assertEquals(s, Scp096State.byId(s.id()));
         assertEquals(s, Scp096State.byName(s.name().toLowerCase()));
         assertFalse(s.calm() && s.enraged(), s + " cannot be both");
      }
      assertEquals(Scp096State.SIT, Scp096State.byId(99));
      assertEquals(Scp096State.SIT, Scp096State.byName("nope"));
      assertFalse(Scp096State.CALMING.calm() || Scp096State.CALMING.enraged(), "calming is neither: no chase, no new trigger yet");
   }
}
