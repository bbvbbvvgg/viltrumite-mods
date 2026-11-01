package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.order.CullMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.order.MawMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.order.OrderMove;
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
 * Asset contracts of THANOS 2.0: the war's own sounds (registered, committed, subtitled, played,
 * nothing vanilla), its strings in the twelve locales (the same keys and arguments, every key the code
 * builds, every voice line the code says), the Black Order's rigs, clips of their moves' exact
 * lengths and textures, and the Sanctuary II's hull shader.
 */
class ThanosWarAssetsTest {
   private static final Path ASSETS = Path.of("src/main/resources/assets/viltrumitecore");
   private static final Path BIN = Path.of("src/main/binassets/assets/viltrumitecore");
   private static final Path SOUND_OVERLAY = Path.of("src/main/sound-overlays/thanos_war.json");
   private static final Path LANG_OVERLAYS = Path.of("src/main/lang-overlays");
   private static final Path LANG = LANG_OVERLAYS.resolve("thanos_war");
   private static final Path JAVA = Path.of("src/main/java/dev/baranhan/viltrumitecore");
   private static final List<String> LOCALES = List.of("en_us", "ru_ru", "de_de", "tr_tr", "zh_cn", "es_es", "es_ar", "es_cl", "es_ec", "es_mx",
      "es_uy", "es_ve");
   /** The two loops: streamed from disk. */
   private static final Set<String> STREAMED = Set.of("ship_hum", "siren");
   private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?[sd]");

   private static JsonObject read(Path path) throws IOException {
      return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
   }

   private static String code(Path... skip) throws IOException {
      StringBuilder all = new StringBuilder();
      Set<Path> skipped = Set.of(skip);
      try (Stream<Path> files = Files.walk(JAVA)) {
         for (Path p : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
            if (!skipped.contains(p)) {
               all.append(Files.readString(p)).append('\n');
            }
         }
      }

      return all.toString();
   }

