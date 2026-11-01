package dev.baranhan.viltrumitecore.worldevent.chitauri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Asset contracts of the Chitauri invasion: every sound event is our own (registry, sounds overlay, .ogg
 * files, subtitles in step; no vanilla SoundEvents in the event's code), every locale carries every line with
 * the same placeholders, every key the code asks for exists, and models, clips and textures parse.
 */
class ChitauriAssetsTest {
   private static final Path ASSETS = Path.of("src/main/resources/assets/viltrumitecore");
   private static final Path BIN = Path.of("src/main/binassets/assets/viltrumitecore");
   private static final Path SOUND_OVERLAY = Path.of("src/main/sound-overlays/chitauri.json");
   private static final Path LANG = Path.of("src/main/lang-overlays/chitauri");
   private static final Path BASE_LANG = ASSETS.resolve("lang");
   private static final Path JAVA = Path.of("src/main/java/dev/baranhan/viltrumitecore");
   private static final List<Path> CODE = List.of(JAVA.resolve("worldevent/chitauri"), JAVA.resolve("entity/chitauri"), JAVA.resolve("client/chitauri"));
   private static final List<String> ELITES = List.of("captor", "heavy", "commander");
   private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[sd]");

   private static JsonObject read(Path path) throws IOException {
      return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
   }

   private static String sounds() throws IOException {
      return Files.readString(JAVA.resolve("worldevent/chitauri/ChitauriSounds.java"));
   }

   /** The NAMES list of ChitauriSounds, read from source (loading the class would touch the mod registries). */
   private static List<String> names() throws IOException {
      Matcher m = Pattern.compile("NAMES\\s*=\\s*List\\.of\\(([^)]*)\\)", Pattern.DOTALL).matcher(sounds());
      assertTrue(m.find(), "ChitauriSounds.NAMES");
      List<String> names = new ArrayList<>();
      Matcher q = Pattern.compile("\"([a-z0-9_]+)\"").matcher(m.group(1));
      while (q.find()) {
         names.add(q.group(1));
      }
      return names;
   }

   private static List<Path> javaFiles() throws IOException {
      List<Path> all = new ArrayList<>();
      for (Path dir : CODE) {
         try (Stream<Path> s = Files.walk(dir)) {
            all.addAll(s.filter(p -> p.toString().endsWith(".java")).toList());
         }
      }
      return all;
   }

   private static byte[] decode(Path b64) throws IOException {
      return Base64.getMimeDecoder().decode(Files.readString(b64).trim());
   }

   @Test
   void everyRegisteredSoundIsListedOnce() throws IOException {
      List<String> names = names();
      assertEquals(names.size(), new HashSet<>(names).size(), "duplicate names");
      Set<String> registered = new LinkedHashSet<>();
      Matcher m = Pattern.compile("reg\\(\"chitauri\\.([a-z0-9_]+)\"\\)").matcher(sounds());
      while (m.find()) {
         registered.add(m.group(1));
      }
      assertEquals(new TreeSet<>(names), new TreeSet<>(registered));
      assertTrue(names.size() >= 25, "the cue sheet has " + names.size() + " events");
   }

   @Test
   void soundsOverlayMatchesTheRegistryAndOwnFiles() throws IOException {
      JsonObject overlay = read(SOUND_OVERLAY);
      Set<String> expected = names().stream().map(n -> "chitauri." + n).collect(Collectors.toCollection(TreeSet::new));
      assertEquals(expected, new TreeSet<>(overlay.keySet()));
      Path dir = BIN.resolve("sounds/chitauri");
      Set<String> used = new HashSet<>();
      for (Map.Entry<String, JsonElement> e : overlay.entrySet()) {
         JsonObject ev = e.getValue().getAsJsonObject();
         assertEquals("subtitles.viltrumitecore." + e.getKey(), ev.get("subtitle").getAsString(), e.getKey() + " subtitle");
         assertFalse(ev.getAsJsonArray("sounds").isEmpty(), e.getKey());
         for (JsonElement s : ev.getAsJsonArray("sounds")) {
            String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
            assertTrue(name.startsWith("viltrumitecore:chitauri/"), e.getKey() + " plays a foreign sound " + name);
            String file = name.substring("viltrumitecore:chitauri/".length());
            used.add(file);
            Path b64 = dir.resolve(file + ".ogg.b64");
            assertTrue(Files.exists(b64), "missing " + b64);
            byte[] ogg = decode(b64);
            assertTrue(ogg.length > 2000, file + " is too short");
            assertEquals("OggS", new String(ogg, 0, 4, StandardCharsets.US_ASCII), file + " is not an Ogg stream");
            assertTrue(new String(ogg, 0, Math.min(ogg.length, 200), StandardCharsets.ISO_8859_1).contains("vorbis"), file + " is not Vorbis");
         }
      }
      try (Stream<Path> files = Files.list(dir)) {
         for (Path f : files.toList()) {
            String n = f.getFileName().toString();
            assertTrue(n.endsWith(".ogg.b64"), n);
            assertTrue(used.contains(n.substring(0, n.length() - ".ogg.b64".length())), "orphan sound file " + n);
         }
      }
   }

   @Test
   void theEventPlaysNoVanillaSounds() throws IOException {
      for (Path f : javaFiles()) {
         String src = Files.readString(f);
         assertFalse(src.contains("SoundEvents."), f + " uses a vanilla SoundEvent");
         assertFalse(src.contains("new ResourceLocation(\"minecraft\""), f + " names a vanilla resource");
      }
      // the base entity sounds are overridden with our own (or silence)
      String soldier = Files.readString(JAVA.resolve("entity/chitauri/ChitauriSoldier.java"));
      for (String hook : new String[]{"getHurtSound", "getDeathSound", "getAmbientSound", "playStepSound", "getFallSounds", "playBlockFallSound"}) {
         assertTrue(soldier.contains(hook), "soldier overrides " + hook);
      }
      String mob = Files.readString(JAVA.resolve("entity/chitauri/ChitauriMob.java"));
      for (String f : new String[]{"ChitauriChariot.java", "ChitauriLeviathan.java"}) {
         String src = Files.readString(JAVA.resolve("entity/chitauri/" + f)) + mob;
         assertTrue(src.contains("getHurtSound") && src.contains("getDeathSound"), f + " overrides hurt/death sounds");
      }
   }

   /** The invasion only lives in chunks players already keep loaded: no tickets, no forced chunks, no sync chunk loads. */
   @Test
   void theEventNeverForcesChunks() throws IOException {
      for (Path f : javaFiles()) {
         String src = Files.readString(f);
         for (String banned : new String[]{"addRegionTicket", "setChunkForced", "TicketType", ".getChunk(", "ForgeChunkManager"}) {
            assertFalse(src.contains(banned), f + " uses " + banned);
         }
      }
   }

   @Test
   void everyLocaleCarriesEveryLineWithTheSamePlaceholders() throws IOException {
      JsonObject en = read(LANG.resolve("en_us.json"));
      Set<String> locales = new TreeSet<>();
      try (Stream<Path> base = Files.list(BASE_LANG)) {
         base.forEach(p -> locales.add(p.getFileName().toString()));
      }
      Set<String> ours = new TreeSet<>();
      try (Stream<Path> s = Files.list(LANG)) {
         s.forEach(p -> ours.add(p.getFileName().toString()));
      }
      assertEquals(locales, ours, "one Chitauri file per shipped locale");
      assertEquals(12, ours.size());
      for (String locale : ours) {
         JsonObject tr = read(LANG.resolve(locale));
         assertEquals(new TreeSet<>(en.keySet()), new TreeSet<>(tr.keySet()), locale + " keys");
         int same = 0;
         for (String key : en.keySet()) {
            String value = tr.get(key).getAsString();
            assertFalse(value.isBlank(), locale + " " + key);
            assertEquals(sorted(PLACEHOLDER, en.get(key).getAsString()), sorted(PLACEHOLDER, value), locale + " " + key + " placeholders");
            if (!locale.equals("en_us.json") && value.equals(en.get(key).getAsString())) {
               same++;
            }
         }
         // a word or two may be spelled alike (Leviathan), the rest is translated
         assertTrue(locale.equals("en_us.json") || same <= 3, locale + " has " + same + " untranslated lines");
      }
   }

   @Test
   void russianIsActuallyRussian() throws IOException {
      JsonObject ru = read(LANG.resolve("ru_ru.json"));
      for (String key : ru.keySet()) {
         String v = ru.get(key).getAsString();
         assertTrue(v.chars().anyMatch(c -> c >= 0x0400 && c <= 0x04FF), "ru_ru " + key + " = " + v);
      }
   }

   private static List<String> sorted(Pattern p, String s) {
      List<String> out = new ArrayList<>();
      Matcher m = p.matcher(s);
      while (m.find()) {
         String g = m.group();
         out.add(g.replaceAll("\\d+\\$", ""));
      }
      out.sort(null);
      return out;
   }

   @Test
   void everyKeyTheCodeAsksForExists() throws IOException {
      JsonObject en = read(LANG.resolve("en_us.json"));
      Pattern literal = Pattern.compile("\"((?:commands\\.|subtitles\\.)?viltrumitecore\\.chitauri\\.[a-z_.]*)\"");
      int checked = 0;
      for (Path f : javaFiles()) {
         Matcher m = literal.matcher(Files.readString(f));
         while (m.find()) {
            String key = m.group(1);
            if (key.endsWith(".")) {
               continue; // a prefix completed by a phase key, checked below
            }
            assertTrue(en.has(key), f.getFileName() + " asks for missing key " + key);
            checked++;
         }
      }
      assertTrue(checked > 20, "found " + checked + " keys");
      for (ChitauriPhase phase : ChitauriPhase.values()) {
         for (String prefix : new String[]{"phase", "title", "subtitle", "chat"}) {
            assertTrue(en.has("viltrumitecore.chitauri." + prefix + "." + phase.key()), prefix + "." + phase.key());
         }
      }
      for (String name : names()) {
         assertTrue(en.has("subtitles.viltrumitecore.chitauri." + name), "subtitle " + name);
      }
      for (String entity : new String[]{"soldier", "chariot", "leviathan", "bolt", "captor", "heavy", "commander", "interceptor", "siege", "beacon"}) {
         assertTrue(en.has("entity.viltrumitecore.chitauri_" + entity), entity);
      }
   }

   private static Set<String> bones(String geo) throws IOException {
      Set<String> names = new HashSet<>();
      JsonObject g = read(ASSETS.resolve("geo/chitauri/" + geo + ".geo.json")).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
      for (JsonElement bone : g.getAsJsonArray("bones")) {
         names.add(bone.getAsJsonObject().get("name").getAsString());
      }
      return names;
   }

   private static JsonObject clips(String file) throws IOException {
      return read(ASSETS.resolve("animations/chitauri/" + file + ".animation.json")).getAsJsonObject("animations");
   }

   @Test
   void clipsAnimateOnlyBonesOfTheirModel() throws IOException {
      Map<String, String> rig = new java.util.HashMap<>(Map.of("soldier", "soldier", "soldier_states", "soldier", "chariot", "chariot"));
      for (String unit : ELITES) {
         for (String suffix : new String[]{"", "_states", "_special"}) {
            rig.put(unit + suffix, unit);
         }
      }
      for (String craft : new String[]{"interceptor", "siege", "beacon"}) {
         rig.put(craft, craft);
      }
      for (Map.Entry<String, String> e : rig.entrySet()) {
         Set<String> bones = bones(e.getValue());
         for (Map.Entry<String, JsonElement> clip : clips(e.getKey()).entrySet()) {
            JsonObject animated = clip.getValue().getAsJsonObject().getAsJsonObject("bones");
            assertNotNull(animated, clip.getKey());
            for (String bone : animated.keySet()) {
               assertTrue(bones.contains(bone), e.getKey() + "/" + clip.getKey() + " animates unknown bone " + bone);
            }
         }
      }
      for (Map.Entry<String, JsonElement> clip : clips("leviathan").entrySet()) {
         String part = clip.getKey().substring(0, clip.getKey().indexOf('_'));
         Set<String> bones = bones("leviathan_" + part);
         for (String bone : clip.getValue().getAsJsonObject().getAsJsonObject("bones").keySet()) {
            assertTrue(bones.contains(bone), "leviathan/" + clip.getKey() + " animates unknown bone " + bone);
         }
      }
   }

   @Test
   void renderersFindTheirClipsAndBones() throws IOException {
      Map<String, List<String>> needed = Map.of(
         "soldier", List.of("idle", "walk", "run", "aim", "shoot", "melee", "hurt"),
         "soldier_states", List.of("fall", "land", "death", "sever", "mounted", "mounted_shoot", "mounted_pilot"),
         "chariot", List.of("idle", "cannon"),
         "leviathan", List.of("head_idle", "head_bite", "head_roar", "head_hurt", "head_dead", "segment_swim", "segment_dead", "tail_swim", "tail_dead"));
      for (Map.Entry<String, List<String>> e : needed.entrySet()) {
         JsonObject c = clips(e.getKey());
         for (String clip : e.getValue()) {
            assertTrue(c.has(clip), e.getKey() + " lacks clip " + clip);
            assertTrue(c.getAsJsonObject(clip).get("animation_length").getAsDouble() > 0.0, clip + " length");
         }
      }
      assertTrue(bones("soldier").containsAll(List.of("torso", "chest", "head")));
      // the evolution units: every clip their renderers ask for, and the bones they drive by code
      Map<String, List<String>> units = Map.of(
         "captor_special", List.of("capture", "carry", "drop"),
         "heavy_special", List.of("slam", "vent", "armor_break", "stagger"),
         "commander_special", List.of("idle_planted", "signal", "command", "sweep", "stagger"),
         "interceptor", List.of("cruise", "hover", "boost", "brake", "takeoff", "perch", "turn_l", "turn_r", "accel", "maneuver", "fire", "hit", "death_fall"),
         "siege", List.of("idle", "move", "deploy", "charge", "fire", "stagger", "dying"),
         "beacon", List.of("deploy", "idle", "hurt", "down"));
      for (String unit : ELITES) {
         for (String clip : List.of("idle", "walk", "run", "turn_l", "turn_r", "aim", "shoot", "reload", "melee", "hurt")) {
            assertTrue(clips(unit).has(clip), unit + " lacks " + clip);
         }
         for (String clip : List.of("knockback", "fall", "land", "death", "sever", "disembark", "mounted", "mounted_shoot")) {
            assertTrue(clips(unit + "_states").has(clip), unit + "_states lacks " + clip);
         }
         assertTrue(bones(unit).containsAll(List.of("torso", "chest", "head", "muzzle")), unit + " look / muzzle bones");
      }
      for (Map.Entry<String, List<String>> e : units.entrySet()) {
         for (String clip : e.getValue()) {
            assertTrue(clips(e.getKey()).has(clip), e.getKey() + " lacks clip " + clip);
            assertTrue(clips(e.getKey()).getAsJsonObject(clip).get("animation_length").getAsDouble() > 0.0, clip + " length");
         }
      }
      assertTrue(bones("heavy").contains("armor_chest"));
      assertTrue(bones("interceptor").containsAll(List.of("thrust_r", "thrust_l")));
      assertTrue(bones("siege").containsAll(List.of("turret", "barrel", "muzzle")));
      assertTrue(bones("beacon").containsAll(List.of("emitter", "cage", "arm1", "arm2", "arm3", "arm4")));
      // each new unit is its own model, not a re-skin of the soldier
      Set<String> soldier = bones("soldier");
      for (String unit : List.of("captor", "heavy", "commander", "interceptor", "siege", "beacon")) {
         Set<String> own = new HashSet<>(bones(unit));
         own.removeAll(soldier);
         assertTrue(own.size() >= 3, unit + " has its own bones");
      }
      assertTrue(bones("chariot").containsAll(List.of("fin_l", "fin_r")));
      assertTrue(bones("leviathan_head").contains("skull"));
   }

   @Test
   void texturesAreOwnPngs() throws IOException {
      for (String tex : new String[]{"soldier", "chariot", "leviathan_head", "leviathan_segment", "leviathan_tail", "captor", "heavy", "commander",
            "interceptor", "siege", "beacon"}) {
         for (String suffix : new String[]{"", "_glow"}) {
            Path p = BIN.resolve("textures/entity/chitauri/" + tex + suffix + ".png.b64");
            assertTrue(Files.exists(p), "missing " + p);
            byte[] png = decode(p);
            assertTrue(png.length > 8 && (png[0] & 0xFF) == 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G', p + " is not a PNG");
         }
      }
   }
}
