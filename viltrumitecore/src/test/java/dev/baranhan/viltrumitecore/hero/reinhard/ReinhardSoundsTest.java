package dev.baranhan.viltrumitecore.hero.reinhard;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import static org.junit.jupiter.api.Assertions.*;

class ReinhardSoundsTest {
   private static final Path REPO = Path.of("..");
   private static final Path BIN = Path.of("src/main/binassets/assets/viltrumitecore/sounds/reinhard");
   private static final Map<String, String> FINAL = Map.ofEntries(
      Map.entry("mode_restrained", "mode_restrained"), Map.entry("mode_knight", "mode_knight"),
      Map.entry("mode_holy_sword", "mode_holy"), Map.entry("reid_choir", "reid_choir"),
      Map.entry("reid_unsheathe", "reid_unsheathe"), Map.entry("reid_rumble", "reid_rumble"),
      Map.entry("world_cut_silence", "world_cut_silence"), Map.entry("world_cut_tail", "world_cut_tail"),
      Map.entry("erase_crack", "erase_crack"), Map.entry("erase_inhale", "erase_inhale"),
      Map.entry("limbo_heartbeat", "limbo_heartbeat"), Map.entry("sky_kick", "sky_kick"),
      Map.entry("stomp", "stomp"), Map.entry("sheath_strike", "sheath_ring"));
   private static final Map<String, String> DERIVED = Map.of(
      "world_cut_hum", "world_cut_hum", "world_cut_pulse", "world_cut_pulse",
      "world_cut_impact", "world_cut_impact", "world_cut_blade", "sheath_ring");
   private static final Map<String,String> CINEMATIC = Map.of(
      "reid_cinematic_layer","reid_cinematic_layer","cut_rumble","cut_rumble",
      "world_cut_blast","cut_cinematic_impact","cut_rain","cut_cinematic_sweep");
   /** REINHARD OVERHAUL: strike/stance cues mixed from CC0 Freesound recordings (tools/sfx/reinhard_strikes.py), random variant per play. */
   private static final Map<String, List<String>> OVERHAUL = Map.of(
      "fist_swing", List.of("fist_swing_1", "fist_swing_2", "fist_swing_3"),
      "fist_hit", List.of("fist_hit_1", "fist_hit_2", "fist_hit_3"),
      "reid_swing", List.of("reid_swing_1", "reid_swing_2", "reid_swing_3"),
      "reid_hit", List.of("reid_hit_1", "reid_hit_2", "reid_hit_3"),
      "reid_heavy", List.of("reid_heavy_1", "reid_heavy_2"),
      "reid_drawn_hum", List.of("reid_drawn_hum"));
   /** REINHARD OVERHAUL item 12: owner-confirmed test sound, shipped byte-identical, license NOT verified. */
   private static final Map<String, String> TEMPORARY = Map.of("world_cut_strike_voice", "sword_strike_voice");
   private static final String TEMPORARY_SHA256 = "b7c97f3fb070687b8ae4252a627f4f9b932dd7975fb16cee1a3f4694b04ae1a6";
   private static final List<String> OVERHAUL_SOURCES = List.of(
      "https://freesound.org/people/mateusboga/sounds/614084", "https://freesound.org/people/qubodup/sounds/60004",
      "https://freesound.org/people/qubodup/sounds/60024", "https://freesound.org/people/Danjocross/sounds/507466",
      "https://freesound.org/people/bolkmar/sounds/475132", "https://freesound.org/people/Rodzuz/sounds/393904",
      "https://freesound.org/people/Kievinay/sounds/512389", "https://freesound.org/people/SypherZent/sounds/420674",
      "https://freesound.org/people/CogFireStudios/sounds/547038", "https://freesound.org/people/Eponn/sounds/547035",
      "https://freesound.org/people/Eponn/sounds/547036", "https://freesound.org/people/CogFireStudios/sounds/547042",
      "https://freesound.org/people/kibibu/sounds/22426");
   private static final Set<String> STREAM = Set.of("reid_choir", "world_cut_tail", "reid_rumble");
   private static final Set<String> LEGACY = Set.of("air_step", "refuse", "phoenix", "parry", "super_jump",
      "light_punch", "step_dash", "catch", "combo_hit", "combo_final", "kick_toss", "grab", "throw",
      "dash_behind", "reflect", "reid_far", "reid_sheathe", "cut_rain", "world_cut_blast");
   private static final Map<String, String> OUTPUT_SOURCE = Map.ofEntries(
      Map.entry("mode_restrained", "398218"), Map.entry("mode_knight", "398218"), Map.entry("mode_holy", "398218"),
      Map.entry("reid_choir", "398218"), Map.entry("reid_unsheathe", "770035"), Map.entry("reid_rumble", "870684"),
      Map.entry("world_cut_silence", "503809"), Map.entry("world_cut_tail", "248150"), Map.entry("erase_crack", "554564"),
      Map.entry("erase_inhale", "331620"), Map.entry("limbo_heartbeat", "249716"), Map.entry("sky_kick", "182050"),
      Map.entry("stomp", "275809"), Map.entry("sheath_ring", "519413"),
      Map.entry("world_cut_hum", "248150"), Map.entry("world_cut_pulse", "248150"),
      Map.entry("world_cut_impact", "248150"), Map.entry("world_cut_blade", "519413"));

