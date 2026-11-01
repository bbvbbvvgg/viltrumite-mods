package dev.baranhan.viltrumitecore.worldevent.aftermath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Asset contracts of the aftermath: sound registry, overlay, packed .ogg files, subtitles, command texts, credits, particles. */
class AftermathAssetsTest {
   private static final Path ASSETS = Path.of("src/main/resources/assets/viltrumitecore");
   private static final Path BIN = Path.of("src/main/binassets/assets/viltrumitecore");
   private static final Path OVERLAY = Path.of("src/main/sound-overlays/aftermath.json");
   private static final Path LANG = Path.of("src/main/lang-overlays/aftermath");
   private static final Path JAVA = Path.of("src/main/java/dev/baranhan/viltrumitecore");
   private static final Path TOOL = Path.of("../tools/sfx/city_aftermath.py");
   private static final Path SOURCES = Path.of("../tools/assets/city_aftermath_sources.md");
   private static final List<String> LOCALES = List.of("de_de", "en_us", "es_ar", "es_cl", "es_ec", "es_es", "es_mx", "es_uy", "es_ve",
      "ru_ru", "tr_tr", "zh_cn");

   private static JsonObject read(Path path) throws IOException {
      return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
   }

   private static List<String> registered() throws IOException {
      Matcher m = Pattern.compile("reg\\(\"(aftermath\\.[a-z_]+)\"\\)").matcher(Files.readString(JAVA.resolve("worldevent/aftermath/AftermathSounds.java")));
      List<String> names = new ArrayList<>();
      while (m.find()) {
         names.add(m.group(1));
      }
      return names;
   }

   @Test
   void everySoundEventHasPackedVariantsAndSubtitles() throws IOException {
      List<String> names = registered();
      assertEquals(12, names.size());
      JsonObject overlay = read(OVERLAY);
      assertEquals(new TreeSet<>(names), new TreeSet<>(overlay.keySet()), "overlay and registry list the same events");
      TreeSet<String> files = new TreeSet<>();
      for (String name : names) {
         JsonObject event = overlay.getAsJsonObject(name);
         String subtitle = event.get("subtitle").getAsString();
         assertEquals("subtitles.viltrumitecore." + name, subtitle);
         for (JsonElement s : event.getAsJsonArray("sounds")) {
            String file = s.getAsJsonObject().get("name").getAsString().replace("viltrumitecore:", "");
            assertTrue(files.add(file), "each variant is used once: " + file);
            Path b64 = BIN.resolve("sounds/" + file + ".ogg.b64");
            assertTrue(Files.isRegularFile(b64), b64.toString());
            byte[] ogg = Base64.getMimeDecoder().decode(Files.readString(b64).trim());
            assertEquals("OggS", new String(ogg, 0, 4, StandardCharsets.US_ASCII), b64.toString());
            assertTrue(ogg.length < 44 * 1024, b64 + " fits one push request");
            assertTrue(!Files.exists(ASSETS.resolve("sounds/" + file + ".ogg")), "no raw twin of " + file);
         }
         boolean oneShot = !name.endsWith("fire_bed") && !name.endsWith("rumble") && !name.endsWith("wind");
         assertTrue(!oneShot || event.getAsJsonArray("sounds").size() >= 2, name + " has variants");
         for (String locale : LOCALES) {
            assertTrue(read(LANG.resolve(locale + ".json")).has(subtitle), locale + " " + subtitle);
         }
      }
      try (Stream<Path> s = Files.list(BIN.resolve("sounds/aftermath"))) {
         assertEquals(files.size(), s.count(), "no packed file without an event");
      }
   }

   @Test
   void everyCommandTextIsTranslated() throws IOException {
      Matcher m = Pattern.compile("\"(commands\\.viltrumitecore\\.aftermath\\.[a-z_]+)\"").matcher(Files.readString(JAVA.resolve("worldevent/aftermath/AftermathCommands.java")));
      TreeSet<String> keys = new TreeSet<>();
      while (m.find()) {
         keys.add(m.group(1));
      }
      assertTrue(keys.size() >= 9);
      JsonObject en = read(LANG.resolve("en_us.json"));
      for (String locale : LOCALES) {
         JsonObject lang = read(LANG.resolve(locale + ".json"));
         assertEquals(en.keySet(), lang.keySet(), locale + " has the same keys as en_us");
         for (String key : keys) {
            assertTrue(lang.has(key), locale + " " + key);
            assertEquals(count(en.get(key).getAsString()), count(lang.get(key).getAsString()), locale + " " + key + " keeps every %s");
         }
      }
   }

   private static int count(String s) {
      return s.split("%s", -1).length - 1;
   }

   @Test
   void everyRecordingIsCc0AndCredited() throws IOException {
      String tool = Files.readString(TOOL);
      String sources = Files.readString(SOURCES);
      String credits = Files.readString(Path.of("../CREDITS.md"));
      Matcher m = Pattern.compile("\\((\\d{5,7}), \"([^\"]+)\", \"").matcher(tool);
      int n = 0;
      while (m.find()) {
         n++;
         String id = m.group(1);
         assertTrue(sources.contains("https://freesound.org/people/" + m.group(2) + "/sounds/" + id + "/"), "landing page of " + id);
         assertTrue(credits.contains(id), "credits name " + id);
      }
      assertTrue(n >= 20, "sources table parsed: " + n);
      assertTrue(sources.contains("CC0 1.0") && !sources.contains("CC BY"), "only CC0 recordings");
   }

   @Test
   void ruinParticlesUseShippedTextures() throws IOException {
      for (String p : List.of("ruin_smoke", "ruin_plume", "ruin_haze", "ruin_dust", "wire_spark")) {
         JsonObject json = read(ASSETS.resolve("particles/" + p + ".json"));
         assertTrue(json.getAsJsonArray("textures").size() > 0, p);
         for (JsonElement t : json.getAsJsonArray("textures")) {
            String tex = t.getAsString().replace("viltrumitecore:", "");
            assertTrue(Files.isRegularFile(BIN.resolve("textures/particle/" + tex + ".png.b64")), p + " -> " + tex);
         }
      }
   }
}
