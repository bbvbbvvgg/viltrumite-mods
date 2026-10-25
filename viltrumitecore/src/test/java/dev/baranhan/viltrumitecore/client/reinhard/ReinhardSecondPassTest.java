package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.*;
import dev.baranhan.viltrumitecore.client.anim.geo.*;
import dev.baranhan.viltrumitecore.hero.reinhard.*;
import java.nio.file.*;
import java.util.Map;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ReinhardSecondPassTest {
   static BakedGeoModel body() throws Exception {return BakedGeoModel.parse(JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/viltrumitecore/geo/reinhard/reinhard.geo.json"))).getAsJsonObject());}
   static Map<String,Animation> clips() throws Exception {return AnimationParser.parse(JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/viltrumitecore/animations/reinhard/reinhard.animation.json"))).getAsJsonObject());}
   @Test void absentChannelsDoNotEraseCrouchOrScale() throws Exception {
      var b=body();var torso=b.getBone("body");torso.posY=-3;torso.posZ=1;torso.scaleX=1.1F;
      ReinhardLayers.overlay(clips().get("combo_1"),b,.2,1);
      assertEquals(-3,torso.posY);assertEquals(1,torso.posZ);assertEquals(1.1F,torso.scaleX);
   }
   @Test void torsoHierarchyHasWaistPivotAndIntactChildren() throws Exception {
      var b=body();var torso=b.getBone("body");assertEquals(12,torso.pivotY);
      for(String n:new String[]{"head","right_arm","left_arm"})assertSame(torso,b.getBone(n).parent);
      assertSame(b.getBone("right_leg"),b.getBone("right_shin").parent);
      assertFalse(b.getBone("right_shin").cubes.isEmpty());
   }
   @Test void measuredGripContactSurvivesWalkRunCrouchAndTorsoTurns() throws Exception {
      var clips=clips();
      for(String gait:new String[]{"walk","run"})for(int i=0;i<=20;i++)for(boolean crouch:new boolean[]{false,true}) {
         var b=body();AnimationController.seek(clips.get("idle_holy"),b,0);
         ReinhardLayers.overlay(clips.get(gait),b,i/20.0,1);
         b.getBone("body").rotY=(float)Math.sin(i*.5)*.4F;
         if(crouch)AnimationController.additive(clips.get("crouch"),b,0,1);
         var hip=ReinhardSword.hip(b);float error=ReinhardGrip.solve(b,hip,1,true);
         assertTrue(error<.03,"grip residual px "+error+" in "+gait+" frame "+i+" crouch "+crouch);
         assertTrue(ReinhardGrip.point(b,true).distance(hip.transformPosition(new Vector3f(ReinhardGrip.HANDLE)))*16<.03);
      }
   }
   @Test void thinEdgeFacesOutWithDownwardSheathAxis() throws Exception {
      var m=ReinhardSword.hip(body());
      var broad=m.transformDirection(new Vector3f(1,0,0)).normalize();
      var thin=m.transformDirection(new Vector3f(0,0,1)).normalize();
      var axis=m.transformDirection(new Vector3f(0,1,0)).normalize();
      assertTrue(Math.abs(thin.x)>.90);assertTrue(Math.abs(broad.x)<.34);
      assertTrue(Math.abs(axis.y)>.55);assertTrue(Math.abs(axis.z)<.79);
   }
   @Test void kickExtendsAtKneeRatherThanSwingingStraightLegThroughAnArc() throws Exception {
      var b=body();var clips=clips();AnimationController.seek(clips.get("kick_toss"),b,4.8/20);
      assertTrue(Math.abs(b.getBone("right_shin").rotX)>Math.toRadians(90));
      float hip=b.getBone("right_leg").rotX;AnimationController.seek(clips.get("kick_toss"),b,6.0/20);
      assertTrue(Math.abs(b.getBone("right_shin").rotX)<.01);
      assertTrue(Math.abs(b.getBone("right_leg").rotX-hip)<Math.toRadians(20));
   }
   @Test void motionWeightsAreContinuousAndRepeatedPassDoesNotAdvanceTime() {
      Object p=new Object();ReinhardMotion.sample(p,false,false,false,false,0);
      float[] a=ReinhardMotion.sample(p,true,true,true,true,10_000_000);
      float[] b=ReinhardMotion.sample(p,true,true,true,true,10_000_000);
      assertArrayEquals(a,b);for(float v:a)assertTrue(v>=0&&v<.2);
      ReinhardMotion.reset(p);
   }
   @Test void combinedCombatCrouchKeepsWaistConnectedAndAllThreeStanceWeights() throws Exception {
      var field=AnimCache.class.getDeclaredField("animationFiles");field.setAccessible(true);Object previous=field.get(null);
      try {field.set(null,Map.of(ReinhardAssets.ANIMATIONS,clips()));var b=body();
         for(int i=0;i<10;i++) {
            var pose=new ReinhardPoses.Pose("idle_knight",i*.03,1,i*.05,new ReinhardPoses.Clip("combo_1",i*.05),ReinhardPoses.BodyState.CROUCH,0);
            ReinhardRenderer.applyClips(b,pose,StanceBlend.mix(new float[]{.2F,.3F,.5F},ReinhardMode.HOLY_SWORD),.3F,0,0,new ParryPulse.Sample(0,0),null,null,new float[]{1,.5F,0,0});
            assertEquals(0,b.getBone("body").posY);assertEquals(0,b.getBone("head").posY);assertEquals(0,b.getBone("right_arm").posY);
            assertTrue(Float.isFinite(b.getBone("body").rotX));
         }
      }finally{field.set(null,previous);}
   }
   @Test void wristEulerReconstructionIsExactIncludingCompoundRotations() {
      for(float x=-3;x<=3;x+=.3F)for(float y=-1.5F;y<=1.5F;y+=.3F)for(float z=-3;z<=3;z+=.3F) {
         var q=new org.joml.Quaternionf().rotationZYX(z,y,x);
         var e=ReinhardGrip.euler(q);
         assertTrue(Math.abs(q.dot(new org.joml.Quaternionf().rotationZYX(e.z,e.y,e.x)))>.99999);
      }
   }

   @Test void firstPersonReidFrameUsesActualGloveNotNominalVanillaAnchor() throws Exception {
      var arms=BakedGeoModel.parse(JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/viltrumitecore/geo/reinhard/reinhard_arms.geo.json"))).getAsJsonObject());
      arms.getBone("right_forearm").rotX=.7F;
      var stack=new com.mojang.blaze3d.vertex.PoseStack();
      var elbow=arms.getBone("right_forearm");ReinhardRenderer.boneFrame(stack,elbow);
      Vector3f centre=stack.last().pose().transformPosition(new Vector3f((5.3281F-elbow.pivotX)/16,(12.6993F-elbow.pivotY)/16,-elbow.pivotZ/16));
      assertTrue(centre.distance(ReinhardGrip.point(arms))<1E-6);
   }

}
