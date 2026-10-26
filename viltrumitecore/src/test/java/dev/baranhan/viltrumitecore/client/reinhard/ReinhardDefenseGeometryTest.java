package dev.baranhan.viltrumitecore.client.reinhard;
import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.*;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import java.lang.Math;
import org.junit.jupiter.api.Test;

class ReinhardDefenseGeometryTest {
   @Test void fullWeightFinalPalmMeetsAuthoritativeTargetAcrossThreatAngles()throws Exception {
      var clips=ReinhardSecondPassTest.clips();
      for(var kind:DefenseReaction.Kind.values())if(kind!=DefenseReaction.Kind.NONE && kind!=DefenseReaction.Kind.EVADE)for(boolean left:new boolean[]{false,true})for(int yaw:new int[]{-55,0,55})for(int pitch:new int[]{-45,0,45}) {
         var b=ReinhardSecondPassTest.body();AnimationController.seek(clips.get("idle_holy_drawn"),b,0);
         ReinhardSword.equipment(b,ReinhardPoses.ReidPhase.DRAWN,0,false,0,1);
         Vec3 p=DefenseContact.local(left,yaw,pitch);
         ReinhardDefense.apply(b,kind,1,p,left);
         assertTrue(ReinhardGrip.point(b,left).distance(new Vector3f((float)p.x,(float)p.y,(float)p.z))*16<.04,"final palm "+left+" "+yaw+" "+pitch);
         var hand=b.getBone(left?"left_hand":"right_hand");
         assertFalse(ReinhardGeometry.intersectsBones(hand,b.getBone("head")),"palm must clear actual head "+left+" "+yaw+" "+pitch);
         assertFalse(ReinhardGeometry.intersectsBones(b.getBone(left?"left_forearm":"right_forearm"),b.getBone("head")),"forearm must clear head "+left+" "+yaw+" "+pitch);
      }
   }
   @Test void freeHandDefensePreservesWeaponHandAndBladeFrameDuringSwordStroke()throws Exception {
      var clips=ReinhardSecondPassTest.clips();
      for(String clip:new String[]{"chop_right","chop_left","saw_sphere","world_cut"})for(int tick=0;tick<=20;tick++) {
         var b=ReinhardSecondPassTest.body();AnimationController.seek(clips.get("idle_holy_drawn"),b,0);
         ReinhardLayers.overlay(clips.get(clip),b,tick/20.0,1);
         var weapon=ReinhardSword.equipment(b,ReinhardPoses.ReidPhase.DRAWN,0,false,0,1);
         Matrix4f rightFrame=ReinhardGrip.hand(b);Vector3f right=ReinhardGrip.point(b);Matrix4f blade=new Matrix4f(weapon[1]);
         ReinhardDefense.apply(b,DefenseReaction.Kind.PROJECTILE,1,DefenseContact.local(true,0,0),true);
         assertTrue(right.distance(ReinhardGrip.point(b))<1E-6,"weapon palm changed "+clip+tick);
         assertEquals(rightFrame,ReinhardGrip.hand(b),"weapon hand orientation changed "+clip+tick);assertEquals(blade,weapon[1]);
      }
   }
   @Test void finalJointRecoveryIsContinuousAt240HzWithoutEulerBranchFlips()throws Exception {
      var clips=ReinhardSecondPassTest.clips();Quaternionf[] previous=null;float max=0;
      for(int frame=0;frame<=240;frame++) {
         var b=ReinhardSecondPassTest.body();AnimationController.seek(clips.get("idle_knight"),b,frame/240.0);
         float t=frame/240F;float w=t<.5F?(float)(1-Math.exp(-25*t)):(float)Math.exp(-16*(t-.5));
         ReinhardDefense.apply(b,DefenseReaction.Kind.ARROW,w,DefenseContact.local(false,25,-15),false);
         Quaternionf[] q=new Quaternionf[3];int i=0;
         for(String n:new String[]{"right_arm","right_forearm","right_hand"}) {
            var stack=new com.mojang.blaze3d.vertex.PoseStack();ReinhardRenderer.boneFrame(stack,b.getBone(n));
            q[i]=stack.last().pose().getUnnormalizedRotation(new Quaternionf()).normalize();
            if(previous!=null)max=Math.max(max,(float)Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(q[i].dot(previous[i]))))));i++;
         }
         previous=q;
      }
      assertTrue(max<12,"final joint discontinuity at 240Hz: "+max);
   }
   @Test void fpEvadeMovesBothHandsAndFreeHandDoesNotStealAttack() {
      var base=new ReinhardFirstPersonPose.Offset(.1F,.2F,.3F,10,20,30);
      assertEquals(base,ReinhardDefense.offset(DefenseReaction.Kind.BEAM,true,1,base,false));
      var dodge=ReinhardDefense.offset(DefenseReaction.Kind.EVADE,true,1,base,false);
      assertNotEquals(base,dodge);assertNotEquals(dodge,ReinhardDefense.offset(DefenseReaction.Kind.PALM,true,1,base,true));
      assertEquals(base,ReinhardDefense.offset(DefenseReaction.Kind.EVADE,false,0,base,true));
   }
   @Test void threatSwitchRecoversBeforeChangingHandAndBeamDoesNotRestart() {
      var s=new ReinhardDefense.State();var d=new DefenseReaction();d.contact(DefenseReaction.Kind.BEAM,0,0);d.aim(false);
      long n=1_000_000_000L;for(int i=0;i<120;i++)s.advance(d.flags(),d.palm(),n+i*8_333_333L);
      long start=s.start;assertTrue(s.weight>.99);
      s.advance(d.flags(),d.palm(),n+1_100_000_000L);assertEquals(start,s.start);
      d.aim(true);s.advance(d.flags(),d.palm(),n+1_108_333_333L);assertFalse(s.left);
      for(int i=0;i<120;i++)s.advance(d.flags(),d.palm(),n+1_116_666_666L+i*8_333_333L);
      assertTrue(s.left);assertTrue(s.weight>.99);
   }
   @Test void recordedContactFollowsTheHandTheRecoveryStillDraws() {
      var d=new DefenseReaction();d.contact(DefenseReaction.Kind.BEAM,0,0);d.aim(false);
      assertFalse(ReinhardDefense.renderedLeft(null,d.flags()),"no state yet: the snapshot decides");
      var s=new ReinhardDefense.State();
      long n=1_000_000_000L;for(int i=0;i<120;i++)s.advance(d.flags(),d.palm(),n+i*8_333_333L);
      assertFalse(ReinhardDefense.renderedLeft(s,d.flags()));
      // The beam continues while a Reid action starts: the snapshot already names the other hand.
      d.aim(true);assertTrue(DefenseReaction.leftHand(d.flags()));
      long t=n+1_008_333_333L;
      s.advance(d.flags(),d.palm(),t);
      assertTrue(s.weight>.02F);
      assertFalse(ReinhardDefense.renderedLeft(s,d.flags()),"the fading old hand keeps the contact");
      for(int i=1;i<120 && !s.left;i++)s.advance(d.flags(),d.palm(),t+i*8_333_333L);
      assertTrue(s.left);
      assertTrue(ReinhardDefense.renderedLeft(s,d.flags()),"the new hand takes over once adopted");
   }
   @Test void finalFpContactKeepsTheSamePixelAcrossWorldAndHandFov() {
      for(float fov:new float[]{40,70,110})for(float aspect:new float[]{4/3F,16/9F}) {
         var hand=new Matrix4f().perspective((float)Math.toRadians(70),aspect,.05F,1000);
         var world=new Matrix4f().perspective((float)Math.toRadians(fov),aspect,.05F,1000);
         Vec3 palm=new Vec3(.3,-.2,-.6);
         Vec3 corrected=dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts.reproject(palm,hand.m00(),hand.m11(),world.m00(),world.m11());
         var a=hand.transformProject(new Vector3f((float)palm.x,(float)palm.y,(float)palm.z));
         var b=world.transformProject(new Vector3f((float)corrected.x,(float)corrected.y,(float)corrected.z));
         assertEquals(a.x,b.x,1E-5);assertEquals(a.y,b.y,1E-5);
      }
   }

 @org.junit.jupiter.api.Test void audioTickGuiProjectionCannotCorruptTheNextRenderedContact() {
   var perspective=new org.joml.Matrix4f().perspective((float)Math.toRadians(80),16F/9,.05F,512);
   dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts.worldProjection(perspective);
   var point=new net.minecraft.world.phys.Vec3(-.7,-.4,-1.3);
   var expected=dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts.reprojectWorld(point,.8F,1.4F);
   dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts.worldProjection(new org.joml.Matrix4f().ortho(0,960,540,0,1000,21000));
   assertEquals(expected,dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts.reprojectWorld(point,.8F,1.4F));
 }
 @org.junit.jupiter.api.Test void minecraftPositiveZCameraAndNegativeZHandFramesAgreeAtEveryYawPitch() {
   for(int yaw=0;yaw<360;yaw+=30)for(int pitch:new int[]{-60,0,60}) {
      var q=new org.joml.Quaternionf().rotationYXZ((float)Math.toRadians(-yaw),(float)Math.toRadians(pitch),0);
      var view=new net.minecraft.world.phys.Vec3(-.7,-.4,-1.3);
      var world=dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts.viewToWorld(view,q);
      var back=dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts.worldToView(world,q);
      assertTrue(view.distanceTo(back)<1E-5);
      var forward=q.transform(new org.joml.Vector3f(0,0,1));
      assertEquals(1.3,world.dot(new net.minecraft.world.phys.Vec3(forward.x,forward.y,forward.z)),1E-5,"Contact must be in front, not behind the camera");
   }
 }
 @org.junit.jupiter.api.Test void firstPersonArrowContactClearsTheRestingReidAcrossTheLowerView() {
   var arrow=ReinhardDefense.offset(DefenseReaction.Kind.ARROW,true,1,ReinhardFirstPersonPose.Offset.NONE,true);
   var beam=ReinhardDefense.offset(DefenseReaction.Kind.BEAM,true,1,ReinhardFirstPersonPose.Offset.NONE,true);
   assertTrue(arrow.y()-beam.y()>.3F,"Raise the catch above the low resting blade; do not hide the shaft behind it");
 }
}
