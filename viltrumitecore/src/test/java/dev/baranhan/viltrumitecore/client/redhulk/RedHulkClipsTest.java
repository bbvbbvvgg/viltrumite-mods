package dev.baranhan.viltrumitecore.client.redhulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.redhulk.RedHulkMove;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The Red Hulk's clips match the server timeline: every {@link RedHulkMove} has a third-person clip in the file
 * {@link RedHulkView#fileOf} names and a first-person clip, both exactly {@code length} ticks long; every stance
 * and face the pose blends exists; every animated bone exists in its mesh and no clip ends a full turn away.
 */
class RedHulkClipsTest {
   private static final Path RES = Path.of("src/main/resources/assets/viltrumitecore");

   private static JsonObject clips(String file) throws IOException {
      return JsonParser.parseString(Files.readString(RES.resolve("animations/red_hulk/" + file))).getAsJsonObject().getAsJsonObject("animations");
   }

   private static String fileName(net.minecraft.resources.ResourceLocation rl) {
      String p = rl.getPath();
      return p.substring(p.lastIndexOf('/') + 1);
   }

   private static Set<String> bones(String geo) throws IOException {
      JsonObject g = JsonParser.parseString(Files.readString(RES.resolve("geo/red_hulk/" + geo))).getAsJsonObject()
         .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
      Set<String> names = new HashSet<>();
      for (JsonElement b : g.getAsJsonArray("bones")) {
         names.add(b.getAsJsonObject().get("name").getAsString());
      }
      return names;
   }

   private static int ticks(JsonObject clip) {
      return (int)Math.round(clip.get("animation_length").getAsDouble() * 20.0);
   }

   @Test
   void everyMoveHasThirdAndFirstPersonClipsOfTheServerLength() throws IOException {
      JsonObject fp = clips(fileName(RedHulkView.FP));
      for (RedHulkMove m : RedHulkMove.values()) {
         JsonObject file = clips(fileName(RedHulkView.fileOf(m)));
         assertTrue(file.has(m.clip()), fileName(RedHulkView.fileOf(m)) + " lacks " + m.clip());
         assertEquals(m.length, ticks(file.getAsJsonObject(m.clip())), m + " third-person length");
         assertTrue(fp.has(m.clip()), "first person lacks " + m.clip());
         assertEquals(m.length, ticks(fp.getAsJsonObject(m.clip())), m + " first-person length");
      }
   }

   @Test
   void stancesAndFacesExist() throws IOException {
      JsonObject stances = clips(fileName(RedHulkView.STANCES));
      for (String s : RedHulkPose.STANCES) {
         assertTrue(stances.has(s), "stance " + s);
      }
      for (String s : new String[]{"jump", "land", "hurt"}) {
         assertTrue(stances.has(s), "overlay " + s);
      }
      JsonObject faces = clips(fileName(RedHulkView.FACE));
      for (String f : RedHulkPose.FACES) {
         assertTrue(faces.has(f), "face " + f);
         JsonObject b = faces.getAsJsonObject(f).getAsJsonObject("bones");
         assertTrue(b != null && b.has("jaw"), f + " moves the jaw");
      }
   }

   @Test
   void animatedBonesExistAndClipsEndWithinHalfATurn() throws IOException {
      Set<String> body = bones("red_hulk.geo.json");
      Set<String> arms = bones("red_hulk_arms.geo.json");
      for (String bone : new String[]{"head", "jaw", "torso", "right_arm", "right_forearm", "right_hand", "right_fingers"}) {
         assertTrue(body.contains(bone), "body bone " + bone + " used by the pose / portrait");
      }
      for (String file : new String[]{"stances", "combat", "skills", "ult", "face", "fp"}) {
         Set<String> mesh = file.equals("fp") ? arms : body;
         for (Map.Entry<String, JsonElement> clip : clips("red_hulk_" + file + ".animation.json").entrySet()) {
            JsonObject bones = clip.getValue().getAsJsonObject().getAsJsonObject("bones");
            if (bones == null) {
               continue;
            }
            for (Map.Entry<String, JsonElement> bone : bones.entrySet()) {
               assertTrue(mesh.contains(bone.getKey()), file + "/" + clip.getKey() + " animates missing bone " + bone.getKey());
               JsonElement rot = bone.getValue().getAsJsonObject().get("rotation");
               if (rot == null) {
                  continue;
               }
               JsonElement last = rot;
               if (rot.isJsonObject()) {
                  double maxT = -1;
                  for (Map.Entry<String, JsonElement> k : rot.getAsJsonObject().entrySet()) {
                     double t = Double.parseDouble(k.getKey());
                     if (t > maxT) {
                        maxT = t;
                        last = k.getValue().isJsonObject() ? k.getValue().getAsJsonObject().get("post") : k.getValue();
                     }
                  }
               }
               if (last instanceof JsonArray arr) {
                  for (JsonElement v : arr) {
                     assertTrue(Math.abs(v.getAsDouble()) <= 180.0, file + "/" + clip.getKey() + "/" + bone.getKey() + " ends at " + v);
                  }
               }
            }
         }
      }
   }
}
