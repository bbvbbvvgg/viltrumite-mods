package dev.baranhan.viltrumitecore.worldevent.ultron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.integration.ShippedLang;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Asset contracts of ULTRON - ВОССТАНИЕ МАШИН: every sound is our own (registry, sounds overlay, .ogg files and
 * subtitles in step, no vanilla SoundEvents, no voice: Ultron speaks only through subtitles), every locale carries
 * every line with the same placeholders, every key the code asks for exists, the six machines' models, clips and
 * atlases parse, every clip animates bones of its model and every move and renderer finds its clip and bones.
 */
class UltronAssetsTest {
   private static final Path ASSETS = Path.of("src/main/resources/assets/viltrumitecore");
   private static final Path BIN = Path.of("src/main/binassets/assets/viltrumitecore");
   private static final Path SOUND_OVERLAY = Path.of("src/main/sound-overlays/ultron.json");
   private static final Path LANG = Path.of("src/main/lang-overlays/ultron");
   private static final Path BASE_LANG = ASSETS.resolve("lang");
   private static final Path JAVA = Path.of("src/main/java/dev/baranhan/viltrumitecore");
   private static final List<Path> CODE = List.of(JAVA.resolve("worldevent/ultron"), JAVA.resolve("entity/ultron"), JAVA.resolve("client/ultron"));
   /** Placeholders the game's translator understands: %s and %n$s (and %% for a percent sign). */
   private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?(.)");
   private static final List<String> MODELS = List.of("drone", "sentry", "brute", "interceptor", "engineer", "prime", "node", "missile");
   private static final List<String> LOOPS = List.of("drone_hover", "interceptor_jet", "prime_jet", "node_hum", "node_tether");

   private static JsonObject read(Path path) throws IOException {
      return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
   }

   /** A shipped text asset: plain under resources, or split as {@code <path>.part0..N} under binassets. */
   private static String text(String rel) throws IOException {
      Path plain = ASSETS.resolve(rel);
      if (Files.exists(plain)) {
         return Files.readString(plain, StandardCharsets.UTF_8);
      }
      StringBuilder out = new StringBuilder();
      for (int i = 0; Files.exists(BIN.resolve(rel + ".part" + i)); i++) {
         out.append(Files.readString(BIN.resolve(rel + ".part" + i), StandardCharsets.UTF_8));
      }
      assertTrue(out.length() > 0, "missing asset " + rel);
      return out.toString();
   }

   /** A shipped binary asset: {@code <path>.b64} or {@code <path>.b64.part0..N} under binassets. */
   private static byte[] binary(String rel) throws IOException {
      Path whole = BIN.resolve(rel + ".b64");
      StringBuilder b64 = new StringBuilder();
      if (Files.exists(whole)) {
         b64.append(Files.readString(whole).trim());
      } else {
         for (int i = 0; Files.exists(BIN.resolve(rel + ".b64.part" + i)); i++) {
            b64.append(Files.readString(BIN.resolve(rel + ".b64.part" + i)).trim());
         }
      }
      assertTrue(b64.length() > 0, "missing asset " + rel);
      return Base64.getMimeDecoder().decode(b64.toString());
   }

   private static String sounds() throws IOException {
      return Files.readString(JAVA.resolve("worldevent/ultron/UltronSounds.java"));
   }

