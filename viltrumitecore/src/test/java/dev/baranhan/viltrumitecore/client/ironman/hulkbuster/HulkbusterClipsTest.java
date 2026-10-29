package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkMove;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkStrikes;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterMotion;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterRules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Clip lengths are server phases (Thor rule §1.3.4): every clip a move plays exists and lasts exactly the
 * ticks HulkMove gives it; the motion clips match the motion rules; clips only name bones of the rig; the
 * renderer's clip selection reads HulkMove; the first-person thrust peaks on the contact.
 */
class HulkbusterClipsTest {
   static final Path ROOT = Path.of("src/main/resources/assets/viltrumitecore");
   static Map<String, Animation> clips;
   static BakedGeoModel body;
   static BakedGeoModel arms;
   static JsonObject raw;

   @BeforeAll
   static void load() throws IOException {
      AnimationParser.lastWarnings.clear();
      raw = json("animations/ironman/hulkbuster/mark48.animation.json");
      clips = AnimationParser.parse(raw);
      assertTrue(AnimationParser.lastWarnings.isEmpty(), AnimationParser.lastWarnings.toString());
      body = BakedGeoModel.parse(json("geo/ironman/hulkbuster/mark48.geo.json"));
      arms = BakedGeoModel.parse(json("geo/ironman/hulkbuster/mark48_fp.geo.json"));
   }

   static JsonObject json(String relative) throws IOException {
      return JsonParser.parseString(Files.readString(ROOT.resolve(relative))).getAsJsonObject();
   }

   static int ticks(Animation clip) {
      return (int)Math.round(clip.lengthSeconds() * 20.0);
   }

   @Test
   void everyMoveClipLastsItsServerPhases() {
      for (HulkMove move : HulkMove.values()) {
         for (int step = 0; step < move.maxSteps(); step++) {
            for (MovePhase phase : MovePhase.values()) {
               if (move.ticks(phase, step) <= 0) {
                  continue;
               }

               String name = move.clip(step, phase);
               Animation clip = clips.get(name);
               assertNotNull(clip, move + " step " + step + " " + phase + " -> " + name);
               if (HulkMove.loops(name)) {
                  assertEquals(Animation.LoopType.LOOP, clip.loopType(), name);
                  assertEquals(HulkbusterClips.loopSeconds(name), clip.lengthSeconds(), 1.0E-6, name);
               } else {
                  assertEquals(move.clipLength(step, phase), ticks(clip), move + " step " + step + " " + phase + " (" + name + ")");
               }
            }
         }
      }
   }

   @Test
   void contactTicksFallInsideTheirClips() {
      for (HulkStrikes.Strike strike : HulkStrikes.ALL) {
         HulkMove move = strike.move();
         String name = move.clip(strike.step(), MovePhase.MAIN);
         int into = move.clipTicks(strike.step(), MovePhase.MAIN, 0);
         Animation clip = clips.get(name);
         assertNotNull(clip, name);
         assertTrue(into >= 0 && into <= ticks(clip), name + " contact at " + into);
         // The authored contact key: the striking bones have a key exactly on the contact tick.
         double contact = into / 20.0;
         boolean keyed = hasKeyAt(name, contact);
         assertTrue(keyed, name + " has no key on its contact tick " + into);
      }
   }

