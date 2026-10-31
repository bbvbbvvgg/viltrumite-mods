package dev.baranhan.viltrumitecore.hero.speech;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.captain.CaptainLines;
import dev.baranhan.viltrumitecore.hero.dio.DioLines;
import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import dev.baranhan.viltrumitecore.hero.homelander.HomelanderLines;
import dev.baranhan.viltrumitecore.hero.ironman.IronManLines;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooLines;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusLines;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardLines;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairLines;
import dev.baranhan.viltrumitecore.hero.thor.ThorLines;
import dev.baranhan.viltrumitecore.integration.ShippedLang;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Global subtitles expansion (docs/reports/2026-10-31-global-subtitles-audit.md): every line of every
 * hero resolves in every shipped locale (no raw key on screen), the tables keep their anti-spam rules
 * and the ultimate pages follow their scene timelines.
 */
class AllHeroLinesTest {
   private static final Path SINCLAIR_LANG = Path.of("src/main/resources/assets/viltrumitecore_sinclair/lang");

   /** Hero key (default speaker) to its line table. */
   private static Map<String, HeroLines> tables() {
      Map<String, HeroLines> tables = new LinkedHashMap<>();
      tables.put("dio", DioLines.LINES);
      tables.put("thor", ThorLines.LINES);
      tables.put("jinwoo", JinwooLines.LINES);
      tables.put("reinhard", ReinhardLines.LINES);
      tables.put("regulus", RegulusLines.LINES);
      tables.put("sinclair", SinclairLines.LINES);
      tables.put("homelander", HomelanderLines.LINES);
      tables.put("ironman", IronManLines.LINES);
      tables.put("captain", CaptainLines.LINES);
      return tables;
   }

