package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.reinhard.StepDashClient;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.StepDash;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Spec §6.1 Step Dash (stage 3 Task 3): client-predicted, server-authoritative cooldown. */
class StepDashTest {
   private static final double GROUND = StepDash.GROUND_FRICTION;

   /** Vanilla horizontal motion: move by v, then v *= friction; the dash re-applies v for {@code ticks} ticks. */
   private static double simulate(double v, int ticks, double friction) {
      double sum = 0.0;
      double speed = v;
      for (int t = 0; t < 200; t++) {
         if (t < ticks) {
            speed = v;
         }

         sum += speed;
         speed *= friction;
      }

      return sum;
   }

   @Test
   void coversSixBlocks() {
      assertEquals(6, ReinhardRules.STEP_DISTANCE);
      assertEquals(4, ReinhardRules.STEP_TICKS);
      assertEquals(0.546, GROUND, 1.0E-9);
      double v = StepDash.velocity(ReinhardRules.STEP_DISTANCE, ReinhardRules.STEP_TICKS, GROUND);
      assertEquals(6.0, simulate(v, ReinhardRules.STEP_TICKS, GROUND), 0.3);
      // In the air (friction 0.91) the same distance: a slower set speed.
      double air = StepDash.velocity(ReinhardRules.STEP_DISTANCE, ReinhardRules.STEP_TICKS, StepDash.AIR_FRICTION);
      assertTrue(air < v);
      assertEquals(6.0, simulate(air, ReinhardRules.STEP_TICKS, StepDash.AIR_FRICTION), 0.3);
   }

   @Test
   void cooldownTwentyTicks() {
      assertEquals(20, ReinhardRules.STEP_COOLDOWN);
      ReinhardState state = new ReinhardState();
      StepDash.apply(state, StepDash.response(ReinhardActions.Refusal.NONE));
      assertEquals(20, state.cooldowns[ReinhardAbility.STEP_DASH.ordinal()]);
      assertTrue(state.action.is(ReinhardAbility.STEP_DASH));
      assertEquals(ReinhardRules.STEP_ACTION_TICKS, state.action.length());
      for (int i = 0; i < 20; i++) {
         state.tick();
      }

      assertEquals(0, state.cooldowns[ReinhardAbility.STEP_DASH.ordinal()]);
   }

   @Test
   void serverRefusalHasNoSideEffects() {
      StepDash.Response ok = StepDash.response(ReinhardActions.Refusal.NONE);
      assertTrue(ok.started() && ok.fx());
      assertFalse(ok.stopMotion(), "an accepted dash keeps the client's own motion (no correction)");
      for (ReinhardActions.Refusal refusal : ReinhardActions.Refusal.values()) {
         if (refusal == ReinhardActions.Refusal.NONE) {
            continue;
         }

         // The client already moved (cooldown desync): the server refuses.
         ReinhardState state = new ReinhardState();
         state.cooldowns[ReinhardAbility.STEP_DASH.ordinal()] = 7;
         StepDash.Response r = StepDash.response(refusal);
         StepDash.apply(state, r);
         assertFalse(r.started(), refusal.name());
         assertFalse(r.fx(), "no FX on a refusal");
         assertTrue(r.stopMotion(), "the predicted dash is stopped by the server's velocity");
         assertFalse(r.correctPosition(), "the position the client reached is accepted");
         assertEquals(7, state.cooldowns[ReinhardAbility.STEP_DASH.ordinal()], "no cooldown reset");
         assertFalse(state.action.running());
      }
   }

   @Test
   void directionFromInputThenLook() {
      // Yaw 0 looks along +z; W = forward.
      Vec3 forward = StepDash.direction(1.0F, 0.0F, 0.0F);
      assertEquals(0.0, forward.x, 1.0E-6);
      assertEquals(1.0, forward.z, 1.0E-6);
      // A (strafe left) at yaw 0 = +x (vanilla moveRelative).
      Vec3 left = StepDash.direction(0.0F, 1.0F, 0.0F);
      assertEquals(1.0, left.x, 1.0E-6);
      assertEquals(0.0, left.z, 1.0E-6);
      // S at yaw 90 (looking toward −x): backward = +x.
      Vec3 back = StepDash.direction(-1.0F, 0.0F, 90.0F);
      assertEquals(1.0, back.x, 1.0E-6);
      assertEquals(0.0, back.z, 1.0E-6);
      // Diagonal input is normalised.
      assertEquals(1.0, StepDash.direction(1.0F, 1.0F, 30.0F).length(), 1.0E-6);
      // No input: the horizontal look (yaw 90 → −x).
      Vec3 look = StepDash.direction(0.0F, 0.0F, 90.0F);
      assertEquals(-1.0, look.x, 1.0E-6);
      assertEquals(0.0, look.y, 1.0E-6);
   }

   @Test
   void onePressOnePacket() throws Exception {
      // The generic hero slot loop (ViltrumiteCoreClient) sends a packet on every key edge;
      // the predictor watches the same key and only moves the player.
      boolean[] keys = {false, true, true, true, true, false, false, true, false};
      boolean loopDown = false;
      int packets = 0;
      int pressPackets = 0;
      StepDashClient.Predictor predictor = new StepDashClient.Predictor();
      int dashes = 0;
      for (boolean down : keys) {
         if (down != loopDown) {
            loopDown = down;
            packets++;
            if (down) {
               pressPackets++;
            }
         }

         if (predictor.onKey(down, true)) {
            dashes++;
         }

         predictor.tick();
      }

      assertEquals(2, pressPackets, "two key presses → two press packets from the generic loop only");
      assertEquals(4, packets);
      assertEquals(1, dashes, "the second press falls in the local cooldown");
      // The predictor itself never talks to the server.
      Path source = Path.of("src/main/java/dev/baranhan/viltrumitecore/client/reinhard/StepDashClient.java");
      String code = Files.readString(source);
      assertFalse(code.contains("sendToServer") || code.contains("CoreMessages"), "StepDashClient must not send packets");
   }

   @Test
   void predictorReappliesThenStops() {
      StepDashClient.Predictor predictor = new StepDashClient.Predictor();
      assertTrue(predictor.onKey(true, true));
      int applied = 0;
      while (predictor.applying()) {
         applied++;
         predictor.tick();
      }

      assertEquals(ReinhardRules.STEP_TICKS, applied);
      assertFalse(new StepDashClient.Predictor().onKey(true, false), "a failed mirrored gate does not move");
      StepDashClient.Predictor wall = new StepDashClient.Predictor();
      wall.onKey(true, true);
      assertTrue(StepDashClient.Predictor.blocked(1.15, 0.1), "speed lost (wall, server stop) ends the dash");
      assertFalse(StepDashClient.Predictor.blocked(1.15, 0.63));
   }
}
