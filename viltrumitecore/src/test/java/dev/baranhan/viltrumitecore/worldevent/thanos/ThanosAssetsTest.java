package dev.baranhan.viltrumitecore.worldevent.thanos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Asset contracts of the Thanos event: every registered sound has an overlay event with its own
 * committed files (whole or split into contiguous parts) and a subtitle in every locale, the loops
 * stream, every locale carries the same keys with the same arguments, every move has its HUD name
 * and hint and its clip, clips only animate bones of their rig, and Od Lagna has her scene clips.
 */
class ThanosAssetsTest {
   private static final Path ASSETS = Path.of("src/main/resources/assets/viltrumitecore");
   private static final Path BIN = Path.of("src/main/binassets/assets/viltrumitecore");
   private static final Path SOUND_OVERLAY = Path.of("src/main/sound-overlays/thanos.json");
   private static final Path LANG = Path.of("src/main/lang-overlays/thanos");
   private static final Path JAVA = Path.of("src/main/java/dev/baranhan/viltrumitecore");
   private static final List<String> LOCALES = List.of("en_us", "ru_ru", "de_de", "tr_tr", "zh_cn", "es_es", "es_ar", "es_cl", "es_ec", "es_mx",
      "es_uy", "es_ve");
   /** Long loops: streamed from disk, no subtitle for the music. */
   private static final Set<String> MUSIC = Set.of("music_battle", "music_final");
   private static final Set<String> STREAMED = Set.of("music_battle", "music_final", "void");
   private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?[sd]");

   private static JsonObject read(Path path) throws IOException {
      return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
   }

   private static List<String> registered() throws IOException {
      String src = Files.readString(JAVA.resolve("worldevent/thanos/ThanosSounds.java"));
      Matcher reg = Pattern.compile("reg\\(\"thanos\\.([a-z0-9_]+)\"\\)").matcher(src);
      List<String> names = new ArrayList<>();
      while (reg.find()) {
         names.add(reg.group(1));
      }

      assertEquals(new HashSet<>(names).size(), names.size(), "no event registered twice");
      return names;
   }

   /** The committed base64 chunks of one sound file: exactly one whole file or parts 0..n-1. */
   private static void chunks(String file) throws IOException {
      Path whole = BIN.resolve("sounds/" + file + ".ogg.b64");
      Path dir = whole.getParent();
      String stem = whole.getFileName().toString() + ".part";
      int parts = 0;
      if (Files.isDirectory(dir)) {
         try (Stream<Path> list = Files.list(dir)) {
            parts = (int)list.filter(p -> p.getFileName().toString().startsWith(stem)).count();
         }
      }

      for (int i = 0; i < parts; i++) {
         assertTrue(Files.exists(dir.resolve(stem + i)), file + " parts are contiguous (missing part" + i + ")");
      }

      assertFalse(parts > 0 && Files.exists(whole), file + " is committed both whole and in parts");
      assertTrue(parts > 0 || Files.exists(whole), "missing sound file " + file);
   }

   @Test
   void everyRegisteredSoundHasAnOverlayEventWithItsOwnFiles() throws IOException {
      JsonObject overlay = read(SOUND_OVERLAY);
      List<String> names = registered();
      assertEquals(new TreeSet<>(names), new TreeSet<>(overlay.keySet().stream().map(k -> k.substring("thanos.".length())).toList()),
         "the overlay defines exactly the registered events");
      for (String name : names) {
         JsonObject event = overlay.getAsJsonObject("thanos." + name);
         JsonArray sounds = event.getAsJsonArray("sounds");
         assertFalse(sounds.isEmpty(), name);
         for (JsonElement sound : sounds) {
            JsonObject s = sound.getAsJsonObject();
            String id = s.get("name").getAsString();
            assertTrue(id.startsWith("viltrumitecore:thanos/"), id);
            chunks(id.substring("viltrumitecore:".length()));
            boolean stream = s.has("stream") && s.get("stream").getAsBoolean();
            assertEquals(STREAMED.contains(name), stream, name + " streams only if it is a long loop");
         }

         assertEquals(!MUSIC.contains(name), event.has("subtitle"), name + ": every sound but the music has a subtitle");
         if (event.has("subtitle")) {
            assertEquals("subtitles.viltrumitecore.thanos." + name, event.get("subtitle").getAsString(), name);
         }
      }

      // the footstep alternates two takes
      assertEquals(2, overlay.getAsJsonObject("thanos.step").getAsJsonArray("sounds").size());
   }

