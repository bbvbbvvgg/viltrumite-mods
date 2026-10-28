package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorAttacks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Exports posed boxes of Thor's clips at their key beats for
 * {@code tools/preview_thor_clips.py} (contact sheets front / side / back + FP).
 * <pre>
 * THOR_PREVIEW=1 ./gradlew :viltrumitecore:test --tests '*ThorClipPreviewTest*'
 * python3 tools/preview_thor_clips.py
 * </pre>
 * Inputs (environment): {@code THOR_PREVIEW_BODY} (geo path; default R1's body, else the vanilla-proportion
 * stand-in), {@code THOR_PREVIEW_GRIP} (grip bone), {@code THOR_PREVIEW_ANIMATIONS} (default thor.animation.json,
 * else a demo clip on the stand-in), {@code THOR_PREVIEW_CLIPS} (comma-separated filter). Output:
 * {@code build/diagnostics/thor-clips/<clip>.json} with every frame's boxes and penetrations.
 */
@EnabledIfEnvironmentVariable(named = "THOR_PREVIEW", matches = "1")
class ThorClipPreviewTest {
   static final Path OUT = Path.of("build/diagnostics/thor-clips");
   private static final Gson GSON = new GsonBuilder().create();

   @Test
   void export() throws Exception {
      String bodyEnv = System.getenv("THOR_PREVIEW_BODY");
      Path bodyPath = bodyEnv != null ? Path.of(bodyEnv) : Files.exists(ThorClipChecks.BODY) ? ThorClipChecks.BODY : null;
      BakedGeoModel body = bodyPath != null ? ThorRig.load(bodyPath) : ThorRig.parse(ThorRig.standIn());
      String bodyName = bodyPath != null ? bodyPath.toString() : "stand-in (vanilla proportions, not Thor)";
      String grip = env("THOR_PREVIEW_GRIP", bodyPath != null ? ThorClipChecks.GRIP_BONE : "right_arm");
      String offHand = bodyPath != null ? ThorClipChecks.OFF_HAND_BONE : "left_arm";
      String animEnv = System.getenv("THOR_PREVIEW_ANIMATIONS");
      Path animPath = animEnv != null ? Path.of(animEnv) : Files.exists(ThorClipContract.FILE) ? ThorClipContract.FILE : null;
      JsonObject animations = animPath != null ? JsonParser.parseString(Files.readString(animPath)).getAsJsonObject() : demo();
      Map<String, Animation> clips = AnimationParser.parse(animations);
      String filter = System.getenv("THOR_PREVIEW_CLIPS");
      List<String> wanted = filter == null || filter.isBlank() ? null : List.of(filter.split(","));
      BakedGeoModel hammer = ThorRig.load(ThorClipChecks.HAMMER);
      ThorIntersection rules = ThorClipChecks.rules(grip, offHand);
      body.resetBones();
      rules.rest(ThorRig.boxes(ThorIntersection.BODY, body, new Matrix4f()));
      Files.createDirectories(OUT);
      int written = 0;
      for (Map.Entry<String, Animation> entry : clips.entrySet()) {
         String name = entry.getKey();
         if (wanted != null && !wanted.contains(name)) {
            continue;
         }

         Animation clip = entry.getValue();
         ThorAttacks.Attack row = ThorAttacks.byClip(name);
         String rowName = name.startsWith("demo_") ? name.substring(5) : name;
         if (row == null) {
            row = ThorAttacks.byClip(rowName);
         }

         int length = (int)Math.round(clip.lengthSeconds() * 20.0);
         List<ThorClipBeats.Beat> beats = row != null ? ThorClipBeats.of(row.phases()) : ThorClipBeats.even(length, 6);
         boolean fp = name.endsWith(ThorClipContract.FP_SUFFIX);
         boolean withHammer = ThorClipChecks.hammerInHand(rowName);
         JsonObject out = new JsonObject();
         out.addProperty("clip", name);
         out.addProperty("fp", fp);
         out.addProperty("body", bodyName);
         out.addProperty("length", length);
         if (row != null) {
            out.addProperty("contact", row.phases().contactTick());
         }

         GeoBone head = body.getBone("head");
         if (head != null) {
            out.add("eye", vec(new Vector3f(head.pivotX, head.pivotY + 4.0F, head.pivotZ).mul(ThorRig.PX)));
         }

         JsonArray frames = new JsonArray();
         for (ThorClipBeats.Beat beat : beats) {
            List<ThorRig.Box> boxes = ThorClipChecks.pose(body, hammer, grip, clip, beat.seconds(), withHammer);
            JsonObject frame = new JsonObject();
            frame.addProperty("tick", beat.tick());
            frame.addProperty("beat", beat.name());
            JsonArray boxArray = new JsonArray();
            for (ThorRig.Box box : boxes) {
               JsonObject b = new JsonObject();
               b.addProperty("m", box.model());
               b.addProperty("b", box.bone());
               JsonArray corners = new JsonArray();
               for (Vector3f c : box.corners()) {
                  corners.add(vec(c));
               }

               b.add("c", corners);
               boxArray.add(b);
            }

            frame.add("boxes", boxArray);
            JsonArray hits = new JsonArray();
            if (!fp) {
               for (ThorIntersection.Hit hit : rules.check(boxes)) {
                  JsonObject h = new JsonObject();
                  h.addProperty("a", hit.a().id());
                  h.addProperty("b", hit.b().id());
                  h.addProperty("px", Math.round(hit.depth() / ThorRig.PX * 100.0F) / 100.0F);
                  hits.add(h);
               }
            }

            frame.add("hits", hits);
            frames.add(frame);
         }

         out.add("frames", frames);
         Files.writeString(OUT.resolve(name + ".json"), GSON.toJson(out));
         written++;
      }

      assertFalse(written == 0, "no clip matched " + filter);
   }

