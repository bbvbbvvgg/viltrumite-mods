package dev.baranhan.viltrumitecore.hero.thor.hammer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** №157/№159: adaptive return, catch, handle turn; ballistic and free fall. */
class MjolnirFlightTest {
   @Test
   void theReturnSpeedGrowsWithDistanceAndIsCapped() {
      double previous = 0.0;
      for (int distance = 0; distance <= 4000; distance += 25) {
         double speed = MjolnirFlight.returnSpeed(distance);
         assertTrue(speed >= previous, "monotone at " + distance);
         assertTrue(speed >= MjolnirTuning.RETURN_MIN_SPEED && speed <= MjolnirTuning.RETURN_MAX_SPEED);
         previous = speed;
      }

      assertEquals(MjolnirTuning.RETURN_MAX_SPEED, MjolnirFlight.returnSpeed(1.0E6), 1.0E-9);
      assertTrue(MjolnirFlight.remoteSpeed(1.0E6) <= MjolnirTuning.REMOTE_MAX_SPEED);
      assertTrue(MjolnirFlight.remoteSpeed(0.0) >= MjolnirTuning.RETURN_MAX_SPEED, "never slower than the loaded flight");
   }

   @Test
   void stepsNeverOvershootAndTheCatchHappensOnArrival() {
      Vec3 from = Vec3.ZERO;
      Vec3 to = new Vec3(0.0, 0.0, 10.0);
      assertEquals(new Vec3(0.0, 0.0, 4.0), MjolnirFlight.step(from, to, 4.0));
      assertEquals(to, MjolnirFlight.step(from, to, 40.0));
      assertTrue(MjolnirFlight.reaches(MjolnirTuning.CATCH_DISTANCE, 0.1));
      assertTrue(MjolnirFlight.reaches(5.0, 6.0), "a fast hammer is caught instead of passing through");
      assertFalse(MjolnirFlight.reaches(50.0, 6.0));
   }

   @Test
   void theHandleTurnsToThePalmOnlyAtTheEnd() {
      assertEquals(0.0F, MjolnirFlight.handleTurn(MjolnirTuning.TURN_DISTANCE + 10.0));
      assertEquals(1.0F, MjolnirFlight.handleTurn(MjolnirTuning.CATCH_DISTANCE), 1.0E-6F);
      float mid = MjolnirFlight.handleTurn((MjolnirTuning.TURN_DISTANCE + MjolnirTuning.CATCH_DISTANCE) / 2.0);
      assertTrue(mid > 0.0F && mid < 1.0F);
   }

   @Test
   void gravityPullsBothArcs() {
      Vec3 throwArc = MjolnirFlight.ballistic(new Vec3(1.0, 0.0, 0.0));
      assertTrue(throwArc.y < 0.0);
      Vec3 fall = Vec3.ZERO;
      for (int i = 0; i < 1000; i++) {
         fall = MjolnirFlight.falling(fall);
      }

      assertEquals(-MjolnirTuning.FREE_FALL_MAX, fall.y, 1.0E-9, "terminal speed");
      Vec3 homing = MjolnirFlight.homing(new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0), 2.0);
      assertEquals(2.0, homing.length(), 1.0E-9, "homing keeps the speed");
      assertTrue(homing.z > 0.0);
   }
}
