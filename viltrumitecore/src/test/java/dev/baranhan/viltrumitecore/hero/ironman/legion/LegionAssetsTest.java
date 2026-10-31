package dev.baranhan.viltrumitecore.hero.ironman.legion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.ironman.IronManAbilities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Asset contracts of the Iron Legion: every sound event has its own synthesized file and a subtitle,
 * every event is played somewhere, every locale carries every key the code builds, and the ability icon exists.
 */
class LegionAssetsTest {
   private static final Path BIN = Path.of("src/main/binassets/assets/viltrumitecore");
   private static final Path SOUND_OVERLAY = Path.of("src/main/sound-overlays/legion.json");
   private static final Path LANG = Path.of("src/main/lang-overlays/legion");
   private static final Path JAVA = Path.of("src/main/java/dev/baranhan/viltrumitecore");
   private static final String[] LOCALES = {"en_us", "ru_ru", "de_de", "tr_tr", "zh_cn", "es_es", "es_mx", "es_ar", "es_cl", "es_ec", "es_uy", "es_ve"};

   private static JsonObject read(Path path) throws IOException {
      return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
   }

   private static List<String> soundNames() throws IOException {
      String src = Files.readString(JAVA.resolve("hero/ironman/IronManLegionSounds.java"));
      Matcher m = Pattern.compile("([A-Z_]+) = reg\\(\"ironman_legion_([a-z_]+)\"\\)").matcher(src);
      List<String> names = new ArrayList<>();
      while (m.find()) {
         assertEquals(m.group(1).toLowerCase(Locale.ROOT), m.group(2), "constant name matches the event");
         names.add(m.group(2));
      }

      assertEquals(12, names.size(), "twelve legion cues");
      return names;
   }

   private static String code(String... dirs) throws IOException {
      StringBuilder code = new StringBuilder();
      for (String dir : dirs) {
         try (Stream<Path> files = Files.walk(JAVA.resolve(dir))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java") && !p.endsWith("IronManLegionSounds.java")).toList()) {
               code.append(Files.readString(file));
            }
         }
      }

      return code.toString();
   }

   @Test
   void everySoundEventHasOwnFileAndSubtitle() throws IOException {
      JsonObject overlay = read(SOUND_OVERLAY);
      List<String> names = soundNames();
      assertEquals(names.size(), overlay.size(), "overlay has exactly the legion events");
      for (String locale : LOCALES) {
         JsonObject lang = read(LANG.resolve(locale + ".json"));
         for (String name : names) {
            JsonObject event = overlay.getAsJsonObject("ironman_legion_" + name);
            assertNotNull(event, name);
            assertTrue(lang.has(event.get("subtitle").getAsString()), locale + " subtitle " + name);
         }
      }

      for (String name : names) {
         JsonArray sounds = overlay.getAsJsonObject("ironman_legion_" + name).getAsJsonArray("sounds");
         assertFalse(sounds.isEmpty(), name);
         for (JsonElement sound : sounds) {
            String id = sound.isJsonObject() ? sound.getAsJsonObject().get("name").getAsString() : sound.getAsString();
            assertTrue(id.startsWith("viltrumitecore:ironman/legion/"), id);
            Path file = BIN.resolve("sounds/" + id.substring("viltrumitecore:".length()) + ".ogg.b64");
            assertTrue(Files.exists(file), "missing " + file);
            assertTrue(Files.size(file) > 1000, "empty " + file);
         }
      }
   }

   @Test
   void everySoundEventIsPlayedSomewhere() throws IOException {
      String code = code("hero/ironman", "client/ironman", "entity");
      for (String name : soundNames()) {
         String ref = "IronManLegionSounds." + name.toUpperCase(Locale.ROOT);
         assertTrue(code.contains(ref + ".") || code.contains(ref + ",") || code.contains(ref + ")"), "unused sound " + name);
      }
   }

   @Test
   void everyLocaleCarriesEveryKey() throws IOException {
      Set<String> en = read(LANG.resolve("en_us.json")).keySet();
      for (String locale : LOCALES) {
         JsonObject lang = read(LANG.resolve(locale + ".json"));
         assertEquals(en, lang.keySet(), locale + " key set");
         for (String key : lang.keySet()) {
            assertFalse(lang.get(key).getAsString().isBlank(), locale + " " + key);
         }
      }

      Set<String> built = new HashSet<>();
      for (LegionOrder order : LegionOrder.values()) {
         String key = "gui.viltrumitecore.legion.order." + order.name().toLowerCase(Locale.ROOT);
         built.add(key);
         built.add(key + ".hint");
      }

      for (LegionPriority priority : LegionPriority.values()) {
         built.add("gui.viltrumitecore.legion.priority." + priority.name().toLowerCase(Locale.ROOT));
      }

      for (LegionRole role : LegionRole.values()) {
         built.add("gui.viltrumitecore.legion.role." + role.name().toLowerCase(Locale.ROOT));
      }

      for (LegionPhase phase : LegionPhase.values()) {
         built.add("gui.viltrumitecore.legion.phase." + phase.name().toLowerCase(Locale.ROOT));
      }

      for (LegionTactics.Stance stance : LegionTactics.Stance.values()) {
         built.add("gui.viltrumitecore.legion.stance." + stance.name().toLowerCase(Locale.ROOT));
      }

      String id = IronManAbilities.LEGION.replace(':', '_');
      built.add("ability.viltrumitecore." + id + ".name");
      built.add("ability.viltrumitecore." + id + ".desc");
      built.add("entity.viltrumitecore.legion_suit");
      // Literal keys in the legion code (menu, HUD, refusals, JARVIS lines).
      Matcher m = Pattern.compile("\"((?:gui|hud|jarvis)\\.viltrumitecore\\.[a-z_.]*legion(?:[a-z_.]*[a-z])?)\"").matcher(code("client/ironman", "hero/ironman"));
      while (m.find()) {
         built.add(m.group(1));
      }

      for (String key : built) {
         assertTrue(en.contains(key), "missing key " + key);
      }
   }

   @Test
   void abilityIconExists() {
      String name = IronManAbilities.LEGION.substring(IronManAbilities.LEGION.indexOf(':') + 1);
      assertTrue(Files.exists(BIN.resolve("textures/gui/ability/ironman/" + name + ".png.b64")), "legion icon");
   }
}
