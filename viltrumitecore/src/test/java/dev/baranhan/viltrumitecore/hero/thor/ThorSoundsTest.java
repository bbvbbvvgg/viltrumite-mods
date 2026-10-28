package dev.baranhan.viltrumitecore.hero.thor;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorStrike;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** R5 (spec §8): one facade, Thor's own files only, every event subtitled in every locale, cues on phase events. */
class ThorSoundsTest {
   private static final Path MAIN = Path.of("src/main/java/dev/baranhan/viltrumitecore");
   private static final Path RES = Path.of("src/main/resources/assets/viltrumitecore");
   private static final Path BIN = Path.of("src/main/binassets/assets/viltrumitecore/sounds/thor");
   private static final List<String> LOCALES = List.of("de_de", "en_us", "es_ar", "es_cl", "es_ec", "es_es", "es_mx", "es_uy", "es_ve", "tr_tr", "zh_cn");
   /** The only Thor files that may touch the sound engine. */
   private static final Set<String> FACADE = Set.of("ThorSounds.java", "ThorSoundLoops.java");
   /** A use or an import of the vanilla sound table (a mention in a comment is fine). */
   private static final Pattern VANILLA = Pattern.compile("\\bSoundEvents\\s*\\.|import\\s+net\\.minecraft\\.sounds\\.SoundEvents");
   private static final Pattern ENGINE = Pattern.compile("\\b(playSound|playNotifySound|playLocalSound|getSoundManager)\\s*\\(");

   private static List<Path> thorSources() throws IOException {
      List<Path> out = new ArrayList<>();
      for (Path root : List.of(MAIN.resolve("hero/thor"), MAIN.resolve("client/thor"))) {
         try (Stream<Path> files = Files.walk(root)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
         }
      }

      assertTrue(out.size() > 50, "Thor sources found: " + out.size());
      return out;
   }

   @Test
   void thorCodeNeverPlaysVanillaSoundEvents() throws IOException {
      List<String> offenders = new ArrayList<>();
      for (Path file : thorSources()) {
         String code = Files.readString(file);
         if (VANILLA.matcher(code).find() || code.contains("ThorFeedback.play(") || code.contains("ElectricFx.sound(")) {
            offenders.add(file.toString());
         }
      }

      assertEquals(List.of(), offenders, "Thor plays sounds through ThorSounds / ThorCue only");
   }

   @Test
   void onlyTheFacadeTouchesTheSoundEngine() throws IOException {
      List<String> offenders = new ArrayList<>();
      for (Path file : thorSources()) {
         Matcher m = ENGINE.matcher(Files.readString(file));
         if (m.find() && !FACADE.contains(file.getFileName().toString())) {
            offenders.add(file + ": " + m.group(1));
         }
      }

      assertEquals(List.of(), offenders);
   }

   @Test
   void cueTableIsConsistent() {
      assertEquals(ThorCue.EVENTS.size(), new HashSet<>(ThorCue.EVENTS).size(), "event names unique");
      Set<String> ids = new HashSet<>();
      Set<String> used = new HashSet<>(List.of(ThorCue.BOLT_CRACK));
      for (ThorCue cue : ThorCue.values()) {
         assertTrue(ids.add(cue.id()), "duplicate cue id " + cue.id());
         assertTrue(cue.id().matches("[a-z_]+(\\.[a-z_]+)+"), cue.id());
         assertFalse(cue.layers().isEmpty(), cue.id());
         for (ThorCue.Layer layer : cue.layers()) {
            assertTrue(ThorCue.EVENTS.contains(layer.event()), cue.id() + " -> " + layer.event());
            assertTrue(layer.volume() > 0.0F && layer.pitch() >= 0.5F && layer.pitch() <= 2.0F && layer.jitter() >= 0.0F, cue.id());
            used.add(layer.event());
         }

         assertEquals(cue.loop(), cue.layers().size() == 1 && cue.layers().get(0).event().endsWith("_loop"), "loops are single _loop layers: " + cue.id());
      }

      assertEquals(new HashSet<>(ThorCue.EVENTS), used, "every event is used by a cue");
   }

   @Test
   void layeredThunderAndHammerBlows() {
      assertEquals(List.of("thunder_crack", "thunder_impact", "thunder_rumble"), events(ThorCue.STORM_BURST).subList(0, 3));
      assertTrue(events(ThorCue.WRATH_CONTACT).containsAll(List.of("thunder_crack", "thunder_impact", "thunder_rumble")));
      assertTrue(events(ThorCue.FIRST_FALL_THUNDER).containsAll(List.of("thunder_crack", "thunder_rumble")));
      for (ThorCue blow : List.of(ThorCue.HAMMER_SLAM, ThorCue.STICK, ThorCue.LAND, ThorCue.IMPACT)) {
         assertTrue(events(blow).containsAll(List.of("hammer_metal", "hammer_earth", "hammer_bass")), blow.id());
      }

      assertTrue(events(ThorCue.HAMMER_HIT).containsAll(List.of("hammer_metal", "hammer_bass")));
   }

   @Test
   void everyStrikeHasItsCues() {
      for (ThorStrike strike : ThorStrike.values()) {
         boolean fist = strike.move() == ThorMove.FIST_COMBO;
         String family = fist ? "fist." : "hammer.";
         assertTrue(ThorCue.windup(strike).id().startsWith(family), strike.name());
         assertTrue(ThorCue.hit(strike).id().startsWith(family), strike.name());
         assertEquals(strike.shockRadius() > 0, ThorCue.ground(strike) != null, strike.name());
         assertEquals(strike.heavy(), ThorCue.tail(strike) != null, strike.name());
         assertEquals(strike.heavy(), ThorCue.windup(strike).id().endsWith("_heavy"), strike.name());
      }
   }