   @Test
   void everyCommittedSoundFileIsUsed() throws IOException {
      Set<String> used = new HashSet<>();
      for (Map.Entry<String, JsonElement> e : read(SOUND_OVERLAY).entrySet()) {
         for (JsonElement sound : e.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
            used.add(sound.getAsJsonObject().get("name").getAsString().substring("viltrumitecore:thanos/".length()));
         }
      }

      try (Stream<Path> list = Files.list(BIN.resolve("sounds/thanos"))) {
         for (Path p : list.toList()) {
            String stem = p.getFileName().toString().replaceAll("\\.ogg\\.b64(\\.part\\d+)?$", "");
            assertTrue(used.contains(stem), "unused sound file " + p.getFileName());
         }
      }
   }

   @Test
   void everySoundTheCodePlaysIsRegisteredAndEveryRegisteredSoundIsPlayed() throws IOException {
      Set<String> constants = new HashSet<>();
      Matcher decl = Pattern.compile("RegistryObject<SoundEvent> ([A-Z0-9_]+) = reg\\(").matcher(Files.readString(JAVA.resolve("worldevent/thanos/ThanosSounds.java")));
      while (decl.find()) {
         constants.add(decl.group(1));
      }

      StringBuilder code = new StringBuilder();
      try (Stream<Path> files = Files.walk(JAVA)) {
         for (Path p : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("ThanosSounds.java")).toList()) {
            code.append(Files.readString(p));
         }
      }

      Set<String> played = new HashSet<>();
      Matcher use = Pattern.compile("ThanosSounds\\.([A-Z0-9_]+)\\b").matcher(code);
      while (use.find()) {
         played.add(use.group(1));
      }

      for (ThanosMove m : ThanosMove.values()) {
         if (m.name().startsWith("COMBO_")) {
            played.add(m.name()); // through ThanosSounds.combo(move)
         }
      }

