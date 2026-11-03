package dev.baranhan.viltrumitecore.t1000;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The T-1000's sounds and texts are complete: every registered event is declared in the overlay with a subtitle and
 * own Vorbis files (no vanilla sounds), every file is used, every locale carries the same keys, every key the Java
 * sources use exists, and the recordings are credited.
 */
class T1000AssetsTest {
   private static final Path ROOT = Path.of("..");
   private static final Path MAIN = Path.of("src/main");
   private static final Path SOUNDS = MAIN.resolve("binassets/assets/viltrumitecore/sounds");
   private static final Path LANG = MAIN.resolve("lang-overlays/t1000");
   private static final String[] LOCALES = {"en_us", "ru_ru", "de_de", "tr_tr", "zh_cn", "es_es", "es_mx", "es_ar", "es_cl", "es_ec", "es_uy",
      "es_ve"};

   private static JsonObject json(Path p) throws IOException {
      return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
   }

   private static Set<String> registeredEvents() throws IOException {
      String src = Files.readString(MAIN.resolve("java/dev/baranhan/viltrumitecore/t1000/T1000Sounds.java"));
      Set<String> out = new TreeSet<>();
      Matcher m = Pattern.compile("reg\\(\"([a-z0-9_.]+)\"\\)").matcher(src);
      while (m.find()) {
         out.add(m.group(1));
      }
      return out;
   }

   @Test
   void everySoundEventHasOwnVorbisFilesAndASubtitle() throws IOException {
      Set<String> events = registeredEvents();
      assertEquals(16, events.size(), "the full T-1000 sound set");
      JsonObject overlay = json(MAIN.resolve("sound-overlays/t1000.json"));
      assertEquals(events, new TreeSet<>(overlay.keySet()), "overlay == T1000Sounds");
      JsonObject en = json(LANG.resolve("en_us.json"));
      Set<String> used = new TreeSet<>();
      for (Map.Entry<String, JsonElement> e : overlay.entrySet()) {
         JsonObject ev = e.getValue().getAsJsonObject();
         assertEquals("hostile", ev.get("category").getAsString(), e.getKey());
         String subtitle = ev.get("subtitle").getAsString();
         assertTrue(en.has(subtitle), e.getKey() + " subtitle " + subtitle);
         assertFalse(ev.getAsJsonArray("sounds").isEmpty(), e.getKey() + " has sounds");
         for (JsonElement s : ev.getAsJsonArray("sounds")) {
            String name = s.getAsJsonObject().get("name").getAsString();
            assertTrue(name.startsWith("viltrumitecore:t1000/"), e.getKey() + " uses an own sound, not " + name);
            String file = name.substring("viltrumitecore:".length()) + ".ogg.b64";
            Path p = SOUNDS.resolve(file);
            assertTrue(Files.isRegularFile(p), "missing " + file);
            byte[] ogg = Base64.getMimeDecoder().decode(Files.readString(p));
            assertTrue(ogg.length > 2000, file + " is a real clip");
            assertEquals("OggS", new String(ogg, 0, 4, java.nio.charset.StandardCharsets.US_ASCII), file + " is Ogg");
            assertTrue(new String(ogg, 0, Math.min(ogg.length, 200), java.nio.charset.StandardCharsets.ISO_8859_1).contains("vorbis"),
               file + " is Vorbis");
            used.add(file);
         }
      }
      try (Stream<Path> files = Files.list(SOUNDS.resolve("t1000"))) {
         Set<String> present = new TreeSet<>();
         files.forEach(f -> present.add("t1000/" + f.getFileName()));
         assertEquals(present, used, "every committed T-1000 clip is used, and only those");
      }
   }

   @Test
   void everyLocaleHasTheSameNonEmptyKeys() throws IOException {
      Set<String> keys = new TreeSet<>(json(LANG.resolve("en_us.json")).keySet());
      for (String locale : LOCALES) {
         JsonObject lang = json(LANG.resolve(locale + ".json"));
         assertEquals(keys, new TreeSet<>(lang.keySet()), locale + " keys");
         for (Map.Entry<String, JsonElement> e : lang.entrySet()) {
            assertFalse(e.getValue().getAsString().isBlank(), locale + " " + e.getKey());
         }
      }
      try (Stream<Path> files = Files.list(LANG)) {
         assertEquals(LOCALES.length, files.count(), "exactly the 12 supported locales");
      }
   }

   @Test
   void keysUsedInJavaExist() throws IOException {
      JsonObject en = json(LANG.resolve("en_us.json"));
      String commands = Files.readString(MAIN.resolve("java/dev/baranhan/viltrumitecore/t1000/T1000Commands.java"));
      Matcher m = Pattern.compile("K \\+ \"([a-z0-9_.]+)\"").matcher(commands);
      String k = "commands.viltrumitecore.t1000.";
      int checked = 0;
      while (m.find()) {
         if (!m.group(1).endsWith(".")) {
            assertTrue(en.has(k + m.group(1)), "T1000Commands uses missing key " + k + m.group(1));
            checked++;
         }
      }
      assertTrue(checked >= 8, "found the command keys, " + checked);
      for (String s : new String[]{"on", "off", "natural.on", "natural.off"}) {
         assertTrue(en.has(k + s), k + s);
      }
      for (T1000SpawnRules.Result r : T1000SpawnRules.Result.values()) {
         assertTrue(en.has(k + "result." + r.name().toLowerCase(java.util.Locale.ROOT)), "spawn result " + r);
      }
      for (T1000State st : T1000State.values()) {
         assertTrue(en.has("t1000.viltrumitecore.state." + st.name().toLowerCase(java.util.Locale.ROOT)), "state " + st);
      }
      for (T1000Form f : T1000Form.values()) {
         assertTrue(en.has("t1000.viltrumitecore.form." + f.name().toLowerCase(java.util.Locale.ROOT)), "form " + f);
      }
   }

   @Test
   void recordingsAreCredited() throws IOException {
      String credits = Files.readString(ROOT.resolve("CREDITS.md"));
      String sources = Files.readString(ROOT.resolve("tools/assets/t1000_sources.md"));
      String recipe = Files.readString(ROOT.resolve("tools/sfx/t1000.py"));
      assertTrue(credits.contains("T-1000") && credits.contains("tools/assets/t1000_sources.md"), "CREDITS.md section");
      Matcher m = Pattern.compile("'([a-z0-9_]+)': \\('([0-9a-f-]{36})', '([^']+)'").matcher(recipe);
      int n = 0;
      while (m.find()) {
         assertTrue(sources.contains(m.group(2)), "sources list has " + m.group(1));
         assertTrue(credits.contains(m.group(3)), "CREDITS.md names " + m.group(3));
         n++;
      }
      assertTrue(n >= 20, "recipe sources parsed: " + n);
      assertFalse(sources.toLowerCase().contains("cc-by") || sources.contains("CC BY"), "CC0 only");
   }
}
