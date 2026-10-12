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
      assertEquals(Set.of(ReinhardAssets.RIGHT_ARM, ReinhardAssets.LEFT_ARM, ReinhardAssets.RIGHT_LEG, ReinhardAssets.LEFT_LEG), walk.boneAnimations().keySet());
      for (Animation.BoneAnimation bone : walk.boneAnimations().values()) {
         assertFalse(bone.rotX().isEmpty(), bone.boneName());
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
