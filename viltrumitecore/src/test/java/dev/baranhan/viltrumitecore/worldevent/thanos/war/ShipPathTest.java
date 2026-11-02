package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import static dev.baranhan.viltrumitecore.worldevent.thanos.war.ShipPath.ARRIVING;
import static dev.baranhan.viltrumitecore.worldevent.thanos.war.ShipPath.GONE;
import static dev.baranhan.viltrumitecore.worldevent.thanos.war.ShipPath.HOVER;
import static dev.baranhan.viltrumitecore.worldevent.thanos.war.ShipPath.LEAVING;
import static dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosWarRules.SHIP_ARRIVAL;
import static dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosWarRules.SHIP_LEAVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The Sanctuary II's path, shared by the server and every client: it comes in from the horizon at
 * speed, comes to rest over the city with its nose square, banks into its turns, and climbs away from
 * wherever it hovers. No pose jumps between the states.
 */
class ShipPathTest {
   private static final Vec3 OVER = new Vec3(120.5, 176.0, -40.5);

   private static Vec3 start(double angle) {
      double a = Math.toRadians(angle);
      return OVER.add(Math.cos(a) * ThanosWarRules.SHIP_START_DISTANCE, ThanosWarRules.SHIP_START_RISE, Math.sin(a) * ThanosWarRules.SHIP_START_DISTANCE);
   }

   private static void close(Vec3 expected, Vec3 actual, double tolerance, String what) {
      assertTrue(expected.distanceTo(actual) <= tolerance, what + ": expected " + expected + " but was " + actual);
   }

   /** Minecraft yaw of a horizontal motion (forward = (-sin, 0, cos)). */
   private static float yawOf(Vec3 motion) {
      return (float)(Mth.atan2(-motion.x, motion.z) * Mth.RAD_TO_DEG);
   }

   @Test
   void theGlideComesInFastAndEndsAtRestOverTheCity() {
      for (double angle = 0.0; angle < 360.0; angle += 45.0) {
         Vec3 start = start(angle);
         close(start, ShipPath.at(ARRIVING, 0.0, start, OVER), 1.0E-6, "starts on the horizon");
         Vec3 end = ShipPath.at(ARRIVING, SHIP_ARRIVAL, start, OVER);
         close(OVER, end, 1.0E-6, "ends over the city");
         close(OVER, ShipPath.at(HOVER, 0.0, start, OVER), 1.0E-6, "the hover starts where the glide ends");
         assertTrue(ShipPath.at(ARRIVING, SHIP_ARRIVAL - 1, start, OVER).distanceTo(end) < 0.01, "at rest at the end of the glide (also sideways)");

         double first = ShipPath.at(ARRIVING, 1.0, start, OVER).distanceTo(start);
         Vec3 last = start;
         for (int t = 1; t <= SHIP_ARRIVAL; t++) {
            Vec3 p = ShipPath.at(ARRIVING, t, start, OVER);
            assertTrue(p.y >= OVER.y - 1.0E-6, "never below its hover height");
            assertTrue(p.distanceTo(last) <= first + 1.0E-6, "never faster than at the horizon");
            last = p;
         }
      }
   }

   @Test
   void theNoseFollowsTheCurveAndSettlesSquareForTheHover() {
      for (double angle = 0.0; angle < 360.0; angle += 30.0) {
         Vec3 start = start(angle);
         float straight = ShipPath.yaw(GONE, 0.0, start, OVER);
         assertEquals(0.0F, Mth.wrapDegrees(ShipPath.yaw(ARRIVING, SHIP_ARRIVAL, start, OVER) - straight), 1.0E-3F, "square at the end");
         assertEquals(0.0F, Mth.wrapDegrees(ShipPath.yaw(HOVER, 0.0, start, OVER) - straight), 1.0E-3F, "the hover starts square");
         assertEquals(0.0F, Mth.wrapDegrees(yawOf(ShipPath.heading(start, OVER)) - straight), 1.0E-3F, "the straight heading");
         float before = ShipPath.yaw(ARRIVING, 0.0, start, OVER);
         for (int t = 4; t <= SHIP_ARRIVAL * 0.7; t += 4) {
            Vec3 motion = ShipPath.at(ARRIVING, t + 1, start, OVER).subtract(ShipPath.at(ARRIVING, t - 1, start, OVER));
            float yaw = ShipPath.yaw(ARRIVING, t, start, OVER);
            assertEquals(0.0F, Mth.wrapDegrees(yawOf(motion) - yaw), 1.5F, "the nose along the motion at tick " + t);
            // the glide curves left: the nose swings left and the ship banks left (roll < 0)
            assertTrue(Mth.wrapDegrees(yaw - before) <= 1.0E-4F, "turning left at tick " + t);
            assertTrue(ShipPath.roll(ARRIVING, t) <= 0.0F, "banks into the turn at tick " + t);
            before = yaw;
         }
      }
   }

