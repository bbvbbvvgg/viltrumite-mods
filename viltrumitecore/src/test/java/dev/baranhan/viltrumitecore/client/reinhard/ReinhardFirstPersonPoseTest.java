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
   void noActionNoOffset() {
      assertSame(ReinhardFirstPersonPose.Offset.NONE, ReinhardFirstPersonPose.of(null, 0, 3.0));
      assertSame(ReinhardFirstPersonPose.Offset.NONE, ReinhardFirstPersonPose.of(ReinhardAbility.REID, 0, 3.0));
   }
}
