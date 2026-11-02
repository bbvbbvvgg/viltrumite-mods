package dev.baranhan.viltrumitecore.client.venom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.venom.VenomMove;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Venom's clips against the server's move table (spec §5.3): every move has a third-person and a first-person clip,
 * timed moves last exactly as long as on the server, held states loop, and tools/assets/venom_clips.py (the clip
 * author) holds the same lengths and contacts as VenomMove.
 */
class VenomClipsTest {
   private static final Path DIR = Path.of("src/main/resources/assets/viltrumitecore/animations/venom");
   private static final Path AUTHOR = Path.of("../tools/assets/venom_clips.py");

   private static JsonObject clips(String file) throws IOException {
      return JsonParser.parseString(Files.readString(DIR.resolve(file + ".animation.json"))).getAsJsonObject().getAsJsonObject("animations");
   }

   @Test
   void everyMoveHasItsClipsWithTheServerLength() throws IOException {
      JsonObject fp = clips("venom_fp");
      Map<String, JsonObject> third = new HashMap<>();
      for (String file : new String[]{"venom_moves", "venom_lash", "venom_skills"}) {
         clips(file).entrySet().forEach(e -> third.put(e.getKey(), e.getValue().getAsJsonObject()));
      }
      for (VenomMove m : VenomMove.values()) {
         assertTrue(third.containsKey(m.clip()), "third-person clip " + m.clip());
         assertTrue(fp.has(m.clip()), "first-person clip " + m.clip());
         for (JsonObject clip : new JsonObject[]{third.get(m.clip()), fp.getAsJsonObject(m.clip())}) {
            boolean loops = clip.has("loop") && clip.get("loop").getAsBoolean();
            assertEquals(m.loops(), loops, m + " loop");
            if (!m.loops()) {
               assertEquals(m.length / 20.0, clip.get("animation_length").getAsDouble(), 1.0E-6, m + " length");
            }
         }
      }
   }

   @Test
   void stancesAndFacesExist() throws IOException {
      JsonObject stances = clips("venom_stances");
      for (String s : new String[]{"idle", "idle_combat", "idle_rage", "walk", "run", "crouch", "air_up", "air_down", "swim", "wall_climb", "wall_hang",
         "wall_run", "jump", "land", "hurt"}) {
         assertTrue(stances.has(s), "stance " + s);
      }
      JsonObject faces = clips("venom_face");
      for (String s : new String[]{"face_idle", "face_snarl", "face_rage", "face_hurt"}) {
         assertTrue(faces.has(s), "face " + s);
      }
   }

   @Test
   void theClipAuthorAgreesWithTheMoveTable() throws IOException {
      String py = Files.readString(AUTHOR);
      int start = py.indexOf("MOVES = {");
      String table = py.substring(start, py.indexOf('}', start));
      Matcher m = Pattern.compile("'([a-z_0-9]+)': \\((\\d+), (-?\\d+)\\)").matcher(table);
      Set<String> authored = new HashSet<>();
      while (m.find()) {
         VenomMove move = null;
         for (VenomMove v : VenomMove.values()) {
            if (v.clip().equals(m.group(1))) {
               move = v;
            }
         }
         assertTrue(move != null, "venom_clips.py authors an unknown move " + m.group(1));
         assertEquals(move.length, Integer.parseInt(m.group(2)), move + " length");
         assertEquals(move.contact, Integer.parseInt(m.group(3)), move + " contact");
         authored.add(move.clip());
      }
      for (VenomMove v : VenomMove.values()) {
         assertTrue(v.loops() || authored.contains(v.clip()), "venom_clips.py lacks " + v.clip());
      }
   }

   @Test
   void everyBoneAClipMovesIsInTheModel() throws IOException {
      Set<String> bones = new HashSet<>();
      for (String geo : new String[]{"venom.geo.json", "venom_arms.geo.json"}) {
         String text = Files.readString(Path.of("src/main/resources/assets/viltrumitecore/geo/venom").resolve(geo));
         Matcher n = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
         while (n.find()) {
            bones.add(n.group(1));
         }
      }
      for (String file : new String[]{"venom_moves", "venom_lash", "venom_skills", "venom_stances", "venom_face", "venom_fp"}) {
         for (var clip : clips(file).entrySet()) {
            JsonObject b = clip.getValue().getAsJsonObject().getAsJsonObject("bones");
            if (b == null) {
               continue;
            }
            for (String bone : b.keySet()) {
               assertTrue(bones.contains(bone), file + "/" + clip.getKey() + " moves a bone the model lacks: " + bone);
            }
         }
      }
   }
}