   @Test
   void noPoseJumpsBetweenTheStates() {
      Vec3 start = start(200.0);
      assertEquals(0.0F, ShipPath.roll(ARRIVING, SHIP_ARRIVAL), 1.0E-3F);
      assertEquals(0.0F, ShipPath.pitch(ARRIVING, SHIP_ARRIVAL), 1.0E-3F);
      assertEquals(ShipPath.roll(HOVER, 0.0), ShipPath.roll(LEAVING, 0.0), 1.0E-3F);
      assertEquals(ShipPath.pitch(HOVER, 0.0), ShipPath.pitch(LEAVING, 0.0), 1.0E-3F);
      assertTrue(ShipPath.pitch(ARRIVING, 0.0) > 0.0F, "nose down while it descends");
      // every tick-to-tick change stays small in every state
      for (int state : new int[]{ARRIVING, HOVER, LEAVING}) {
         int length = state == ARRIVING ? SHIP_ARRIVAL : state == LEAVING ? SHIP_LEAVE : 600;
         for (int t = 1; t <= length; t++) {
            assertTrue(Math.abs(Mth.wrapDegrees(ShipPath.yaw(state, t, start, OVER) - ShipPath.yaw(state, t - 1, start, OVER))) < 0.6F, "yaw rate");
            assertTrue(Math.abs(ShipPath.roll(state, t) - ShipPath.roll(state, t - 1)) < 0.3F, "roll rate");
            assertTrue(Math.abs(ShipPath.pitch(state, t) - ShipPath.pitch(state, t - 1)) < 0.3F, "pitch rate");
         }
      }
   }

   @Test
   void itClimbsAwayFromWhereItHovers() {
      Vec3 start = start(90.0);
      Vec3 bobbing = ShipPath.at(HOVER, 37.0, start, OVER);
      close(bobbing, ShipPath.at(LEAVING, 0.0, start, bobbing), 1.0E-6, "the climb starts where it is");
      double y = bobbing.y;
      float yaw = ShipPath.yaw(LEAVING, 0.0, start, bobbing);
      for (int t = 1; t <= SHIP_LEAVE; t++) {
         Vec3 p = ShipPath.at(LEAVING, t, start, bobbing);
         assertTrue(p.y > y, "climbs at tick " + t);
         y = p.y;
         float next = ShipPath.yaw(LEAVING, t, start, bobbing);
         assertTrue(Mth.wrapDegrees(next - yaw) >= 0.0F, "turns right");
         assertTrue(ShipPath.roll(LEAVING, t) >= 0.0F, "banks right into the turn");
         yaw = next;
      }

      assertTrue(ShipPath.at(LEAVING, SHIP_LEAVE, start, bobbing).distanceTo(bobbing) > 600.0, "out of sight at the end");
   }

   @Test
   void theHoverBobsGently() {
      Vec3 start = start(10.0);
      for (int t = 0; t < 2 * ThanosWarRules.SHIP_BOB_PERIOD; t++) {
         Vec3 p = ShipPath.at(HOVER, t, start, OVER);
         assertEquals(OVER.x, p.x, 1.0E-9);
         assertEquals(OVER.z, p.z, 1.0E-9);
         assertTrue(Math.abs(p.y - OVER.y) <= ThanosWarRules.SHIP_BOB + 1.0E-9);
         assertEquals(ThanosWarRules.bob(t), p.y - OVER.y, 1.0E-9, "the same bob as the rules");
      }
   }

   @Test
   void theGunsAreUnderTheHullAndTheClosestOneFires() {
      Vec3 centre = new Vec3(10.0, 180.0, -5.0);
      java.util.SplittableRandom r = new java.util.SplittableRandom(5);
      for (int i = 0; i < 200; i++) {
         float yaw = (float)(r.nextDouble() * 360.0 - 180.0);
         close(centre.add(0.0, -16.0, 0.0), ShipPath.emitter(0, centre, yaw), 1.0E-9, "the hub is under the centre at any yaw");
         for (int e = 0; e < ShipPath.EMITTERS.length; e++) {
            double[] local = ShipPath.EMITTERS[e];
            Vec3 world = ShipPath.emitter(e, centre, yaw);
            assertTrue(world.y < centre.y, "under the hull");
            assertEquals(Math.hypot(local[0], local[2]), Math.hypot(world.x - centre.x, world.z - centre.z), 1.0E-9, "rotated, not stretched");
         }

         Vec3 target = centre.add(r.nextDouble() * 400.0 - 200.0, -120.0 - r.nextDouble() * 40.0, r.nextDouble() * 400.0 - 200.0);
         Vec3 gun = ShipPath.gun(centre, yaw, target);
         for (int e = 0; e < ShipPath.EMITTERS.length; e++) {
            assertTrue(gun.distanceToSqr(target) <= ShipPath.emitter(e, centre, yaw).distanceToSqr(target) + 1.0E-9, "the closest gun fires");
         }
      }

      // the forward arms point along the heading
      Vec3 frontLeft = ShipPath.emitter(1, centre, 0.0F);
      assertTrue(frontLeft.z > centre.z, "yaw 0 faces +z");
   }
}
