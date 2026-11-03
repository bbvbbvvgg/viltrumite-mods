package dev.baranhan.viltrumitecore.city.life;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Assets of the living city (issue #136): the generated catalog matches the voice files, every event plays the
 * mod's own file (no vanilla stand-ins) that is really in the jar, every subtitle exists in all 12 locales and the
 * Russian ones are Russian.
 */
class CityLifeAssetsTest {
   private static final Path MAIN = Path.of("src/main");
   private static final Path OVERLAYS = MAIN.resolve("sound-overlays");
   private static final Path LANG = MAIN.resolve("lang-overlays/city_life");
   private static final String[] LOCALES = {"en_us", "ru_ru", "de_de", "tr_tr", "zh_cn", "es_es", "es_mx", "es_ar", "es_cl", "es_ec", "es_uy",
      "es_ve"};

   private static JsonObject json(Path p) throws IOException {
      return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
   }

   private static boolean shipped(String name) {
      String rel = "assets/viltrumitecore/sounds/" + name.substring("viltrumitecore:".length()) + ".ogg";
      return Files.isRegularFile(MAIN.resolve("resources").resolve(rel)) || Files.isRegularFile(MAIN.resolve("binassets").resolve(rel + ".b64"))
         || Files.isRegularFile(MAIN.resolve("binassets").resolve(rel + ".b64.part0"));
   }

   private static JsonObject events() throws IOException {
      JsonObject all = json(OVERLAYS.resolve("city_life.json"));
      for (String v : CityLifeCatalog.VOICES) {
         for (Map.Entry<String, JsonElement> e : json(OVERLAYS.resolve("city_life_voice_" + v + ".json")).entrySet()) {
            assertFalse(all.has(e.getKey()), "one overlay per event: " + e.getKey());
            all.add(e.getKey(), e.getValue());
         }
      }
      return all;
   }

   @Test
   void catalogIsConsistent() {
      int lines = CityLifeCatalog.LINES.length;
      assertEquals(CityLifeCatalog.VOICES.length, CityLifeCatalog.TICKS.length);
      for (short[] row : CityLifeCatalog.TICKS) {
         assertEquals(lines, row.length);
         for (short t : row) {
            assertTrue(t >= 8 && t <= 160, "a take of 0.4..8 s: " + t);
         }
      }
      int n = CityLifeCatalog.DIALOGUES.length;
      assertEquals(n, CityLifeCatalog.MOOD.length);
      assertEquals(n, CityLifeCatalog.WHEN.length);
      assertEquals(n, CityLifeCatalog.WEIGHT.length);
      assertEquals(n, CityLifeCatalog.TURNS.length);
      Set<Integer> used = new HashSet<>();
      for (int d = 0; d < n; d++) {
         int[] turns = CityLifeCatalog.TURNS[d];
         assertTrue(turns.length >= 2, CityLifeCatalog.DIALOGUES[d] + " is a dialogue");
         for (int i = 0; i < turns.length; i++) {
            assertEquals(i % 2, turns[i] >>> 16, "roles alternate in " + CityLifeCatalog.DIALOGUES[d]);
            int line = turns[i] & 0xFFFF;
            assertTrue(line < lines && used.add(line), "line " + line + " belongs to one dialogue");
            assertTrue(CityLifeCatalog.LINES[line].startsWith(CityLifeCatalog.DIALOGUES[d] + "_"), CityLifeCatalog.LINES[line]);
         }
         assertTrue(CityLifeCatalog.WEIGHT[d] > 0);
      }
      String[] prefixes = {"startle_", "flee_", "help_", "survivor_"};
      int[][] calls = {CityLifeCatalog.CALL_STARTLE, CityLifeCatalog.CALL_FLEE, CityLifeCatalog.CALL_HELP, CityLifeCatalog.CALL_SURVIVOR};
      for (int k = 0; k < calls.length; k++) {
         assertTrue(calls[k].length >= 2, prefixes[k] + " has variants against repetition");
         for (int line : calls[k]) {
            assertTrue(CityLifeCatalog.LINES[line].startsWith(prefixes[k]) && used.add(line), CityLifeCatalog.LINES[line]);
         }
      }
      assertEquals(lines, used.size(), "every line is spoken somewhere");
   }

   @Test
   void everyEventPlaysAShippedFileWithASubtitle() throws IOException {
      JsonObject all = events();
      Set<String> subtitles = new TreeSet<>();
      for (int v = 0; v < CityLifeCatalog.VOICES.length; v++) {
         for (int l = 0; l < CityLifeCatalog.LINES.length; l++) {
            assertTrue(all.has(CityLife.lineEvent(v, l)), CityLife.lineEvent(v, l));
         }
      }
      for (String bed : new String[]{"crowd", "crowd_far", "steps", "running", "street_day", "street_night", "wind", "panic", "hush"}) {
         assertTrue(all.has("city_life.bed." + bed), bed);
         for (JsonElement s : all.getAsJsonObject("city_life.bed." + bed).getAsJsonArray("sounds")) {
            // static: the beds loop from memory and leave the 8 stream channels to music and SCP-096
            assertFalse(s.getAsJsonObject().has("stream"), "beds are static sounds: " + bed);
         }
      }
      for (String e : new String[]{"city_life.scream.male", "city_life.scream.female", "city_life.sob.male", "city_life.sob.female", "city_life.cough",
         "city_life.dog", "city_life.bike_bell", "city_life.pigeons", "city_life.laugh", "city_life.bells"}) {
         assertTrue(all.has(e), e);
      }
      for (Map.Entry<String, JsonElement> e : all.entrySet()) {
         assertTrue(e.getKey().startsWith("city_life."), e.getKey());
         JsonObject ev = e.getValue().getAsJsonObject();
         String sub = ev.get("subtitle").getAsString();
         assertTrue(sub.startsWith("subtitles.viltrumitecore.city_life."), e.getKey());
         subtitles.add(sub);
         assertTrue(ev.getAsJsonArray("sounds").size() >= 1);
         for (JsonElement s : ev.getAsJsonArray("sounds")) {
            String name = s.isJsonPrimitive() ? s.getAsString() : s.getAsJsonObject().get("name").getAsString();
            assertFalse(s.isJsonObject() && s.getAsJsonObject().has("type"), e.getKey() + " plays a file, not a vanilla event");
            assertTrue(name.startsWith("viltrumitecore:city_life/"), e.getKey() + ": " + name);
            assertTrue(shipped(name), "the sound is in the jar: " + name);
         }
      }
      for (String locale : LOCALES) {
         JsonObject lang = json(LANG.resolve(locale + ".json"));
         for (String sub : subtitles) {
            assertTrue(lang.has(sub) && !lang.get(sub).getAsString().isBlank(), locale + " " + sub);
         }
      }
   }

   @Test
   void localesMatchAndRussianIsRussian() throws IOException {
      Set<String> keys = new TreeSet<>(json(LANG.resolve("en_us.json")).keySet());
      for (String locale : LOCALES) {
         assertEquals(keys, new TreeSet<>(json(LANG.resolve(locale + ".json")).keySet()), locale);
      }
      try (var files = Files.list(LANG)) {
         assertEquals(LOCALES.length, files.count(), "exactly the 12 supported locales");
      }
      JsonObject ru = json(LANG.resolve("ru_ru.json"));
      for (String l : CityLifeCatalog.LINES) {
         String text = ru.get("subtitles.viltrumitecore.city_life.line." + l).getAsString();
         assertTrue(text.codePoints().anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC), l + ": " + text);
      }
      for (String k : new String[]{"viltrumitecore.city.life.status", "viltrumitecore.city.life.mood.calm", "viltrumitecore.city.life.mood.wary",
         "viltrumitecore.city.life.mood.panic", "viltrumitecore.city.life.mood.after", "viltrumitecore.city.life.talks", "viltrumitecore.city.life.calls",
         "viltrumitecore.city.life.reset", "viltrumitecore.city.help.life"}) {
         assertTrue(keys.contains(k), k);
      }
   }
}
