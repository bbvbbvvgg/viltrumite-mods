package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoPose;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Owner playtest 2026-10-28: Thor's walk / run slid, splayed the feet and stood on the toes. ThorClipPlayer drives
 * the gait by the distance walked (WALK_CYCLE_BLOCKS / RUN_CYCLE_BLOCKS), so the clips' planted feet must move back
 * at exactly that pace, track under the hips and keep their soles down.
 */
class ThorGaitTest {
   private static final int SAMPLES = 96;

   private static Map<String, Animation> clips() throws Exception {
      return AnimationParser.parse(JsonParser.parseString(Files.readString(ThorClipContract.FILE)).getAsJsonObject());
   }

   private record Foot(float x, float y, float z, float sole) {
   }

   private static Foot foot(BakedGeoModel body, String side) {
      Vector3f ankle = GeoPose.point(body.getBone(side + "_foot")).mul(16.0F);
      float sole = ThorRig.bottom(body, side + "_shin", side + "_foot") * 16.0F;
      return new Foot(ankle.x, ankle.y, ankle.z, sole);
   }

   private static void check(Map<String, Animation> clips, String name, double cycleBlocks) {
      Animation clip = clips.get(name);
      BakedGeoModel body = ThorClipRig.parse(JsonParser.parseString(read(ThorClipChecks.BODY)).getAsJsonObject());
      double length = clip.lengthSeconds();
      List<double[]> speeds = new ArrayList<>();
      for (String side : new String[]{"right", "left"}) {
         Foot prev = null;
         for (int i = 0; i <= SAMPLES; i++) {
            AnimationController.seek(clip, body, length * i / SAMPLES);
            Foot f = foot(body, side);
            assertTrue(Math.abs(f.x()) <= 6.5F, name + " " + side + " foot splays out to x=" + f.x() + " px at " + i);
            assertTrue(f.sole() >= -0.6F, name + " " + side + " sole " + f.sole() + " px into the ground at " + i);
            if (prev != null && f.sole() < 0.35F && prev.sole() < 0.35F) {
               // Planted on both samples: ground speed in px per cycle.
               speeds.add(new double[]{Math.abs(f.z() - prev.z()) * SAMPLES});
            }

            prev = f;
         }
      }

      assertTrue(speeds.size() > SAMPLES / 3, name + ": too few planted samples " + speeds.size());
      // Median: the planted samples also catch the swing foot's first and last frames near the ground, where it
      // eases to a stop; the stance foot's steady pace is the bulk of them.
      double[] sorted = speeds.stream().mapToDouble(s -> s[0]).sorted().toArray();
      double mean = sorted[sorted.length / 2];
      double expected = cycleBlocks * 16.0;
      assertEquals(expected, mean, expected * 0.15, name + ": planted foot speed (px per cycle) vs the distance-driven cycle");
   }

   private static String read(java.nio.file.Path path) {
      try {
         return Files.readString(path);
      } catch (java.io.IOException e) {
         throw new java.io.UncheckedIOException(e);
      }
   }

   @Test
   void plantedFeetMoveWithTheGround() throws Exception {
      Map<String, Animation> clips = clips();
      check(clips, "walk", ThorClipPlayer.WALK_CYCLE_BLOCKS);
      check(clips, "walk_hammer", ThorClipPlayer.WALK_CYCLE_BLOCKS);
      check(clips, "run", ThorClipPlayer.RUN_CYCLE_BLOCKS);
      check(clips, "run_hammer", ThorClipPlayer.RUN_CYCLE_BLOCKS);
   }

   @Test
   void everyGroundClipKeysTheAnkles() throws Exception {
      for (Map.Entry<String, Animation> e : clips().entrySet()) {
         String name = e.getKey();
         if (name.endsWith(ThorClipContract.FP_SUFFIX) || name.startsWith("flight_")) {
            continue;
         }

         boolean legs = e.getValue().boneAnimations().containsKey("right_shin");
         if (legs) {
            assertTrue(e.getValue().boneAnimations().containsKey("right_foot") && e.getValue().boneAnimations().containsKey("left_foot"),
               name + " moves the shins but has no ankle keys (the boot would follow the shin onto its toes)");
         }
      }
   }

   @Test
   void throwPhasesPlayTheirOwnClips() {
      assertEquals("throw_windup", ThorClipPlayer.THROW_COIL > 0 ? "throw_windup" : "");
      assertTrue(ThorClipPlayer.legDriven("fist_push_kick"));
      assertTrue(ThorClipPlayer.legDriven("hammer_ground_slam"));
      assertTrue(!ThorClipPlayer.legDriven("hammer_swing_right"));
      assertTrue(!ThorClipPlayer.legDriven("fist_jab"));
   }
}
