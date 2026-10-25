package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import org.junit.jupiter.api.Test;

/** First-person action offsets of the main arm (stage 3). */
class ReinhardFirstPersonPoseTest {
   @Test
   void lightPunchJabPeaksOnTheHitTick() {
      ReinhardFirstPersonPose.Offset start = ReinhardFirstPersonPose.of(ReinhardAbility.LIGHT_PUNCH, 0, 0.0);
      ReinhardFirstPersonPose.Offset hit = ReinhardFirstPersonPose.of(ReinhardAbility.LIGHT_PUNCH, 0, ReinhardRules.LIGHT_PUNCH_HIT_TICK);
      ReinhardFirstPersonPose.Offset end = ReinhardFirstPersonPose.of(ReinhardAbility.LIGHT_PUNCH, 0, ReinhardRules.LIGHT_PUNCH_TICKS);
      assertEquals(0.0F, start.z(), 1.0E-6F);
      assertTrue(hit.z() < -0.2F, "the fist goes forward (away from the camera)");
      assertEquals(0.0F, end.z(), 1.0E-6F);
      assertTrue(ReinhardFirstPersonPose.of(ReinhardAbility.LIGHT_PUNCH, 0, 5.0).z() > hit.z(), "it comes back after the hit");
   }

   @Test
   void stepDashDropsTheArmThenReturns() {
      assertTrue(ReinhardFirstPersonPose.of(ReinhardAbility.STEP_DASH, 0, 2.0).y() < 0.0F);
      assertEquals(0.0F, ReinhardFirstPersonPose.of(ReinhardAbility.STEP_DASH, 0, ReinhardRules.STEP_ACTION_TICKS).y(), 1.0E-6F);
   }

   @Test
   void catchHandUpThenClosesAndHolds() {
      ReinhardFirstPersonPose.Offset ready = ReinhardFirstPersonPose.of(ReinhardAbility.CATCH, 0, 5.0, 10);
      assertTrue(ready.y() > 0.1F && ready.pitch() < -30.0F, "the palm is up in front of the face");
      ReinhardFirstPersonPose.Offset snap = ReinhardFirstPersonPose.of(ReinhardAbility.CATCH, ReinhardClips.PHASE_SNAP, 1.0, 6);
      assertTrue(snap.z() < ready.z(), "the closing hand jerks forward");
      ReinhardFirstPersonPose.Offset held = ReinhardFirstPersonPose.of(ReinhardAbility.CATCH, ReinhardClips.PHASE_SNAP, 4.0, 9);
      assertEquals(ready.y(), held.y(), 1.0E-6F);
      assertEquals(0.0F, ReinhardFirstPersonPose.of(ReinhardAbility.CATCH, 0, 10.0, 10).y(), 1.0E-6F, "lowered when the window ends");
   }

   @Test
   void comboHitsLandOnTheirContactTicks() {
      for (int step = 0; step < 3; step++) {
         int contact = ReinhardRules.COMBO_HIT_TICKS[step];
         assertTrue(ReinhardFirstPersonPose.of(ReinhardAbility.FIST_COMBO, step, contact).z() < -0.2F, "hit " + (step + 1) + " reaches forward");
         assertEquals(0.0F, ReinhardFirstPersonPose.of(ReinhardAbility.FIST_COMBO, step, ReinhardRules.COMBO_STEP_TICKS[step]).z(), 1.0E-6F);
      }

      assertTrue(ReinhardFirstPersonPose.of(ReinhardAbility.FIST_COMBO, 1, ReinhardRules.COMBO_HIT_TICKS[1]).yaw() > 0.0F, "the second hit is a hook");
      assertTrue(ReinhardFirstPersonPose.of(ReinhardAbility.FIST_COMBO, 2, 2.0).y() > 0.0F, "the third is raised first");
   }

   @Test
   void skyKickTossThenBalance() {
      assertTrue(ReinhardFirstPersonPose.of(ReinhardAbility.SKY_KICK, 0, 3.0).y() > 0.0F, "the toss lifts the hand");
      assertTrue(ReinhardFirstPersonPose.of(ReinhardAbility.SKY_KICK, 0, ReinhardRules.KICK_TOSS_TICKS + 1.0).y() < 0.0F, "the arm swings down for the kick");
      assertEquals(0.0F, ReinhardFirstPersonPose.of(ReinhardAbility.SKY_KICK, 0, ReinhardRules.KICK_ACTION_TICKS).y(), 1.0E-6F);
   }

   @Test
   void grabHoldsThenThrows() {
      assertTrue(ReinhardFirstPersonPose.of(ReinhardAbility.GRAB_THROW, 0, 40.0).z() < 0.0F, "the hand holds out in front");
      assertTrue(ReinhardFirstPersonPose.of(ReinhardAbility.GRAB_THROW, ReinhardClips.PHASE_SNAP, 2.0).z() > 0.0F, "cocked back during the hit-stop");
      assertTrue(ReinhardFirstPersonPose.of(ReinhardAbility.GRAB_THROW, ReinhardClips.PHASE_SNAP, ReinhardRules.HIT_STOP_TICKS + 1.0).z() < 0.0F,
         "driven forward on the launch");
      assertEquals(0.0F, ReinhardFirstPersonPose.of(ReinhardAbility.GRAB_THROW, ReinhardClips.PHASE_SNAP, ReinhardRules.THROW_ACTION_TICKS).z(), 1.0E-6F);
   }

   @Test
   void dashBehindDipsThenSettles() {
      assertTrue(ReinhardFirstPersonPose.of(ReinhardAbility.DASH_BEHIND, 0, 2.0).y() < 0.0F, "the arm sweeps low on the vanish");
      assertEquals(0.0F, ReinhardFirstPersonPose.of(ReinhardAbility.DASH_BEHIND, 0, ReinhardRules.BEHIND_ACTION_TICKS).y(), 1.0E-6F);
   }

   @Test
   void stompJoltsOnTheSlam() {
      float slam = ReinhardFirstPersonPose.of(ReinhardAbility.STOMP, 0, ReinhardRules.STOMP_HIT_TICK).y();
      assertTrue(slam < 0.0F, "the hand jolts down on the slam");
      assertTrue(ReinhardFirstPersonPose.of(ReinhardAbility.STOMP, 0, 2.0).y() > slam, "it builds up to the slam");
      assertEquals(0.0F, ReinhardFirstPersonPose.of(ReinhardAbility.STOMP, 0, ReinhardRules.STOMP_TICKS).y(), 1.0E-6F);
   }

   @Test
   void reflectGuardsThenSwats() {
      assertTrue(ReinhardFirstPersonPose.of(ReinhardAbility.REFLECT, 0, 5.0, ReinhardRules.REFLECT_WINDOW).x() > 0.0F, "the backhand across the chest");
      assertTrue(ReinhardFirstPersonPose.of(ReinhardAbility.REFLECT, ReinhardClips.PHASE_SNAP, 2.0, 10).x() < 0.0F, "swatted out to the side");
      assertEquals(0.0F, ReinhardFirstPersonPose.of(ReinhardAbility.REFLECT, 0, ReinhardRules.REFLECT_WINDOW, ReinhardRules.REFLECT_WINDOW).x(), 1.0E-6F);
   }

   @Test
   void noActionNoOffset() {
      assertSame(ReinhardFirstPersonPose.Offset.NONE, ReinhardFirstPersonPose.of(null, 0, 3.0));
      assertSame(ReinhardFirstPersonPose.Offset.NONE, ReinhardFirstPersonPose.of(ReinhardAbility.REID, 0, 3.0));
   }
}
