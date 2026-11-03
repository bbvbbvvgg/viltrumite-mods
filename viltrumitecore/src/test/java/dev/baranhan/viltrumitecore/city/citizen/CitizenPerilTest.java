package dev.baranhan.viltrumitecore.city.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Issue #138: rules of citizens in peril (falls, ledges, voices) and the wiring that must not regress. */
class CitizenPerilTest {
   private static final Path SRC = Path.of("src/main/java/dev/baranhan/viltrumitecore");

   @Test
   void peril_states_are_panicked_and_spread_the_panic() {
      for (CitizenState s : new CitizenState[]{CitizenState.TEETER, CitizenState.HANGING, CitizenState.FALLING}) {
         assertTrue(s.peril(), s + " is peril");
         assertTrue(s.panicked(), s + " owns the body like a panic");
         assertTrue(s.contagious(), s + " alarms the people around");
         assertFalse(s.calm(), s + " is not calm");
      }

      for (CitizenState s : CitizenState.values()) {
         if (s != CitizenState.TEETER && s != CitizenState.HANGING && s != CitizenState.FALLING) {
            assertFalse(s.peril(), s + " is not peril");
         }
      }
   }

   @Test
   void a_fall_needs_a_real_drop_and_a_scream_a_long_one() {
      assertFalse(PerilRules.isFall(1.0), "a step down");
      assertFalse(PerilRules.isFall(3.9), "a short hop (vanilla fall damage starts at 3)");
      assertTrue(PerilRules.isFall(4.0));
      assertTrue(PerilRules.isFall(80.0));
      assertTrue(PerilRules.SCREAM_DROP > PerilRules.FALL_MIN_DROP, "short falls do not scream");
   }

   @Test
   void grabbing_a_ledge_is_likelier_in_a_collapse_and_never_certain() {
      for (float b = 0.0F; b <= 1.0F; b += 0.25F) {
         float cause = PerilRules.grabChance(b, true);
         float plain = PerilRules.grabChance(b, false);
         assertTrue(cause > plain, "the floor gave way under the feet: more time to grab");
         assertTrue(cause > 0.0F && cause <= 0.85F);
         assertTrue(plain > 0.0F && plain <= 0.85F);
      }

      assertTrue(PerilRules.grabChance(1.0F, true) > PerilRules.grabChance(0.0F, true), "braver is quicker");
      assertTrue(PerilRules.climbChance(1.0F) > PerilRules.climbChance(0.0F));
      assertTrue(PerilRules.climbChance(1.0F) < 1.0F, "some let go");
   }

   @Test
   void a_ledge_is_in_reach_of_raised_hands_only() {
      assertFalse(PerilRules.reachable(10.0, 10.3), "at the feet");
      assertTrue(PerilRules.reachable(10.0, 11.0));
      assertTrue(PerilRules.reachable(10.0, 12.2));
      assertFalse(PerilRules.reachable(10.0, 12.6), "above the hands");
   }

   @Test
   void hang_time_is_bounded_and_varies_by_person() {
      int min = Integer.MAX_VALUE;
      int max = Integer.MIN_VALUE;
      for (int seed = 0; seed < 4000; seed += 7) {
         int t = PerilRules.hangTicks(seed * 0x9E3779B1);
         min = Math.min(min, t);
         max = Math.max(max, t);
      }

      assertTrue(min >= PerilRules.HANG_MIN && max <= PerilRules.HANG_MIN + PerilRules.HANG_SPREAD, min + ".." + max);
      assertTrue(max - min > PerilRules.HANG_SPREAD / 2, "different people hang on differently long");
   }

   @Test
   void the_voice_budget_caps_bursts_and_keeps_a_reserve_for_falls() {
      PerilRules.VoiceBudget b = new PerilRules.VoiceBudget();
      int calls = 0;
      while (b.take(100L, 1.5F)) {
         calls++;
      }

      // calls for help keep 1.5 tokens back: a falling scream (reserve 0) still gets through
      assertTrue(calls >= 3 && calls <= 5, "help calls in a burst: " + calls);
      assertTrue(b.take(100L, 0.0F), "a falling scream is never crowded out by calls");
      int falls = 1;
      while (b.take(100L, 0.0F)) {
         falls++;
      }

      assertEquals((int)PerilRules.VoiceBudget.CAPACITY, calls + falls, "a burst never exceeds the capacity");
      assertFalse(b.take(100L, 0.0F));
      assertFalse(b.take(102L, 0.0F), "0.4 tokens after 2 ticks");
      assertTrue(b.take(106L, 0.0F), "refilled at 0.2 per tick");
      assertFalse(b.take(90L, 0.0F), "time going back never refills");
   }

