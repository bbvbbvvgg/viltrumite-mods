package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * REINHARD OVERHAUL item 15: the {@code reid_draw} / {@code reid_sheathe} clips are the baked
 * choreography ({@link ReidDrawBake}), and the draw they play is slow, heavy and clean.
 * Regenerate the clips: {@code REINHARD_BAKE=1 ./gradlew :viltrumitecore:test --tests '*ReidDrawBakeTest*'}.
 */
class ReidDrawBakeTest {
   private static final double ROTATION_TOLERANCE = Math.toRadians(0.35);
   private static final double POSITION_TOLERANCE = 0.04;

   @Test
   void theClipsInTheFileAreTheBakedChoreography() throws Exception {
      Map<String, Animation> clips = ReinhardSecondPassTest.clips();
      if ("1".equals(System.getenv("REINHARD_BAKE"))) {
         ReidDrawBake.write(clips);
         ReidDrawBake.dense = null;
         clips = ReinhardSecondPassTest.clips();
      }
      JsonObject draw = ReidDrawBake.drawClip(clips), sheathe = ReidDrawBake.sheatheClip(clips);
      List<String> drift = new ArrayList<>();
      compare("reid_draw", clips.get("reid_draw"), ReidDrawBake.parse("baked_draw", 6, draw.getAsJsonObject("bones")), drift);
      compare("reid_sheathe", clips.get("reid_sheathe"), ReidDrawBake.parse("baked_sheathe", 1, sheathe.getAsJsonObject("bones")), drift);
      assertTrue(drift.isEmpty(), "the clips are stale, regenerate them (REINHARD_BAKE=1): " + drift);
   }

   private static void compare(String name, Animation file, Animation baked, List<String> drift) throws Exception {
      assertEquals(baked.lengthSeconds(), file.lengthSeconds(), 1e-6, name + " length");
      for (double s = 0; s <= file.lengthSeconds() + 1e-9 && drift.size() < 12; s += 1 / 80.0) {
         BakedGeoModel a = ReinhardSecondPassTest.body(), b = ReinhardSecondPassTest.body();
         AnimationController.seek(file, a, s);
         AnimationController.seek(baked, b, s);
         for (GeoBone bone : a.allBones()) {
            GeoBone other = b.getBone(bone.name);
            double rotation = Math.max(Math.abs(bone.rotX - other.rotX), Math.max(Math.abs(bone.rotY - other.rotY), Math.abs(bone.rotZ - other.rotZ)));
            double position = Math.max(Math.abs(bone.posX - other.posX), Math.max(Math.abs(bone.posY - other.posY), Math.abs(bone.posZ - other.posZ)));
            if (rotation > ROTATION_TOLERANCE || position > POSITION_TOLERANCE) {
               drift.add(String.format(java.util.Locale.ROOT, "%s %s at %.3fs: %.2f deg, %.3f px", name, bone.name, s, Math.toDegrees(rotation), position));
            }
         }
      }
   }

   @Test
   void theTimelineIsOneSlowPullThatNeverSlidesBack() {
      float previous = 0;
      for (double d = 0; d <= ReidDrawChoreo.RELEASE; d += 0.25) {
         float out = ReidDrawChoreo.extraction(d);
         assertTrue(out >= previous - 1e-5, "the blade never slides back in at " + d);
         previous = out;
      }
      assertEquals(0, ReidDrawChoreo.extraction(ReidDrawChoreo.KOIGUCHI - 2), 1e-6, "sheathed until the koiguchi");
      assertTrue(ReidDrawChoreo.extraction(ReidDrawChoreo.KOIGUCHI + 1) < 1.0F, "the koiguchi only breaks the mouth");
      assertEquals(ReidDrawChoreo.FULL, ReidDrawChoreo.extraction(ReidDrawChoreo.RELEASE), 1e-4, "the tip clears the mouth at the release");
      assertFalse(ReidDrawChoreo.sheathFree(ReidDrawChoreo.RELEASE - 0.01));
      assertTrue(ReidDrawChoreo.sheathFree(ReidDrawChoreo.RELEASE));
      // Slow and heavy: under a third of the blade is out by the half of the pull, the strain slows it.
      assertTrue(ReidDrawChoreo.bladeOut(60) < 0.3F);
      assertTrue(ReidDrawChoreo.extractionRate(89) < ReidDrawChoreo.extractionRate(75), "the sword resists in the strain");
      assertTrue(ReidDrawChoreo.extractionRate(94) > ReidDrawChoreo.extractionRate(89), "the last hand-span snaps free");
   }

   @Test
   void theRenderedDrawIsSlowHeavyAndWithoutJumps() throws Exception {
      Map<String, Animation> clips = ReinhardSecondPassTest.clips();
      Vector3f previousTip = null, previousAxis = null, previousRight = null, previousLeft = null;
      double step = 0.25;
      List<String> bad = new ArrayList<>();
      for (double t = 0; t <= ReidDrawChoreo.LENGTH; t += step) {
         BakedGeoModel body = ReinhardSecondPassTest.body();
         AnimationController.seek(clips.get("reid_draw"), body, t / 20);
         var frames = ReinhardSword.equipment(body, ReinhardPoses.ReidPhase.DRAWING, t, false, 0, 1);
         Vector3f tip = frames[1].transformPosition(new Vector3f(0, 49.58F / 16, 0));
         Vector3f axis = frames[1].transformDirection(new Vector3f(0, 1, 0)).normalize();
         Vector3f right = ReinhardGrip.point(body), left = ReinhardGrip.point(body, true);
         if (previousTip != null) {
            double tipSpeed = tip.distance(previousTip) * 16 / step;
            double turn = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, axis.dot(previousAxis))))) / step;
            boolean pull = t <= ReidDrawChoreo.RELEASE;
            // Before the release the sword is pulled, never swung; the freed blade rises in one arc.
            if (tipSpeed > (pull ? 5.5 : 8.0)) bad.add(String.format(java.util.Locale.ROOT, "tip %.2f px/tick at %.2f", tipSpeed, t));
            if (turn > (pull ? 13.0 : 20.0)) bad.add(String.format(java.util.Locale.ROOT, "blade turns %.1f deg/tick at %.2f", turn, t));
            double palms = Math.max(right.distance(previousRight), left.distance(previousLeft)) * 16 / step;
            if (palms > 4.0) bad.add(String.format(java.util.Locale.ROOT, "palm %.2f px/tick at %.2f", palms, t));
         }
         if (t == Math.rint(t)) {
            for (String limb : new String[]{"right_arm", "right_forearm", "left_arm", "left_forearm"}) {
               float depth = ReidClearance.limbDepth(body, limb, "body", "head");
               // The shoulder cubes always overlap the torso ~0.8 px (idle); a sleeve never sinks deeper than 1.6 px.
               if (depth > 1.6F) bad.add(String.format(java.util.Locale.ROOT, "%s %.2f px into the body at %.0f", limb, depth, t));
            }
         }
         previousTip = tip;
         previousAxis = axis;
         previousRight = right;
         previousLeft = left;
         if (bad.size() > 12) break;
      }
      assertTrue(bad.isEmpty(), "draw motion: " + bad);
   }
}
