package dev.baranhan.viltrumitecore.hero.speech;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardLines;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeroLinesTest {
   private static final Path LANG = Path.of("src/main/resources/assets/viltrumitecore/lang");

   @Test
   void builderValidatesAndDefaults() {
      HeroLine line = HeroLine.builder("hero:line").text("a.key").build();
      assertEquals(List.of("a.key"), line.fragments());
      assertEquals(HeroLine.Priority.ABILITY, line.priority());
      assertEquals(0, line.durationTicks(), "auto duration by default");
      assertEquals(HeroLine.DEFAULT_COOLDOWN_TICKS, line.cooldownTicks());
      assertNull(line.speakerKey());
      assertThrows(IllegalArgumentException.class, () -> HeroLine.builder("noNamespace").text("k").build());
      assertThrows(IllegalArgumentException.class, () -> HeroLine.builder("hero:empty").build());
      assertThrows(IllegalArgumentException.class, () -> HeroLine.builder("hero:blank").text(" ").build());
      assertThrows(IllegalArgumentException.class, () -> HeroLine.builder("hero:long").textSeries("k.", HeroLine.MAX_FRAGMENTS + 1).build());
      assertThrows(IllegalArgumentException.class, () -> HeroLine.builder("hero:t").text("k").duration(-1).build());
      assertThrows(IllegalArgumentException.class, () -> HeroLine.builder("hero:t").text("k").range(1000).build());
      assertEquals(List.of("m.1", "m.2", "m.3"), HeroLine.builder("hero:monologue").textSeries("m.", 3).build().fragments());
   }

   @Test
   void triggersHoldVariantsInOrderAndIdsAreUnique() {
      HeroLine a = HeroLine.builder("hero:a").text("k.a").build();
      HeroLine b = HeroLine.builder("hero:b").text("k.b").build();
      HeroLine scene = HeroLine.builder("hero:scene").textSeries("k.scene.", 2).build();
      HeroLines lines = HeroLines.builder().onAbility("hero:strike", a).onAbility("hero:strike", b).add(scene).build();
      assertEquals(List.of(a, b), lines.on(HeroLineTrigger.ability("hero:strike")));
      assertTrue(lines.on(HeroLineTrigger.event("hero:strike")).isEmpty(), "abilities and events are separate triggers");
      assertEquals(scene, lines.byId("hero:scene").orElseThrow());
      assertEquals(3, lines.all().size());
      assertThrows(IllegalStateException.class, () -> HeroLines.builder().add(a).add(a));
      assertTrue(HeroLines.NONE.isEmpty());
   }

   @Test
   void reinhardSpeaksOnWorldCutAsAnUltimate() {
      List<HeroLine> lines = ReinhardLines.LINES.on(HeroLineTrigger.ability("reinhard:world_cut"));
      assertEquals(List.of(ReinhardLines.WORLD_CUT), lines);
      assertEquals(HeroLine.Priority.ULTIMATE, ReinhardLines.WORLD_CUT.priority());
      assertEquals(List.of("subtitle.viltrumitecore.reinhard.world_cut"), ReinhardLines.WORLD_CUT.fragments());
   }

   @Test
   void everyLocaleCarriesTheSubtitleText() throws Exception {
      List<String> keys = new ArrayList<>();
      keys.add(HeroSpeechRules.defaultSpeakerKey("reinhard"));
      ReinhardLines.LINES.all().forEach(line -> keys.addAll(line.fragments()));
      keys.addAll(List.of("subtitle.viltrumitecore.preview.short", "subtitle.viltrumitecore.preview.long.1", "subtitle.viltrumitecore.preview.long.2",
         "commands.viltrumitecore.herolines.unknown", "commands.viltrumitecore.herolines.skipped", "commands.viltrumitecore.herolines.played",
         "commands.viltrumitecore.herolines.stopped"));
      List<Path> files;
      try (var listing = Files.list(LANG)) {
         files = listing.filter(p -> p.toString().endsWith(".json")).toList();
      }

      assertTrue(files.stream().anyMatch(p -> p.endsWith("ru_ru.json")), "Russian is the reference language of the lines");
      for (Path file : files) {
         JsonObject lang = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
         for (String key : keys) {
            assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), file.getFileName() + " " + key);
         }
      }

      JsonObject ru = JsonParser.parseString(Files.readString(LANG.resolve("ru_ru.json"))).getAsJsonObject();
      assertEquals("Мой меч способен рассечь сам мир.\nНи расстояние, ни пространство\nне станут преградой для этого удара.",
         ru.get("subtitle.viltrumitecore.reinhard.world_cut").getAsString());
      assertEquals("Рейнхард", ru.get("subtitle.viltrumitecore.speaker.reinhard").getAsString());
   }
}
