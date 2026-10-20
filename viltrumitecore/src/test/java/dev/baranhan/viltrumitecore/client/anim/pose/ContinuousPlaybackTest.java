package dev.baranhan.viltrumitecore.client.anim.pose;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import org.junit.jupiter.api.Test;

class ContinuousPlaybackTest {
   @Test void jitteredSnapshotsNeverRewindOrJumpAtAnyFrameRate() {
      for(int fps:new int[]{30,60,144,240}) {
         var clock=new ActionClock();double previous=0,step=20.0/fps;
         for(int frame=0;frame<fps*2;frame++) {
            double now=frame*step;
            int elapsed=(int)(Math.floor(now/2)*2); // 100ms delivery, not every rendered frame
            double t=clock.sample(1,0,elapsed,60,now);
            assertTrue(t>=previous-1e-9,"rewind at "+fps+"fps");
            assertTrue(t-previous<=step*1.251,"packet caused a pose jump");
            assertTrue(t<=elapsed+3+1e-9,"unbounded prediction");
            assertEquals(t,clock.sample(1,0,elapsed,60,now),1e-9,"second view/shadow pass");
            previous=t;
         }
      }
   }
   @Test void stallPauseCancelPhaseRecastAndLateJoinAreBounded() {
      var c=new ActionClock();assertEquals(8,c.sample(1,0,8,20,100));
      double t=c.sample(1,0,8,20,100.9);
      assertEquals(t,c.sample(1,0,8,20,100.9));
      assertEquals(11,c.sample(1,0,8,20,200));
      assertEquals(0,c.sample(1,1,0,10,201));
      c.sample(1,1,5,10,205);assertEquals(0,c.sample(1,1,0,10,206));
      assertEquals(0,c.sample(-1,0,0,0,207));
      assertEquals(4,c.sample(1,1,4,4,208));
      assertEquals(4,c.sample(1,1,4,4,300));
      c.reset();assertEquals(0,c.sample(1,1,0,10,300));
   }
   @Test void continuousKeysAreExactBoundedAndCarryVelocityThroughIntermediateKeys() {
      float[][] keys={PoseKeys.key(0,0,0,0),PoseKeys.key(1,20,0,0),PoseKeys.key(3,30,0,0),PoseKeys.under(5)};
      for(int i=0;i<keys.length;i++) assertEquals(i==3?0:keys[i][1],PoseKeys.sampleContinuous(keys,keys[i][0],0,0,false),1e-5);
      float h=.001F;
      float at=PoseKeys.sampleContinuous(keys,1,0,0,false);
      double left=(at-PoseKeys.sampleContinuous(keys,1-h,0,0,false))/h;
      double right=(PoseKeys.sampleContinuous(keys,1+h,0,0,false)-at)/h;
      assertTrue(left>5,"not a stop at every intermediate key");assertEquals(left,right,.1);
      for(int f=0;f<=1000;f++) {
         float v=PoseKeys.sampleContinuous(keys,f/200F,0,0,false);
         assertTrue(v>=0 && v<=30,"overshoot at frame "+f);
      }
   }
   @Test void parserOptInPreservesLegacyAndIntentionalPrePostJumps() {
      var clips=AnimationParser.parse(JsonParser.parseString("""
         {"animations":{"c":{"bones":{"b":{"position":{
         "0":{"vector":[0,0,0],"lerp_mode":"monotone"},
         "1":{"vector":[20,0,0],"lerp_mode":"monotone"},
         "3":{"vector":[30,0,0],"lerp_mode":"monotone"}}}}},
         "legacy":{"bones":{"b":{"position":{"0":[0,0,0],"1":[20,0,0],"3":[30,0,0]}}}},
         "jump":{"bones":{"b":{"position":{"0":{"vector":[0,0,0],"lerp_mode":"monotone"},
         "1":{"pre":[10,0,0],"post":[40,0,0],"lerp_mode":"monotone"},"2":[50,0,0]}}}}}}
         """).getAsJsonObject());
      var cubic=clips.get("c").boneAnimations().get("b").posX();
      assertEquals(20,cubic.sample(1));assertEquals(30,cubic.sample(3));
      assertTrue(cubic.sample(.5)<10); // carried velocity at next key, unlike zero-end tangent
      var legacy=clips.get("legacy").boneAnimations().get("b").posX();
      assertEquals(10,legacy.sample(.5));assertEquals(25,legacy.sample(2));
      var jump=clips.get("jump").boneAnimations().get("b").posX();
      assertEquals(10,jump.sample(.99999),.001);assertEquals(40,jump.sample(1),.001);
   }
}
