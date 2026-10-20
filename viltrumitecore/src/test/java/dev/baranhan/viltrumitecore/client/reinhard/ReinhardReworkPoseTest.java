package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ReinhardReworkPoseTest {
   private static BakedGeoModel model() throws Exception {
      return BakedGeoModel.parse(JsonParser.parseString(Files.readString(Path.of(
         "src/main/resources/assets/viltrumitecore", ReinhardAssets.BODY.getPath()))).getAsJsonObject());
   }
   @Test void neutralElbowPreservesVanillaItemAnchor() throws Exception {
      var body=model();
      for (String name : new String[]{"left_arm","right_arm"}) {
         var shoulder=body.getBone(name);
         var a=new PoseStack(); var b=new PoseStack();
         ReinhardRenderer.boneFrame(a,shoulder);
         ReinhardRenderer.handFrame(b,body,shoulder);
         for (int row=0;row<4;row++) for (int col=0;col<4;col++)
            assertEquals(a.last().pose().get(col,row),b.last().pose().get(col,row),1e-6);
      }
   }
   @Test void knightHandIsBehindTorsoAndWalkStillCyclesInActualComposition() throws Exception {
      var animations=AnimationParser.parse(JsonParser.parseString(Files.readString(Path.of(
         "src/main/resources/assets/viltrumitecore", ReinhardAssets.ANIMATIONS.getPath()))).getAsJsonObject());
      var field=AnimCache.class.getDeclaredField("animationFiles");field.setAccessible(true);Object previous=field.get(null);
      try {
         field.set(null,Map.of(ReinhardAssets.ANIMATIONS,animations));
         var body=model();
         float first=0;
         for (double time : new double[]{.25,1.25,10001.25}) {
            var pose=new ReinhardPoses.Pose("idle_knight",0,1,time,null,ReinhardPoses.BodyState.STAND,0);
            ReinhardRenderer.applyClips(body,pose,new StanceBlend.Mix(ReinhardMode.KNIGHT,ReinhardMode.KNIGHT,1),
               1,0,0,new ParryPulse.Sample(0,0),null,null);
            float leg=body.getBone("right_leg").rotX;
            if (time==.25) first=leg; else assertEquals(first,leg,1e-5);
            var hand=new PoseStack();
            ReinhardRenderer.handFrame(hand,body,body.getBone("left_arm"));
            hand.mulPoseMatrix(HeldItemAnchor.local(HumanoidArm.LEFT));
            var wrist=hand.last().pose().transformPosition(new Vector3f());
            assertTrue(wrist.z>2F/16,"left hand must be behind the back, not in front of the belt: "+wrist);
         }
         AnimationController.seek(animations.get("run"),body,0);
         assertTrue(Math.abs(body.getBone("right_leg").rotX)>Math.toRadians(40));
      } finally {field.set(null,previous);}
   }
}