   @Test
   void everyLineResolvesInEveryLocale() throws Exception {
      Map<String, JsonObject> merged = ShippedLang.merged();
      assertEquals(12, merged.size(), "12 shipped locales");
      for (Map.Entry<String, JsonObject> locale : merged.entrySet()) {
         JsonObject lang = locale.getValue().deepCopy();
         Path own = SINCLAIR_LANG.resolve(locale.getKey());
         assertTrue(Files.isRegularFile(own), "Sinclair lang " + own);
         JsonParser.parseString(Files.readString(own)).getAsJsonObject().entrySet().forEach(e -> lang.add(e.getKey(), e.getValue()));
         for (Map.Entry<String, HeroLines> table : tables().entrySet()) {
            for (HeroLine line : table.getValue().all()) {
               List<String> keys = new ArrayList<>(line.fragments());
               keys.add(line.speakerKey() != null ? line.speakerKey() : HeroSpeechRules.defaultSpeakerKey(table.getKey()));
               for (String key : keys) {
                  assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), locale.getKey() + ": " + line.id() + " -> " + key);
               }
            }
         }
      }
   }

   @Test
   void russianAndEnglishAreReallyTranslated() throws Exception {
      Map<String, JsonObject> merged = ShippedLang.merged();
      JsonObject ru = merged.get("ru_ru.json");
      JsonObject en = merged.get("en_us.json");
      for (String key : List.of("subtitle.viltrumitecore.homelander.enter", "subtitle.viltrumitecore.ironman.missiles", "subtitle.viltrumitecore.jinwoo.riposte",
         "subtitle.viltrumitecore.thor.fatality", "subtitle.viltrumitecore.reinhard.phoenix", "subtitle.viltrumitecore.dio.knives",
         "subtitle.viltrumitecore.regulus.sand")) {
         String text = ru.get(key).getAsString();
         assertTrue(text.chars().anyMatch(c -> c >= 0x0400 && c <= 0x04FF), key + ": " + text);
         assertNotEquals(text, en.get(key).getAsString(), key);
      }
   }

   @Test
   void lineIdsAreUniqueAcrossHeroesAndSharedEventsStayQuiet() {
      Set<String> ids = new HashSet<>();
      for (Map.Entry<String, HeroLines> table : tables().entrySet()) {
         for (HeroLine line : table.getValue().all()) {
            assertTrue(ids.add(line.id()), "duplicate line id " + line.id());
         }

         // Shared events: the kill / low-health remarks are ambient (never cut a line) with a long cooldown.
         for (String event : List.of(HeroLineEvents.KILL, HeroLineEvents.LOW_HEALTH)) {
            for (HeroLine line : table.getValue().on(HeroLineTrigger.event(event))) {
               assertEquals(HeroLine.Priority.AMBIENT, line.priority(), line.id());
               assertTrue(line.cooldownTicks() >= 20 * 45, line.id() + " cooldown");
            }
         }

         assertFalse(table.getValue().on(HeroLineTrigger.event(HeroLineEvents.HERO_ENTER)).isEmpty(), table.getKey() + " greets on hero choice");
         assertTrue(table.getValue().on(HeroLineTrigger.event(HeroLineEvents.HERO_ENTER)).get(0).cooldownTicks() >= 20 * 60,
            table.getKey() + ": no greeting spam when switching heroes back and forth");
      }
   }

   @Test
   void ultimatePagesFollowTheirTimelines() {
      // DIO Road Roller: one page per beat, each ends where the next one begins.
      assertEquals(DioRules.ROLLER_APPEAR, DioLines.ROAD_ROLLER.durationTicks());
      assertEquals(DioRules.ROLLER_SLAM - DioRules.ROLLER_APPEAR, DioLines.ROLLER_APPEAR.durationTicks());
      assertEquals(DioRules.ROLLER_BARRAGE_END - DioRules.ROLLER_SLAM, DioLines.ROLLER_SLAM.durationTicks());
      for (HeroLine page : List.of(DioLines.ROAD_ROLLER, DioLines.ROLLER_APPEAR, DioLines.ROLLER_SLAM, DioLines.ROLLER_WRYYY)) {
         assertEquals(HeroLine.Priority.ULTIMATE, page.priority());
         assertNotNull(page.activeWhile(), page.id() + " stops with a cancelled ult");
         assertEquals(1, page.fragments().size());
      }

      // Thor Wrath: the call covers exactly the windup, "look up" comes with the strike.
      assertEquals(ThorLines.WRATH_WINDUP, ThorLines.WRATH.durationTicks());
      assertEquals(List.of("subtitle.viltrumitecore.thor.wrath.1"), ThorLines.WRATH.fragments());
      assertEquals(List.of(ThorLines.WRATH_STRIKE), ThorLines.LINES.on(HeroLineTrigger.event(ThorLines.WRATH_STRIKE_EVENT)));

      // Regulus: each phase line lasts its phase and stops if the scene aborts.
      assertEquals(RegulusRules.ULT_SENTENCE, RegulusLines.ULT_ACCUSE.durationTicks());
      assertEquals(RegulusRules.ULT_FLICK - RegulusRules.ULT_SENTENCE, RegulusLines.ULT_SENTENCE.durationTicks());
      assertNotNull(RegulusLines.ULT_FLICK.activeWhile());

      // Jinwoo: the ultimate is no longer timed by the reader's subtitle scale.
      assertEquals(JinwooLines.ARMY_TICKS, JinwooLines.ARMY.durationTicks());
   }

   @Test
   void phaseLinesAreReadableAtTheirPace() throws Exception {
      // A page with an explicit duration must not ask for more than ~22 characters a second (ru and en).
      Map<String, JsonObject> merged = ShippedLang.merged();
      List<HeroLine> timed = List.of(DioLines.ROAD_ROLLER, DioLines.ROLLER_APPEAR, DioLines.ROLLER_SLAM, ThorLines.WRATH, ThorLines.WRATH_STRIKE,
         RegulusLines.ULT_ACCUSE, RegulusLines.ULT_SENTENCE, RegulusLines.ULT_FLICK, JinwooLines.ARMY, IronManLines.HULK_SLEEP, IronManLines.HULK_RAM);
      for (String locale : List.of("ru_ru.json", "en_us.json")) {
         for (HeroLine line : timed) {
            int chars = 0;
            for (String key : line.fragments()) {
               chars += merged.get(locale).get(key).getAsString().length();
            }

            assertTrue(chars <= line.durationTicks() * 22 / 20 + 4, locale + " " + line.id() + ": " + chars + " chars in " + line.durationTicks() + " ticks");
         }
      }
   }
}