   @Test
   void cuesAreBoundToPhaseEvents() throws IOException {
      String melee = Files.readString(MAIN.resolve("hero/thor/combat/MeleeMoves.java"));
      assertTrue(melee.contains("case ENTER_FINISH -> ThorSounds.strikeTail(player, combat.strike);"));
      assertTrue(melee.contains("Event.ENTER_FINISH) {\n            ThorSounds.strikeTail(player, combat.strike);"));
      assertTrue(melee.contains("ThorSounds.strikeContact(player, strike, !landed.isEmpty(), shockCenter);"));
      assertEquals(2, count(melee, "ThorSounds.strikeWindup("), "COMBO step start (onStart / CHAIN) and STRIKE.onStart");
      String wrath = Files.readString(MAIN.resolve("hero/thor/combat/WrathMove.java"));
      assertTrue(wrath.contains("Event.ENTER_FINISH) {\n            ThorSounds.play(player, ThorCue.WRATH_TAIL);"));
      assertTrue(wrath.contains("ThorSounds.stop(level, player.position(), ThorCue.WRATH_WINDUP);"), "the riser stops at the contact");
      String electric = Files.readString(MAIN.resolve("hero/thor/combat/ElectricMoves.java"));
      assertEquals(2, count(electric, "case ENTER_FINISH -> ThorSounds.play(player, ThorCue.ELECTRIC_TAIL);"));
      assertEquals(3, count(electric, "ThorCue.STORM_CHARGE);"), "storm riser: started once, stopped by the burst and by the cancel");
   }

   @Test
   void soundsJsonFilesAndSubtitles() throws IOException {
      JsonObject sounds = JsonParser.parseString(Files.readString(RES.resolve("sounds.json"))).getAsJsonObject();
      Set<String> thorKeys = sounds.keySet().stream().filter(k -> k.startsWith("thor.")).collect(Collectors.toSet());
      assertEquals(ThorCue.EVENTS.stream().map(e -> "thor." + e).collect(Collectors.toSet()), thorKeys);
      Set<String> referenced = new HashSet<>();
      Map<String, JsonObject> langs = new HashMap<>();
      for (String locale : LOCALES) {
         langs.put(locale, JsonParser.parseString(Files.readString(RES.resolve("lang/" + locale + ".json"), StandardCharsets.UTF_8)).getAsJsonObject());
      }

      for (String event : ThorCue.EVENTS) {
         JsonObject entry = sounds.getAsJsonObject("thor." + event);
         assertEquals("player", entry.get("category").getAsString(), event);
         String subtitle = entry.get("subtitle").getAsString();
         assertEquals("subtitles.viltrumitecore.thor_" + event, subtitle);
         for (String locale : LOCALES) {
            JsonElement text = langs.get(locale).get(subtitle);
            assertNotNull(text, locale + " " + subtitle);
            assertFalse(text.getAsString().isBlank(), locale + " " + subtitle);
         }

         for (JsonElement sound : entry.getAsJsonArray("sounds")) {
            String name = sound.getAsJsonObject().get("name").getAsString();
            assertTrue(name.startsWith("viltrumitecore:thor/"), name);
            String file = name.substring("viltrumitecore:thor/".length());
            assertTrue(file.equals(event) || file.matches(Pattern.quote(event) + "_\\d+"), name);
            Path asset = BIN.resolve(file + ".ogg.b64");
            assertTrue(Files.isRegularFile(asset), asset.toString());
            byte[] ogg = Base64.getMimeDecoder().decode(Files.readString(asset).trim());
            assertEquals("OggS", new String(ogg, 0, 4, StandardCharsets.US_ASCII), asset.toString());
            referenced.add(file);
         }
      }

      try (Stream<Path> files = Files.list(BIN)) {
         Set<String> shipped = files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".ogg.b64"))
            .map(n -> n.substring(0, n.length() - ".ogg.b64".length())).collect(Collectors.toSet());
         assertEquals(shipped, referenced, "every shipped Thor file is used, every used file is shipped");
      }
   }

   @Test
   void sourcesAreDocumented() throws IOException {
      String sources = Files.readString(Path.of("../tools/assets/thor_sources.md"));
      String credits = Files.readString(Path.of("../CREDITS.md"));
      assertTrue(sources.contains("tools/sfx/thor.py") && sources.contains("CC0"));
      assertTrue(credits.contains("tools/sfx/thor.py"));
      for (ThorCue cue : ThorCue.values()) {
         assertTrue(sources.contains("`" + cue.id() + "`"), "cue documented: " + cue.id());
      }
   }

   @Test
   void boltGate() {
      assertTrue(ThorCue.gate(0, ThorCue.RUMBLE_GAP, ThorCue.RUMBLE_GAP));
      assertFalse(ThorCue.gate(10, 10 + ThorCue.RUMBLE_GAP - 1, ThorCue.RUMBLE_GAP));
      assertTrue(ThorCue.gate(100, 5, ThorCue.RUMBLE_GAP), "a new level clock (lower time) does not block forever");
   }

   private static List<String> events(ThorCue cue) {
      return cue.layers().stream().map(ThorCue.Layer::event).toList();
   }

   private static int count(String text, String needle) {
      int n = 0;
      for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
         n++;
      }

      return n;
   }
}