      assertTrue(constants.containsAll(played), "played but not registered: " + played.stream().filter(p -> !constants.contains(p)).toList());
      assertTrue(played.containsAll(constants), "registered but never played: " + constants.stream().filter(c -> !played.contains(c)).toList());
   }

   private static Map<String, JsonObject> locales() throws IOException {
      Map<String, JsonObject> all = new TreeMap<>();
      try (Stream<Path> files = Files.list(LANG)) {
         for (Path p : files.toList()) {
            all.put(p.getFileName().toString().replace(".json", ""), read(p));
         }
      }

      return all;
   }

   private static Set<String> args(String text) {
      Set<String> found = new TreeSet<>();
      Matcher m = ARG.matcher(text);
      int next = 1;
      while (m.find()) {
         found.add(m.group(1) != null ? m.group(1) : String.valueOf(next++));
      }

      return found;
   }

   @Test
   void everyLocaleHasEveryKeyWithTheSameArguments() throws IOException {
      Map<String, JsonObject> all = locales();
      assertEquals(new TreeSet<>(LOCALES), all.keySet(), "the twelve locales");
      JsonObject en = all.get("en_us");
      for (Map.Entry<String, JsonObject> locale : all.entrySet()) {
         assertEquals(en.keySet(), locale.getValue().keySet(), locale.getKey() + " has the same keys as en_us");
         for (String key : en.keySet()) {
            String text = locale.getValue().get(key).getAsString();
            assertFalse(text.isBlank(), locale.getKey() + " " + key);
            assertEquals(args(en.get(key).getAsString()), args(text), locale.getKey() + " " + key + " arguments");
         }
      }

      // translated, not copied: the Russian file differs from English for the player-facing lines
      JsonObject ru = all.get("ru_ru");
      assertFalse(ru.get("event.viltrumitecore.thanos.arrived").getAsString().equals(en.get("event.viltrumitecore.thanos.arrived").getAsString()));
      assertEquals("Од Лагуна", ru.get("scene.viltrumitecore.thanos.od_lagna").getAsString());
      assertEquals("Od Lagna", en.get("scene.viltrumitecore.thanos.od_lagna").getAsString());
   }

   @Test
   void everyKeyTheCodeBuildsExists() throws IOException {
      JsonObject en = locales().get("en_us");
      List<String> keys = new ArrayList<>();
      for (ThanosMove m : ThanosMove.values()) {
         keys.add("hud.viltrumitecore.thanos.move." + m.name().toLowerCase(Locale.ROOT));
         keys.add("hud.viltrumitecore.thanos.hint." + m.name().toLowerCase(Locale.ROOT));
      }

      for (ThanosEvent.Stage s : ThanosEvent.Stage.values()) {
         keys.add("commands.viltrumitecore.thanos.stage." + s.name().toLowerCase(Locale.ROOT));
      }

      for (ThanosPhase p : ThanosPhase.values()) {
         keys.add("commands.viltrumitecore.thanos.phase." + p.name().toLowerCase(Locale.ROOT));
         if (p != ThanosPhase.ARRIVAL) {
            keys.add("event.viltrumitecore.thanos.phase." + p.name().toLowerCase(Locale.ROOT));
         }
      }

      for (int line = 1; line <= ThanosSceneTimeline.LINES; line++) {
         keys.add("scene.viltrumitecore.thanos.line." + line);
      }

      for (String name : registered()) {
         if (!MUSIC.contains(name)) {
            keys.add("subtitles.viltrumitecore.thanos." + name);
         }
      }

      // every complete key literal in the code
      Pattern literal = Pattern.compile("\"((?:commands|event|hud|scene|entity|subtitles)\\.viltrumitecore\\.thanos(?:\\.[a-z0-9_]+)*)\"");
      try (Stream<Path> files = Files.walk(JAVA)) {
         for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
            Matcher m = literal.matcher(Files.readString(p));
            while (m.find()) {
               keys.add(m.group(1));
            }
         }
      }

      for (String key : keys) {
         assertTrue(en.has(key), "missing key " + key);
      }

      // stage, time left, health, heroes, phase, moves started
      assertEquals(6, args(en.get("commands.viltrumitecore.thanos.status").getAsString()).size(), "status shows its six fields");
   }

   // ---- clips ----------------------------------------------------------------------------------

   private static Map<String, JsonObject> clips(String... files) throws IOException {
      Map<String, JsonObject> all = new HashMap<>();
      for (String file : files) {
         for (Map.Entry<String, JsonElement> e : read(ASSETS.resolve("animations/thanos/" + file)).getAsJsonObject("animations").entrySet()) {
            assertTrue(all.put(e.getKey(), e.getValue().getAsJsonObject()) == null, "duplicate clip " + e.getKey());
         }
      }

      return all;
   }

   private static Set<String> bones(Path geo) throws IOException {
      Set<String> names = new HashSet<>();
      JsonArray list = read(geo).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
      for (JsonElement bone : list) {
         names.add(bone.getAsJsonObject().get("name").getAsString());
      }

      return names;
   }

   private static final String[] THANOS_FILES = {"thanos_base.animation.json", "thanos_melee.animation.json", "thanos_stones.animation.json",
      "thanos_combos.animation.json", "thanos_scene.animation.json"};

   @Test
   void theViewLoadsEveryClipFileThatExists() throws IOException {
      String view = Files.readString(JAVA.resolve("client/thanos/ThanosView.java"));
      Matcher m = Pattern.compile("rl\\(\"(animations/thanos/[a-z_]+\\.animation\\.json)\"\\)").matcher(view);
      Set<String> loaded = new HashSet<>();
      while (m.find()) {
         loaded.add(m.group(1));
         assertTrue(Files.exists(ASSETS.resolve(m.group(1))), "missing " + m.group(1));
      }

      try (Stream<Path> files = Files.list(ASSETS.resolve("animations/thanos"))) {
         for (Path p : files.toList()) {
            assertTrue(loaded.contains("animations/thanos/" + p.getFileName()), "clip file not loaded by ThanosView: " + p.getFileName());
         }
      }
   }

   @Test
   void everyMoveHasItsClipOfTheBaseLength() throws IOException {
      Map<String, JsonObject> all = clips(THANOS_FILES);
      for (ThanosMove move : ThanosMove.values()) {
         String name = move.name().toLowerCase(Locale.ROOT);
         JsonObject clip = all.get(name);
         assertNotNull(clip, "missing clip " + name);
         assertEquals(move.telegraph + move.active + move.recovery, Math.round(clip.get("animation_length").getAsDouble() * 20.0), name + " length");
         assertFalse(clip.has("loop") && clip.get("loop").getAsBoolean(), name + " must not loop");
      }

      for (String loop : new String[]{"idle_fight", "walk_fight", "turn_left", "turn_right", "victory", "snap_hold"}) {
         assertNotNull(all.get(loop), loop);
         assertTrue(all.get(loop).has("loop") && all.get(loop).get("loop").getAsBoolean(), loop + " loops");
      }

      for (String once : new String[]{"intro", "defeat", "snap", "stun", "hurt"}) {
         assertNotNull(all.get(once), once);
      }
   }

   @Test
   void thanosClipsOnlyAnimateBonesOfHisRig() throws IOException {
      Set<String> rig = bones(ASSETS.resolve("geo/thanos/thanos.geo.json"));
      for (String bone : new String[]{"head", "torso", "left_hand", "right_hand", "stone_time", "stone_space", "stone_reality", "stone_power",
         "stone_soul", "stone_mind"}) {
         assertTrue(rig.contains(bone), "rig bone " + bone);
      }

      for (Map.Entry<String, JsonObject> clip : clips(THANOS_FILES).entrySet()) {
         JsonObject animated = clip.getValue().getAsJsonObject("bones");
         assertNotNull(animated, clip.getKey());
         for (String bone : animated.keySet()) {
            assertTrue(rig.contains(bone), clip.getKey() + " animates unknown bone " + bone);
         }
      }
   }

   @Test
   void odLagnaHasHerSceneClipsOnReinhardsRig() throws IOException {
      Map<String, JsonObject> all = clips("thanos_od_lagna.animation.json");
      Set<String> rig = bones(ASSETS.resolve("geo/reinhard/reinhard.geo.json"));
      List<String> expected = new ArrayList<>(List.of("od_lagna_form", "od_lagna_idle", "od_lagna_restore"));
      for (int line = 1; line <= ThanosSceneTimeline.LINES; line++) {
         expected.add("od_lagna_line_" + line);
      }

      assertEquals(new TreeSet<>(expected), new TreeSet<>(all.keySet()), "Od Lagna's clips");
      assertTrue(all.get("od_lagna_idle").get("loop").getAsBoolean(), "the idle loops");
      for (int line = 1; line <= ThanosSceneTimeline.LINES; line++) {
         assertEquals(ThanosSceneTimeline.LINE_TICKS, Math.round(all.get("od_lagna_line_" + line).get("animation_length").getAsDouble() * 20.0),
            "line clip " + line + " spans its line");
      }

      for (Map.Entry<String, JsonObject> clip : all.entrySet()) {
         for (String bone : clip.getValue().getAsJsonObject("bones").keySet()) {
            assertTrue(rig.contains(bone), clip.getKey() + " animates unknown bone " + bone);
         }
      }

      for (String texture : new String[]{"od_lagna_scene.png.b64", "od_lagna_scene_glow.png.b64"}) {
         assertTrue(Files.exists(BIN.resolve("textures/entity/thanos/" + texture)), texture);
      }
   }
}
