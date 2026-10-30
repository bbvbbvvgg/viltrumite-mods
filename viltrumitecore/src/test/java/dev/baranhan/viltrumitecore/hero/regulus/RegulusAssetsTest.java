package dev.baranhan.viltrumitecore.hero.regulus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Asset contracts of Regulus Reborn: clip names and lengths equal the server's move lengths, every
 * animated bone exists in the rig, every sound event has its own files and subtitle, and every
 * locale carries every line.
 */
class RegulusAssetsTest {
   private static final Path ASSETS = Path.of("src/main/resources/assets/viltrumitecore");
   private static final Path BIN = Path.of("src/main/binassets/assets/viltrumitecore");
   private static final Path SOUND_OVERLAY = Path.of("src/main/sound-overlays/regulus.json");
   private static final Path LANG = Path.of("src/main/lang-overlays/regulus");
   private static final Path JAVA = Path.of("src/main/java/dev/baranhan/viltrumitecore");

   private static JsonObject read(Path path) throws IOException {
      return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
   }

   private static Map<String, JsonObject> clips(String prefix) throws IOException {
      Map<String, JsonObject> all = new HashMap<>();
      try (Stream<Path> files = Files.list(ASSETS.resolve("animations/regulus"))) {
         for (Path file : files.filter(p -> p.getFileName().toString().startsWith(prefix)).toList()) {
            for (Map.Entry<String, JsonElement> e : read(file).getAsJsonObject("animations").entrySet()) {
               assertTrue(all.put(e.getKey(), e.getValue().getAsJsonObject()) == null, "duplicate clip " + e.getKey());
            }
         }
      }

      return all;
   }

   private static Set<String> bones(String geo) throws IOException {
      Set<String> names = new HashSet<>();
      JsonArray list = read(ASSETS.resolve("geo/regulus/" + geo)).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
      for (JsonElement bone : list) {
         names.add(bone.getAsJsonObject().get("name").getAsString());
      }

      return names;
   }

   @Test
   void everyMoveHasABodyAndAnArmsClipOfTheServerLength() throws IOException {
      Map<String, JsonObject> body = clips("regulus_");
      for (RegulusMove move : RegulusMove.values()) {
         for (String name : new String[]{move.clip(), "fp_" + move.clip()}) {
            JsonObject clip = body.get(name);
            assertNotNull(clip, "missing clip " + name);
            double seconds = clip.get("animation_length").getAsDouble();
            assertEquals(move.length(), Math.round(seconds * 20.0), name + " length");
            assertFalse(clip.has("loop") && clip.get("loop").getAsBoolean(), name + " must not loop");
         }
      }

      for (String loop : new String[]{"idle", "walk", "run", "crouch_walk", "crouch", "air", "fp_idle"}) {
         assertTrue(body.containsKey(loop), loop);
         assertTrue(body.get(loop).get("loop").getAsBoolean(), loop + " loops");
      }
   }

   @Test
   void clipsOnlyAnimateBonesOfTheirRig() throws IOException {
      Set<String> body = bones("regulus.geo.json");
      Set<String> arms = bones("regulus_arms.geo.json");
      for (Map.Entry<String, JsonObject> clip : clips("regulus_").entrySet()) {
         Set<String> rig = clip.getKey().startsWith("fp_") ? arms : body;
         JsonObject animated = clip.getValue().getAsJsonObject("bones");
         assertNotNull(animated, clip.getKey());
         for (String bone : animated.keySet()) {
            assertTrue(rig.contains(bone), clip.getKey() + " animates unknown bone " + bone);
         }
      }
   }

   @Test
   void rigHasTheBonesTheRendererAndVfxUse() throws IOException {
      Set<String> body = bones("regulus.geo.json");
      for (String bone : new String[]{"root", "hips", "torso", "chest", "head", "right_hand", "left_hand", "right_index", "right_palm", "left_palm",
         "heart", "right_foot", "left_foot", "coat_back"}) {
         assertTrue(body.contains(bone), bone);
      }

      assertTrue(Files.exists(BIN.resolve("textures/entity/regulus/regulus.png.b64")) || Files.exists(ASSETS.resolve("textures/entity/regulus/regulus.png")),
         "skin texture");
   }

   private static List<String> soundNames() throws IOException {
      String src = Files.readString(JAVA.resolve("hero/regulus/RegulusSounds.java"));
      Matcher block = Pattern.compile("NAMES = List\\.of\\((.*?)\\);", Pattern.DOTALL).matcher(src);
      assertTrue(block.find());
      List<String> names = new ArrayList<>();
      Matcher m = Pattern.compile("\"([a-z_]+)\"").matcher(block.group(1));
      while (m.find()) {
         names.add(m.group(1));
      }

      Matcher reg = Pattern.compile("reg\\(\"regulus\\.([a-z_]+)\"\\)").matcher(src);
      Set<String> registered = new HashSet<>();
      while (reg.find()) {
         registered.add(reg.group(1));
      }

      assertEquals(registered, new HashSet<>(names), "NAMES equals the registered events");
      return names;
   }

