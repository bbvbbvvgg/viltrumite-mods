package dev.baranhan.viltrumitecore.client.ironman.legion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.client.anim.animation.KeyframeStack;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionMove;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionRules;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Legion suit clips (tools/assets/make_legion_clips.py): every clip the renderer asks for exists, fits
 * the server's ticks, animates only the empty-suit shell's top bones, loops seamlessly, and the
 * one-shots start / end where the layering expects (animation-system §4–5).
 */
class LegionClipsTest {
   static final Path ROOT = Path.of("src/main/resources/assets/viltrumitecore");
   static Map<String, Animation> clips;
   static BakedGeoModel shell;

   @BeforeAll
   static void load() throws IOException {
      AnimationParser.lastWarnings.clear();
      clips = AnimationParser.parse(json("animations/ironman/legion_suit.animation.json"));
      assertTrue(AnimationParser.lastWarnings.isEmpty(), AnimationParser.lastWarnings.toString());
      shell = BakedGeoModel.parse(json("geo/ironman/marks/empty_suit.geo.json"));
   }

   static JsonObject json(String relative) throws IOException {
      return JsonParser.parseString(Files.readString(ROOT.resolve(relative))).getAsJsonObject();
   }

   static Animation clip(String name) {
      Animation clip = clips.get(name);
      assertNotNull(clip, "missing clip " + name);
      return clip;
   }

   static double ticks(Animation clip) {
      return clip.lengthSeconds() * 20.0;
   }

   static float[] sample(Animation.BoneAnimation bone, double seconds) {
      List<KeyframeStack> ch = List.of(bone.rotX(), bone.rotY(), bone.rotZ(), bone.posX(), bone.posY(), bone.posZ());
      float[] out = new float[ch.size()];
      for (int i = 0; i < out.length; i++) {
         out[i] = ch.get(i).sample(seconds);
      }

      return out;
   }

   static void assertPose(Animation a, double ta, Animation b, double tb, String what) {
      for (String bone : LegionSuitRenderer.topBones()) {
         Animation.BoneAnimation ba = a.boneAnimations().get(bone);
         Animation.BoneAnimation bb = b.boneAnimations().get(bone);
         assertNotNull(ba, what + ": " + a.name() + " misses " + bone);
         assertNotNull(bb, what + ": " + b.name() + " misses " + bone);
         float[] pa = sample(ba, ta);
         float[] pb = sample(bb, tb);
         for (int i = 0; i < pa.length; i++) {
            assertEquals(pb[i], pa[i], 1.0E-3F, what + " " + bone + " channel " + i);
         }
      }
   }

   @Test
   void everyClipTheRendererPlaysExists() {
      for (String name : new String[]{"hover", "cruise", "brake", "takeoff", "crash"}) {
         clip(name);
      }

      for (LegionMove move : LegionMove.values()) {
         if (move != LegionMove.NONE && move != LegionMove.READY) {
            clip(move.clip());
         }
      }

      for (MarkId mark : MarkId.values()) {
         clip("ready_" + mark.key());
      }
   }

   @Test
   void clipsOnlyAnimateShellTopBones() {
      Set<String> top = Set.of(LegionSuitRenderer.topBones());
      for (String bone : top) {
         assertNotNull(shell.getBone(bone), "shell bone " + bone);
      }

      for (Animation clip : clips.values()) {
         for (String bone : clip.boneAnimations().keySet()) {
            assertTrue(top.contains(bone), clip.name() + " animates " + bone);
         }
      }
   }

   @Test
   void loopsAreSeamlessAndOneShotsHold() {
      for (String name : new String[]{"hover", "cruise", "crash"}) {
         Animation clip = clip(name);
         assertEquals(Animation.LoopType.LOOP, clip.loopType(), name);
         assertPose(clip, 0.0, clip, clip.lengthSeconds(), name + " loop seam");
      }

      for (Animation clip : clips.values()) {
         if (!Set.of("hover", "cruise", "crash").contains(clip.name())) {
            assertEquals(Animation.LoopType.HOLD_ON_LAST_FRAME, clip.loopType(), clip.name());
         }
      }

      // hover and cruise blend per bone: both key all six bones.
      assertEquals(6, clip("hover").boneAnimations().size());
      assertEquals(6, clip("cruise").boneAnimations().size());
   }

   @Test
   void lengthsFitTheServerTicks() {
      for (MarkId mark : MarkId.values()) {
         assertEquals(LegionRules.READY_TICKS, ticks(clip("ready_" + mark.key())), 1.0E-6, mark.key());
      }

      for (LegionMove move : LegionMove.values()) {
         if (move != LegionMove.NONE && move != LegionMove.READY) {
            assertTrue(ticks(clip(move.clip())) <= move.length() + 1.0E-6, move + " clip longer than its " + move.length() + " ticks");
         }
      }

      assertTrue(ticks(clip("brake")) <= LegionRules.BRAKE_TICKS + 6 + 1.0E-6, "brake fits its overlay window");
   }

   @Test
   void readyBeatsStartAndEndOnTheHover() {
      Animation hover = clip("hover");
      for (MarkId mark : MarkId.values()) {
         Animation ready = clip("ready_" + mark.key());
         assertPose(ready, 0.0, hover, 0.0, ready.name() + " start");
         assertPose(ready, ready.lengthSeconds(), hover, 0.0, ready.name() + " end");
      }

      for (String name : new String[]{"missiles", "signature", "dodge_left", "dodge_right", "land", "takeoff"}) {
         assertPose(clip(name), 0.0, hover, 0.0, name + " start");
      }

      assertPose(clip("brake"), 0.0, clip("cruise"), 0.0, "brake starts from the cruise");
   }

   @Test
   void additiveClipsStartAndEndAtZero() {
      for (LegionMove move : new LegionMove[]{LegionMove.FIRE_RIGHT, LegionMove.FIRE_LEFT, LegionMove.FIRE_BOTH, LegionMove.HIT}) {
         Animation clip = clip(move.clip());
         for (Animation.BoneAnimation bone : clip.boneAnimations().values()) {
            for (double t : new double[]{0.0, clip.lengthSeconds()}) {
               for (float v : sample(bone, t)) {
                  assertEquals(0.0F, v, 1.0E-4F, clip.name() + " " + bone.boneName() + " at " + t);
               }
            }
         }
      }

      // The firing palm kicks up (bone X grows after the parser's sign flip) and only the firing side moves.
      Animation right = clip("fire_right");
      assertTrue(right.boneAnimations().get("armorrightarm").rotX().sample(0.05) > 0.2F);
      assertTrue(!right.boneAnimations().containsKey("armorleftarm"));
      Animation left = clip("fire_left");
      assertTrue(left.boneAnimations().get("armorleftarm").rotX().sample(0.05) > 0.2F);
      assertTrue(!left.boneAnimations().containsKey("armorrightarm"));
   }

   @Test
   void wrapKeepsLoopTimeInsideTheClip() {
      Animation hover = clip("hover");
      assertEquals(0.5, LegionSuitRenderer.wrap(2.5, hover), 1.0E-9);
      assertEquals(1.5, LegionSuitRenderer.wrap(-0.5, hover), 1.0E-9);
      assertEquals(0.0, LegionSuitRenderer.wrap(4.0, hover), 1.0E-9);
      for (double t = -7.3; t < 9.0; t += 0.37) {
         double w = LegionSuitRenderer.wrap(t, hover);
         assertTrue(w >= 0.0 && w < hover.lengthSeconds(), "wrap " + t);
      }
   }
}
