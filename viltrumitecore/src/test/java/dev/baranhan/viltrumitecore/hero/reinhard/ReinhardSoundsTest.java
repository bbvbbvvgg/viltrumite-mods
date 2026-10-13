package dev.baranhan.viltrumitecore.hero.reinhard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
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
   private static final Set<String> LEGACY = Set.of("air_step", "refuse", "phoenix", "parry", "super_jump",
      "light_punch", "step_dash", "catch", "combo_hit", "combo_final", "kick_toss", "grab", "throw",
      "dash_behind", "reflect", "reid_far", "reid_sheathe", "cut_rain", "world_cut_blast");

   private JsonObject sounds() throws Exception {
      return JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/viltrumitecore/sounds.json"))).getAsJsonObject();
   }
   private JsonObject entry(String event) throws Exception {
      return sounds().getAsJsonObject("reinhard." + event).getAsJsonArray("sounds").get(0).getAsJsonObject();
   }
   @Test void noPlaceholdersLeft() throws Exception {
      for (var pair : FINAL.entrySet()) {
         var e = entry(pair.getKey());
         assertEquals("viltrumitecore:reinhard/" + pair.getValue(), e.get("name").getAsString(), pair.getKey());
         assertFalse(e.has("type") && e.get("type").getAsString().equals("event"), pair.getKey());
      }
      // Deliberate exceptions are documented; do not silently accept any additional placeholder.
      assertEquals(33, sounds().keySet().stream().filter(k -> k.startsWith("reinhard.")).count());
      for (String id : LEGACY) assertTrue(sounds().has("reinhard." + id), id);
   }
   @Test void everyFileExists() throws Exception {
      for (String file : FINAL.values()) {
         Path p = BIN.resolve(file + ".ogg.b64");
         assertTrue(Files.isRegularFile(p), p.toString());
         byte[] bytes = Base64.getMimeDecoder().decode(Files.readString(p).trim());
         assertEquals("OggS", new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
      }
      try (var paths = Files.list(BIN)) { assertEquals(Set.copyOf(FINAL.values()), paths.map(p -> p.getFileName().toString().replace(".ogg.b64", "")).collect(java.util.stream.Collectors.toSet())); }
   }
   @Test void everySoundCredited() throws Exception {
      String sources = Files.readString(REPO.resolve("tools/assets/reinhard_sources.md"));
      String credits = Files.readString(REPO.resolve("CREDITS.md"));
      String spec = Files.readString(REPO.resolve("docs/design/2026-10-11-reinhard-design.md"));
      for (String file : FINAL.values()) assertTrue(sources.contains(file), file);
      for (String author : Set.of("klankbeeld", "hykenfreak", "dingus23")) {
         assertTrue(credits.contains(author), author); assertTrue(spec.contains(author), author);
      }
      assertTrue(sources.contains("CC-BY-NC 4.0"));
      assertTrue(sources.contains("2026-10-13"));
      assertTrue(credits.contains("trimmed / layered / normalized"));
   }
   @Test void blockedSourcesStayExactPlaceholders() throws Exception {
      assertEquals("minecraft:entity.generic.explode", entry("world_cut_blast").get("name").getAsString());
      assertEquals("minecraft:entity.player.attack.sweep", entry("cut_rain").get("name").getAsString());
      assertEquals("event", entry("world_cut_blast").get("type").getAsString());
      assertEquals("event", entry("cut_rain").get("type").getAsString());
      assertFalse(Files.exists(BIN.resolve("world_cut_blast.ogg.b64")));
      assertFalse(Files.exists(BIN.resolve("cut_rain.ogg.b64")));
   }
   @Test void longFilesStream() throws Exception {
      for (String e : Set.of("reid_choir", "world_cut_tail", "reid_rumble")) { assertTrue(entry(e).has("stream"), e + " must stream"); assertTrue(entry(e).get("stream").getAsBoolean(), e); }
   }
   @Test void spec102ReferencesRemainUnchanged() throws Exception {
      Map<String, String> refs = Map.of("light_punch", "viltrumitecore:punch_impact", "combo_hit", "viltrumitecore:regulus_punch_hit",
         "combo_final", "viltrumitecore:regulus_impact_heavy", "kick_toss", "minecraft:entity.player.attack.sweep", "throw", "viltrumitecore:regulus_impact_heavy");
      for (var p : refs.entrySet()) { assertEquals(p.getValue(), entry(p.getKey()).get("name").getAsString()); assertEquals("event", entry(p.getKey()).get("type").getAsString()); }
   }
}
