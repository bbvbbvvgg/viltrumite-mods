package dev.baranhan.viltrumitecore.client.sinclair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairAbilities;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairLines;
import dev.baranhan.viltrumitecore.hero.sinclair.act.SinclairRepertoire;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every Sinclair string the code asks for exists, non-empty, in all 12 locales (plan step 8). The strings live in the
 * hero's own lang pack namespace {@code assets/viltrumitecore_sinclair/lang} (vanilla merges every namespace's lang files).
 */
class SinclairLangTest {
   private static final Path RES = Path.of("src/main/resources/assets/viltrumitecore");
   private static final Path OWN_LANG = Path.of("src/main/resources/assets/viltrumitecore_sinclair/lang");
   private static final String[] LOCALES = {"de_de", "en_us", "es_ar", "es_cl", "es_ec", "es_es", "es_mx", "es_uy", "es_ve", "ru_ru", "tr_tr", "zh_cn"};

   private static JsonObject read(Path path) throws IOException {
      return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
   }

   private static List<String> requiredKeys() throws IOException {
      List<String> keys = new ArrayList<>();
      keys.add("hero.viltrumitecore.sinclair");
      keys.add("gui.viltrumitecore.race_selection.become_sinclair");
      keys.add("subtitle.viltrumitecore.speaker.sinclair");
      for (String id : SinclairAbilities.OWN) {
         keys.add("ability.viltrumitecore.sinclair_" + SinclairAbilities.key(id) + ".name");
         keys.add("ability.viltrumitecore.sinclair_" + SinclairAbilities.key(id) + ".desc");
      }

      for (String line : SinclairLines.KEYS) {
         keys.add(SinclairLines.PREFIX + line);
      }

      for (ActShape shape : ActShape.values()) {
         keys.add(SinclairRepertoire.nameKey(shape));
         keys.add(SinclairRepertoire.nameKey(shape) + ".desc");
      }

      for (String hud : new String[]{"hexed", "window", "veil", "assistant", "ovation", "fallback", "card_title", "card_from"}) {
         keys.add("hud.viltrumitecore.sinclair." + hud);
      }

      // pass-1 events in the shared sounds.json, pass-2 events in Sinclair's own namespace (SinclairSounds.stage)
      for (Path file : new Path[]{RES.resolve("sounds.json"), OWN_LANG.getParent().resolve("sounds.json")}) {
         JsonObject sounds = read(file);
         for (String event : sounds.keySet()) {
            if (event.startsWith("sinclair.")) {
               keys.add(sounds.getAsJsonObject(event).get("subtitle").getAsString());
            }
         }
      }

      keys.addAll(dev.baranhan.viltrumitecore.hero.sinclair.act.CopyCard.allKeys());
      for (String command : new String[]{"on", "off", "status", "state_on", "state_off"}) {
         keys.add("commands.viltrumitecore.sinclair.cinematic." + command);
      }

      for (String entity : new String[]{"spark", "vexing_bolt", "assistant", "decoy", "hex_hat", "hex_mark", "act_prop", "act_card"}) {
         keys.add("entity.viltrumitecore.sinclair_" + entity);
      }

      return keys;
   }

   @Test
   void everyLocaleHasEveryKey() throws IOException {
      List<String> keys = requiredKeys();
      // Base 78; #92 adds 8 (six act descriptions, card_title, card_from) = 86; #91 adds 51 (copy cinematic, cinematic
      // command, act parts, own-namespace subtitles) = 129; both together: 78 + 8 + 51 = 137.
      assertEquals(137, keys.size(), "key table changed: update tools/lang/sinclair_lang.py");
      for (String locale : LOCALES) {
         JsonObject lang = read(OWN_LANG.resolve(locale + ".json"));
         for (String key : keys) {
            assertTrue(lang.has(key), locale + " misses " + key);
            assertFalse(lang.get(key).getAsString().isBlank(), locale + " blank " + key);
         }
      }
   }

   @Test
   void sameKeySetEverywhereAndNoClashWithSharedFiles() throws IOException {
      JsonObject en = read(OWN_LANG.resolve("en_us.json"));
      JsonObject shared = read(RES.resolve("lang/en_us.json"));
      for (String locale : LOCALES) {
         assertEquals(en.keySet(), read(OWN_LANG.resolve(locale + ".json")).keySet(), locale);
      }

      for (String key : en.keySet()) {
         assertFalse(shared.has(key), "also in the shared lang file: " + key);
      }
   }

   @Test
   void placeholdersMatchEnglish() throws IOException {
      JsonObject en = read(OWN_LANG.resolve("en_us.json"));
      for (String locale : LOCALES) {
         JsonObject lang = read(OWN_LANG.resolve(locale + ".json"));
         for (String key : requiredKeys()) {
            assertEquals(count(en.get(key).getAsString()), count(lang.get(key).getAsString()), locale + " placeholders " + key);
         }
      }
   }

   @Test
   void russianIsTranslated() throws IOException {
      JsonObject en = read(OWN_LANG.resolve("en_us.json"));
      JsonObject ru = read(OWN_LANG.resolve("ru_ru.json"));
      assertFalse(en.get("hero.viltrumitecore.sinclair").getAsString().equals(ru.get("hero.viltrumitecore.sinclair").getAsString()));
      assertFalse(en.get(SinclairLines.PREFIX + "capture_a").getAsString().equals(ru.get(SinclairLines.PREFIX + "capture_a").getAsString()));
   }

   private static int count(String text) {
      int n = 0;
      for (int i = text.indexOf("%s"); i >= 0; i = text.indexOf("%s", i + 2)) {
         n++;
      }

      return n;
   }
}
