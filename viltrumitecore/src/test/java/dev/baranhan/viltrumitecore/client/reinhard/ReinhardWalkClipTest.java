package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReinhardWalkClipTest {
   private static final Path ROOT = Path.of("src/main/resources/assets/viltrumitecore");

   private static Animation walk() throws IOException {
      AnimationParser.lastWarnings.clear();
      Map<String, Animation> clips = AnimationParser.parse(JsonParser.parseString(Files.readString(ROOT.resolve(ReinhardAssets.ANIMATIONS.getPath()))).getAsJsonObject());
      assertTrue(AnimationParser.lastWarnings.isEmpty(), AnimationParser.lastWarnings.toString());
      Animation walk = clips.get("walk");
      assertNotNull(walk, "clip walk");
      return walk;
   }

   @Test
   void walkIsOneSecondLoop() throws IOException {
      Animation walk = walk();
      assertEquals(1.0, walk.lengthSeconds(), 1.0E-9);
      assertEquals(Animation.LoopType.LOOP, walk.loopType());
   }

   @Test
   void walkAnimatesFourLimbs() throws IOException {
      Animation walk = walk();
      // The torso and head add the bob and the counter-rotation; the four limbs carry the stride.
      assertTrue(walk.boneAnimations().keySet().containsAll(
         Set.of(ReinhardAssets.RIGHT_ARM, ReinhardAssets.LEFT_ARM, ReinhardAssets.RIGHT_LEG, ReinhardAssets.LEFT_LEG)),
         walk.boneAnimations().keySet().toString());
      for (Animation.BoneAnimation bone : walk.boneAnimations().values()) {
         assertFalse(bone.rotX().isEmpty(), bone.boneName());
      }
   }

   @Test
   void walkBobsTheTorsoTwicePerCycle() throws IOException {
      Animation walk = walk();
      Animation.BoneAnimation body = walk.boneAnimations().get(ReinhardAssets.BODY_BONE);
      assertNotNull(body, "body");
      float atStart = body.posY().sample(0.0);
      float atQuarter = body.posY().sample(0.25);
      float atHalf = body.posY().sample(0.5);
      assertTrue(atQuarter > atStart + 0.005F, "the body rises at the quarter step");
      assertEquals(atStart, atHalf, 0.005F, "and returns at the half step");
   }

   @Test
   void allClipsParse() throws IOException {
      AnimationParser.lastWarnings.clear();
      Map<String, Animation> clips = AnimationParser.parse(JsonParser.parseString(Files.readString(ROOT.resolve(ReinhardAssets.ANIMATIONS.getPath()))).getAsJsonObject());
      assertTrue(AnimationParser.lastWarnings.isEmpty(), AnimationParser.lastWarnings.toString());
      for (String name : new String[]{"walk", "idle_restrained", "idle_knight", "idle_holy", "crouch", "sit", "knight_left_arm_hold", "swim"}) {
         assertNotNull(clips.get(name), name);
         assertEquals(Animation.LoopType.LOOP, clips.get(name).loopType(), name);
      }
      Set<String> six = Set.of(ReinhardAssets.HEAD, ReinhardAssets.BODY_BONE, ReinhardAssets.RIGHT_ARM, ReinhardAssets.LEFT_ARM, ReinhardAssets.RIGHT_LEG,
         ReinhardAssets.LEFT_LEG);
      for (String stance : new String[]{"idle_restrained", "idle_knight", "idle_holy"}) {
         assertEquals(six, clips.get(stance).boneAnimations().keySet(), stance + " animates all six bones");
         double length = clips.get(stance).lengthSeconds();
         assertTrue(length >= 2.0 && length <= 4.0, stance + " breathing loop 2-4 s");
      }
      assertEquals(Set.of(ReinhardAssets.LEFT_ARM), clips.get("knight_left_arm_hold").boneAnimations().keySet());
      // Knight's hold is the stance's left arm, so the walk overlay keeps the arm behind the back.
      Animation knight = clips.get("idle_knight");
      Animation hold = clips.get("knight_left_arm_hold");
      for (double t = 0.0; t < knight.lengthSeconds(); t += 0.25) {
         assertEquals(knight.boneAnimations().get(ReinhardAssets.LEFT_ARM).rotX().sample(t), hold.boneAnimations().get(ReinhardAssets.LEFT_ARM).rotX().sample(t), 1.0E-6F);
         assertEquals(knight.boneAnimations().get(ReinhardAssets.LEFT_ARM).posZ().sample(t), hold.boneAnimations().get(ReinhardAssets.LEFT_ARM).posZ().sample(t), 1.0E-6F);
      }
   }

   @Test
   void rightArmStartsForward() throws IOException {
      // Source: right_arm rotation x = -31.51 deg at 0 s (Java ModelPart: negative x swings the arm forward).
      // The parser negates x, so BakedGeoModel's bone holds +31.51 deg, which it applies as -x: the same forward swing.
      float rotX = walk().boneAnimations().get(ReinhardAssets.RIGHT_ARM).rotX().sample(0.0);
      assertEquals((float) Math.toRadians(31.512679), rotX, 1.0E-4F);
      float leftX = walk().boneAnimations().get(ReinhardAssets.LEFT_ARM).rotX().sample(0.0);
      assertEquals(-rotX, leftX, 1.0E-4F, "arms swing in opposite phase");
   }
}
