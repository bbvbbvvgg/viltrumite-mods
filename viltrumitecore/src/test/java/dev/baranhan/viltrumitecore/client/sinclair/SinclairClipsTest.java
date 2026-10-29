package dev.baranhan.viltrumitecore.client.sinclair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairMove;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Generated clips (tools/assets/make_sinclair_clips.py) agree with the server's move table and the model's bones. */
class SinclairClipsTest {
   private static final Path RES = Path.of("src/main/resources/assets/viltrumitecore");

   private static Map<String, JsonObject> clips() throws IOException {
      Map<String, JsonObject> out = new HashMap<>();
      try (DirectoryStream<Path> files = Files.newDirectoryStream(RES.resolve("animations/sinclair"), "*.animation.json")) {
         for (Path file : files) {
            JsonObject anims = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations");
            for (String name : anims.keySet()) {
               assertTrue(out.put(name, anims.getAsJsonObject(name)) == null, "duplicate clip " + name);
            }
         }
      }
      return out;
   }

   private static Set<String> bones(String model) throws IOException {
      JsonObject geo = JsonParser.parseString(Files.readString(RES.resolve("geo/sinclair/" + model + ".geo.json"), StandardCharsets.UTF_8)).getAsJsonObject();
      Set<String> out = new HashSet<>();
      geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")
         .forEach(b -> out.add(b.getAsJsonObject().get("name").getAsString()));
      return out;
   }

   @Test
   void everyMoveHasBodyAndFirstPersonClipsOfItsLength() throws IOException {
      Map<String, JsonObject> clips = clips();
      for (SinclairMove move : SinclairMove.values()) {
         for (String name : new String[]{move.clip(), "fp_" + move.clip()}) {
            JsonObject clip = clips.get(name);
            assertNotNull(clip, "missing clip " + name);
            assertEquals(move.length() / 20.0, clip.get("animation_length").getAsDouble(), 1e-6, name);
         }
      }
   }

   @Test
   void clipsOnlyAnimateExistingBones() throws IOException {
      Set<String> body = bones("sinclair");
      Set<String> arms = bones("sinclair_arms");
      Set<String> savannah = bones("savannah");
      Set<String> rabbit = bones("rabbit");
      Set<String> props = new HashSet<>(bones("chest"));
      props.addAll(bones("curtain"));
      for (Map.Entry<String, JsonObject> e : clips().entrySet()) {
         String name = e.getKey();
         Set<String> target = name.startsWith("fp_") ? arms : name.startsWith("sav_") ? savannah : name.startsWith("rab_") ? rabbit
            : name.startsWith("chest_") || name.startsWith("curtain_") ? props : body;
         for (String bone : e.getValue().getAsJsonObject("bones").keySet()) {
            assertTrue(target.contains(bone), name + " animates unknown bone " + bone);
         }
      }
   }

   @Test
   void bodyHasTheArticulationTheSpecAsksFor() throws IOException {
      Set<String> body = bones("sinclair");
      for (String side : new String[]{"right", "left"}) {
         for (String b : new String[]{"_arm", "_forearm", "_hand", "_thumb", "_thumb2", "_index", "_index2", "_middle", "_middle2", "_ring", "_ring2",
            "_leg", "_knee"}) {
            assertTrue(body.contains(side + b), side + b);
         }
         assertTrue(body.contains("coat_tail_" + side) && body.contains("coat_tail_" + side + "2"), "coat tail " + side);
      }
      for (String b : new String[]{"head", "hat", "held_hat", "wand", "wand_tip", "card", "medallion", "lapel_left", "lapel_right", "vest_hem"}) {
         assertTrue(body.contains(b), b);
      }
   }
}
