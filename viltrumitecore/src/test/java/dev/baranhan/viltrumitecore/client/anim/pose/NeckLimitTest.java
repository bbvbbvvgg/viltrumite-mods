package dev.baranhan.viltrumitecore.client.anim.pose;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Playtest: heads twisted past the neck when a layered body twist/lean met the look. */
class NeckLimitTest {
   private static float rad(double deg) {
      return (float)Math.toRadians(deg);
   }

   @Test
   void aLookInsideTheLimitIsUntouched() {
      assertEquals(rad(60), PoseRig.neck(rad(60), 0, PoseRig.NECK_YAW), 1.0E-5);
      assertEquals(rad(-45), PoseRig.neck(rad(-45), rad(10), PoseRig.NECK_PITCH), 1.0E-5);
   }

   @Test
   void aBodyTwistAgainstTheLookIsLimited() {
      // looking 60° right while a strike twists the torso 40° left: 100° relative -> 80°
      assertEquals(rad(-40 + 80), PoseRig.neck(rad(60), rad(-40), PoseRig.NECK_YAW), 1.0E-5);
      // roar head-up on top of looking straight up
      assertEquals(-PoseRig.NECK_PITCH, PoseRig.neck(rad(-118), 0, PoseRig.NECK_PITCH), 1.0E-5);
   }

   @Test
   void wrappedAnglesAreNotMisreadAsExtreme() {
      assertEquals(rad(-10), PoseRig.neck(rad(350), 0, PoseRig.NECK_YAW), 1.0E-4);
   }

   @Test
   void brokenAnglesCollapseOntoTheTorso() {
      assertEquals(rad(5), PoseRig.neck(Float.NaN, rad(5), PoseRig.NECK_ROLL), 1.0E-6);
   }
}