   private static List<String> registered() throws IOException {
      Matcher reg = Pattern.compile("reg\\(\"thanos_war\\.([a-z0-9_]+)\"\\)").matcher(Files.readString(JAVA.resolve("worldevent/thanos/war/ThanosWarSounds.java")));
      List<String> names = new ArrayList<>();
      while (reg.find()) {
         names.add(reg.group(1));
      }

      assertEquals(new HashSet<>(names).size(), names.size(), "no event registered twice");
      assertTrue(names.size() >= 30, "the war has its own soundscape: " + names.size());
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

   // ---- sounds -----------------------------------------------------------------------------------

   @Test
   void everyRegisteredSoundHasAnOverlayEventWithItsOwnFiles() throws IOException {
      JsonObject overlay = read(SOUND_OVERLAY);
      List<String> names = registered();
      assertEquals(new TreeSet<>(names), new TreeSet<>(overlay.keySet().stream().map(k -> k.substring("thanos_war.".length())).toList()),
         "the overlay defines exactly the registered events");
      for (String name : names) {
         JsonObject event = overlay.getAsJsonObject("thanos_war." + name);
         JsonArray sounds = event.getAsJsonArray("sounds");
         assertFalse(sounds.isEmpty(), name);
         for (JsonElement sound : sounds) {
            JsonObject s = sound.getAsJsonObject();
            String id = s.get("name").getAsString();
            assertTrue(id.startsWith("viltrumitecore:thanos_war/"), name + " plays its own file, not " + id);
            chunks(id.substring("viltrumitecore:".length()));
            boolean stream = s.has("stream") && s.get("stream").getAsBoolean();
            assertEquals(STREAMED.contains(name), stream, name + " streams only if it is a loop");
            assertTrue(s.has("attenuation_distance") && s.get("attenuation_distance").getAsInt() >= 16, name + " carries over the battle");
         }

         assertEquals("subtitles.viltrumitecore.thanos_war." + name, event.get("subtitle").getAsString(), name + " has its subtitle");
         assertEquals(name.equals("siren") ? "ambient" : "hostile", event.get("category").getAsString(), name + " category");
      }
   }

   @Test
   void everyCommittedSoundFileIsUsed() throws IOException {
      Set<String> used = new HashSet<>();
      for (Map.Entry<String, JsonElement> e : read(SOUND_OVERLAY).entrySet()) {
         for (JsonElement sound : e.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
            used.add(sound.getAsJsonObject().get("name").getAsString().substring("viltrumitecore:thanos_war/".length()));
         }
      }

      try (Stream<Path> list = Files.list(BIN.resolve("sounds/thanos_war"))) {
         for (Path p : list.toList()) {
            String stem = p.getFileName().toString().replaceAll("\\.ogg\\.b64(\\.part\\d+)?$", "");
            assertTrue(used.contains(stem), "unused sound file " + p.getFileName());
         }
      }
   }

   @Test
   void everySoundTheCodePlaysIsRegisteredAndEveryRegisteredSoundIsPlayed() throws IOException {
      Path sounds = JAVA.resolve("worldevent/thanos/war/ThanosWarSounds.java");
      Set<String> constants = new TreeSet<>();
      Matcher decl = Pattern.compile("RegistryObject<SoundEvent> ([A-Z0-9_]+) = reg\\(").matcher(Files.readString(sounds));
      while (decl.find()) {
         constants.add(decl.group(1));
      }

      Set<String> played = new TreeSet<>();
      Matcher use = Pattern.compile("ThanosWarSounds\\.([A-Z][A-Z0-9_]+)\\b").matcher(code(sounds));
      while (use.find()) {
         played.add(use.group(1));
      }

      assertTrue(constants.containsAll(played), "played but not registered: " + played.stream().filter(p -> !constants.contains(p)).toList());
      assertTrue(played.containsAll(constants), "registered but never played: " + constants.stream().filter(c -> !played.contains(c)).toList());
   }

   @Test
   void noVanillaSoundInTheWar() throws IOException {
      for (String dir : new String[]{"worldevent/thanos", "client/thanos", "entity/thanos"}) {
         try (Stream<Path> files = Files.walk(JAVA.resolve(dir))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
               String src = Files.readString(p);
               assertFalse(src.contains("SoundEvents."), p + " plays a vanilla sound");
            }
         }
      }

      // the generals answer hits, deaths and idle moments with their own voices
      for (String general : new String[]{"EbonyMawEntity", "CullObsidianEntity"}) {
         String src = Files.readString(JAVA.resolve("entity/thanos/" + general + ".java"));
         for (String hook : new String[]{"getHurtSound", "getDeathSound", "getAmbientSound"}) {
            assertTrue(src.contains(hook), general + " overrides " + hook);
         }
      }
   }

   // ---- strings ----------------------------------------------------------------------------------

