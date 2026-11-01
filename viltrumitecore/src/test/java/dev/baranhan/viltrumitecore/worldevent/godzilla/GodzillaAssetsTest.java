package dev.baranhan.viltrumitecore.worldevent.godzilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
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
 * Asset contracts of the Godzilla event: every sound is our own (registry, sounds overlay, .ogg files, subtitles and
 * the ledger in step; no vanilla SoundEvents), every locale carries every line with the same placeholders, every key
 * the code asks for exists, the three model LODs parse and keep their joints on {@link GodzillaRules} (the server hit
 * zones and the client model agree), every move has its clip and the textures are PNGs of the atlas size.
 */
class GodzillaAssetsTest {
   private static final Path ASSETS = Path.of("src/main/resources/assets/viltrumitecore");
   private static final Path BIN = Path.of("src/main/binassets/assets/viltrumitecore");
   private static final Path SOUND_OVERLAY = Path.of("src/main/sound-overlays/godzilla.json");
   private static final Path LANG = Path.of("src/main/lang-overlays/godzilla");
   private static final Path DATA = Path.of("src/main/resources/data/viltrumitecore/damage_type");
   private static final Path JAVA = Path.of("src/main/java/dev/baranhan/viltrumitecore");
   private static final Path LEDGER = Path.of("../tools/assets/godzilla_sources.md");
   private static final List<Path> CODE = List.of(JAVA.resolve("worldevent/godzilla"), JAVA.resolve("entity/godzilla"), JAVA.resolve("client/godzilla"));
   private static final List<String> LOCALES = List.of("de_de", "en_us", "es_ar", "es_cl", "es_ec", "es_es", "es_mx", "es_uy", "es_ve", "ru_ru",
      "tr_tr", "zh_cn");
   private static final List<String> LODS = List.of("geo/godzilla/godzilla.geo.json", "geo/godzilla/godzilla_lod1.geo.json",
      "geo/godzilla/godzilla_lod2.geo.json");
   private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[sd]|%%");
   /** Model px per HEIGHT fraction (the geo is MODEL_HEIGHT blocks tall). */
   private static final double F = GodzillaRules.MODEL_HEIGHT * 16.0;

   private static JsonObject json(String text) {
      return JsonParser.parseString(text).getAsJsonObject();
   }

   /** A text file, or its {@code .part0..N} joined in order (the Gradle decodeBinaryAssets contract). */
   private static String text(Path plain) throws IOException {
      if (Files.exists(plain)) {
         return Files.readString(plain, StandardCharsets.UTF_8);
      }
      StringBuilder sb = new StringBuilder();
      for (int i = 0; ; i++) {
         Path part = plain.resolveSibling(plain.getFileName() + ".part" + i);
         if (!Files.exists(part)) {
            assertTrue(i > 0, "missing " + plain);
            return sb.toString();
         }
         sb.append(Files.readString(part, StandardCharsets.UTF_8));
      }
   }

   /** A model or clip: in resources, or split in binassets. */
   private static JsonObject resource(String rel) throws IOException {
      Path plain = ASSETS.resolve(rel);
      return json(Files.exists(plain) ? Files.readString(plain, StandardCharsets.UTF_8) : text(BIN.resolve(rel)));
   }

   /** A binary asset packed as {@code <file>.b64} or {@code <file>.b64.part0..N}. */
   private static byte[] binary(String rel) throws IOException {
      return Base64.getMimeDecoder().decode(text(BIN.resolve(rel + ".b64")).trim());
   }

   private static String sounds() throws IOException {
      return Files.readString(JAVA.resolve("worldevent/godzilla/GodzillaSounds.java"));
   }

