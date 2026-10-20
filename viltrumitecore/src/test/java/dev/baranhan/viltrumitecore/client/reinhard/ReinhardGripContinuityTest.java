package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import java.util.HashMap;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

/** Check the FINAL solved arms, not just the pre-constraint clip channels. */
class ReinhardGripContinuityTest {
   @Test void wristLimitRemainsContinuousAtTheQuaternionAntipode() {
      Quaternionf previous=null;
      for(int i=0;i<=3600;i++) {
         var wrist=ReinhardGrip.limitedWrist(new Quaternionf().rotationY((float)Math.toRadians(i/10.0)));
         assertTrue(2*Math.acos(Math.min(1,Math.abs(wrist.w)))<=Math.toRadians(35.01));
         if(previous!=null) assertTrue(2*Math.acos(Math.min(1,Math.abs(wrist.dot(previous))))<Math.toRadians(1));
         previous=wrist;
      }
   }
   @Test void solvedSwordArmsDoNotFlipBetweenHighFrameRateSamples() throws Exception {
      var clips=ReinhardSecondPassTest.clips();
      var bad=new java.util.LinkedHashMap<String,String>();
      for(String name:new String[]{"light_punch","sheath_strike","chop_left","chop_right","cut_rain","reid_draw","reid_sheathe","world_cut","diagonal_world_cut","world_erasure"}) {
         var body=ReinhardSecondPassTest.body();var previous=new HashMap<String,Quaternionf>();
         int frames=(int)Math.ceil(clips.get(name).lengthSeconds()*240);
         for(int frame=0;frame<=frames;frame++) {
            double seconds=frame/240.0;
            AnimationController.seek(clips.get("idle_holy_drawn"),body,0);
            ReinhardLayers.overlay(clips.get(name),body,seconds,1);
            var phase=name.equals("reid_draw")?ReinhardPoses.ReidPhase.DRAWING:
               name.equals("reid_sheathe")?ReinhardPoses.ReidPhase.SHEATHING:ReinhardPoses.ReidPhase.DRAWN;
            ReinhardSword.equipment(body,phase,seconds*20,name.equals("sheath_strike"),seconds*20,1);
            for(String bone:new String[]{"left_arm","right_arm","left_forearm","right_forearm","left_hand","right_hand"}) {
               var q=ReinhardGeometry.frame(body.getBone(bone)).getUnnormalizedRotation(new Quaternionf()).normalize();
               var old=previous.put(bone,q);
               if(old!=null) {
                  double angle=2*Math.acos(Math.min(1,Math.abs(q.dot(old))));
                  if(angle>=Math.toRadians(20)) bad.putIfAbsent(name+" "+bone,
                     "frame="+frame+" discontinuity="+Math.toDegrees(angle));
               }
            }
         }
      }
      assertTrue(bad.isEmpty(),"Solved arm discontinuities: "+bad);
   }
}
