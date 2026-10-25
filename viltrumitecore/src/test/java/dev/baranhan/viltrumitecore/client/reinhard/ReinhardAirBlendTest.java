package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode;
import java.util.Map;
import java.util.Set;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

/** PR #39 review: the air poses blend over the full gait, and an elytra glide has no gait. */
class ReinhardAirBlendTest {
   @Test
   void glideAndSeatStopTheGait() {
      assertEquals(0.0F, ReinhardRenderer.gaitSwing(false, true, 0.8F));
      assertEquals(0.0F, ReinhardRenderer.gaitSwing(true, false, 0.8F));
      assertEquals(0.8F, ReinhardRenderer.gaitSwing(false, false, 0.8F), "an ordinary jump keeps its cross-fade");
   }

   @Test
   void sequentialAirWeightsStayComplementary() {
      assertArrayEquals(new float[]{0.5F, 0.0F}, ReinhardRenderer.airWeights(0.5F, 0.5F, 0.0F), 1e-6F);
      assertArrayEquals(new float[]{1.0F, 0.0F}, ReinhardRenderer.airWeights(0.0F, 1.0F, 0.0F), 1e-6F);
      // ground .4, rise .2, fall .4: rise over the gait at .2/.6, then fall at .4/1.
      float[] w = ReinhardRenderer.airWeights(0.4F, 0.2F, 0.4F);
      assertEquals(0.2F / 0.6F, w[0], 1e-6F);
      assertEquals(0.4F, w[1], 1e-6F);
      float gait = (1 - w[1]) * (1 - w[0]), rise = (1 - w[1]) * w[0], fall = w[1];
      assertEquals(0.4F, gait, 1e-6F);
      assertEquals(0.2F, rise, 1e-6F);
      assertEquals(0.4F, fall, 1e-6F);
      assertArrayEquals(new float[]{0.0F, 0.0F}, ReinhardRenderer.airWeights(0.0F, 0.0F, 0.0F));
   }

   @Test
   void gaitWeightKeepsAirCoveredBonesFull() {
      var weight = ReinhardRenderer.gaitWeight(Set.of("right_leg"), 0.9F, 0.25F);
      assertEquals(0.9, weight.applyAsDouble("right_leg"), 1e-6);
      assertEquals(0.225, weight.applyAsDouble("right_arm"), 1e-6);
   }

   @Test
   void takeoffHalfwayIsAnEvenGaitToAirBlendWithoutIdleDip() throws Exception {
      var clips = ReinhardSecondPassTest.clips();
      var field = AnimCache.class.getDeclaredField("animationFiles");
      field.setAccessible(true);
      Object previous = field.get(null);
      try {
         field.set(null, Map.of(ReinhardAssets.ANIMATIONS, clips));
         var pose = new ReinhardPoses.Pose("idle_restrained", 0.4, 1.0F, 0.3, null, ReinhardPoses.BodyState.STAND, 0);
         var actual = ReinhardSecondPassTest.body();
         ReinhardRenderer.applyClips(actual, pose, new StanceBlend.Mix(ReinhardMode.RESTRAINED, ReinhardMode.RESTRAINED, 1), 0, 0, 0,
            new ParryPulse.Sample(0, 0), null, null, new float[]{0, 0, 0.5F, 0});

         var gait = ReinhardSecondPassTest.body();
         AnimationController.seek(clips.get("idle_restrained"), gait, ReinhardPlayback.time(clips.get("idle_restrained"), 0.4));
         ReinhardLayers.overlay(clips.get("walk"), gait, ReinhardPlayback.time(clips.get("walk"), 0.3), 1);
         var air = ReinhardSecondPassTest.body();
         AnimationController.seek(clips.get("idle_restrained"), air, ReinhardPlayback.time(clips.get("idle_restrained"), 0.4));
         ReinhardLayers.overlay(clips.get("air_rise"), air, 0, 1);
         for (String bone : new String[]{"right_leg", "left_leg", "right_shin", "left_shin"}) {
            var g = gait.getBone(bone);
            var a = air.getBone(bone);
            var even = new Quaternionf().rotationZYX(g.rotZ, g.rotY, g.rotX).slerp(new Quaternionf().rotationZYX(a.rotZ, a.rotY, a.rotX), 0.5F);
            var b = actual.getBone(bone);
            var got = new Quaternionf().rotationZYX(b.rotZ, b.rotY, b.rotX);
            assertTrue(Math.abs(even.dot(got)) > 0.99999, bone + ": half gait + half air pose, no stance share");
         }
      } finally {
         field.set(null, previous);
      }
   }
}