   /** Any rotation / position channel of the clip keyed at this time (raw Bedrock JSON: keys are seconds). */
   private static boolean hasKeyAt(String clip, double seconds) {
      JsonObject bones = raw.getAsJsonObject("animations").getAsJsonObject(clip).getAsJsonObject("bones");
      for (String bone : bones.keySet()) {
         JsonObject channels = bones.getAsJsonObject(bone);
         for (String channel : List.of("rotation", "position")) {
            if (channels.has(channel) && channels.get(channel).isJsonObject()) {
               for (String key : channels.getAsJsonObject(channel).keySet()) {
                  if (Math.abs(Double.parseDouble(key) - seconds) < 1.0E-3) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   @Test
   void motionClipsMatchTheMotionRules() {
      for (int direction = 0; direction < 4; direction++) {
         Animation dash = clips.get(HulkbusterClips.dashClip(direction));
         assertNotNull(dash);
         assertTrue(ticks(dash) >= HulkbusterRules.DASH_WINDUP + HulkbusterRules.DASH_BURST, "wind-up + burst fit in the dash clip");
      }

      assertEquals(HulkbusterRules.JET_CHARGE_MAX, ticks(clips.get("jet_charge")));
      assertEquals(HulkbusterRules.LANDING_SQUAT, ticks(clips.get("land")));
      assertEquals(HulkbusterLayer.ASSEMBLE_TICKS, ticks(clips.get("suit_assemble")));
      assertEquals(HulkbusterLayer.EXIT_TICKS, ticks(clips.get("exit")));
      for (String loop : List.of(HulkbusterClips.IDLE, HulkbusterClips.WALK, HulkbusterClips.RUN, HulkbusterClips.RAM, HulkbusterClips.FALL, "hover")) {
         assertEquals(Animation.LoopType.LOOP, clips.get(loop).loopType(), loop);
      }

      assertEquals(HulkbusterClips.WALK_SECONDS, clips.get(HulkbusterClips.WALK).lengthSeconds(), 1.0E-6);
      assertEquals(HulkbusterClips.RUN_SECONDS, clips.get(HulkbusterClips.RUN).lengthSeconds(), 1.0E-6);
      assertEquals(HulkbusterClips.RAM_SECONDS, clips.get(HulkbusterClips.RAM).lengthSeconds(), 1.0E-6);
   }

   @Test
   void clipsOnlyNameRigBonesAndTheArmsShareTheirNames() {
      Set<String> rig = new HashSet<>();
      for (GeoBone bone : body.allBones()) {
         rig.add(bone.name);
      }

      for (Animation clip : clips.values()) {
         for (String bone : clip.boneAnimations().keySet()) {
            assertTrue(rig.contains(bone), clip.name() + " animates unknown bone " + bone);
         }
      }

      for (GeoBone bone : arms.allBones()) {
         assertTrue(rig.contains(bone.name), "FP bone not in the body: " + bone.name);
      }

      assertEquals(List.of(HulkbusterAssets.SHOULDER_R, HulkbusterAssets.SHOULDER_L), arms.topLevelBones().stream().map(b -> b.name).toList());
      for (String name : List.of(HulkbusterAssets.ROOT, HulkbusterAssets.HEAD, HulkbusterAssets.FIST_R, HulkbusterAssets.HAND_L, HulkbusterAssets.PISTON_R,
         HulkbusterAssets.REACTOR, HulkbusterAssets.FOOT_THRUSTER_L, HulkbusterAssets.FOOT_THRUSTER_R)) {
         assertNotNull(body.getBone(name), name);
      }

      for (String[] stage : HulkbusterAssets.DAMAGE_PLATES) {
         for (String plate : stage) {
            assertNotNull(body.getBone(plate), plate);
         }
      }
   }

   static HulkbusterClips.Input at(HulkMove move, int step, double ticks) {
      MovePhase phase = StagedTimeline.phaseOf(0);
      int intro = move.ticks(MovePhase.INTRO, step);
      int main = move.ticks(MovePhase.MAIN, step);
      phase = ticks < intro ? MovePhase.INTRO : ticks < intro + main ? MovePhase.MAIN : MovePhase.FINISH;
      return new HulkbusterClips.Input(move, phase, step, ticks, HulkbusterMotion.State.NONE, 0.0, 0, 1.0, 0.0F, 0.0, false);
   }

   @Test
   void selectionPlaysTheMoveClipOnTheServerClock() {
      HulkbusterClips.Pose pose = HulkbusterClips.select(at(HulkMove.COMBO, 1, 12.0));
      assertEquals("combo_2", pose.move().clip());
      assertEquals(12.0 / 20.0, pose.move().seconds(), 1.0E-9);
      assertNull(pose.gait(), "standing still: no gait layer");
      assertNull(pose.motion());
      // A split move: the charge loop then the release from its own start.
      HulkbusterClips.Input release = new HulkbusterClips.Input(HulkMove.CHARGED, MovePhase.MAIN, 0, 25.0, HulkbusterMotion.State.NONE, 0.0, 0, 0.0, 0.0F,
         0.0, false);
      assertEquals("charged_release", HulkbusterClips.select(release).move().clip());
      // Never past the clip's end.
      HulkbusterClips.Pose late = HulkbusterClips.select(at(HulkMove.GRAB_MISS, 0, 400.0));
      assertTrue(late.move().seconds() <= HulkMove.GRAB_MISS.length(0) / 20.0 + 1.0E-9);
   }

   @Test
   void motionAndGaitLayers() {
      HulkbusterClips.Input dash = new HulkbusterClips.Input(null, MovePhase.INTRO, 0, 0.0, HulkbusterMotion.State.DASH, 2.0, 3, 0.0, 0.0F, 0.0, false);
      HulkbusterClips.Layer layer = HulkbusterClips.select(dash).motion();
      assertEquals("dash_right", layer.clip());
      assertEquals((HulkbusterRules.DASH_WINDUP + 2.0) / 20.0, layer.seconds(), 1.0E-9, "the burst continues the wind-up clip");
      HulkbusterClips.Input walk = new HulkbusterClips.Input(null, MovePhase.INTRO, 0, 0.0, HulkbusterMotion.State.NONE, 0.0, 0, 0.0, 0.8F,
         HulkbusterClips.STRIDE_UNITS * 1.5, false);
      HulkbusterClips.Layer gait = HulkbusterClips.select(walk).gait();
      assertEquals(HulkbusterClips.WALK, gait.clip());
      assertEquals(HulkbusterClips.WALK_SECONDS * 0.5, gait.seconds(), 1.0E-9, "a stride and a half in: half way through the loop");
      HulkbusterClips.Input ram = new HulkbusterClips.Input(null, MovePhase.INTRO, 0, 0.0, HulkbusterMotion.State.RAM, 0.0, 0, 0.0, 1.0F, 0.0, true);
      assertEquals(HulkbusterClips.RAM, HulkbusterClips.select(ram).gait().clip());
   }

   @Test
   void firstPersonThrustPeaksOnTheContact() {
      HulkStrikes.Strike hook = HulkStrikes.of(HulkMove.COMBO, 0);
      int intro = HulkMove.COMBO.ticks(MovePhase.INTRO, 0);
      assertTrue(HulkbusterClips.firstPersonPush(HulkMove.COMBO, MovePhase.INTRO, 0, intro * 0.5, true) < 0.0F, "drawn back in the wind-up");
      assertEquals(hook.fpPush(), HulkbusterClips.firstPersonPush(HulkMove.COMBO, MovePhase.MAIN, 0, intro, true), 1.0E-6F);
      assertEquals(0.0F, HulkbusterClips.firstPersonPush(HulkMove.COMBO, MovePhase.MAIN, 0, intro, false), "the left arm stays for a right hook");
      float tail = HulkbusterClips.firstPersonPush(HulkMove.COMBO, MovePhase.FINISH, 0, HulkMove.COMBO.length(0), true);
      assertEquals(0.0F, tail, 1.0E-4F, "back at rest on the last tick");
      assertEquals(0.0F, HulkbusterClips.firstPersonPush(HulkMove.BLOCK, MovePhase.MAIN, 0, 10.0, true), "no strike, no thrust");
   }
}