   private static Map<String, JsonObject> locales(Path dir) throws IOException {
      Map<String, JsonObject> all = new TreeMap<>();
      try (Stream<Path> files = Files.list(dir)) {
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
      Map<String, JsonObject> all = locales(LANG);
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

      JsonObject ru = all.get("ru_ru");
      assertEquals("Эбони Мо", ru.get("entity.viltrumitecore.ebony_maw").getAsString());
      assertEquals("Кулл Обсидиан", ru.get("entity.viltrumitecore.cull_obsidian").getAsString());
      assertEquals("Ebony Maw", en.get("entity.viltrumitecore.ebony_maw").getAsString());
      for (String key : en.keySet()) {
         if (key.startsWith("subtitle.viltrumitecore.thanos_war.")) {
            assertFalse(ru.get(key).getAsString().equals(en.get(key).getAsString()), "the Russian subtitle is translated: " + key);
            assertTrue(ru.get(key).getAsString().chars().anyMatch(c -> c >= 0x0400 && c <= 0x04FF), "Cyrillic: " + key);
         }
      }
   }

   @Test
   void noKeyComesFromTwoPlaces() throws IOException {
      Set<String> war = locales(LANG).get("en_us").keySet();
      JsonObject base = read(ASSETS.resolve("lang/en_us.json"));
      for (String key : war) {
         assertFalse(base.has(key), key + " is also in the base locale file");
      }

      try (Stream<Path> dirs = Files.list(LANG_OVERLAYS)) {
         for (Path dir : dirs.filter(Files::isDirectory).toList()) {
            Path en = dir.resolve("en_us.json");
            if (dir.equals(LANG) || !Files.exists(en)) {
               continue;
            }

            JsonObject other = read(en);
            for (String key : war) {
               assertFalse(other.has(key), key + " is also in the overlay " + dir.getFileName());
            }
         }
      }
   }

   @Test
   void everyKeyTheCodeBuildsExistsAndEveryVoiceLineIsSaid() throws IOException {
      JsonObject en = locales(LANG).get("en_us");
      String code = code();
      Set<String> keys = new TreeSet<>();
      for (String name : registered()) {
         keys.add("subtitles.viltrumitecore.thanos_war." + name);
      }

      for (ThanosPhase phase : ThanosPhase.values()) {
         keys.add("hud.viltrumitecore.thanos_war.phase." + phase.name().toLowerCase(Locale.ROOT));
      }

      for (String entity : new String[]{"ebony_maw", "cull_obsidian", "war_debris"}) {
         keys.add("entity.viltrumitecore." + entity);
      }

      // the voices and every line said with them
      Map<String, String> voices = new HashMap<>();
      Matcher voice = Pattern.compile("static final String (VOICE_[A-Z]+) = \"([a-z_]+)\";").matcher(Files.readString(JAVA.resolve("worldevent/thanos/war/ThanosWar.java")));
      while (voice.find()) {
         voices.put(voice.group(1), voice.group(2));
         keys.add("subtitle.viltrumitecore.speaker." + voice.group(2));
      }

      assertEquals(Set.of("thanos", "ebony_maw", "cull_obsidian"), new HashSet<>(voices.values()), "the war's three voices");
      Set<String> said = new TreeSet<>();
      Matcher say = Pattern.compile("\\bsay\\((?:ThanosWar\\.)?(VOICE_[A-Z]+),\\s*[^,]+,\\s*(.+?),\\s*(?:[\\w.]+\\.)?HeroLine\\.Priority").matcher(code);
      while (say.find()) {
         Matcher literal = Pattern.compile("\"([a-z0-9_]+)\"").matcher(say.group(2));
         int found = 0;
         while (literal.find()) {
            said.add("subtitle.viltrumitecore.thanos_war." + voices.get(say.group(1)) + "." + literal.group(1));
            found++;
         }

         assertTrue(found > 0, "a voice line without a literal name: " + say.group());
      }

      assertTrue(said.size() >= 20, "the war's lines: " + said);
      keys.addAll(said);
      for (String key : en.keySet()) {
         if (key.startsWith("subtitle.viltrumitecore.thanos_war.")) {
            assertTrue(said.contains(key), "a voice line the code never says: " + key);
         }
      }

      // every complete key literal in the code
      Matcher literal = Pattern.compile("\"((?:commands|event|hud|scene|entity|subtitles?)\\.viltrumitecore\\.thanos_war(?:\\.[a-z0-9_]+)+)\"").matcher(code);
      while (literal.find()) {
         keys.add(literal.group(1));
      }

      for (String key : keys) {
         assertTrue(en.has(key), "missing key " + key);
      }
   }

   // ---- the Black Order --------------------------------------------------------------------------

   private static Set<String> bones(Path geo) throws IOException {
      Set<String> names = new HashSet<>();
      JsonArray list = read(geo).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
      for (JsonElement bone : list) {
         assertTrue(names.add(bone.getAsJsonObject().get("name").getAsString()), geo + " names a bone twice");
      }

      return names;
   }

   private static Map<String, JsonObject> clips(String general) throws IOException {
      Map<String, JsonObject> all = new HashMap<>();
      for (String file : new String[]{general + ".animation.json", general + "_moves.animation.json"}) {
         for (Map.Entry<String, JsonElement> e : read(ASSETS.resolve("animations/black_order/" + file)).getAsJsonObject("animations").entrySet()) {
            assertTrue(all.put(e.getKey(), e.getValue().getAsJsonObject()) == null, "duplicate clip " + e.getKey());
         }
      }

      return all;
   }

   private static void general(String name, OrderMove[] moves, String locomotion, String... extraBones) throws IOException {
      String renderer = Files.readString(JAVA.resolve("client/thanos/war/BlackOrderRenderer.java"));
      assertTrue(Pattern.compile("new Look\\(\"" + name + "\",[^;]*\"" + locomotion + "\"").matcher(renderer).find(), name + " moves by " + locomotion);
      Map<String, JsonObject> all = clips(name);
      for (OrderMove move : moves) {
         String clip = move.name().toLowerCase(Locale.ROOT);
         JsonObject c = all.get(clip);
         assertNotNull(c, name + " is missing the clip " + clip);
         assertEquals(move.length(), Math.round(c.get("animation_length").getAsDouble() * 20.0), name + " " + clip + " spans its move");
         assertFalse(c.has("loop") && c.get("loop").getAsBoolean(), name + " " + clip + " must not loop");
      }

      for (String loop : new String[]{"idle", locomotion}) {
         assertNotNull(all.get(loop), name + " " + loop);
         assertTrue(all.get(loop).has("loop") && all.get(loop).get("loop").getAsBoolean(), name + " " + loop + " loops");
      }

      for (String once : new String[]{"death", "beam_in", "beam_out", "stagger", "hurt"}) {
         assertNotNull(all.get(once), name + " " + once);
      }

      Set<String> rig = bones(ASSETS.resolve("geo/thanos/" + name + ".geo.json"));
      assertTrue(rig.contains("head"), name + " has a head bone");
      for (String bone : extraBones) {
         assertTrue(rig.contains(bone), name + " has " + bone);
      }

      for (Map.Entry<String, JsonObject> clip : all.entrySet()) {
         JsonObject animated = clip.getValue().getAsJsonObject("bones");
         assertNotNull(animated, clip.getKey());
         for (String bone : animated.keySet()) {
            assertTrue(rig.contains(bone), name + " " + clip.getKey() + " animates unknown bone " + bone);
         }
      }

      for (String texture : new String[]{name + ".png.b64", name + "_glow.png.b64"}) {
         assertTrue(Files.exists(BIN.resolve("textures/entity/thanos/" + texture)), texture);
      }
   }

   @Test
   void ebonyMawHasHisRigClipsAndTextures() throws IOException {
      general("ebony_maw", MawMove.values(), "glide");
   }

   @Test
   void cullObsidianHasHisRigClipsAndTextures() throws IOException {
      general("cull_obsidian", CullMove.values(), "walk", "weapon_head");
   }

   // ---- the Sanctuary II -------------------------------------------------------------------------

   @Test
   void theHullShaderIsComplete() throws IOException {
      Path dir = ASSETS.resolve("shaders/core");
      JsonObject shader = read(dir.resolve("sanctuary_hull.json"));
      assertEquals("viltrumitecore:sanctuary_hull", shader.get("vertex").getAsString());
      assertEquals("viltrumitecore:sanctuary_hull", shader.get("fragment").getAsString());
      String vsh = Files.readString(dir.resolve("sanctuary_hull.vsh"));
      String fsh = Files.readString(dir.resolve("sanctuary_hull.fsh"));
      for (JsonElement attribute : shader.getAsJsonArray("attributes")) {
         assertTrue(vsh.contains("in vec3 " + attribute.getAsString()) || vsh.contains("in vec4 " + attribute.getAsString()), "vertex input " + attribute);
      }

      for (JsonElement uniform : shader.getAsJsonArray("uniforms")) {
         String name = uniform.getAsJsonObject().get("name").getAsString();
         assertTrue(vsh.contains("uniform") && (vsh.contains(" " + name + ";") || fsh.contains(" " + name + ";")), "uniform " + name + " is used");
      }

      String renderer = Files.readString(JAVA.resolve("client/thanos/war/SanctuaryRenderer.java"));
      assertTrue(renderer.contains("\"sanctuary_hull\""), "the renderer loads the hull shader");
   }
}
