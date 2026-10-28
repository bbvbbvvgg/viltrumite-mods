package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumiteflight.util.FlightState;
import org.junit.jupiter.api.Test;

/** R4, spec §5: Mjölnir whirls like helicopter blades at 5–8 rev/s in flight, about the fist, with a blur trail. */
class MjolnirSpinTest {
   @Test
   void flightSpinsAtFiveToEightRevolutionsASecond() {
      for (FlightState state : new FlightState[]{FlightState.HOVER, FlightState.CRUISE, FlightState.SONIC}) {
         float rps = MjolnirSpin.targetRps(state, true);
         assertTrue(rps >= 5.0F && rps <= 8.0F, state + " spins at " + rps);
         assertEquals(0.0F, MjolnirSpin.targetRps(state, false), "no hammer in the hand, nothing spins");
      }

      assertEquals(0.0F, MjolnirSpin.targetRps(FlightState.NONE, true));
      assertEquals(0.0F, MjolnirSpin.targetRps(null, true));
   }

   @Test
   void spinRampsUpAndDownHeavily() {
      MjolnirSpin.State state = new MjolnirSpin.State(0.0);
      float target = MjolnirSpin.targetRps(FlightState.HOVER, true);
      state.step(4.0, target);
      assertTrue(state.rps > 0.0F && state.rps < target, "no instant full spin: " + state.rps);
      state.step(40.0, target);
      assertEquals(target, state.rps, 1.0E-4);
      state.step(44.0, 0.0F);
      assertTrue(state.rps > 0.0F, "it winds down, it does not stop dead");
      state.step(80.0, 0.0F);
      assertEquals(0.0F, state.rps);
   }

   @Test
   void oneFrameDrawnTwiceTurnsOnce() {
      MjolnirSpin.State state = new MjolnirSpin.State(0.0);
      state.step(30.0, MjolnirSpin.SONIC_RPS);
      float angle = state.angle;
      state.step(30.0, MjolnirSpin.SONIC_RPS);
      assertEquals(angle, state.angle, "third person, first person and the shadow pass share a frame");
      state.step(30.5, MjolnirSpin.SONIC_RPS);
      float turned = (float)Math.IEEEremainder(state.angle - angle, Math.PI * 2.0);
      // 8 rev/s over half a tick = 0.2 rev = 72°.
      assertEquals(Math.toRadians(72.0), turned, 1.0E-3);
   }

   @Test
   void advanceMatchesRevolutionsPerSecond() {
      assertEquals(Math.PI * 2.0 * 6.5, MjolnirSpin.advance(6.5F, 20.0F), 1.0E-3, "one second at 6.5 rev/s");
   }

   @Test
   void turnsAboutTheGripNotTheBonePivot() {
      for (float angle : new float[]{0.3F, 1.7F, 3.1F, 5.0F}) {
         float[] pose = MjolnirSpin.bodyPose(angle);
         // The grip g = (0, GRIP_OFFSET, 0) above the pivot must stay put: R·g + d == g.
         double ry = MjolnirSpin.GRIP_OFFSET * Math.cos(angle);
         double rz = MjolnirSpin.GRIP_OFFSET * Math.sin(angle);
         assertEquals(MjolnirSpin.GRIP_OFFSET, ry + pose[2], 1.0E-4);
         assertEquals(0.0, rz + pose[3], 1.0E-4);
         assertEquals(0.0F, pose[1]);
         assertEquals(angle, pose[0]);
      }
   }

   @Test
   void ghostsTrailBehindAndFade() {
      float rps = MjolnirSpin.CRUISE_RPS;
      float previous = 1.0F;
      for (int i = 1; i <= MjolnirSpin.GHOSTS; i++) {
         assertTrue(MjolnirSpin.ghostAngle(1.0F, rps, i) < 1.0F, "behind the hammer");
         float alpha = MjolnirSpin.ghostAlpha(rps, i);
         assertTrue(alpha > 0.0F && alpha < previous, "fading copies");
         previous = alpha;
      }

      assertEquals(0.0F, MjolnirSpin.ghostAlpha(1.0F, 1), "a slow turn does not blur");
      assertTrue(MjolnirSpin.trail(MjolnirSpin.SONIC_RPS) <= MjolnirSpin.MAX_TRAIL);
   }

   @Test
   void onlyTheHammerRenderSpins() throws Exception {
      String parts = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/dev/baranhan/viltrumitecore/client/thor/ThorParts.java"));
      assertTrue(parts.contains("MjolnirSpin.bodyPose"));
      for (String pose : new String[]{"ThorScenePoser", "ThorPoser"}) {
         String text = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/dev/baranhan/viltrumitecore/client/thor/"
            + (pose.equals("ThorScenePoser") ? "scene/" : "") + pose + ".java"));
         assertTrue(!text.contains("MjolnirSpin"), pose + ": arm poses stay with R1/R3");
      }
   }
}
