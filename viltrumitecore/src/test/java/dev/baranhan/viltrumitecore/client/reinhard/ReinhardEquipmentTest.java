package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.animation.*;
import dev.baranhan.viltrumitecore.client.anim.geo.*;
import java.nio.file.*;
import java.util.*;
import org.joml.*;
import java.lang.Math;
import org.junit.jupiter.api.Test;

class ReinhardEquipmentTest {
   static BakedGeoModel weapon(String name)throws Exception {
      return BakedGeoModel.parse(JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/viltrumitecore/geo/reinhard/"+name+".geo.json"))).getAsJsonObject());
   }
   @Test void fullWeaponMeshClearsHeadThroughoutDrawSheatheAndSwordActions()throws Exception {
      var clips=ReinhardSecondPassTest.clips();var blade=weapon("blade");var sheath=weapon("sheath");
      List<String> bad=new ArrayList<>();
      List<String> torsoHits=new ArrayList<>();
      for(String name:new String[]{"reid_draw","reid_sheathe","world_cut","world_erasure","cut_rain","sheath_strike","light_punch","chop_left","chop_right"}) {
         var clip=clips.get(name);
         for(double t=0;t<=clip.lengthSeconds()*20;t+=.5)for(boolean crouch:new boolean[]{false,true}) {
            var b=ReinhardSecondPassTest.body();AnimationController.seek(clips.get("idle_holy_drawn"),b,0);
            ReinhardLayers.overlay(clip,b,t/20,1);
            if(crouch)AnimationController.additive(clips.get("crouch"),b,0,1);
            var phase=name.equals("reid_draw")?ReinhardPoses.ReidPhase.DRAWING:name.equals("reid_sheathe")?ReinhardPoses.ReidPhase.SHEATHING:ReinhardPoses.ReidPhase.DRAWN;
            var frames=ReinhardSword.equipment(b,phase,t,name.equals("sheath_strike"),t,1);
            for(String bone:new String[]{"body","right_leg","left_leg","right_shin","left_shin"})
               if((ReinhardGeometry.intersectsBoneExactly(b.getBone(bone),blade,frames[1]) || ReinhardGeometry.intersectsBoneExactly(b.getBone(bone),sheath,frames[0])) && torsoHits.size()<35)
                  torsoHits.add(name+" tick="+t+" crouch="+crouch+" bone="+bone);
            // Test actual rotated head/hair cubes: their combined envelope includes empty space.
            if(ReinhardGeometry.intersectsBoneExactly(b.getBone("head"),blade,frames[1]) || ReinhardGeometry.intersectsBoneExactly(b.getBone("head"),sheath,frames[0])) {
               if(bad.size()<40)bad.add(name+" tick="+t+" crouch="+crouch);
            }
         }
      }
      assertTrue(torsoHits.isEmpty(),"Weapon/torso or leg collisions: "+torsoHits);
      assertTrue(bad.isEmpty(),"Actual sword/head mesh collisions: "+bad);
   }
   @Test void handOffUsesReachablePalmTargetsNotWeaponTeleports()throws Exception {
      var clips=ReinhardSecondPassTest.clips();
      for(double t=0;t<=120;t+=.5) {
         var b=ReinhardSecondPassTest.body();AnimationController.seek(clips.get("reid_draw"),b,t/20);
         var f=ReinhardSword.equipment(b,ReinhardPoses.ReidPhase.DRAWING,t,false,0,1);
         Vector3f hilt=f[1].transformPosition(new Vector3f(ReinhardGrip.HANDLE));
         if(t>=24 && t<=94)assertTrue(ReinhardGrip.point(b,true).distance(hilt)*16<.04,"left hand-off "+t);
         if(t>=94) {
            var right=ReinhardSword.interpolate(f[1],ReinhardSword.gripAt(f[1],0),ReinhardSword.twoHandWeight(ReinhardPoses.ReidPhase.DRAWING,t));
            assertTrue(ReinhardGrip.point(b).distance(right.transformPosition(new Vector3f(ReinhardGrip.HANDLE)))*16<.04,"right hand-off "+t);
         }
      }
   }
   @Test void bothDrawnPalmsReachDistinctHandlePointsWithoutIntersecting()throws Exception {
      var clips=ReinhardSecondPassTest.clips();List<String> bad=new ArrayList<>();
      for(String name:new String[]{"idle_holy_drawn","world_cut","world_erasure","cut_rain","light_punch","chop_left","chop_right"})
         for(double t=0;t<=clips.get(name).lengthSeconds()*20;t+=1) {
            var b=ReinhardSecondPassTest.body();AnimationController.seek(clips.get("idle_holy_drawn"),b,0);
            ReinhardLayers.overlay(clips.get(name),b,t/20,1);
            var f=ReinhardSword.equipment(b,ReinhardPoses.ReidPhase.DRAWN,0,false,0,1);
            for(boolean left:new boolean[]{false,true}) {
               var target=ReinhardSword.gripAt(f[1],left?-8.5F:0).transformPosition(new Vector3f(ReinhardGrip.HANDLE));
               if(ReinhardGrip.point(b,left).distance(target)*16>.05 && bad.size()<20)bad.add(name+" "+t+" left="+left+" residual="+ReinhardGrip.point(b,left).distance(target)*16);
            }
            if(ReinhardGeometry.intersectsBones(b.getBone("left_hand"),b.getBone("right_hand")) && bad.size()<20){if(bad.stream().noneMatch(x->x.startsWith(name+" overlapping")))bad.add(name+" overlapping palms at "+t);};
         }
      for(String gait:new String[]{"walk","run","air_rise","air_fall"})for(int tick=0;tick<=20;tick+=2)for(boolean crouch:new boolean[]{false,true}) {
         var b=ReinhardSecondPassTest.body();AnimationController.seek(clips.get("idle_holy_drawn"),b,0);
         ReinhardLayers.overlay(clips.get(gait),b,tick/20.0,1);
         if(crouch)AnimationController.additive(clips.get("crouch"),b,0,1);
         var f=ReinhardSword.equipment(b,ReinhardPoses.ReidPhase.DRAWN,0,false,0,1);
         for(boolean left:new boolean[]{false,true}) {
            var target=ReinhardSword.gripAt(f[1],left?-8.5F:0).transformPosition(new Vector3f(ReinhardGrip.HANDLE));
            assertTrue(ReinhardGrip.point(b,left).distance(target)*16<.05,"moving paired grip "+gait+" "+tick+" "+crouch);
         }
         assertFalse(ReinhardGeometry.intersectsBones(b.getBone("left_hand"),b.getBone("right_hand")),"moving palms overlap");
      }
      assertTrue(bad.isEmpty(),"Two-handed grip: "+bad);
   }
   @Test void ownerSelectedQuarterTurnShowsEdgeInFrontAndFaceAtSide()throws Exception {
      var clips=ReinhardSecondPassTest.clips();
      for(String name:new String[]{"idle_holy_drawn","world_cut","world_erasure"}) {
         var b=ReinhardSecondPassTest.body();
         AnimationController.seek(clips.get(name),b,name.equals("world_cut")?.6:name.equals("world_erasure")?.75:0);
         var f=ReinhardSword.equipment(b,ReinhardPoses.ReidPhase.DRAWN,0,false,0,1);
         var face=f[1].transformDirection(new Vector3f(0,0,1)).normalize();
         assertTrue(Math.abs(face.z)<.3,"owner-selected variant: front sees the edge: "+name+" "+face);
         assertTrue(Math.abs(face.x)>(name.equals("idle_holy_drawn")?.5:.9),"owner-selected variant: side sees the face: "+name);
      }
   }
   @Test void cameraAxialCorrectionPreservesScaleHiltAndLongitudinalAxis() {
      var original=new Matrix4f().translation(.2F,.3F,-.4F).rotateX(.7F)
         .scale(ReinhardSword.SWORD_SCALE,ReinhardSword.SWORD_SCALE*ReinhardSword.LENGTH_SCALE,ReinhardSword.SWORD_SCALE);
      var corrected=ReinhardSword.rollDrawnBlade(original,ReinhardPoses.ReidPhase.DRAWN,0);
      assertTrue(original.transformDirection(new Vector3f(0,1,0)).distance(corrected.transformDirection(new Vector3f(0,1,0)))<1E-6);
      assertTrue(original.transformPosition(new Vector3f(ReinhardGrip.HANDLE)).distance(corrected.transformPosition(new Vector3f(ReinhardGrip.HANDLE)))<1E-6);
      assertTrue(original.getScale(new Vector3f()).distance(corrected.getScale(new Vector3f()))<1E-6);
      assertTrue(original.equals(ReinhardSword.rollDrawnBlade(original,ReinhardPoses.ReidPhase.SHEATHED,0),1E-6F));
      assertEquals(0,ReinhardSword.drawnRoll(ReinhardPoses.ReidPhase.DRAWING,64));
      assertEquals(ReinhardSword.drawnRoll(ReinhardPoses.ReidPhase.DRAWING,88),ReinhardSword.drawnRoll(ReinhardPoses.ReidPhase.SHEATHING,(120-88)/6.0));
      assertEquals((float)Math.PI/2,ReinhardSword.drawnRoll(ReinhardPoses.ReidPhase.DRAWN,0),1E-6);
   }
   @Test void reservedHiltPalmDoesNotAlsoRenderASlotItem() {
      for(var phase:ReinhardPoses.ReidPhase.values())
         assertFalse(ReinhardPoses.drawsHeldItem(net.minecraft.world.entity.HumanoidArm.LEFT,phase,null,dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode.HOLY_SWORD));
      for(var mode:new dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode[]{dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode.RESTRAINED,dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode.KNIGHT})
         assertTrue(ReinhardPoses.drawsHeldItem(net.minecraft.world.entity.HumanoidArm.LEFT,ReinhardPoses.ReidPhase.SHEATHED,null,mode));
   }
   @Test void authoredArmsDoNotGoThroughHead()throws Exception {
      var clips=ReinhardSecondPassTest.clips();Map<String,String> bad=new LinkedHashMap<>();
      for(var entry:clips.entrySet())for(double t=0;t<=entry.getValue().lengthSeconds()*20;t+=1) {
         var b=ReinhardSecondPassTest.body();AnimationController.seek(entry.getValue(),b,t/20);
         var phase=entry.getKey().equals("reid_draw")?ReinhardPoses.ReidPhase.DRAWING:entry.getKey().equals("reid_sheathe")?ReinhardPoses.ReidPhase.SHEATHING:
            Set.of("world_cut","world_erasure","cut_rain","idle_holy_drawn","chop_left","chop_right").contains(entry.getKey())?ReinhardPoses.ReidPhase.DRAWN:ReinhardPoses.ReidPhase.SHEATHED;
         if(phase!=ReinhardPoses.ReidPhase.SHEATHED || Set.of("idle_holy","sheath_strike").contains(entry.getKey()))ReinhardSword.equipment(b,phase,t,entry.getKey().equals("sheath_strike"),t,1);
         for(String limb:new String[]{"left_hand","right_hand","left_forearm","right_forearm"})
            if(ReinhardGeometry.intersectsBones(b.getBone("head"),b.getBone(limb)) && bad.size()<50)bad.putIfAbsent(entry.getKey()+" "+limb," tick="+t+" arm="+b.getBone(limb.startsWith("left")?"left_arm":"right_arm").rotX+","+b.getBone(limb.startsWith("left")?"left_arm":"right_arm").rotY+","+b.getBone(limb.startsWith("left")?"left_arm":"right_arm").rotZ);
      }
      assertTrue(bad.isEmpty(),"Arm/head intersections: "+bad);
   }
   @Test void sheathedHiltIsInLeftGloveAndMountFollowsBodyAtAllGaitPhases()throws Exception {
      var clips=ReinhardSecondPassTest.clips();
      for(String name:new String[]{"idle_holy","walk","run","air_rise","air_fall"})for(int t=0;t<=20;t++)for(boolean crouch:new boolean[]{false,true}) {
         var b=ReinhardSecondPassTest.body();AnimationController.seek(clips.get("idle_holy"),b,0);
         ReinhardLayers.overlay(clips.get(name),b,t/20.0,1);
         if(crouch)AnimationController.additive(clips.get("crouch"),b,0,1);
         var f=ReinhardSword.equipment(b,ReinhardPoses.ReidPhase.SHEATHED,0,false,0,1);
         assertEquals(.574F,f[1].getScale(new Vector3f()).x,1E-5);
         Vector3f rest=f[1].transformPosition(new Vector3f(ReinhardGrip.HANDLE)).add(0,1.3F/16,0);
         assertTrue(ReinhardGrip.point(b,true).distance(rest)*16<.035,"resting palm "+name+" "+t);
         var wrist=b.getBone("left_hand");
         assertEquals(0,wrist.rotX,1E-6);assertEquals(0,wrist.rotY,1E-6);assertEquals(0,wrist.rotZ,1E-6);

      }
   }
}