   @Test
   void citizen_voices_are_recognised_by_their_id() {
      assertTrue(PerilRules.isVoice(new ResourceLocation("viltrumitecore", "citizen.fall.female")));
      assertTrue(PerilRules.isVoice(new ResourceLocation("viltrumitecore", "citizen.crowd_panic")));
      assertFalse(PerilRules.isVoice(new ResourceLocation("viltrumitecore", "collapse.roar")));
      assertFalse(PerilRules.isVoice(new ResourceLocation("minecraft", "citizen.scream.male")));
   }

   @Test
   void every_voice_event_has_sounds_subtitles_and_no_vanilla_stand_in() throws IOException {
      String overlay = Files.readString(Path.of("src/main/sound-overlays/city_citizens.json"), StandardCharsets.UTF_8);
      Path sounds = Path.of("src/main/binassets/assets/viltrumitecore/sounds/citizen");
      for (String ev : new String[]{"citizen.scream.male", "citizen.scream.female", "citizen.fall.male", "citizen.fall.female", "citizen.help.male",
         "citizen.help.female", "citizen.witness.male", "citizen.witness.female", "citizen.crowd_panic"}) {
         assertTrue(overlay.contains("\"" + ev + "\""), ev);
      }

      assertFalse(overlay.contains("minecraft:"), "no vanilla sounds");
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"viltrumitecore:citizen/([a-z_0-9]+)\"").matcher(overlay);
      int n = 0;
      while (m.find()) {
         assertTrue(Files.isRegularFile(sounds.resolve(m.group(1) + ".ogg.b64")), m.group(1));
         n++;
      }

      assertEquals(27, n, "27 voice files");
      for (String loc : new String[]{"en_us", "ru_ru", "de_de", "tr_tr", "zh_cn", "es_es"}) {
         String lang = Files.readString(Path.of("src/main/lang-overlays/city_citizens/" + loc + ".json"), StandardCharsets.UTF_8);
         for (String sub : new String[]{"scream", "fall", "help", "witness", "crowd_panic"}) {
            assertTrue(lang.contains("\"subtitles.viltrumitecore.citizen." + sub + "\""), loc + " " + sub);
         }
      }

      String ru = Files.readString(Path.of("src/main/lang-overlays/city_citizens/ru_ru.json"), StandardCharsets.UTF_8);
      assertTrue(ru.contains("Падающий житель кричит"), "Russian subtitles");
   }

   @Test
   void wiring_peril_owns_the_body_and_reacts_to_collapses() throws IOException {
      String entity = Files.readString(SRC.resolve("city/citizen/CitizenEntity.java"), StandardCharsets.UTF_8);
      assertTrue(entity.contains("!CitizenPeril.tick(this, city)"), "the peril runs before the brain");
      assertTrue(entity.contains("CitizenPeril.onDeath(this, city, source.getMsgId())"));
      String nodes = Files.readString(SRC.resolve("city/citizen/CitizenNodeEvaluator.java"), StandardCharsets.UTF_8);
      assertTrue(nodes.contains("c.occupant ? null : city.grid()"), "occupants walk indoors (the street grid knows no interiors)");
      assertTrue(entity.contains("new CitizenGoals.Occupy(this)"));
      String events = Files.readString(SRC.resolve("city/citizen/CitizenPerilEvents.java"), StandardCharsets.UTF_8);
      assertTrue(events.contains("CityCollapseEvent.Started") && events.contains("EventPriority.LOWEST") && events.contains("isCanceled()"),
         "collapse start -> teeter / calls / crowd, after any cancelling listener");
      String peril = Files.readString(SRC.resolve("city/citizen/CitizenPeril.java"), StandardCharsets.UTF_8);
      assertTrue(peril.contains("playSound(null, c,"), "voices are played on the entity: they move with the body for everyone");
      assertTrue(peril.contains("c.stateTicks++"), "peril states count their own time (the brain sleeps)");
      String goals = Files.readString(SRC.resolve("city/citizen/CitizenGoals.java"), StandardCharsets.UTF_8);
      assertTrue(goals.contains("case HANGING, FALLING -> this.c.getNavigation().stop();"), "no walking in the air");
      assertTrue(goals.contains("if (this.c.occupant) {"), "occupants are not marched back to the street");
   }
}
