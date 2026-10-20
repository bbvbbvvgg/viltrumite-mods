package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Samples production clips/composition, not a second interpolation implementation. */
class ReinhardContinuityTest {
   private static Map<String, Animation> clips() throws Exception {
      return AnimationParser.parse(JsonParser.parseString(Files.readString(Path.of(
         "src/main/resources/assets/viltrumitecore/animations/reinhard/reinhard.animation.json"))).getAsJsonObject());
   }

   @Test void gaitDoesNotStopAtIntermediateKeys() throws Exception {
      for (String name : new String[]{"walk", "run"}) {
         var leg = clips().get(name).boneAnimations().get("right_leg").rotX();
         double amplitude = name.equals("walk") ? 32 : 48;
         for (double t : new double[]{.1, .2, .3, .4, .6, .7, .8, .9}) {
            double h = .0001;
            double velocity = Math.toDegrees(leg.sample(t + h) - leg.sample(t - h)) / (2 * h);
            double expected = amplitude * 2 * Math.PI * Math.sin(2 * Math.PI * t);
            assertEquals(expected, velocity, Math.abs(expected) * .03, name + " key " + t);
         }
      }
   }

   @Test void gaitTracksCosineAndClosesItsLoop() throws Exception {
      for (String name : new String[]{"walk", "run"}) {
         var leg = clips().get(name).boneAnimations().get("right_leg").rotX();
         double amplitude = name.equals("walk") ? 32 : 48;
         for (int frame = 0; frame <= 600; frame++) {
            double t = frame / 600.0;
            assertEquals(-amplitude * Math.cos(2 * Math.PI * t),
               Math.toDegrees(leg.sample(t)), .04, name + " frame " + frame);
         }
         assertEquals(leg.sample(0), leg.sample(1), 1e-6);
      }
   }

   @Test void reflectedAndCaughtProjectilesReturnToGuardWithoutIdleFlash() throws Exception {
      var field = AnimCache.class.getDeclaredField("animationFiles");
      field.setAccessible(true);
      Object previous = field.get(null);
      try {
         field.set(null, Map.of(ReinhardAssets.ANIMATIONS, clips()));
         for (var ability : new ReinhardAbility[]{ReinhardAbility.CATCH, ReinhardAbility.REFLECT}) {
            var mode = ability == ReinhardAbility.CATCH ? ReinhardMode.RESTRAINED : ReinhardMode.KNIGHT;
            float[] before = windowPose(ability, mode, 6 - .0001);
            float[] after = windowPose(ability, mode, 6);
            assertArrayEquals(before, after, .001F, ability + " snap -> ready");
         }
      } finally {
         field.set(null, previous);
      }
   }

   private static float[] windowPose(ReinhardAbility ability, ReinhardMode mode, double ticks) throws Exception {
      var body = ReinhardSecondPassTest.body();
      var pose = new ReinhardPoses.Pose(ReinhardPoses.stance(mode), 0, 0, 0,
         ReinhardClips.clip(ability, ReinhardClips.PHASE_SNAP, ticks), ReinhardPoses.BodyState.STAND, 0);
      ReinhardRenderer.applyClips(body, pose, new StanceBlend.Mix(mode, mode, 1),
         mode == ReinhardMode.KNIGHT ? 1 : 0, 0, 0, new ParryPulse.Sample(0, 0), null, null);
      var arm = body.getBone("right_arm");
      var elbow = body.getBone("right_forearm");
      var head = body.getBone("head");
      return new float[]{arm.rotX, arm.rotY, arm.rotZ, elbow.rotX, elbow.rotY, elbow.rotZ, head.rotX, head.rotY};
   }

   @Test void cameraDrawAndSheatheMeetNeutralWithoutDroppingTheArm() {
      assertEquals(ReinhardFirstPersonPose.Offset.NONE,
         ReinhardFirstPersonPose.reid(ReinhardPoses.ReidPhase.DRAWING, 120));
      assertEquals(ReinhardFirstPersonPose.Offset.NONE,
         ReinhardFirstPersonPose.reid(ReinhardPoses.ReidPhase.SHEATHING, 0));
      assertTrue(Math.abs(ReinhardFirstPersonPose.reid(ReinhardPoses.ReidPhase.DRAWING, 100).pitch()) > 20);
      assertTrue(Math.abs(ReinhardFirstPersonPose.reid(ReinhardPoses.ReidPhase.SHEATHING, 5).pitch()) > 20);
   }

