package dev.baranhan.viltrumitecore.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeechRules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for the multi-hero integration build (subtitles #65, icons #64, DIO #73,
 * Sinclair #75, Jinwoo #76 = #82, plus Thor + Hulkbuster #74). Each hero branch was written alone on top of the same base, so
 * a merge can silently drop a registration line, reorder wire ordinals or lose locale keys.
 * These checks look at the shared seams for all heroes at once.
 */
class HeroRosterIntegrationTest {
   private static final Path SRC = Path.of("src/main/java/dev/baranhan/viltrumitecore");
   private static final Path ASSETS = Path.of("src/main/resources/assets");
   private static final Path BINASSETS = Path.of("src/main/binassets/assets");
   private static final Path OVERLAYS = Path.of("src/main/lang-overlays");
   private static final Path SOUND_OVERLAYS = Path.of("src/main/sound-overlays");
   private static final String[] LOCALES = {"de_de", "en_us", "es_ar", "es_cl", "es_ec", "es_es", "es_mx", "es_uy", "es_ve", "ru_ru", "tr_tr", "zh_cn"};
   /** Heroes a player can pick; HUMAN is the default and VILTRUMITE is the legacy alias of HOMELANDER. */
   private static final Set<HeroId> NOT_SELECTABLE = EnumSet.of(HeroId.HUMAN, HeroId.VILTRUMITE);

   @BeforeAll
   static void registerHeroes() {
      HeroRegistry.registerDefaults();
   }

   @Test
   void wireOrdinalsAreFrozenAndNewHeroesAppendInMergeOrder() {
      HeroId[] ids = HeroId.values();
      assertSame(HeroId.REINHARD, ids[5], "released heroes keep their ordinals");
      // all-heroes-final: Thor keeps #74's ordinal 6, the #82 heroes follow. Saves store keys (HeroSession "Id"),
      // so only the in-memory / wire ordinal moves; the bumped protocol refuses mixed peers.
      assertSame(HeroId.THOR, ids[6]);
      assertSame(HeroId.DIO, ids[7]);
      assertSame(HeroId.SINCLAIR, ids[8]);
      assertSame(HeroId.JINWOO, ids[9]);
      assertEquals(10, ids.length, "a hero was added or lost: update this guard, the race screen and the protocol");
      Set<String> keys = new HashSet<>();
      for (HeroId id : ids) {
         assertTrue(keys.add(id.key()), "duplicate hero key " + id.key());
         assertSame(id == HeroId.VILTRUMITE ? HeroId.HOMELANDER : id, HeroId.fromKey(id.key()));
      }
   }

   @Test
   void heroActionsKeepReleasedOrdinalsAndEachHeroBlockIsContiguous() {
      HeroAction[] actions = HeroAction.values();
      int reinhardLast = HeroAction.DIAGONAL_WORLD_CUT.ordinal();
      assertEquals(reinhardLast + 1, HeroAction.CONTEXT_POWER.ordinal(), "Thor block follows the released actions");
      assertEquals(HeroAction.CONTEXT_POWER.ordinal() + 3, HeroAction.CONTEXT_TELEPORT.ordinal(), "Thor block is contiguous");
      assertEquals(HeroAction.CONTEXT_TELEPORT.ordinal() + 1, HeroAction.THE_WORLD.ordinal(), "DIO block follows Thor");
      assertEquals(HeroAction.ROAD_ROLLER.ordinal() + 1, HeroAction.VEXING_BOLT.ordinal(), "Sinclair block follows DIO");
      assertEquals(HeroAction.AUDIENCE_PARTICIPATION.ordinal() + 1, HeroAction.DAGGER_RUSH.ordinal(), "Jinwoo block follows Sinclair");
      assertSame(HeroAction.MONARCH_CHARGE, actions[actions.length - 1]);
      for (HeroAction action : actions) {
         assertSame(action, HeroAction.byId(action.ordinal()));
      }
      assertNull(HeroAction.byId(actions.length));
   }

   @Test
   void everyHeroIsRegisteredOnce() {
      for (HeroId id : HeroId.values()) {
         if (id == HeroId.VILTRUMITE) continue;
         HeroDefinition definition = HeroRegistry.get(id);
         assertNotNull(definition, id.name());
         assertSame(id, definition.id(), id + " falls back to another hero: its register(...) line is missing");
      }
   }

   @Test
   void subtitleLinesAreUniqueAndTranslatedInEveryLocale() throws IOException {
      Map<String, Map<String, String>> locales = mergedLocales();
      Map<String, HeroId> owners = new HashMap<>();
      for (HeroId id : HeroId.values()) {
         if (id == HeroId.VILTRUMITE) continue;
         HeroDefinition hero = HeroRegistry.get(id);
         List<String> keys = new ArrayList<>();
         for (HeroLine line : hero.lines().all()) {
            HeroId previous = owners.put(line.id(), id);
            assertNull(previous, "line id " + line.id() + " is used by " + previous + " and " + id);
            keys.addAll(line.fragments());
            keys.add(line.speakerKey() != null ? line.speakerKey() : HeroSpeechRules.defaultSpeakerKey(id.key()));
         }
         for (String locale : LOCALES) {
            for (String key : keys) {
               String value = locales.get(locale).get(key);
               assertTrue(value != null && !value.isBlank(), locale + " misses subtitle " + key + " of " + id);
            }
         }
      }
      for (HeroId id : List.of(HeroId.REINHARD, HeroId.DIO, HeroId.SINCLAIR, HeroId.JINWOO)) {
         // Thor (#74) has no cinematic lines yet; his lines() may stay empty.
         assertFalse(HeroRegistry.get(id).lines().isEmpty(), id + " lost its cinematic lines");
      }
   }

   @Test
   void raceSelectionOffersEveryHeroWithATranslatedButton() throws IOException {
      String screen = Files.readString(SRC.resolve("client/gui/RaceSelectionScreen.java"));
      List<String> heroes = arrayItems(screen, "HeroId\\[\\] HEROES");
      List<String> labels = arrayItems(screen, "String\\[\\] LABELS");
      assertEquals(heroes.size(), labels.size(), "every button needs a label");
      Set<String> expected = new HashSet<>();
      for (HeroId id : HeroId.values()) {
         if (!NOT_SELECTABLE.contains(id)) expected.add("HeroId." + id.name());
      }
      assertEquals(expected, new HashSet<>(heroes), "the race selection screen must list every selectable hero");
      Map<String, Map<String, String>> locales = mergedLocales();
      for (String label : labels) {
         String key = "gui.viltrumitecore.race_selection." + label.replace("\"", "");
         for (String locale : LOCALES) {
            assertNotNull(locales.get(locale).get(key), locale + " misses " + key);
         }
      }
   }

   @Test
   void everyLocaleHasEveryEnglishKey() throws IOException {
      Map<String, Map<String, String>> locales = mergedLocales();
      Set<String> english = locales.get("en_us").keySet();
      for (String locale : LOCALES) {
         Set<String> missing = new HashSet<>(english);
         missing.removeAll(locales.get(locale).keySet());
         assertTrue(missing.isEmpty(), locale + " misses " + missing.size() + " keys, e.g. " + missing.stream().sorted().limit(5).toList());
      }
   }

   @Test
   void coreChannelRegistersEachPacketOnceWithABumpedProtocol() throws IOException {
      String source = Files.readString(SRC.resolve("network/CoreMessages.java"));
      Matcher m = Pattern.compile("messageBuilder\\(([\\w.]+)\\.class").matcher(source);
      List<String> packets = new ArrayList<>();
      while (m.find()) packets.add(m.group(1).replaceAll(".*\\.", ""));
      assertEquals(new HashSet<>(packets).size(), packets.size(), "a packet is registered twice: " + packets);
      for (String required : List.of("HeroLineS2CPacket", "DioTimeS2CPacket", "DioFxS2CPacket", "JinwooFxS2CPacket",
            "ThorSceneS2CPacket", "ThorSceneC2SPacket", "HulkbusterMotionC2SPacket", "HulkbusterMotionS2CPacket", "ThorDashS2CPacket")) {
         assertTrue(packets.contains(required), required + " is not registered");
      }
      assertTrue(packets.indexOf("HeroLineS2CPacket") < packets.indexOf("DioTimeS2CPacket"), "hero packets append after the subtitle packet");
      assertTrue(packets.indexOf("ThorDashS2CPacket") < packets.indexOf("HeroLineS2CPacket"), "Thor / Hulkbuster packets keep their #74 slots");
      // 12 = #74 alone, 13 = #82 alone, 14 = the merged table; 15 = Regulus Reborn's packet table
      // (RegulusFxS2CPacket replaces the first kit's packets) must refuse all of them.
      assertTrue(source.contains("PROTOCOL_VERSION = \"15\""), "a changed packet table needs its own protocol version");
      assertTrue(packets.contains("RegulusFxS2CPacket"), "RegulusFxS2CPacket is not registered");
   }

   @Test
   void everySoundEventHasAFileAndATranslatedSubtitle() throws IOException {
      Map<String, Map<String, String>> locales = mergedLocales();
      List<Path> soundFiles;
      try (Stream<Path> walk = Files.walk(ASSETS)) {
         soundFiles = new ArrayList<>(walk.filter(p -> p.getFileName().toString().equals("sounds.json")).toList());
      }
      // sound overlays are merged into sounds.json by processResources (build.gradle)
      if (Files.isDirectory(SOUND_OVERLAYS)) {
         try (Stream<Path> list = Files.list(SOUND_OVERLAYS)) {
            soundFiles.addAll(list.filter(p -> p.toString().endsWith(".json")).sorted().toList());
         }
      }
      assertFalse(soundFiles.isEmpty());
      // Missing legacy audio from the original JARs is tracked by the add_hero checker; do not hide new gaps.
      Set<String> knownMissing = new HashSet<>();
      Matcher known = Pattern.compile("(?m)^\\[sounds] \\S+ \\S+: (\\S+)\\.ogg not found$")
         .matcher(Files.readString(Path.of("../.agents/skills/add_hero/assets/known_problems.txt")));
      while (known.find()) knownMissing.add(known.group(1));
      int events = 0;
      for (Path file : soundFiles) {
         JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
         for (Map.Entry<String, JsonElement> event : json.entrySet()) {
            events++;
            JsonObject body = event.getValue().getAsJsonObject();
            // Eleven legacy events (Viltrumite, Regulus, Iron Man) carry literal English subtitles; only keys are checked.
            if (body.has("subtitle") && body.get("subtitle").getAsString().startsWith("subtitles.")) {
               String subtitle = body.get("subtitle").getAsString();
               for (String locale : LOCALES) {
                  assertNotNull(locales.get(locale).get(subtitle), locale + " misses " + subtitle);
               }
            }
            for (JsonElement entry : body.getAsJsonArray("sounds")) {
               boolean isEvent = entry.isJsonObject() && entry.getAsJsonObject().has("type") && "event".equals(entry.getAsJsonObject().get("type").getAsString());
               if (isEvent) continue;
               String name = entry.isJsonObject() ? entry.getAsJsonObject().get("name").getAsString() : entry.getAsString();
               String ns = name.contains(":") ? name.substring(0, name.indexOf(':')) : "minecraft";
               if (ns.equals("minecraft")) continue;
               if (knownMissing.contains(name)) continue;
               String path = name.substring(name.indexOf(':') + 1) + ".ogg";
               Path plain = ASSETS.resolve(ns).resolve("sounds").resolve(path);
               Path encoded = BINASSETS.resolve(ns).resolve("sounds").resolve(path + ".b64");
               assertTrue(Files.isRegularFile(plain) || Files.isRegularFile(encoded), event.getKey() + " has no audio file " + name);
            }
         }
      }
      assertTrue(events > 200, "sounds.json lost events in a merge: " + events);
   }

   /** Locale -> key -> text for all namespaces, with lang overlays merged as processResources does. */
   private static Map<String, Map<String, String>> mergedLocales() throws IOException {
      Map<String, Map<String, String>> out = new LinkedHashMap<>();
      List<Path> langDirs;
      try (Stream<Path> list = Files.list(ASSETS)) {
         langDirs = list.map(ns -> ns.resolve("lang")).filter(Files::isDirectory).sorted().toList();
      }
      List<Path> overlayDirs;
      try (Stream<Path> list = Files.isDirectory(OVERLAYS) ? Files.list(OVERLAYS) : Stream.empty()) {
         overlayDirs = list.filter(Files::isDirectory).sorted().toList();
      }
      for (String locale : LOCALES) {
         Map<String, String> merged = new HashMap<>();
         for (Path dir : langDirs) read(dir.resolve(locale + ".json"), merged);
         for (Path dir : overlayDirs) read(dir.resolve(locale + ".json"), merged);
         out.put(locale, merged);
      }
      return out;
   }

   private static void read(Path file, Map<String, String> into) throws IOException {
      if (!Files.isRegularFile(file)) return;
      JsonParser.parseString(Files.readString(file)).getAsJsonObject().entrySet()
         .forEach(e -> into.put(e.getKey(), e.getValue().getAsString()));
   }

   private static List<String> arrayItems(String source, String declaration) {
      Matcher m = Pattern.compile(declaration + "\\s*=\\s*\\{([^}]*)}").matcher(source);
      assertTrue(m.find(), declaration + " not found");
      List<String> items = new ArrayList<>();
      for (String item : m.group(1).split(",")) {
         if (!item.isBlank()) items.add(item.trim());
      }
      return items;
   }
}