   private JsonObject sounds() throws Exception {
      return JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/viltrumitecore/sounds.json"))).getAsJsonObject();
   }
   private JsonObject fixture(String file) throws Exception {
      try (var s = getClass().getResourceAsStream("/reinhard/" + file)) {
         assertNotNull(s, file); return JsonParser.parseString(new String(s.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
      }
   }
   private JsonObject entry(String event) throws Exception { return sounds().getAsJsonObject("reinhard." + event).getAsJsonArray("sounds").get(0).getAsJsonObject(); }
   private String text(String file) throws Exception { return Files.readString(REPO.resolve(file)); }

   private void validateSounds(JsonObject actual) throws Exception {
      JsonObject expected = fixture("stage6-sounds.json").deepCopy();
      for (var pair : FINAL.entrySet()) {
         JsonObject choice = new JsonObject(); choice.addProperty("name", "viltrumitecore:reinhard/" + pair.getValue());
         if (STREAM.contains(pair.getKey())) choice.addProperty("stream", true);
         if (pair.getKey().equals("world_cut_tail")) choice.addProperty("attenuation_distance",320);
         JsonArray array = new JsonArray(); array.add(choice);
         expected.getAsJsonObject("reinhard." + pair.getKey()).add("sounds", array);
      }
      for(var pair:CINEMATIC.entrySet()) {
         String event="reinhard."+pair.getKey();
         JsonObject definition=expected.has(event)?expected.getAsJsonObject(event):new JsonObject();
         JsonObject choice=new JsonObject();choice.addProperty("name","viltrumitecore:reinhard/"+pair.getValue());
         choice.addProperty("stream",false);choice.addProperty("attenuation_distance",pair.getKey().equals("cut_rain")?128:320);
         JsonArray array=new JsonArray();array.add(choice);definition.add("sounds",array);expected.add(event,definition);
      }
      for(var pair:DERIVED.entrySet()) {
         JsonObject choice=new JsonObject();choice.addProperty("name","viltrumitecore:reinhard/"+pair.getValue());
         choice.addProperty("stream",false);choice.addProperty("attenuation_distance",320);
         JsonObject definition=new JsonObject();JsonArray array=new JsonArray();array.add(choice);
         definition.add("sounds",array);expected.add("reinhard."+pair.getKey(),definition);
      }
      for (var pair : OVERHAUL.entrySet()) {
         JsonObject definition = new JsonObject(); definition.addProperty("category", "player");
         // The drawn hum is a quiet stance loop: no subtitle spam while Reid is held.
         if (!pair.getKey().equals("reid_drawn_hum")) definition.addProperty("subtitle", "subtitles.viltrumitecore.reinhard_" + pair.getKey());
         JsonArray array = new JsonArray();
         for (String file : pair.getValue()) {
            JsonObject choice = new JsonObject(); choice.addProperty("name", "viltrumitecore:reinhard/" + file);
            if (pair.getKey().equals("reid_heavy")) choice.addProperty("attenuation_distance", 32);
            array.add(choice);
         }
         definition.add("sounds", array); expected.add("reinhard." + pair.getKey(), definition);
      }
      for (var pair : TEMPORARY.entrySet()) {
         // Streamed, no attenuation: the stereo voice is played head-relative by WorldCutAudio.strike.
         JsonObject choice = new JsonObject(); choice.addProperty("name", "viltrumitecore:reinhard/" + pair.getValue());
         choice.addProperty("stream", true);
         JsonObject definition = new JsonObject(); JsonArray array = new JsonArray(); array.add(choice);
         definition.add("sounds", array); expected.add("reinhard." + pair.getKey(), definition);
      }
      JsonObject current = new JsonObject();
      for (String key : actual.keySet()) if (key.startsWith("reinhard.")) current.add(key, actual.get(key));
      assertEquals(expected, current, "Complete arrays, metadata and preserved legacy and scoped cinematic definitions must match");
      allFileReferencesExist(actual);
   }
   private void allFileReferencesExist(JsonObject actual) throws Exception {
      for (String key : actual.keySet()) if (key.startsWith("reinhard.")) {
         for (JsonElement element : actual.getAsJsonObject(key).getAsJsonArray("sounds")) {
            assertTrue(element.isJsonObject(), key + " explicit sound definition");
            JsonObject entry = element.getAsJsonObject();
            if (entry.has("type") && entry.get("type").getAsString().equals("event")) continue;
            String name = entry.get("name").getAsString();
            assertTrue(name.startsWith("viltrumitecore:reinhard/"), key + " file namespace");
            Path file = BIN.resolve(name.substring("viltrumitecore:reinhard/".length()) + ".ogg.b64");
            assertTrue(Files.isRegularFile(file), key + " missing actual reference " + name);
         }
      }
   }
   private List<String> cells(String line) {
      String[] raw = line.split("\\|", -1);
      return Arrays.stream(raw).skip(1).limit(raw.length - 2).map(String::trim).toList();
   }
   private String link(String cell) {
      var match = java.util.regex.Pattern.compile("\\]\\((https?://[^)]+)\\)").matcher(cell);
      assertTrue(match.find(), "Linked license required: " + cell); return match.group(1);
   }
   private boolean hasExactSourceLink(String text, String expected) {
      var urls = java.util.regex.Pattern.compile("\\]\\((https?://[^)]+)\\)").matcher(text);
      while (urls.find()) if (urls.group(1).replaceAll("/$", "").equals(expected)) return true;
      return false;
   }
   private void validateCredits(String sources, String credits, String spec) throws Exception {
      JsonObject approved = fixture("approved-provenance.json");
      Map<String, List<String>> rawRows = new HashMap<>();
      for (String line : sources.split("\\R")) if (line.matches("\\| [0-9]{5,6} —.*")) {
         List<String> c = cells(line); assertEquals(7, c.size(), line);
         String id = c.get(0).split(" ")[0]; assertNull(rawRows.put(id, c), "Duplicate source " + id);
      }
      assertEquals(approved.keySet(), rawRows.keySet(), "All 14 checked sources need their own record");
      assertTrue(sources.contains("2026-10-13"), "Shared live-page check date");
      for (String id : approved.keySet()) {
         JsonObject a = approved.getAsJsonObject(id); List<String> c = rawRows.get(id);
         assertEquals(a.get("author").getAsString(), c.get(1), id + " author");
         assertTrue(c.get(2).contains(a.get("license").getAsString()), id + " license version");
         assertEquals(a.get("license_url").getAsString(), link(c.get(2)), id + " license URL");
         assertEquals(a.get("url").getAsString(), c.get(3).replaceAll("/$", ""), id + " source URL");
         assertEquals(a.get("file").getAsString(), c.get(4).replace("`", ""), id + " archive filename");
         assertEquals(a.get("sha256").getAsString(), c.get(6).replace("`", ""), id + " input digest");
      }
      String outputs = sources.substring(sources.indexOf("### Processed outputs / reproducible edits"));
      Map<String, String> joins = new HashMap<>();
      for (String line : outputs.split("\\R")) if (line.startsWith("| `")) {
         List<String> c = cells(line); assertEquals(5, c.size(), line);
         assertNull(joins.put(c.get(0).replace("`", ""), c.get(1)), "Duplicate output");
      }
      assertEquals(OUTPUT_SOURCE, joins, "Each final file must bind to its selected source");
      for (var pair : OUTPUT_SOURCE.entrySet()) {
         JsonObject a = approved.getAsJsonObject(pair.getValue());
         assertNotEquals("CC-BY-NC 4.0", a.get("license").getAsString(), pair.getKey());
         assertTrue(java.util.regex.Pattern.compile("(?<!\\w)" + pair.getKey() + "(?!\\w)").matcher(rawRows.get(pair.getValue()).get(5)).find(), pair.getKey() + " raw source output record");
      }
      String table = spec.substring(spec.indexOf("### 10.1"), spec.indexOf("### 10.2"));
      Set<String> specIds = new HashSet<>(); int rowCount = 0;
      for (String line : table.split("\\R")) if (line.startsWith("| ") && !line.startsWith("| Событие |")) {
         List<String> c = cells(line); assertEquals(4, c.size(), "Four declared source columns: " + line);
         for (String cell : c) assertFalse(cell.isBlank(), line);
         var url = java.util.regex.Pattern.compile("https://freesound\\.org/[^\\s]+").matcher(c.get(1));
         assertTrue(url.find(), line); String sourceUrl = url.group().replaceAll("/$", "");
         String id = sourceUrl.substring(sourceUrl.lastIndexOf('/') + 1); assertTrue(approved.has(id), line);
         JsonObject a = approved.getAsJsonObject(id); specIds.add(id); rowCount++;
         assertEquals(a.get("url").getAsString(), sourceUrl, id + " spec URL");
         assertEquals(a.get("author").getAsString(), c.get(2), id + " spec author");
         assertTrue(c.get(3).contains(a.get("license").getAsString()), id + " spec license version");
         assertEquals(a.get("license_url").getAsString(), link(c.get(3)), id + " spec license URL");
      }
      assertEquals(15, rowCount); assertEquals(approved.keySet(), specIds);
      Set<String> used = new HashSet<>(OUTPUT_SOURCE.values());
      for (String id : used) {
         JsonObject a = approved.getAsJsonObject(id);
         if (!a.get("license").getAsString().equals("CC-BY 4.0")) continue;
         String author = a.get("author").getAsString(), sourceUrl = a.get("url").getAsString();
         String credit = Arrays.stream(credits.split("\\R")).filter(l -> l.startsWith("- **" + author + "**") && hasExactSourceLink(l, sourceUrl)).findFirst().orElse("");
         assertFalse(credit.isBlank(), id + " linked author credit");
         assertTrue(credit.contains(a.get("license_url").getAsString()), id + " CC-BY link");
         assertTrue(credit.contains("trimmed / layered / normalized"), id + " modification note");
         if (id.equals("398218")) assertTrue(credit.contains("Freesound.org"), "Additional requested Freesound credit");
      }
   }

   @Test void noPlaceholdersLeft() throws Exception { validateSounds(sounds()); }
   @Test void everyFileExists() throws Exception {
      allFileReferencesExist(sounds());
      Set<String> all=new HashSet<>(FINAL.values());all.addAll(CINEMATIC.values());all.addAll(DERIVED.values());
      OVERHAUL.values().forEach(all::addAll); all.addAll(TEMPORARY.values());
      for (String f : all) {
         byte[] bytes = Base64.getMimeDecoder().decode(Files.readString(BIN.resolve(f + ".ogg.b64")).trim());
         assertEquals("OggS", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
      }
      try (var paths = Files.list(BIN)) { assertEquals(all, paths.map(p -> p.getFileName().toString().replace(".ogg.b64", "")).collect(java.util.stream.Collectors.toSet())); }
   }
   @Test void everySoundCredited() throws Exception { validateCredits(text("tools/assets/reinhard_sources.md"), text("CREDITS.md"), text("docs/design/2026-10-11-reinhard-design.md")); }
   @Test void blockedSourcesAreNotShippedAndOriginalSynthesisReplacesPlaceholders() throws Exception {
      assertEquals("viltrumitecore:reinhard/cut_cinematic_impact",entry("world_cut_blast").get("name").getAsString());
      assertEquals("viltrumitecore:reinhard/cut_cinematic_sweep",entry("cut_rain").get("name").getAsString());
      assertFalse(entry("world_cut_blast").has("type"));assertFalse(entry("cut_rain").has("type"));
      assertFalse(Files.exists(BIN.resolve("world_cut_blast.ogg.b64")));assertFalse(Files.exists(BIN.resolve("cut_rain.ogg.b64")));
      assertTrue(text("tools/assets/reinhard_sources.md").contains("tools/sfx/reinhard_cinematic.py"));
      assertTrue(text("tools/assets/reinhard_sources.md").contains("tools/sfx/reinhard_strikes.py"));
      assertTrue(text("CREDITS.md").contains("tools/sfx/reinhard_strikes.py"));
      assertTrue(text("CREDITS.md").contains("CC0-1.0"));
   }
   /** Every recording behind the overhaul cues is credited with its exact Freesound page and the CC0 1.0 link. */
   @Test void overhaulRecordingsAreCreditedCc0() throws Exception {
      String sources = text("tools/assets/reinhard_sources.md"), credits = text("CREDITS.md");
      String section = sources.substring(sources.indexOf("## REINHARD OVERHAUL strike sounds"), sources.indexOf("## World Cut ambience"));
      for (String page : OVERHAUL_SOURCES) {
         String id = page.substring(page.lastIndexOf('/') + 1);
         String row = Arrays.stream(section.split("\\R")).filter(l -> l.startsWith("| [" + id + "](" + page + ")")).findFirst().orElse("");
         assertFalse(row.isBlank(), id + " source row");
         assertTrue(row.contains("[CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)"), id + " CC0 license link");
         assertTrue(hasExactSourceLink(credits, page), id + " credit link");
      }
      assertTrue(section.contains("not a listening test"), "No listening claim without playback");
   }
   /** The temporary strike voice is the owner's file byte for byte and is documented as unlicensed, to be replaced. */
   @Test void temporaryStrikeVoiceIsTheOwnerFileAndMarkedUnverified() throws Exception {
      byte[] bytes = Base64.getMimeDecoder().decode(Files.readString(BIN.resolve("sword_strike_voice.ogg.b64")).trim());
      assertEquals(159533, bytes.length);
      assertEquals(TEMPORARY_SHA256, java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
      String sources = text("tools/assets/reinhard_sources.md"), credits = text("CREDITS.md");
      String section = sources.substring(sources.indexOf("## TEMPORARY test asset"));
      for (String required : List.of(TEMPORARY_SHA256, "https://github.com/bbvbbvvgg/Codex-Superheroes",
            "be526ea313d7f5f652346c70a88b995f28977cee", "https://www.youtube.com/watch?v=GtFqJpCrwCQ",
            "License NOT verified", "Replace it with a licensed or original sound", "public release", "WORLD_CUT_BLAST",
            "not a listening test"))
         assertTrue(section.contains(required), "source record mentions " + required);
      String credit = credits.substring(credits.indexOf("### TEMPORARY test asset"));
      assertTrue(credit.contains("not verified") && credit.contains("replaced"), "credits flag the temporary use");
      assertFalse(OUTPUT_SOURCE.containsKey("sword_strike_voice"), "not presented as an approved CC0 / CC-BY output");
   }
   @Test void longFilesStream() throws Exception { for (String e : STREAM) { assertTrue(entry(e).has("stream")); assertTrue(entry(e).get("stream").getAsBoolean()); } }
   @Test void spec102ReferencesRemainUnchanged() throws Exception { validateSounds(sounds()); }

   @Test void extraVanillaChoiceAfterFirstFinalEntryIsRejected() throws Exception {
      var mutated = sounds().deepCopy(); var choice = new JsonObject(); choice.addProperty("name", "minecraft:entity.generic.explode"); choice.addProperty("type", "event");
      mutated.getAsJsonObject("reinhard.mode_restrained").getAsJsonArray("sounds").add(choice);
      assertThrows(AssertionFailedError.class, () -> validateSounds(mutated));
   }
   @Test void missingExtraFileReferenceIsRejected() throws Exception {
      var mutated = sounds().deepCopy(); var choice = new JsonObject(); choice.addProperty("name", "viltrumitecore:reinhard/not_committed");
      mutated.getAsJsonObject("reinhard.mode_restrained").getAsJsonArray("sounds").add(choice);
      assertThrows(AssertionFailedError.class, () -> allFileReferencesExist(mutated));
   }
   @Test void legacyAttributesCannotChangeUnderPreservedId() throws Exception {
      var mutated = sounds().deepCopy(); mutated.getAsJsonObject("reinhard.air_step").getAsJsonArray("sounds").get(0).getAsJsonObject().addProperty("volume", .1);
      assertThrows(AssertionFailedError.class, () -> validateSounds(mutated));
   }
   @Test void deletingSourceRowsCannotBeHiddenByOutputNames() throws Exception {
      String sources = text("tools/assets/reinhard_sources.md").replaceAll("(?m)^\\| [0-9]{5,6} —.*\\R", "");
      assertThrows(AssertionFailedError.class, () -> validateCredits(sources, text("CREDITS.md"), text("docs/design/2026-10-11-reinhard-design.md")));
   }
   @Test void wrongOutputToSourceJoinIsRejected() throws Exception {
      String sources = text("tools/assets/reinhard_sources.md").replace("| `mode_restrained` | 398218 |", "| `mode_restrained` | 770035 |");
      assertThrows(AssertionFailedError.class, () -> validateCredits(sources, text("CREDITS.md"), text("docs/design/2026-10-11-reinhard-design.md")));
   }
   @Test void wrongSourceUrlIsRejectedDespiteCreditElsewhere() throws Exception {
      String sources = text("tools/assets/reinhard_sources.md").replace("https://freesound.org/people/klankbeeld/sounds/398218/", "https://example.invalid/398218/");
      assertThrows(AssertionFailedError.class, () -> validateCredits(sources, text("CREDITS.md"), text("docs/design/2026-10-11-reinhard-design.md")));
   }
   @Test void missingCcByCreditLinkIsRejectedDespiteAuthorName() throws Exception {
      String credits = text("CREDITS.md").replace("https://freesound.org/people/klankbeeld/sounds/398218/", "https://example.invalid/");
      assertThrows(AssertionFailedError.class, () -> validateCredits(text("tools/assets/reinhard_sources.md"), credits, text("docs/design/2026-10-11-reinhard-design.md")));
   }
   @Test void wrongCreditLinkSuffixCannotMasqueradeAsExactSource() throws Exception {
      String credits = text("CREDITS.md").replace("https://freesound.org/people/klankbeeld/sounds/398218/", "https://freesound.org/people/klankbeeld/sounds/398218/not-the-sound/");
      assertThrows(AssertionFailedError.class, () -> validateCredits(text("tools/assets/reinhard_sources.md"), credits, text("docs/design/2026-10-11-reinhard-design.md")));
   }
   @Test void wrongLicenseInSourceRecordIsRejected() throws Exception {
      String sources = text("tools/assets/reinhard_sources.md").replaceFirst("https://creativecommons.org/licenses/by/4.0/", "https://creativecommons.org/licenses/by-nc/4.0/");
      assertThrows(AssertionFailedError.class, () -> validateCredits(sources, text("CREDITS.md"), text("docs/design/2026-10-11-reinhard-design.md")));
   }
   @Test void blankSpecAuthorAndLicenseCellsAreRejected() throws Exception {
      String spec = text("docs/design/2026-10-11-reinhard-design.md");
      int start = spec.indexOf("| Смена режима |"); int end = spec.indexOf('\n', start);
      spec = spec.substring(0, start) + "| Смена режима | https://freesound.org/people/klankbeeld/sounds/398218/ | | |" + spec.substring(end);
      final String changed = spec;
      assertThrows(AssertionFailedError.class, () -> validateCredits(text("tools/assets/reinhard_sources.md"), text("CREDITS.md"), changed));
   }
}
