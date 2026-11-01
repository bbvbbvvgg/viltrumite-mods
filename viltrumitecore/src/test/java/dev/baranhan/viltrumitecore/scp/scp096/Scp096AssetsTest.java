package dev.baranhan.viltrumitecore.scp.scp096;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.scp096.Scp096Pose;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * SCP-096 assets: every sound event has a subtitle (the music excepted) and a vanilla fallback, no SCP: Containment
 * Breach audio is committed (the originals come only from the player's own copy), all 12 locales carry the same
 * keys, every key the Java code builds exists, and every clip the renderer asks for is in the clip files.
 */
class Scp096AssetsTest {
   private static final Path MAIN = Path.of("src/main");
   private static final Path LANG = MAIN.resolve("lang-overlays/scp096");
   private static final Path JAVA = MAIN.resolve("java/dev/baranhan/viltrumitecore");
   private static final Path ANIMS = MAIN.resolve("resources/assets/viltrumitecore/animations/scp096");
   private static final String[] LOCALES = {"en_us", "ru_ru", "de_de", "tr_tr", "zh_cn", "es_es", "es_mx", "es_ar", "es_cl", "es_ec", "es_uy",
      "es_ve"};

   private static JsonObject json(Path p) throws IOException {
      return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
   }

   @Test
   void soundEventsHaveSubtitlesAndOnlyVanillaFallbacks() throws IOException {
      String src = Files.readString(JAVA.resolve("scp/scp096/Scp096Sounds.java"));
      Set<String> events = new TreeSet<>();
      Matcher m = Pattern.compile("reg\\(\"([a-z0-9_.]+)\"\\)").matcher(src);
      while (m.find()) {
         events.add(m.group(1));
      }
      JsonObject overlay = json(MAIN.resolve("sound-overlays/scp096.json"));
      assertEquals(events, new TreeSet<>(overlay.keySet()), "overlay == Scp096Sounds");
      JsonObject en = json(LANG.resolve("en_us.json"));
      for (Map.Entry<String, JsonElement> e : overlay.entrySet()) {
         JsonObject ev = e.getValue().getAsJsonObject();
         if (!e.getKey().equals("scp096.chase_music")) {
            assertTrue(en.has(ev.get("subtitle").getAsString()), e.getKey() + " subtitle");
         }
         for (JsonElement s : ev.getAsJsonArray("sounds")) {
            JsonObject so = s.getAsJsonObject();
            assertEquals("event", so.get("type").getAsString(), e.getKey() + " falls back to a vanilla event");
            assertTrue(so.get("name").getAsString().startsWith("minecraft:"), e.getKey());
         }
      }
   }

   @Test
   void noContainmentBreachAudioIsCommitted() throws IOException {
      for (Path root : new Path[]{MAIN.resolve("binassets"), MAIN.resolve("resources")}) {
         try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile).forEach(p -> {
               String n = p.toString().toLowerCase().replace('\\', '/');
               assertFalse(n.contains("scp096") && (n.endsWith(".ogg") || n.endsWith(".ogg.b64") || n.contains(".ogg.b64.part")),
                  "SCP: CB audio must not be committed: " + p);
               assertFalse(n.contains("/sounds/scp096/"), "no scp096 sound folder in the jar: " + p);
            });
         }
      }
   }

   @Test
   void everyLocaleHasTheSameNonEmptyKeys() throws IOException {
      Set<String> keys = new TreeSet<>(json(LANG.resolve("en_us.json")).keySet());
      for (String locale : LOCALES) {
         JsonObject lang = json(LANG.resolve(locale + ".json"));
         assertEquals(keys, new TreeSet<>(lang.keySet()), locale + " keys");
         for (Map.Entry<String, JsonElement> e : lang.entrySet()) {
            assertFalse(e.getValue().getAsString().isBlank(), locale + " " + e.getKey());
         }
      }
      try (Stream<Path> files = Files.list(LANG)) {
         assertEquals(LOCALES.length, files.count(), "exactly the 12 supported locales");
      }
   }

   @Test
   void everyKeyTheCodeBuildsExists() throws IOException {
      Set<String> en = json(LANG.resolve("en_us.json")).keySet();
      String commands = Files.readString(JAVA.resolve("scp/scp096/Scp096Commands.java"));
      Matcher m = Pattern.compile("K \\+ \"([a-z_.]+)\"").matcher(commands);
      int n = 0;
      while (m.find()) {
         String suffix = m.group(1);
         if (!suffix.endsWith(".")) {
            assertTrue(en.contains("commands.viltrumitecore.scp096." + suffix), suffix);
            n++;
         }
      }
      assertTrue(n >= 12, "found the command keys: " + n);
      for (Scp096Spawner.Result r : Scp096Spawner.Result.values()) {
         assertTrue(en.contains("commands.viltrumitecore.scp096.natural.result." + r.name().toLowerCase()), r.name());
      }
      for (Scp096State s : Scp096State.values()) {
         assertTrue(en.contains("scp096.viltrumitecore.state." + s.name().toLowerCase()), s.name());
      }
      for (String k : new String[]{"entity.viltrumitecore.scp096", "death.attack.viltrumitecore.scp096", "pack.viltrumitecore.scp096_original.title",
         "pack.viltrumitecore.scp096_original.desc"}) {
         assertTrue(en.contains(k), k);
      }
   }

   @Test
   void everyClipTheRendererUsesExists() throws IOException {
      String[] files = {"scp096_calm", "scp096_rage", "scp096_chase"};
      for (int i = 0; i < Scp096Pose.LAYERS; i++) {
         JsonObject anims = json(ANIMS.resolve(files[Scp096Pose.FILE[i]] + ".animation.json")).getAsJsonObject("animations");
         assertTrue(anims.has(Scp096Pose.CLIP[i]), Scp096Pose.CLIP[i] + " in " + files[Scp096Pose.FILE[i]]);
         double len = anims.getAsJsonObject(Scp096Pose.CLIP[i]).get("animation_length").getAsDouble();
         assertEquals(Scp096Pose.LENGTH[i], len, 1.0E-6, Scp096Pose.CLIP[i] + " length");
      }
      assertTrue(json(ANIMS.resolve("scp096_calm.animation.json")).getAsJsonObject("animations").has("sob"));
      JsonObject chase = json(ANIMS.resolve("scp096_chase.animation.json")).getAsJsonObject("animations");
      assertTrue(chase.has("smash") && chase.has("attack"));
      assertTrue(Files.isRegularFile(MAIN.resolve("resources/assets/viltrumitecore/geo/scp096/scp096.geo.json"))
         || Files.isRegularFile(MAIN.resolve("binassets/assets/viltrumitecore/geo/scp096/scp096.geo.json.part0")), "the model");
   }
}
