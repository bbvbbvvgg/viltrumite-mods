package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.*;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.hero.reinhard.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

class WorldErasureClipTest {
   static JsonObject json() throws Exception{return JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/viltrumitecore",ReinhardAssets.ANIMATIONS.getPath()))).getAsJsonObject();}
   @Test void clipShipsForFull150TickAction() throws Exception {var clips=AnimationParser.parse(json());assertTrue(AnimationParser.lastWarnings.isEmpty());assertEquals(List.of("world_erasure"),ReinhardClips.names(ReinhardAbility.WORLD_ERASURE));var clip=clips.get("world_erasure");assertNotNull(clip);assertEquals(7.5,clip.lengthSeconds());assertEquals(Animation.LoopType.HOLD_ON_LAST_FRAME,clip.loopType());assertEquals("world_erasure",ReinhardClips.clip(ReinhardAbility.WORLD_ERASURE,0,30).animation());}
   @Test void allPreviousAnimationDataIsUnchanged() throws Exception {var old=json();old.getAsJsonObject("animations").remove("world_erasure");var hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(old.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));assertEquals("bc3f49015564265786661b5fe48c1838e40e2ee86b06aff556f053ef1b28b79f",hash,"canonical Stage 5+Task 1-7 animation data");}
   @Test void actionSurvivesDrawnIdleInActualRendererComposition() throws Exception {var clips=AnimationParser.parse(json());var field=AnimCache.class.getDeclaredField("animationFiles");field.setAccessible(true);Object old=field.get(null);try{field.set(null,Map.of(ReinhardAssets.ANIMATIONS,clips));var geo=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/viltrumitecore",ReinhardAssets.BODY.getPath()))).getAsJsonObject();for(double seconds:new double[]{1.5,2,6}){var expected=BakedGeoModel.parse(geo);var actual=BakedGeoModel.parse(geo);AnimationController.overlay(clips.get("world_erasure"),expected,seconds);var pose=new ReinhardPoses.Pose("idle_holy",0,0,0,new ReinhardPoses.Clip("world_erasure",seconds),ReinhardPoses.BodyState.STAND,0);ReinhardRenderer.applyClips(actual,pose,new StanceBlend.Mix(ReinhardMode.HOLY_SWORD,ReinhardMode.HOLY_SWORD,1),0,0,0,new ParryPulse.Sample(0,0),new ReinhardPoses.Clip("idle_holy_drawn",0),null);assertEquals(expected.getBone("right_arm").rotX,actual.getBone("right_arm").rotX,1e-6);assertEquals(expected.getBone("right_arm").rotY,actual.getBone("right_arm").rotY,1e-6);assertTrue(Math.abs(actual.getBone("right_arm").rotX)>.5);}}finally{field.set(null,old);}}
   @Test void firstPersonBladeRaisesThenMakesOneCutAndHolds(){var guard=ReinhardFirstPersonPose.worldErasure(30,false);var cut=ReinhardFirstPersonPose.worldErasure(40,false);assertTrue(guard.y()>0);assertTrue(cut.y()<guard.y());assertTrue(cut.x()>guard.x());assertTrue(cut.yaw()>guard.yaw());assertEquals(cut,ReinhardFirstPersonPose.worldErasure(130,false));assertEquals(guard,ReinhardFirstPersonPose.of(ReinhardAbility.WORLD_ERASURE,0,30,150));assertEquals(ReinhardFirstPersonPose.Offset.NONE,ReinhardFirstPersonPose.worldErasure(150,false));}
   @Test void firstPersonBothPhysicalArmsRemainFinite(){for(boolean left:new boolean[]{true,false})for(int t=0;t<=150;t++){var p=ReinhardFirstPersonPose.worldErasure(t,left);for(float value:new float[]{p.x(),p.y(),p.z(),p.pitch(),p.yaw(),p.roll()})assertTrue(Float.isFinite(value));}assertEquals(ReinhardFirstPersonPose.Offset.NONE,ReinhardFirstPersonPose.worldErasure(Double.NaN,false));}
   @Test void bothOccupiedSlotsUsePhysicalArmPoseWithoutChangingInventory(){for(HumanoidArm main:HumanoidArm.values())for(InteractionHand hand:InteractionHand.values())assertFalse(ReinhardPoses.drawsHeldItem(ReinhardPoses.physicalArm(main,hand),ReinhardPoses.ReidPhase.DRAWN,ReinhardAbility.WORLD_ERASURE));assertEquals(ReinhardAbility.WORLD_ERASURE,ReinhardPoses.cinematicAction(ReinhardAbility.WORLD_ERASURE.ordinal()));assertNull(ReinhardPoses.cinematicAction(-1));assertNull(ReinhardPoses.cinematicAction(ReinhardAbility.LIGHT_PUNCH.ordinal()));}
   @Test void bothViewsActuallyUseCinematicPolicy() throws Exception {var source=Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/client/reinhard/ReinhardRenderer.java"));assertTrue(source.contains("ReinhardFirstPersonPose.worldErasure("));assertTrue(source.contains("ReinhardPoses.cinematicAction(snapshot.actionId())"));assertTrue(source.contains("ReinhardPoses.drawsHeldItem(arm, reid, action)"));}
}