   @Test void reidBodyFadeLeavesTheAuthoredEquipmentFrameUntouched() throws Exception {
      var animations = clips();
      for (String name : new String[]{ReinhardClips.REID_DRAW, ReinhardClips.REID_SHEATHE}) {
         var clip = animations.get(name);
         double seconds = name.equals(ReinhardClips.REID_DRAW) ? 0 : clip.lengthSeconds();
         var body = ReinhardSecondPassTest.body();
         var exact = ReinhardSecondPassTest.body();
         dev.baranhan.viltrumitecore.client.anim.animation.AnimationController.seek(animations.get("idle_holy"),body,.4);
         float shoulder=body.getBone("right_arm").rotX, elbow=body.getBone("right_forearm").rotX;
         dev.baranhan.viltrumitecore.client.anim.animation.AnimationController.seek(clip,exact,seconds);
         float weight=ReinhardPlayback.reidWeight(name.equals(ReinhardClips.REID_DRAW),seconds,clip.lengthSeconds());
         assertEquals(0,weight);
         ReinhardLayers.overlay(clip,body,seconds,weight,"reid_grip");
         assertEquals(shoulder,body.getBone("right_arm").rotX,1e-6);
         assertEquals(elbow,body.getBone("right_forearm").rotX,1e-6);
         var actual=body.getBone("reid_grip");var target=exact.getBone("reid_grip");
         assertArrayEquals(new float[]{target.rotX,target.rotY,target.rotZ,target.posX,target.posY,target.posZ},
            new float[]{actual.rotX,actual.rotY,actual.rotZ,actual.posX,actual.posY,actual.posZ},1e-6F);
      }
   }

   @Test void windowEnvelopeUsesRemainingServerDurationAndKeepsContactsAtFullWeight() {
      assertEquals(1,ReinhardPlayback.window(.05,1,true));
      assertEquals(1,ReinhardPlayback.window(.15,1,false));
      assertEquals(0,ReinhardPlayback.window(.5,.5,true));
      assertTrue(ReinhardPlayback.window(.45,.5,true)<1);
      assertEquals(1,ReinhardPlayback.window(.45,1,true));
   }

   @Test void cameraElbowsHaveTheSameSnapToGuardContinuity() throws Exception {
      var field=AnimCache.class.getDeclaredField("animationFiles");field.setAccessible(true);
      Object previous=field.get(null);
      try {
         var animations=clips();field.set(null,Map.of(ReinhardAssets.ANIMATIONS,animations));
         for (var ability:new ReinhardAbility[]{ReinhardAbility.CATCH,ReinhardAbility.REFLECT}) {
            float[] angles=new float[2];
            for(int i=0;i<2;i++) {
               double ticks=i==0?6-.0001:6;
               var selected=ReinhardClips.clip(ability,ReinhardClips.PHASE_SNAP,ticks);
               var body=ReinhardSecondPassTest.body();
               body.getBone("right_forearm").rotX=(float)Math.toRadians(7);
               assertTrue(ReinhardRenderer.applyWindow(body,animations.get(selected.animation()),selected.seconds(),20,true));
               angles[i]=body.getBone("right_forearm").rotX;
               assertEquals(0,body.getBone("right_arm").rotX,1e-6,"FP must not apply the shoulder clip twice");
            }
            assertEquals(angles[0],angles[1],.001);
         }
      } finally {field.set(null,previous);}
   }

   @Test void takeoffAndLandingCrossFadeWithoutAnInstantGaitCutAtAnyFrameRate() {
      for(int fps:new int[]{30,60,144,240}) {
         Object owner=new Object();
         float[] grounded=ReinhardMotion.sample(owner,false,false,false,false,0);
         float[] airborne=ReinhardMotion.sample(owner,false,false,true,true,0);
         assertEquals(1,ReinhardMotion.ground(grounded));
         assertEquals(1,ReinhardMotion.ground(airborne),"takeoff timestamp retains the old gait");
         for(int frame=1;frame<=fps;frame++) {
            long now=(long)(frame*1e9/fps);
            airborne=ReinhardMotion.sample(owner,false,false,true,true,now);
            assertEquals(1,ReinhardMotion.ground(airborne)+airborne[2]+airborne[3],1e-6);
            assertArrayEquals(airborne,ReinhardMotion.sample(owner,false,false,true,true,now));
         }
         assertTrue(ReinhardMotion.ground(airborne)<1e-5);
         assertEquals(ReinhardMotion.ground(airborne),
            ReinhardMotion.ground(ReinhardMotion.sample(owner,false,false,false,false,1_000_000_000L)),1e-6);
         float[] landing=ReinhardMotion.sample(owner,false,false,false,false,1_050_000_000L);
         assertTrue(ReinhardMotion.ground(landing)>0 && ReinhardMotion.ground(landing)<1);
         ReinhardMotion.reset(owner);
      }
   }
}