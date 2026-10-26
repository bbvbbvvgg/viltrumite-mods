package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.reinhard.reid.ReidDrawAudio;
import dev.baranhan.viltrumitecore.client.reinhard.reid.ReidDrawScene;
import dev.baranhan.viltrumitecore.client.reinhard.reid.ReidDrawSceneVfx;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** REINHARD OVERHAUL item 15: the draw's cues start once each, on their phase, for the caster, onlookers and late joiners alike. */
class ReidDrawAudioTest {
   private static final Path SCRIPT = Path.of("../tools/sfx/reinhard_draw.py");
   private static final Path SOUNDS = Path.of("src/main/resources/assets/viltrumitecore/sounds.json");

   private static ReidDrawAudio.Cue cue(String name) {
      return ReidDrawAudio.CUES.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
   }

   /** name -> {start, distance} from the CUES table of the mix script. */
   private static Map<String, int[]> scriptCues() throws Exception {
      String text = Files.readString(SCRIPT);
      String table = text.substring(text.indexOf("CUES = {"), text.indexOf("}", text.indexOf("CUES = {")));
      Matcher m = Pattern.compile("\"(reid_draw_\\w+)\": dict\\(start=(\\d+), distance=(\\d+),").matcher(table);
      Map<String, int[]> out = new LinkedHashMap<>();
      while (m.find()) {
         out.put(m.group(1), new int[]{Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))});
      }
      return out;
   }

   /** Every cue started over a whole draw seen from tick `from`, each client tick processed `repeats` times. */
   private static Map<String, List<Long>> run(long from, int repeats) {
      boolean[] started = new boolean[ReidDrawAudio.CUES.size()];
      Map<String, List<Long>> out = new HashMap<>();
      for (long d = from; d <= ReinhardRules.REID_DRAW_TICKS + 80; d++) {
         for (int i = 0; i < repeats; i++) {
            for (ReidDrawAudio.Cue c : ReidDrawAudio.due(d, started)) {
               out.computeIfAbsent(c.name(), key -> new ArrayList<>()).add(d);
            }
         }
      }
      return out;
   }

   @Test
   void cuesMatchTheMixScriptAndTheSoundDefinitions() throws Exception {
      Map<String, int[]> script = scriptCues();
      assertEquals(List.copyOf(script.keySet()), ReidDrawAudio.CUES.stream().map(ReidDrawAudio.Cue::name).toList(),
         "same cues, same order as tools/sfx/reinhard_draw.py");
      JsonObject sounds = JsonParser.parseString(Files.readString(SOUNDS)).getAsJsonObject();
      for (ReidDrawAudio.Cue c : ReidDrawAudio.CUES) {
         assertEquals(script.get(c.name())[0], c.start(), c.name() + " starts on the tick it was mixed for");
         JsonObject entry = sounds.getAsJsonObject("reinhard." + c.name()).getAsJsonArray("sounds").get(0).getAsJsonObject();
         assertEquals(script.get(c.name())[1], entry.get("attenuation_distance").getAsInt(), c.name() + " attenuation");
         assertTrue(c.volume() > 0.0F && c.volume() <= 1.0F, c.name() + " volume");
      }
   }

   @Test
   void cuesSitOnTheDrawPhases() throws Exception {
      String text = Files.readString(SCRIPT);
      Matcher m = Pattern.compile("KOIGUCHI, STRAIN, SNAP, RELEASE, LENGTH = (\\d+), (\\d+), (\\d+), (\\d+), (\\d+)").matcher(text);
      assertTrue(m.find(), "timeline constants of the mix script");
      assertEquals(List.of(ReidDrawChoreo.KOIGUCHI, ReidDrawChoreo.STRAIN, ReidDrawChoreo.SNAP, ReidDrawChoreo.RELEASE, ReidDrawChoreo.LENGTH),
         List.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)),
            Integer.parseInt(m.group(5))), "the script mixes on the Java draw timeline");
      assertEquals(ReinhardRules.REID_RELEASE_TICK, cue("reid_draw_release").start(), "the final accent is the release");
      // The clack is 0.05 s (one tick) into its file: the mouth breaks on the koiguchi tick.
      assertEquals(ReidDrawChoreo.KOIGUCHI, cue("reid_draw_koiguchi").start() + 1);
      // The scrape's exit is 3.000 s in: it lands on the release; the air is cut there (2.0 s file).
      assertEquals(ReinhardRules.REID_RELEASE_TICK, cue("reid_draw_scrape").start() + 60);
      assertEquals(ReinhardRules.REID_RELEASE_TICK, cue("reid_draw_air").start() + 40);
      assertTrue(cue("reid_draw_hum").start() < ReidDrawChoreo.KOIGUCHI, "the hum rises before the mouth breaks");
      int previous = -1;
      for (ReidDrawAudio.Cue c : ReidDrawAudio.CUES) {
         assertTrue(c.start() > previous && c.start() <= ReinhardRules.REID_RELEASE_TICK, c.name() + " in phase order, none after the release");
         previous = c.start();
      }
   }

   @Test
   void everyCueStartsOnceOnItsTickEvenWhenTicksRepeat() {
      Map<String, List<Long>> started = run(0, 3);
      assertEquals(ReidDrawAudio.CUES.size(), started.size());
      for (ReidDrawAudio.Cue c : ReidDrawAudio.CUES) {
         assertEquals(List.of((long)c.start()), started.get(c.name()), c.name());
      }
   }

   @Test
   void aLateJoinerHearsOnlyWhatIsStillAhead() {
      Map<String, List<Long>> started = run(40, 1);
      assertEquals(Map.of("reid_draw_air", List.of(56L), "reid_draw_release", List.of(96L)), started,
         "no pile of late starts: the hum, the koiguchi and the scrape are behind him");
      Map<String, List<Long>> afterRelease = run(ReinhardRules.REID_RELEASE_TICK + ReidDrawAudio.LATE_TICKS + 1, 1);
      assertTrue(afterRelease.isEmpty(), "after the release nothing starts");
   }

   @Test
   void aCueStartsAtMostLateTicksLate() {
      ReidDrawAudio.Cue scrape = cue("reid_draw_scrape");
      Map<String, List<Long>> justInTime = run(scrape.start() + ReidDrawAudio.LATE_TICKS, 1);
      assertEquals(List.of((long)scrape.start() + ReidDrawAudio.LATE_TICKS), justInTime.get("reid_draw_scrape"));
      Map<String, List<Long>> tooLate = run(scrape.start() + ReidDrawAudio.LATE_TICKS + 1, 1);
      assertTrue(!tooLate.containsKey("reid_draw_scrape"), "a missed scrape stays missed");
   }

   @Test
   void beforeTheStartNothingSounds() {
      boolean[] started = new boolean[ReidDrawAudio.CUES.size()];
      for (long d = -40; d < cue("reid_draw_hum").start(); d++) {
         assertTrue(ReidDrawAudio.due(d, started).isEmpty(), "d " + d);
      }
   }

   @Test
   void anAbortFadesOutSmoothlyAndEnds() {
      assertEquals(1.0F, ReidDrawAudio.fadeOut(0), 1.0E-6F);
      assertEquals(0.0F, ReidDrawAudio.fadeOut(ReidDrawScene.ABORT_FADE_TICKS), 1.0E-6F);
      float previous = 1.0F;
      for (int t = 1; t <= ReidDrawScene.ABORT_FADE_TICKS + 4; t++) {
         float k = ReidDrawAudio.fadeOut(t);
         assertTrue(k <= previous && previous - k <= 1.0F / ReidDrawScene.ABORT_FADE_TICKS + 1.0E-6F, "tick " + t);
         previous = k;
      }
   }

   @Test
   void theSceneDuckSparesItsOwnCues() {
      for (ReidDrawAudio.Cue c : ReidDrawAudio.CUES) {
         assertTrue(ReidDrawSceneVfx.OWN_SOUNDS.contains(new ResourceLocation("viltrumitecore", "reinhard." + c.name())), c.name());
      }
   }
}