   @Test
   void everySoundEventHasOwnFilesAndASubtitle() throws IOException {
      JsonObject overlay = read(SOUND_OVERLAY);
      JsonObject en = read(LANG.resolve("en_us.json"));
      JsonObject ru = read(LANG.resolve("ru_ru.json"));
      List<String> names = soundNames();
      assertEquals(names.size(), overlay.size(), "overlay has exactly the Reborn events");
      for (String name : names) {
         JsonObject event = overlay.getAsJsonObject("regulus." + name);
         assertNotNull(event, name);
         String subtitle = event.get("subtitle").getAsString();
         assertTrue(en.has(subtitle), "en subtitle " + subtitle);
         assertTrue(ru.has(subtitle), "ru subtitle " + subtitle);
         JsonArray sounds = event.getAsJsonArray("sounds");
         assertFalse(sounds.isEmpty(), name);
         for (JsonElement sound : sounds) {
            String id = sound.isJsonObject() ? sound.getAsJsonObject().get("name").getAsString() : sound.getAsString();
            assertTrue(id.startsWith("viltrumitecore:regulus/reborn/"), id);
            String file = id.substring("viltrumitecore:".length()) + ".ogg.b64";
            assertTrue(Files.exists(BIN.resolve("sounds/" + file)), "missing " + file);
         }
      }
   }

   @Test
   void everySoundEventIsPlayedSomewhere() throws IOException {
      StringBuilder code = new StringBuilder();
      for (String dir : new String[]{"hero/regulus", "client/regulus"}) {
         try (Stream<Path> files = Files.walk(JAVA.resolve(dir))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java") && !p.endsWith("RegulusSounds.java")).toList()) {
               code.append(Files.readString(file));
            }
         }
      }

      for (String name : soundNames()) {
         assertTrue(code.indexOf("RegulusSounds." + name.toUpperCase() + ",") >= 0 || code.indexOf("RegulusSounds." + name.toUpperCase() + ".") >= 0
            || code.indexOf("RegulusSounds." + name.toUpperCase() + ")") >= 0, "unused sound " + name);
      }
   }

   @Test
   void everyLocaleCarriesEveryKey() throws IOException {
      JsonObject en = read(LANG.resolve("en_us.json"));
      Set<String> required = new HashSet<>();
      for (String key : RegulusLines.KEYS) {
         required.add(RegulusLines.PREFIX + key);
      }

      for (String id : RegulusAbilities.OWN) {
         required.add("ability.viltrumitecore.regulus_" + RegulusAbilities.key(id) + ".name");
         required.add("ability.viltrumitecore.regulus_" + RegulusAbilities.key(id) + ".desc");
      }

      for (String id : new String[]{RegulusAbilities.GREEDS_TOUCH, RegulusAbilities.LITTLE_KING}) {
         required.add("ability.viltrumitecore.regulus_" + RegulusAbilities.key(id) + ".name");
      }

      Matcher used = Pattern.compile("\"((?:hud|scan)\\.viltrumitecore\\.regulus\\.[a-z_]+)\"").matcher(
         Files.readString(JAVA.resolve("client/regulus/RegulusHud.java")) + Files.readString(JAVA.resolve("hero/regulus/RegulusHero.java")));
      while (used.find()) {
         required.add(used.group(1));
      }

      for (String key : required) {
         assertTrue(en.has(key), "en_us " + key);
      }

      try (Stream<Path> files = Files.list(LANG)) {
         List<Path> locales = files.toList();
         assertTrue(locales.size() >= 12, "locales " + locales.size());
         for (Path locale : locales) {
            JsonObject lang = read(locale);
            assertEquals(en.keySet(), lang.keySet(), locale.getFileName() + " key set");
            for (String key : lang.keySet()) {
               assertFalse(lang.get(key).getAsString().isBlank(), locale.getFileName() + " " + key);
            }
         }
      }
   }

   @Test
   void russianIsReallyRussian() throws IOException {
      JsonObject ru = read(LANG.resolve("ru_ru.json"));
      for (String key : RegulusLines.KEYS) {
         String line = ru.get(RegulusLines.PREFIX + key).getAsString();
         assertTrue(line.chars().anyMatch(c -> c >= 0x0400 && c <= 0x04FF), key + ": " + line);
      }
   }
}