   /** GodzillaSounds.NAMES read from source (loading the class would touch the mod registries). */
   private static List<String> names() throws IOException {
      Matcher m = Pattern.compile("NAMES\\s*=\\s*List\\.of\\(([^)]*)\\)", Pattern.DOTALL).matcher(sounds());
      assertTrue(m.find(), "GodzillaSounds.NAMES");
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

   private static JsonObject lang(String locale) throws IOException {
      return json(Files.readString(LANG.resolve(locale + ".json"), StandardCharsets.UTF_8));
   }

   private static List<String> placeholders(String s) {
      List<String> out = new ArrayList<>();
      Matcher m = PLACEHOLDER.matcher(s);
      while (m.find()) {
         out.add(m.group());
      }
      out.sort(String::compareTo);
      return out;
   }

   // ------------------------------------------------------------------ sounds

   @Test
   void everyRegisteredSoundIsListedOnce() throws IOException {
      List<String> names = names();
      assertEquals(names.size(), new HashSet<>(names).size(), "duplicate names");
      Set<String> registered = new LinkedHashSet<>();
      Matcher m = Pattern.compile("reg\\(\"godzilla\\.([a-z0-9_]+)\"\\)").matcher(sounds());
      while (m.find()) {
         registered.add(m.group(1));
      }
      assertEquals(new TreeSet<>(names), new TreeSet<>(registered));
      assertTrue(names.size() >= 22, "the cue sheet has " + names.size() + " events");
   }

   @Test
   void soundsOverlayMatchesTheRegistryAndOwnFiles() throws IOException {
      JsonObject overlay = json(Files.readString(SOUND_OVERLAY, StandardCharsets.UTF_8));
      Set<String> expected = names().stream().map(n -> "godzilla." + n).collect(Collectors.toCollection(TreeSet::new));
      assertEquals(expected, new TreeSet<>(overlay.keySet()));
      Set<String> used = new HashSet<>();
      for (Map.Entry<String, JsonElement> e : overlay.entrySet()) {
         JsonObject ev = e.getValue().getAsJsonObject();
         assertEquals("subtitles.viltrumitecore." + e.getKey(), ev.get("subtitle").getAsString(), e.getKey() + " subtitle");
         assertEquals("hostile", ev.get("category").getAsString(), e.getKey() + " category");
         assertFalse(ev.getAsJsonArray("sounds").isEmpty(), e.getKey());
         for (JsonElement s : ev.getAsJsonArray("sounds")) {
            String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
            assertTrue(name.startsWith("viltrumitecore:godzilla/"), e.getKey() + " plays a foreign sound " + name);
            String file = name.substring("viltrumitecore:godzilla/".length());
            used.add(file);
            byte[] ogg = binary("sounds/godzilla/" + file + ".ogg");
            assertTrue(ogg.length > 2000, file + " is too short");
            assertEquals("OggS", new String(ogg, 0, 4, StandardCharsets.US_ASCII), file + " is not an Ogg stream");
            assertTrue(new String(ogg, 0, Math.min(ogg.length, 200), StandardCharsets.ISO_8859_1).contains("vorbis"), file + " is not Vorbis");
         }
      }
      try (Stream<Path> files = Files.list(BIN.resolve("sounds/godzilla"))) {
         for (Path f : files.toList()) {
            String n = f.getFileName().toString().replaceAll("\\.part\\d+$", "");
            assertTrue(n.endsWith(".ogg.b64"), n);
            assertTrue(used.contains(n.substring(0, n.length() - ".ogg.b64".length())), "orphan sound file " + f.getFileName());
         }
      }
   }

   @Test
   void theLedgerListsEveryCue() throws IOException {
      String ledger = Files.readString(LEDGER, StandardCharsets.UTF_8);
      for (String n : names()) {
         assertTrue(ledger.contains("`godzilla." + n + "`"), "ledger misses godzilla." + n);
      }
      assertTrue(ledger.contains("CC0"), "ledger states the licence");
   }

   @Test
   void theEventPlaysNoVanillaSounds() throws IOException {
      for (Path f : javaFiles()) {
         String src = Files.readString(f);
         assertFalse(src.contains("SoundEvents."), f + " uses a vanilla SoundEvent");
         assertFalse(src.contains("ParticleTypes."), f + " uses a vanilla particle");
         assertFalse(src.contains("GuardianRenderer") || src.contains("guardian_beam"), f + " borrows the guardian beam");
      }
      String entity = Files.readString(JAVA.resolve("entity/godzilla/GodzillaEntity.java"));
      for (String hook : new String[]{"getHurtSound", "getDeathSound", "getAmbientSound", "playStepSound", "getMovementEmission"}) {
         assertTrue(entity.contains(hook), "the kaiju overrides " + hook);
      }
   }

   /** The event only lives in chunks players keep loaded: no tickets, no forced chunks, no sync chunk loads. */
   @Test
   void theEventNeverForcesChunks() throws IOException {
      for (Path f : javaFiles()) {
         String src = Files.readString(f);
         for (String banned : new String[]{"addRegionTicket", "setChunkForced", "TicketType", ".getChunk(", "ForgeChunkManager"}) {
            assertFalse(src.contains(banned), f + " uses " + banned);
         }
      }
   }

   // ------------------------------------------------------------------ language

   @Test
   void everyLocaleCarriesEveryLineWithTheSamePlaceholders() throws IOException {
      JsonObject en = lang("en_us");
      for (String locale : LOCALES) {
         JsonObject l = lang(locale);
         assertEquals(new TreeSet<>(en.keySet()), new TreeSet<>(l.keySet()), locale + " keys");
         for (String k : en.keySet()) {
            String v = l.get(k).getAsString();
            assertFalse(v.isBlank(), locale + " " + k);
            assertEquals(placeholders(en.get(k).getAsString()), placeholders(v), locale + " " + k);
         }
      }
      // Russian is written, not copied
      JsonObject ru = lang("ru_ru");
      int same = 0;
      for (String k : en.keySet()) {
         if (ru.get(k).getAsString().equals(en.get(k).getAsString())) {
            same++;
         }
      }
      assertTrue(same == 0, same + " Russian lines are English");
   }

   @Test
   void everyKeyTheCodeAsksForExists() throws IOException {
      JsonObject en = lang("en_us");
      Pattern key = Pattern.compile("\"((?:commands\\.)?viltrumitecore\\.godzilla[a-z0-9_.]*|entity\\.viltrumitecore\\.godzilla)\"");
      Set<String> asked = new TreeSet<>();
      for (Path f : javaFiles()) {
         String text = Files.readString(f);
         Matcher m = key.matcher(text);
         while (m.find()) {
            // system property names (the QA switch) are not lang keys
            if (!text.startsWith("getBoolean(", Math.max(0, m.start() - 11))) {
               asked.add(m.group(1));
            }
         }
      }
      assertTrue(asked.size() > 20, "found " + asked);
      for (String k : asked) {
         if (k.endsWith(".")) {
            assertTrue(en.keySet().stream().anyMatch(x -> x.startsWith(k)), "no key under " + k);
         } else {
            assertTrue(en.has(k), "missing " + k);
         }
      }
      for (GodzillaPhase p : GodzillaPhase.values()) {
         assertTrue(en.has("viltrumitecore.godzilla.phase." + p.key()), "phase " + p.key());
      }
      for (String n : names()) {
         assertTrue(en.has("subtitles.viltrumitecore.godzilla." + n), "subtitle " + n);
      }
      for (String type : new String[]{"godzilla", "godzilla_breath"}) {
         String id = json(Files.readString(DATA.resolve(type + ".json"))).get("message_id").getAsString();
         assertTrue(en.has("death.attack." + id), "death message " + id);
         assertTrue(en.has("death.attack." + id + ".player"), "death message " + id + ".player");
      }
   }

   // ------------------------------------------------------------------ model, clips, textures

   private static Map<String, double[]> pivots(JsonObject geo) {
      Map<String, double[]> out = new HashMap<>();
      for (JsonElement b : geo.getAsJsonArray("bones")) {
         JsonObject o = b.getAsJsonObject();
         JsonArray p = o.getAsJsonArray("pivot");
         out.put(o.get("name").getAsString(), p == null ? new double[3] : new double[]{p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble()});
      }
      return out;
   }

   private static int cubes(JsonObject geo) {
      int n = 0;
      for (JsonElement b : geo.getAsJsonArray("bones")) {
         JsonArray c = b.getAsJsonObject().getAsJsonArray("cubes");
         n += c == null ? 0 : c.size();
      }
      return n;
   }

   /** Rules body frame (x left, y up, z forward; HEIGHT fractions) to geo px (z back). */
   private static double[] px(float[] rule) {
      return new double[]{rule[0] * F, rule[1] * F, -rule[2] * F};
   }

   private static void near(double[] want, double[] got, String what) {
      assertNotNull(got, what);
      for (int i = 0; i < 3; i++) {
         assertTrue(Math.abs(want[i] - got[i]) < 1.0, what + " axis " + i + ": rules " + want[i] + " model " + got[i]);
      }
   }

   @Test
   void everyLodKeepsItsJointsOnTheRules() throws IOException {
      int last = Integer.MAX_VALUE;
      for (String lod : LODS) {
         JsonObject geo = resource(lod).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
         Map<String, double[]> p = pivots(geo);
         for (String bone : new String[]{"root", "hips", "waist", "chest", "neck", "head", "jaw", "eyes", "mouth_glow", "arm_l", "arm_r", "leg_l", "leg_r"}) {
            assertTrue(p.containsKey(bone), lod + " misses " + bone);
         }
         near(px(GodzillaRules.NECK_PIVOT), p.get("head"), lod + " head pivot");
         near(px(GodzillaRules.SHOULDER_L), p.get("arm_l"), lod + " left shoulder");
         double[] r = px(GodzillaRules.SHOULDER_L);
         near(new double[]{-r[0], r[1], r[2]}, p.get("arm_r"), lod + " right shoulder");
         // the tail chain: the server hit boxes follow these joints
         double[] joint = px(GodzillaRules.TAIL_ROOT);
         for (int k = 0; k < GodzillaRules.TAIL_SEGMENTS; k++) {
            near(joint, p.get("tail_" + k), lod + " tail_" + k);
            double a = Math.toRadians(GodzillaRules.TAIL_PITCH[k]);
            double len = GodzillaRules.TAIL_LENGTH[k] * F;
            joint = new double[]{joint[0], joint[1] - Math.sin(a) * len, joint[2] + Math.cos(a) * len};
         }
         long plates = p.keySet().stream().filter(b -> b.startsWith("plate_")).count();
         assertTrue(plates >= 6, lod + " has " + plates + " plates");
         for (String b : p.keySet()) {
            if (b.startsWith("plate_")) {
               float s = Float.parseFloat(b.substring(6, 10)) / 1000.0F;
               assertTrue(s >= 0.0F && s <= 1.0F, b);
            }
         }
         int n = cubes(geo);
         assertTrue(n < last, lod + " is not lighter than the LOD before it (" + n + " cubes)");
         last = n;
         assertEquals(512, geo.getAsJsonObject("description").get("texture_width").getAsInt(), lod);
      }
      assertTrue(last >= 30, "the far LOD keeps a silhouette");
   }

   @Test
   void everyMoveHasItsClipOnExistingBones() throws IOException {
      JsonObject anims = resource("animations/godzilla/godzilla.animation.json").getAsJsonObject("animations");
      Set<String> need = new TreeSet<>(List.of("idle", "walk", "tail_strike_m", "stomp_m"));
      for (GodzillaMove m : GodzillaMove.values()) {
         if (m != GodzillaMove.NONE) {
            need.add(m.clip);
         }
      }
      for (String clip : need) {
         assertTrue(anims.has(clip), "missing clip " + clip);
      }
      for (String loop : new String[]{"idle", "walk", "breath_fire", "down"}) {
         JsonElement l = anims.getAsJsonObject(loop).get("loop");
         assertTrue(l != null && l.getAsBoolean(), loop + " loops");
      }
      Set<String> bones = pivots(resource(LODS.get(0)).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()).keySet();
      for (Map.Entry<String, JsonElement> clip : anims.entrySet()) {
         JsonObject b = clip.getValue().getAsJsonObject().getAsJsonObject("bones");
         assertNotNull(b, clip.getKey() + " animates nothing");
         for (String bone : b.keySet()) {
            assertTrue(bones.contains(bone), clip.getKey() + " animates a missing bone " + bone);
         }
         JsonElement len = clip.getValue().getAsJsonObject().get("animation_length");
         GodzillaMove move = null;
         for (GodzillaMove m : GodzillaMove.values()) {
            if (m.clip.equals(clip.getKey().replaceAll("_m$", "")) && m.length > 0) {
               move = m;
            }
         }
         if (move != null && len != null) {
            assertTrue(Math.abs(len.getAsDouble() * 20.0 - move.length) <= 2.0, clip.getKey() + " lasts " + len + " s, the move " + move.length + " ticks");
         }
      }
   }

   @Test
   void texturesArePngsOfTheAtlasSize() throws IOException {
      for (String t : new String[]{"textures/entity/godzilla/godzilla.png", "textures/entity/godzilla/godzilla_glow.png"}) {
         byte[] png = binary(t);
         assertTrue(png.length > 100, t);
         assertEquals((byte)0x89, png[0], t);
         assertEquals("PNG", new String(png, 1, 3, StandardCharsets.US_ASCII), t);
         int w = ((png[16] & 255) << 24) | ((png[17] & 255) << 16) | ((png[18] & 255) << 8) | (png[19] & 255);
         int h = ((png[20] & 255) << 24) | ((png[21] & 255) << 16) | ((png[22] & 255) << 8) | (png[23] & 255);
         assertEquals(512, w, t);
         assertEquals(512, h, t);
      }
   }

   /** The client draws every effect kind the server may send. */
   @Test
   void theClientHandlesEveryFxKind() throws IOException {
      String packet = Files.readString(JAVA.resolve("worldevent/godzilla/net/GodzillaFxS2CPacket.java"));
      String client = Files.readString(JAVA.resolve("client/godzilla/GodzillaClient.java"));
      Matcher m = Pattern.compile("public static final int ([A-Z_]+) = (\\d+);").matcher(packet);
      int kinds = 0;
      while (m.find()) {
         assertTrue(client.contains("case GodzillaFxS2CPacket." + m.group(1) + " ->"), "client ignores " + m.group(1));
         kinds++;
      }
      assertTrue(kinds >= 10, kinds + " kinds");
   }
}