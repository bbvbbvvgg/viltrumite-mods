package dev.baranhan.viltrumitecore.hero.redhulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The Red Hulk's sounds and texts are complete: every registered sound event is declared in the overlay with a
 * subtitle and points at an own file (no vanilla {@code minecraft:} sounds), every locale carries the same keys,
 * and every red_hulk lang key the Java sources use exists.
 */
class RedHulkAssetsTest {
   private static final Path MAIN = Path.of("src/main");
   private static final Path SOUNDS = MAIN.resolve("binassets/assets/viltrumitecore/sounds");
   private static final Path LANG = MAIN.resolve("lang-overlays/red_hulk");
   private static final String[] LOCALES = {"en_us", "ru_ru", "de_de", "tr_tr", "zh_cn", "es_es", "es_mx", "es_ar", "es_cl", "es_ec", "es_uy",
      "es_ve"};

   private static JsonObject json(Path p) throws IOException {
      return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
   }

   private static Set<String> registeredEvents() throws IOException {
      String src = Files.readString(MAIN.resolve("java/dev/baranhan/viltrumitecore/hero/redhulk/RedHulkSounds.java"));
      Set<String> out = new TreeSet<>();
      Matcher m = Pattern.compile("reg\\(\"([a-z0-9_.]+)\"\\)").matcher(src);
      while (m.find()) {
         out.add(m.group(1));
      }
      return out;
   }

   @Test
   void everySoundEventIsDeclaredWithAnOwnFileAndSubtitle() throws IOException {
      Set<String> events = registeredEvents();
      assertTrue(events.size() >= 20, "the hero has a full sound set, was " + events.size());
      JsonObject overlay = json(MAIN.resolve("sound-overlays/red_hulk.json"));
      assertEquals(events, new TreeSet<>(overlay.keySet()), "overlay == RedHulkSounds");
      JsonObject en = json(LANG.resolve("en_us.json"));
      for (Map.Entry<String, JsonElement> e : overlay.entrySet()) {
         JsonObject ev = e.getValue().getAsJsonObject();
         String subtitle = ev.get("subtitle").getAsString();
         assertTrue(en.has(subtitle), e.getKey() + " subtitle " + subtitle);
         for (JsonElement s : ev.getAsJsonArray("sounds")) {
            String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
            assertTrue(name.startsWith("viltrumitecore:red_hulk/"), e.getKey() + " uses an own sound, not " + name);
            String file = name.substring("viltrumitecore:".length()) + ".ogg.b64";
            assertTrue(Files.isRegularFile(SOUNDS.resolve(file)) || Files.isRegularFile(SOUNDS.resolve(file + ".part0")), "missing " + file);
         }
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
      Pattern key = Pattern.compile("\"((?:hud|ability|hero|codex|subtitles?|speech|line|gui|race)[a-z0-9_.]*red_hulk[a-z0-9_.]*)\"");
      int checked = 0;
      for (String dir : new String[]{"hero/redhulk", "client/redhulk"}) {
         try (Stream<Path> files = Files.list(MAIN.resolve("java/dev/baranhan/viltrumitecore/" + dir))) {
            for (Path f : (Iterable<Path>)files::iterator) {
               Matcher m = key.matcher(Files.readString(f));
               while (m.find()) {
                  String k = m.group(1);
                  if (k.endsWith(".")) {
                     continue;
                  }
                  assertTrue(en.has(k), f.getFileName() + " uses missing key " + k);
                  checked++;
               }
            }
         }
      }
      for (int tier = 0; tier <= 3; tier++) {
         assertTrue(en.has("hud.viltrumitecore.red_hulk.tier." + tier));
      }
      assertTrue(en.has("hud.viltrumitecore.red_hulk.overheat"));
      for (RedHulkAbility a : RedHulkAbility.values()) {
         assertTrue(en.has("ability.viltrumitecore.red_hulk_" + a.key() + ".name"), a + " name");
         assertTrue(en.has("ability.viltrumitecore.red_hulk_" + a.key() + ".desc"), a + " description");
      }
      for (String line : new String[]{"enter", "fury", "critical", "overheated"}) {
         assertTrue(en.has("subtitle.viltrumitecore.red_hulk." + line), "subtitle line " + line);
      }
      assertTrue(en.has("hero.viltrumitecore.red_hulk") && en.has("gui.viltrumitecore.race_selection.become_red_hulk"));
      assertTrue(checked > 0, "found the keys the sources use");
   }
}
