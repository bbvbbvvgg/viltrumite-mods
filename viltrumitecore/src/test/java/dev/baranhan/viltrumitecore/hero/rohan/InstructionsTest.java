package dev.baranhan.viltrumitecore.hero.rohan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.book.BookClass;
import dev.baranhan.viltrumitecore.hero.rohan.instruction.AttackKind;
import dev.baranhan.viltrumitecore.hero.rohan.instruction.Instruction;
import dev.baranhan.viltrumitecore.hero.rohan.instruction.Instructions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The instruction registry is the extension point: stable unique ids, every text in every locale, sane numbers. */
class InstructionsTest {
   static final List<String> LOCALES = List.of("en_us", "ru_ru", "de_de", "tr_tr", "zh_cn", "es_ar", "es_cl", "es_ec", "es_es", "es_mx", "es_uy",
      "es_ve");
   static final Path OVERLAY = Path.of("src/main/lang-overlays/rohan");

   static JsonObject lang(String locale) throws IOException {
      return JsonParser.parseString(Files.readString(OVERLAY.resolve(locale + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
   }

   @Test
   void idsAreStableUniqueAndLowercase() {
      Set<String> seen = new HashSet<>();
      for (Instruction i : Instructions.all()) {
         assertTrue(i.id().matches("[a-z][a-z_]{1,31}"), i.id());
         assertTrue(seen.add(i.id()), "duplicate " + i.id());
         assertSame(i, Instructions.byId(i.id()));
         assertTrue(i.inkCost() > 0.0F, i.id() + " costs nothing");
      }
      assertTrue(seen.size() >= 11, "the canon list plus game additions");
      assertEquals(null, Instructions.byId("nope"));
      assertEquals(null, Instructions.byId(null));
      assertThrows(IllegalStateException.class, () -> Instructions.register(Instructions.CALM), "ids are claimed once");
   }

   @Test
   void everyInstructionFitsSomeBookAndBossesTakeFewer() {
      int forBoss = 0;
      for (Instruction i : Instructions.all()) {
         boolean any = false;
         for (BookClass c : BookClass.values()) {
            any |= Instructions.classAllows(i, c);
            assertTrue(i.baseTicks(c) >= 0);
         }
         assertTrue(any, i.id() + " fits no book");
         if (Instructions.classAllows(i, BookClass.BOSS)) {
            forBoss++;
         }
      }
      assertTrue(forBoss < Instructions.all().size(), "a boss book refuses some lines");
   }

   @Test
   void everyTextExistsInEveryLocale() throws IOException {
      JsonObject en = lang("en_us");
      Set<String> refusals = refusalKeys();
      assertFalse(refusals.isEmpty());
      for (String locale : LOCALES) {
         JsonObject l = lang(locale);
         assertEquals(en.keySet(), l.keySet(), locale + " has other keys than en_us");
         for (Instruction i : Instructions.all()) {
            for (String k : List.of("book.viltrumitecore.instruction." + i.id(), "book.viltrumitecore.instruction." + i.id() + ".desc")) {
               assertTrue(l.has(k) && !l.get(k).getAsString().isBlank(), locale + " misses " + k);
            }
         }
         for (AttackKind k : AttackKind.values()) {
            String key = "book.viltrumitecore.kind." + k.name().toLowerCase(java.util.Locale.ROOT);
            assertTrue(l.has(key), locale + " misses " + key);
         }
         for (String r : refusals) {
            assertTrue(l.has("book.viltrumitecore.refuse." + r), locale + " misses refusal " + r);
         }
         for (String ev : List.of("door_summon", "door_touch", "dismiss", "book_open", "page_flip", "write", "book_close", "page_rip", "sketch",
            "nib_throw", "nib_hit", "counter", "whoosh", "manuscript", "resist", "refuse")) {
            assertTrue(l.has("subtitles.viltrumitecore.rohan." + ev), locale + " misses the subtitle of " + ev);
         }
         assertTrue(l.has("hero.viltrumitecore.rohan"));
      }
      assertTrue(lang("ru_ru").get("hero.viltrumitecore.rohan").getAsString().matches(".*[А-Яа-я].*"));
   }

   /** Every refusal reason the code returns ("return \"x\"" in the book code, the last argument of an instruction). */
   static Set<String> refusalKeys() throws IOException {
      Set<String> out = new HashSet<>();
      Set<String> ids = new HashSet<>(Instructions.ids());
      String root = "src/main/java/dev/baranhan/viltrumitecore/hero/rohan/";
      Matcher m = Pattern.compile("return \"([a-z_]+)\"").matcher(Files.readString(Path.of(root + "RohanBooks.java")));
      while (m.find()) {
         out.add(m.group(1));
      }
      Matcher q = Pattern.compile("\"([a-z_]+)\"").matcher(Files.readString(Path.of(root + "instruction/Instructions.java")));
      while (q.find()) {
         if (!ids.contains(q.group(1))) {
            out.add(q.group(1));
         }
      }
      for (String generic : List.of("no_subject", "no_point", "no_kind")) {
         out.add(generic);
      }
      return out;
   }

   @Test
   void soundOverlayMatchesTheRegistryAndTheFiles() throws IOException {
      JsonObject sounds = JsonParser.parseString(Files.readString(Path.of("src/main/sound-overlays/rohan.json"))).getAsJsonObject();
      String registry = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/rohan/RohanSounds.java"));
      Matcher m = Pattern.compile("reg\\(\"(rohan\\.[a-z_]+)\"\\)").matcher(registry);
      int n = 0;
      while (m.find()) {
         n++;
         String ev = m.group(1);
         assertTrue(sounds.has(ev), "no sound entry for " + ev);
         for (var e : sounds.getAsJsonObject(ev).getAsJsonArray("sounds")) {
            String name = e.getAsJsonObject().get("name").getAsString().substring("viltrumitecore:".length());
            assertTrue(Files.isRegularFile(Path.of("src/main/binassets/assets/viltrumitecore/sounds/" + name + ".ogg.b64")), "no audio " + name);
         }
      }
      assertEquals(16, n);
      assertEquals(16, sounds.size());
      assertNotNull(Files.readString(Path.of("src/main/resources/assets/viltrumitecore/sounds/rohan/SOURCES.md")));
   }
}
