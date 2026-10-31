package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.reinhard.finalpunch.FinalPunchFist;
import dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchRules;
import dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchTargets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * THE FINAL PUNCH take on the real rig (BakedGeoModel + ReinhardRenderer.boneFrame): the fist
 * lands on the jaw point of a person and, with the jump correction, of an iron golem; the hit-stop
 * holds still; the take starts and ends on the stance; the generated fallback fist table agrees.
 */
class FinalPunchPoseTest {
   private static final float SCALE = 0.9375F;
   private static Animation idle;

   private static BakedGeoModel stance() throws Exception {
      if (idle == null) idle = ReinhardSecondPassTest.clips().get("idle_restrained");
      BakedGeoModel body = ReinhardSecondPassTest.body();
      AnimationController.seek(idle, body, 0);
      return body;
   }

   private static Animation clip() throws Exception {
      AnimationParser.lastWarnings.clear();
      Map<String, Animation> clips = AnimationParser.parse(JsonParser.parseString(Files.readString(
         Path.of("src/main/resources/assets/viltrumitecore/" + FinalPunchPose.CLIP.getPath()))).getAsJsonObject());
      assertTrue(AnimationParser.lastWarnings.isEmpty(), AnimationParser.lastWarnings.toString());
      Animation clip = clips.get(ReinhardClips.FINAL_PUNCH);
      assertNotNull(clip);
      return clip;
   }

   /** Fist centre (forward, up, right) of the feet frame at scene tick t. */
   private static double[] fist(Animation clip, double t, FinalPunchTargets.Kind kind) throws Exception {
      BakedGeoModel body = stance();
      FinalPunchPose.apply(body, clip, t, 1.0F, kind);
      Vector3f p = ReinhardGrip.point(body, false);
      return new double[]{-p.z * SCALE, p.y * SCALE, p.x * SCALE};
   }

   private static double distance(double[] a, double[] b) {
      return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
   }

   @Test
   void oneTwentyFourSecondTakeThatHolds() throws Exception {
      Animation clip = clip();
      assertEquals(FinalPunchRules.LENGTH / 20.0, clip.lengthSeconds(), 1.0E-6);
      assertEquals(Animation.LoopType.HOLD_ON_LAST_FRAME, clip.loopType());
      for (String bone : new String[]{"head", "body", "right_arm", "right_forearm", "right_hand", "left_arm", "left_forearm",
         "right_leg", "left_leg", "right_shin", "left_shin"}) {
         assertTrue(clip.boneAnimations().containsKey(bone), "the take owns " + bone);
      }
   }

   @Test
   void theFistLandsOnAPersonsJaw() throws Exception {
      double[] f = fist(clip(), FinalPunchRules.CONTACT, FinalPunchTargets.Kind.HUMAN);
      double[] jaw = {0.95, 1.406, 0.13};
      // fist centre just in front of the chin: within a fist's half size of the jaw point
      assertEquals(0.87, f[0], 0.05, "reach");
      assertEquals(1.44, f[1], 0.05, "height");
      assertEquals(0.13, f[2], 0.05, "on the fist line");
      assertTrue(distance(f, jaw) < 0.14, "touches the jaw: " + distance(f, jaw));
   }

   @Test
   void theJumpReachesAGolemsJaw() throws Exception {
      Animation clip = clip();
      double[] f = fist(clip, FinalPunchRules.CONTACT, FinalPunchTargets.Kind.GOLEM);
      assertEquals(2.10, f[1], 0.07, "golem jaw height");
      assertEquals(0.87, f[0], 0.07, "reach");
      double[] calm = fist(clip, 120, FinalPunchTargets.Kind.GOLEM);
      double[] person = fist(clip, 120, FinalPunchTargets.Kind.HUMAN);
      assertTrue(distance(calm, person) < 1.0E-5, "the correction only runs around the strike");
   }

   @Test
   void theHitStopHoldsAndTheWindupIsBehind() throws Exception {
      Animation clip = clip();
      double[] contact = fist(clip, FinalPunchRules.CONTACT, FinalPunchTargets.Kind.HUMAN);
      double[] launch = fist(clip, FinalPunchRules.LAUNCH, FinalPunchTargets.Kind.HUMAN);
      assertTrue(distance(contact, launch) < 0.005, "hit-stop: the fist does not move");
      double[] coil = fist(clip, FinalPunchRules.WINDUP_MAX, FinalPunchTargets.Kind.HUMAN);
      assertTrue(coil[0] < -0.3, "the fist is drawn far behind at the coil: " + coil[0]);
      double[] silence = fist(clip, FinalPunchRules.SILENCE + 6, FinalPunchTargets.Kind.HUMAN);
      double[] strike = fist(clip, FinalPunchRules.STRIKE, FinalPunchTargets.Kind.HUMAN);
      assertTrue(distance(silence, strike) < 0.01, "absolute stillness in the silence");
   }

   @Test
   void theTakeStartsAndEndsOnTheStance() throws Exception {
      Animation clip = clip();
      BakedGeoModel rest = stance();
      for (double seconds : new double[]{0, FinalPunchRules.LENGTH / 20.0}) {
         BakedGeoModel body = stance();
         ReinhardLayers.overlay(clip, body, seconds, 1);
         for (GeoBone bone : body.allBones()) {
            GeoBone ref = rest.getBone(bone.name);
            assertEquals(ref.rotX, bone.rotX, 0.03, bone.name + " rotX at " + seconds + " s");
            assertEquals(ref.rotY, bone.rotY, 0.03, bone.name + " rotY at " + seconds + " s");
            assertEquals(ref.rotZ, bone.rotZ, 0.03, bone.name + " rotZ at " + seconds + " s");
            assertEquals(ref.posY, bone.posY, 0.05, bone.name + " posY at " + seconds + " s");
         }
      }
   }

   @Test
   void theFallbackFistTableMatchesTheRig() throws Exception {
      Animation clip = clip();
      for (int t = 0; t <= FinalPunchRules.LENGTH; t += 6) {
         double[] rig = fist(clip, Math.max(t, 8), FinalPunchTargets.Kind.HUMAN);
         double[] table = FinalPunchFist.local(Math.max(t, 8));
         assertTrue(distance(rig, table) < 0.05, "fist table at " + t + ": rig " + java.util.Arrays.toString(rig) + " table " + java.util.Arrays.toString(table));
      }
   }

}
