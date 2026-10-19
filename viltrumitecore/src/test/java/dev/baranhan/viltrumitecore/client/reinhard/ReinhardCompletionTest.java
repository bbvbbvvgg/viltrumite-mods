package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.GrabThrow;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ReinhardCompletionTest {
   private static BakedGeoModel model(String name) throws Exception {
      return BakedGeoModel.parse(JsonParser.parseString(Files.readString(Path.of(
         "src/main/resources/assets/viltrumitecore/geo/reinhard/"+name+".geo.json"))).getAsJsonObject());
   }
   private static void matrix(Matrix4f a,Matrix4f b) {
      for(int col=0;col<4;col++) for(int row=0;row<4;row++) assertEquals(a.get(col,row),b.get(col,row),1E-5);
   }
   @Test void rigidTransferHasExactEndpointsAndNeverCollapsesOrShears() {
      Matrix4f a=new Matrix4f().translate(-.6F,1,0).rotateX(1.3F).rotateZ(3.14F).scale(.7F);
      Matrix4f b=new Matrix4f().translate(.4F,1.8F,-.5F).rotateY(2).rotateZ(-1).scale(.7F);
      matrix(a,ReinhardSword.interpolate(a,b,0));matrix(b,ReinhardSword.interpolate(a,b,1));
      for(int i=0;i<=100;i++) {
         Matrix4f m=ReinhardSword.interpolate(a,b,i/100F);
         assertEquals(.343F,m.determinant(),1E-5);
         Vector3f x=new Vector3f(m.m00(),m.m01(),m.m02());
         Vector3f y=new Vector3f(m.m10(),m.m11(),m.m12());
         assertEquals(.7,x.length(),1E-5);assertEquals(0,x.dot(y),1E-5);
      }
   }
   @Test void weaponTimelineMeetsBothPhaseBoundariesAndStrikeReturnsHome() {
      Matrix4f hip=new Matrix4f().translate(-1,1,0).scale(.7F),hand=new Matrix4f().translate(1,2,0).scale(.7F);
      var start=ReinhardSword.frames(hip,hand,ReinhardPoses.ReidPhase.DRAWING,0,false,0);
      var end=ReinhardSword.frames(hip,hand,ReinhardPoses.ReidPhase.DRAWING,120,false,0);
      var drawn=ReinhardSword.frames(hip,hand,ReinhardPoses.ReidPhase.DRAWN,0,false,0);
      matrix(hip,start[1]);matrix(hand,end[1]);matrix(end[1],drawn[1]);
      matrix(hand,ReinhardSword.frames(hip,hand,ReinhardPoses.ReidPhase.SHEATHING,0,false,0)[1]);
      matrix(hip,ReinhardSword.frames(hip,hand,ReinhardPoses.ReidPhase.SHEATHING,20,false,0)[1]);
      matrix(hip,ReinhardSword.frames(hip,hand,ReinhardPoses.ReidPhase.SHEATHED,0,true,0)[1]);
      matrix(hand,ReinhardSword.frames(hip,hand,ReinhardPoses.ReidPhase.SHEATHED,0,true,6)[1]);
      matrix(hip,ReinhardSword.frames(hip,hand,ReinhardPoses.ReidPhase.SHEATHED,0,true,12)[1]);
   }
   @Test void interruptionKeepsLastPoseThenReleasesIntoMovingBase() throws Exception {
      Object player=new Object();var body=model("reinhard");var arm=body.getBone("right_arm");
      arm.rotX=1;ReinhardRecovery.body(player,body,1,1_000_000_000L);
      arm.rotX=0;ReinhardRecovery.body(player,body,2,1_010_000_000L);assertEquals(1,arm.rotX,1E-5);
      arm.rotX=.25F;ReinhardRecovery.body(player,body,2,1_110_000_000L);assertTrue(arm.rotX>.25F&&arm.rotX<.35F);
      arm.rotX=-.3F;ReinhardRecovery.body(player,body,2,1_320_000_000L);assertEquals(-.3F,arm.rotX,1E-5);
      ReinhardRecovery.reset(player);
   }
   @Test void firstPersonViewsDoNotLeakAndExtraPassDoesNotAdvanceRecovery() {
      Object player=new Object();var action=new ReinhardFirstPersonPose.Offset(.1F,.2F,-.4F,30,8,2);
      ReinhardRecovery.firstPerson(player,false,action,1,1_000_000_000L);
      var a=ReinhardRecovery.firstPerson(player,false,ReinhardFirstPersonPose.Offset.NONE,2,1_100_000_000L);
      var b=ReinhardRecovery.firstPerson(player,false,ReinhardFirstPersonPose.Offset.NONE,2,1_100_000_000L);
      assertEquals(action,a);assertEquals(a,b);
      assertEquals(ReinhardFirstPersonPose.Offset.NONE,ReinhardRecovery.firstPerson(player,true,ReinhardFirstPersonPose.Offset.NONE,2,1_100_000_000L));
      assertEquals(0,ReinhardRecovery.firstPerson(player,false,ReinhardFirstPersonPose.Offset.NONE,2,1_500_000_000L).pitch());
      ReinhardRecovery.reset(player);
   }
   @Test void firstPersonNeutralGripMatchesVanillaAndBentElbowMovesIt() throws Exception {
      var arms=model("reinhard_arms");var shoulder=arms.getBone("armorRightArm");
      PoseStack p=new PoseStack();p.translate(5/16F,22/16F,0);p.scale(-1,-1,1);
      ReinhardRenderer.handFrame(p,arms,shoulder);p.mulPoseMatrix(HeldItemAnchor.local(HumanoidArm.RIGHT));
      matrix(HeldItemAnchor.vanilla(HumanoidArm.RIGHT),p.last().pose());
      arms.getBone("right_forearm").rotX=.7F;
      PoseStack bent=new PoseStack();bent.translate(5/16F,22/16F,0);bent.scale(-1,-1,1);
      ReinhardRenderer.handFrame(bent,arms,shoulder);bent.mulPoseMatrix(HeldItemAnchor.local(HumanoidArm.RIGHT));
      assertTrue(bent.last().pose().getTranslation(new Vector3f()).distance(p.last().pose().getTranslation(new Vector3f()))>.15);
   }
   @Test void neckAnchorIsYawCovariantAndNeverSinksTallTargets() {
      Vec3 a=GrabThrow.anchor(Vec3.ZERO,0,.6F,1.8F),b=GrabThrow.anchor(Vec3.ZERO,90,.6F,1.8F);
      assertEquals(a.y,b.y,1E-6);assertEquals(a.x,b.z,1E-5);assertEquals(-a.z,b.x,1E-5);
      assertTrue(GrabThrow.anchor(Vec3.ZERO,0,2,8).y>=.3);
   }
   @Test void swatReturnsToGuardWhileTheWindowIsStillOpen() {
      var ability=dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility.REFLECT;
      assertEquals(ReinhardClips.REFLECT,ReinhardClips.clip(ability,ReinhardClips.PHASE_SNAP,2).animation());
      assertEquals(ReinhardClips.REFLECT_READY,ReinhardClips.clip(ability,ReinhardClips.PHASE_SNAP,8).animation());
      assertEquals(ReinhardFirstPersonPose.of(ability,0,8,20),ReinhardFirstPersonPose.of(ability,ReinhardClips.PHASE_SNAP,8,20));
      assertNull(ReinhardClips.clip(null,0,0));
   }
   @Test void newBodyStatesArticulateElbowsAndSwimmingLoopHasNoWrapJump() throws Exception {
      var clips=AnimationParser.parse(JsonParser.parseString(Files.readString(Path.of(
         "src/main/resources/assets/viltrumitecore/animations/reinhard/reinhard.animation.json"))).getAsJsonObject());
      for(String clip:new String[]{"crouch","sit","swim"}) {
         var body=model("reinhard");AnimationController.seek(clips.get(clip),body,clip.equals("swim")?.5:0);
         assertTrue(Math.abs(body.getBone("right_forearm").rotX)>.1);
      }
      var body=model("reinhard");AnimationController.seek(clips.get("swim"),body,0);float x=body.getBone("right_arm").rotX;
      AnimationController.seek(clips.get("swim"),body,1);assertEquals(x,body.getBone("right_arm").rotX,1E-5);
   }
}