   /** The NAMES list of UltronSounds, read from source (loading the class would touch the mod registries). */
   private static List<String> names() throws IOException {
      Matcher m = Pattern.compile("NAMES\\s*=\\s*List\\.of\\(([^)]*)\\)", Pattern.DOTALL).matcher(sounds());
      assertTrue(m.find(), "UltronSounds.NAMES");
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

   // ------------------------------------------------------------------ sounds

   @Test
   void everyRegisteredSoundIsListedOnce() throws IOException {
      List<String> names = names();
      assertEquals(names.size(), new HashSet<>(names).size(), "duplicate names");
      Set<String> registered = new LinkedHashSet<>();
      Matcher m = Pattern.compile("reg\\(\"ultron\\.([a-z0-9_]+)\"\\)").matcher(sounds());
      while (m.find()) {
         registered.add(m.group(1));
      }
      assertEquals(new TreeSet<>(names), new TreeSet<>(registered));
      assertTrue(names.size() >= 70, "the cue sheet has " + names.size() + " events");
   }

   @Test
   void soundsOverlayMatchesTheRegistryAndOwnFiles() throws IOException {
      JsonObject overlay = read(SOUND_OVERLAY);
      Set<String> expected = names().stream().map(n -> "ultron." + n).collect(Collectors.toCollection(TreeSet::new));
      assertEquals(expected, new TreeSet<>(overlay.keySet()));
      Path dir = BIN.resolve("sounds/ultron");
      Set<String> used = new HashSet<>();
      for (Map.Entry<String, JsonElement> e : overlay.entrySet()) {
         JsonObject ev = e.getValue().getAsJsonObject();
         assertEquals("subtitles.viltrumitecore." + e.getKey(), ev.get("subtitle").getAsString(), e.getKey() + " subtitle");
         assertFalse(ev.getAsJsonArray("sounds").isEmpty(), e.getKey());
         for (JsonElement s : ev.getAsJsonArray("sounds")) {
            String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
            assertTrue(name.startsWith("viltrumitecore:ultron/"), e.getKey() + " plays a foreign sound " + name);
            String file = name.substring("viltrumitecore:ultron/".length());
            used.add(file);
            byte[] ogg = Base64.getMimeDecoder().decode(Files.readString(dir.resolve(file + ".ogg.b64")).trim());
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
      // loops carry their own hearing distance (the client loop manager fades them by it)
      for (String loop : LOOPS) {
         JsonObject first = overlay.getAsJsonObject("ultron." + loop).getAsJsonArray("sounds").get(0).getAsJsonObject();
         assertTrue(first.has("attenuation_distance"), loop + " attenuation");
      }
   }

   @Test
   void theEventPlaysNoVanillaSounds() throws IOException {
      for (Path f : javaFiles()) {
         String src = Files.readString(f);
         assertFalse(src.contains("SoundEvents."), f + " uses a vanilla SoundEvent");
         assertFalse(src.contains("new ResourceLocation(\"minecraft\""), f + " names a vanilla resource");
      }
      // every machine replaces the base entity sounds with its own (or silence)
      String unit = Files.readString(JAVA.resolve("entity/ultron/UltronUnit.java"));
      for (String hook : new String[]{"getHurtSound", "getDeathSound", "playStepSound", "getFallSounds", "playBlockFallSound", "getSwimSound",
         "getSwimSplashSound", "getSwimHighSpeedSplashSound"}) {
         assertTrue(unit.contains(hook), "machines override " + hook);
      }
      String node = Files.readString(JAVA.resolve("entity/ultron/UltronNodeEntity.java"));
      for (String hook : new String[]{"getHurtSound", "getDeathSound", "getAmbientSound"}) {
         assertTrue(node.contains(hook), "nodes override " + hook);
      }
   }

   /** Ultron has no voice: a line is a subtitle plus the short transmission blip, and no cue is speech. */
   @Test
   void ultronSpeaksOnlyThroughSubtitles() throws IOException {
      String voice = Files.readString(JAVA.resolve("worldevent/ultron/UltronVoice.java"));
      Matcher played = Pattern.compile("UltronSounds\\.([A-Z_]+)").matcher(voice);
      Set<String> cues = new TreeSet<>();
      while (played.find()) {
         cues.add(played.group(1));
      }
      assertEquals(Set.of("TRANSMISSION"), cues, "a line only plays the transmission blip");
      Set<String> speech = Set.of("voice", "speech", "speak", "say", "tts", "vo", "talk", "line", "dialogue");
      for (String name : names()) {
         for (String word : name.split("_")) {
            assertFalse(speech.contains(word), "sound event " + name + " looks like a voice line");
         }
      }
      String kit = Files.readString(Path.of("../tools/sfx/ultron.py"));
      for (String tts : new String[]{"pyttsx", "espeak", "gTTS", "festival", "say -v"}) {
         assertFalse(kit.contains(tts), "the sound kit uses speech synthesis: " + tts);
      }
   }

   /** Loop files are cut to loop seamlessly (no attack, no tail): only the client loop manager plays them. */
   @Test
   void loopsOnlyRunInTheLoopManager() throws IOException {
      for (Path f : javaFiles()) {
         String file = f.getFileName().toString();
         if (file.equals("UltronLoops.java") || file.equals("UltronSounds.java")) {
            continue;
         }
         String src = Files.readString(f);
         for (String loop : LOOPS) {
            assertFalse(src.contains("UltronSounds." + loop.toUpperCase(Locale.ROOT)), file + " plays the loop " + loop + " as a one-shot");
         }
      }
      String loops = Files.readString(JAVA.resolve("client/ultron/UltronLoops.java"));
      for (String loop : LOOPS) {
         assertTrue(loops.contains("UltronSounds." + loop.toUpperCase(Locale.ROOT)), "nothing runs the loop " + loop);
      }
   }

   /** The uprising only lives in chunks players already keep loaded: no tickets, no forced chunks, no sync chunk loads. */
   @Test
   void theEventNeverForcesChunks() throws IOException {
      for (Path f : javaFiles()) {
         String src = Files.readString(f);
         for (String banned : new String[]{"addRegionTicket", "setChunkForced", "TicketType", ".getChunk(", "ForgeChunkManager"}) {
            assertFalse(src.contains(banned), f + " uses " + banned);
         }
      }
   }

   // ------------------------------------------------------------------ translations

   /** Placeholder indices of a translation (%s counted in order, %n$s by index); fails on what the game cannot format. */
   private static List<Integer> placeholders(String where, String s) {
      List<Integer> out = new ArrayList<>();
      Matcher m = FORMAT.matcher(s);
      int next = 1;
      while (m.find()) {
         String type = m.group(2);
         if (type.equals("%") && m.group(1) == null) {
            continue;
         }
         assertEquals("s", type, where + ": only %s, %n$s and %% format (" + s + ")");
         out.add(m.group(1) == null ? next++ : Integer.parseInt(m.group(1)));
      }
      out.sort(null);
      return out;
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
      assertEquals(locales, ours, "one Ultron file per shipped locale");
      assertEquals(12, ours.size());
      for (String locale : ours) {
         JsonObject tr = read(LANG.resolve(locale));
         assertEquals(new TreeSet<>(en.keySet()), new TreeSet<>(tr.keySet()), locale + " keys");
         int same = 0;
         for (String key : en.keySet()) {
            String value = tr.get(key).getAsString();
            assertFalse(value.isBlank(), locale + " " + key);
            assertEquals(placeholders("en_us " + key, en.get(key).getAsString()), placeholders(locale + " " + key, value), locale + " " + key + " placeholders");
            if (!locale.equals("en_us.json") && value.equals(en.get(key).getAsString()) && value.chars().anyMatch(Character::isLetter)) {
               same++;
            }
         }
         // names spelled alike (ULTRON, Ultron Prime, the protocol tag) may stay, the rest is translated
         assertTrue(locale.equals("en_us.json") || same <= 5, locale + " has " + same + " untranslated lines");
      }
   }

   @Test
   void russianIsActuallyRussian() throws IOException {
      JsonObject ru = read(LANG.resolve("ru_ru.json"));
      JsonObject en = read(LANG.resolve("en_us.json"));
      int latinOnly = 0;
      for (String key : ru.keySet()) {
         String v = ru.get(key).getAsString();
         if (v.chars().noneMatch(Character::isLetter)) {
            continue; // a pure format string (numbers, separators)
         }
         if (v.chars().noneMatch(c -> c >= 0x0400 && c <= 0x04FF)) {
            latinOnly++;
            assertEquals(en.get(key).getAsString(), v, "ru_ru " + key + " is neither Russian nor the shared name");
         }
      }
      assertTrue(latinOnly <= 3, "ru_ru keeps " + latinOnly + " Latin-only lines");
   }

   private static final Pattern LITERAL = Pattern.compile("\"((?:commands\\.|subtitles?\\.|entity\\.)?viltrumitecore[._](?:speaker\\.)?ultron[a-z0-9_.]*)\"");

   @Test
   void everyKeyTheCodeAsksForExists() throws IOException {
      Map<String, JsonObject> shipped = ShippedLang.merged();
      JsonObject en = shipped.get("en_us.json");
      assertNotNull(en);
      int checked = 0;
      for (Path f : javaFiles()) {
         Matcher m = LITERAL.matcher(Files.readString(f));
         while (m.find()) {
            String key = m.group(1);
            if (key.endsWith(".") || key.endsWith("_") || key.equals("viltrumitecore_ultron")) {
               continue; // a prefix completed by an enum key (checked below) or a resource id
            }
            assertTrue(en.has(key), f.getFileName() + " asks for missing key " + key);
            checked++;
         }
      }
      assertTrue(checked > 40, "found " + checked + " keys");
      List<String> keys = new ArrayList<>();
      for (UltronPhase phase : UltronPhase.values()) {
         keys.add("viltrumitecore.ultron.phase." + phase.key());
         if (phase != UltronPhase.ENDED) {
            keys.add("viltrumitecore.ultron.title." + phase.key());
            keys.add("viltrumitecore.ultron.subtitle." + phase.key());
         }
      }
      for (String end : new String[]{"victory", "defeat"}) {
         keys.add("viltrumitecore.ultron.title." + end);
         keys.add("viltrumitecore.ultron.subtitle." + end);
         keys.add("viltrumitecore.ultron.bar." + end);
      }
      for (UltronAdaptation.Protocol p : UltronAdaptation.Protocol.values()) {
         keys.add("viltrumitecore.ultron.protocol." + p.key());
      }
      for (UltronNodeKind k : UltronNodeKind.values()) {
         keys.add("viltrumitecore.ultron.node." + k.key());
      }
      for (UltronNodeState st : UltronNodeState.values()) {
         keys.add("viltrumitecore.ultron.node_state." + st.name().toLowerCase(java.util.Locale.ROOT));
      }
      for (UltronScenario.Variant v : UltronScenario.Variant.values()) {
         keys.add("viltrumitecore.ultron.variant." + v.key());
      }
      for (UltronBossRules.Stage st : UltronBossRules.Stage.values()) {
         keys.add("viltrumitecore.ultron.stage." + st.key());
      }
      for (UltronDoctrine d : UltronDoctrine.values()) {
         keys.add("viltrumitecore.ultron.doctrine." + d.key());
      }
      for (UltronUnitKind k : UltronUnitKind.values()) {
         keys.add("entity.viltrumitecore.ultron_" + k.key());
      }
      for (String entity : new String[]{"prime", "node", "bolt", "missile"}) {
         keys.add("entity.viltrumitecore.ultron_" + entity);
      }
      for (String name : names()) {
         keys.add("subtitles.viltrumitecore.ultron." + name);
      }
      keys.addAll(UltronVoice.keys());
      for (Map.Entry<String, JsonObject> locale : shipped.entrySet()) {
         for (String key : keys) {
            assertTrue(locale.getValue().has(key), locale.getKey() + " lacks " + key);
         }
      }
   }

   /** Every line he can say is wired to a trigger: no dead subtitle. */
   @Test
   void everyLineIsSaidSomewhere() throws IOException {
      StringBuilder code = new StringBuilder();
      for (Path f : javaFiles()) {
         if (!f.getFileName().toString().equals("UltronVoice.java")) {
            code.append(Files.readString(f));
         }
      }
      String all = code.toString();
      for (UltronVoice.Line line : UltronVoice.lines()) {
         String id = line.id();
         boolean said = all.contains("\"" + id + "\"")
            || UltronVoice.TAUNTS.contains(id) && all.contains("UltronVoice.nextTaunt("); // taunts rotate
         for (int cut = id.indexOf('_'); !said && cut > 0; cut = id.indexOf('_', cut + 1)) {
            said = all.contains("\"" + id.substring(0, cut + 1) + "\""); // composed: "protocol_" + key
         }
         assertTrue(said, "line " + id + " is never said");
      }
   }

   // ------------------------------------------------------------------ models, clips, atlases

   private static JsonObject geometry(String model) throws IOException {
      JsonObject root = JsonParser.parseString(text("geo/ultron/" + model + ".geo.json")).getAsJsonObject();
      return root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
   }

   private static Set<String> bones(String model) throws IOException {
      Set<String> names = new HashSet<>();
      for (JsonElement bone : geometry(model).getAsJsonArray("bones")) {
         assertTrue(names.add(bone.getAsJsonObject().get("name").getAsString()), model + " repeats bone " + bone.getAsJsonObject().get("name"));
      }
      return names;
   }

   private static JsonObject clips(String model) throws IOException {
      return JsonParser.parseString(text("animations/ultron/" + model + ".animation.json")).getAsJsonObject().getAsJsonObject("animations");
   }

   @Test
   void modelsAreRichAndBonesFormOneTree() throws IOException {
      // detail floor per machine (each is a jointed, plated model, not a block)
      Map<String, Integer> minCubes = Map.of("drone", 100, "sentry", 140, "brute", 180, "interceptor", 50, "engineer", 80, "prime", 220, "node", 250);
      for (String model : MODELS) {
         JsonObject g = geometry(model);
         Set<String> names = bones(model);
         int cubes = 0;
         for (JsonElement b : g.getAsJsonArray("bones")) {
            JsonObject bone = b.getAsJsonObject();
            if (bone.has("parent")) {
               assertTrue(names.contains(bone.get("parent").getAsString()), model + "/" + bone.get("name") + " has an unknown parent");
            }
            if (bone.has("cubes")) {
               cubes += bone.getAsJsonArray("cubes").size();
            }
         }
         assertTrue(cubes >= minCubes.getOrDefault(model, 1), model + " has only " + cubes + " cubes");
         JsonObject desc = g.getAsJsonObject("description");
         assertTrue(desc.get("texture_width").getAsInt() >= 64 && desc.get("texture_height").getAsInt() >= 64, model + " atlas size");
      }
   }

   @Test
   void clipsAnimateOnlyBonesOfTheirModel() throws IOException {
      for (String model : MODELS) {
         Set<String> bones = bones(model);
         for (Map.Entry<String, JsonElement> clip : clips(model).entrySet()) {
            JsonObject c = clip.getValue().getAsJsonObject();
            assertTrue(c.get("animation_length").getAsDouble() > 0.0, model + "/" + clip.getKey() + " length");
            JsonObject animated = c.getAsJsonObject("bones");
            assertNotNull(animated, model + "/" + clip.getKey());
            for (String bone : animated.keySet()) {
               assertTrue(bones.contains(bone), model + "/" + clip.getKey() + " animates unknown bone " + bone);
            }
         }
      }
   }

   /** The model a move belongs to, by its prefix (shared moves: every fabricated machine). */
   private static List<String> owners(UltronMove move) {
      String n = move.name().toLowerCase(Locale.ROOT);
      for (String model : List.of("drone", "sentry", "brute", "interceptor", "engineer", "prime")) {
         if (n.startsWith(model + "_")) {
            return List.of(model);
         }
      }
      return switch (move) {
         case HEAVY_HIT, FABRICATE -> List.of("drone", "sentry", "brute", "interceptor", "engineer");
         case LAND -> List.of("prime");
         default -> List.of();
      };
   }

   @Test
   void everyMoveFindsItsClip() throws IOException {
      Map<String, JsonObject> clips = new TreeMap<>();
      for (String model : MODELS) {
         clips.put(model, clips(model));
      }
      for (UltronMove move : UltronMove.values()) {
         if (move == UltronMove.NONE) {
            continue;
         }
         List<String> owners = owners(move);
         assertFalse(owners.isEmpty(), move + " belongs to no machine");
         for (String model : owners) {
            assertTrue(clips.get(model).has(move.clip), model + " lacks clip " + move.clip + " for " + move);
         }
      }
      assertTrue(clips.get("prime").has("stagger"), "Prime staggers instead of the shared heavy hit");
   }

   @Test
   void renderersFindTheirClipsAndBones() throws IOException {
      Map<String, List<String>> needed = Map.of(
         "drone", List.of("hover", "fly", "accel", "decel", "turn_l", "turn_r", "hurt", "death"),
         "interceptor", List.of("hover", "fly", "accel", "decel", "turn_l", "turn_r", "hurt", "death"),
         "sentry", List.of("idle", "combat_idle", "walk", "run", "fall", "land", "accel", "decel", "turn_l", "turn_r", "hurt", "death", "guard"),
         "brute", List.of("idle", "combat_idle", "walk", "run", "fall", "land", "accel", "decel", "turn_l", "turn_r", "hurt", "death", "shield_hold"),
         "engineer", List.of("idle", "walk", "run", "accel", "decel", "turn_l", "turn_r", "hurt", "death", "weld"),
         "prime", List.of("idle", "combat_idle", "overdrive_idle", "walk", "run", "fall", "land", "hover", "fly", "flare", "talk", "accel", "decel",
            "turn_l", "turn_r", "hurt", "death"),
         "node", List.of("boot", "build", "collapse", "disabled", "hit"),
         "missile", List.of("fly"));
      for (Map.Entry<String, List<String>> e : needed.entrySet()) {
         JsonObject c = clips(e.getKey());
         for (String clip : e.getValue()) {
            assertTrue(c.has(clip), e.getKey() + " lacks clip " + clip);
         }
      }
      JsonObject node = clips("node");
      Set<String> nodeBones = bones("node");
      for (UltronNodeKind k : UltronNodeKind.values()) {
         assertTrue(node.has("idle_" + k.key()), "node lacks clip idle_" + k.key());
         assertTrue(nodeBones.contains("mod_" + k.key()), "node lacks module bone mod_" + k.key());
      }
      // every name a renderer looks up is a bone or a clip of its model
      Map<String, String> renderers = Map.of("Drone", "drone", "Sentry", "sentry", "Brute", "brute", "Interceptor", "interceptor", "Engineer", "engineer",
         "Prime", "prime", "Node", "node", "Missile", "missile");
      Pattern name = Pattern.compile("\"([a-z][a-z0-9_]*[a-z0-9])\"");
      for (Map.Entry<String, String> r : renderers.entrySet()) {
         String src = Files.readString(JAVA.resolve("client/ultron/Ultron" + r.getKey() + "Renderer.java"));
         Set<String> bones = bones(r.getValue());
         Set<String> clips = clips(r.getValue()).keySet();
         Matcher m = name.matcher(src);
         int seen = 0;
         while (m.find()) {
            String n = m.group(1);
            assertTrue(bones.contains(n) || clips.contains(n), r.getKey() + " renderer asks for " + n + ", not a bone or clip of " + r.getValue());
            seen++;
         }
         assertTrue(seen > 0, r.getKey());
      }
   }

   @Test
   void atlasesAreOwnPngs() throws IOException {
      for (String model : MODELS) {
         for (String suffix : new String[]{"", "_glow"}) {
            byte[] png = binary("textures/entity/ultron/" + model + suffix + ".png");
            assertTrue(png.length > 8 && (png[0] & 0xFF) == 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G', model + suffix + " is not a PNG");
            int w = ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16) | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF);
            int h = ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16) | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF);
            JsonObject desc = geometry(model).getAsJsonObject("description");
            assertEquals(desc.get("texture_width").getAsInt(), w, model + suffix + " width matches the model's UV space");
            assertEquals(desc.get("texture_height").getAsInt(), h, model + suffix + " height matches the model's UV space");
         }
      }
   }
}