   private static String env(String name, String fallback) {
      String value = System.getenv(name);
      return value == null || value.isBlank() ? fallback : value;
   }

   private static JsonArray vec(Vector3f v) {
      JsonArray a = new JsonArray();
      a.add(Math.round(v.x * 10000.0F) / 10000.0F);
      a.add(Math.round(v.y * 10000.0F) / 10000.0F);
      a.add(Math.round(v.z * 10000.0F) / 10000.0F);
      return a;
   }

   /**
    * A pipeline demo on the stand-in only (no authored clips yet): the first Mjölnir strike's beats with a
    * rough arm/torso path. Not a Thor clip and never saved under a table name.
    */
   static JsonObject demo() {
      ThorAttacks.Phases p = ThorAttacks.byClip("hammer_swing_right").phases();
      List<ThorClipBeats.Beat> beats = ThorClipBeats.of(p);
      // Bedrock degrees per beat: start, gather, loaded, contact, overswing, settle, end.
      float[][] arm = {{-15, 0, 10}, {-60, 20, 60}, {-80, 35, 85}, {-80, -40, 75}, {-70, -75, 60}, {-35, -30, 25}, {-15, 0, 10}};
      float[][] torso = {{0, 0, 0}, {0, 25, 0}, {0, 35, 0}, {0, -25, 0}, {0, -40, 0}, {0, -15, 0}, {0, 0, 0}};
      JsonObject clip = new JsonObject();
      clip.addProperty("loop", "hold_on_last_frame");
      clip.addProperty("animation_length", p.length() / 20.0);
      JsonObject bones = new JsonObject();
      bones.add("right_arm", track(beats, arm));
      bones.add("body", track(beats, torso));
      clip.add("bones", bones);
      JsonObject animations = new JsonObject();
      animations.add("demo_hammer_swing_right", clip);
      animations.add("demo_hammer_swing_right" + ThorAttacks.FP_SUFFIX, clip.deepCopy());
      JsonObject root = new JsonObject();
      root.addProperty("format_version", "1.8.0");
      root.add("animations", animations);
      return root;
   }

   private static JsonObject track(List<ThorClipBeats.Beat> beats, float[][] values) {
      JsonObject rotation = new JsonObject();
      for (int i = 0; i < beats.size() && i < values.length; i++) {
         JsonArray v = new JsonArray();
         for (float f : values[i]) {
            v.add(f);
         }

         rotation.add(Double.toString(beats.get(i).seconds()), v);
      }

      JsonObject bone = new JsonObject();
      bone.add("rotation", rotation);
      return bone;
   }
}
