package dev.baranhan.viltrumitecore.worldevent.meteor;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Asset contracts of Meteor Rain 2.0: registry, sound overlay, .ogg files and subtitles in step; particle,
 * texture, block-state and model files present; the old flat meteor smoke is no longer used by the meteor.
 */
class MeteorRainAssetsTest {
   private static final Path ASSETS = Path.of("src/main/resources/assets/viltrumitecore");
   private static final Path BIN = Path.of("src/main/binassets/assets/viltrumitecore");
   private static final Path OVERLAY = Path.of("src/main/sound-overlays/meteor_rain_fx.json");
   private static final Path LANG = Path.of("src/main/lang-overlays/meteor_rain");
   private static final Path JAVA = Path.of("src/main/java/dev/baranhan/viltrumitecore");
   private static final List<String> LOCALES = List.of("de_de", "en_us", "es_ar", "es_cl", "es_ec", "es_es", "es_mx", "es_uy", "es_ve",
      "ru_ru", "tr_tr", "zh_cn");

   private static JsonObject read(Path path) throws IOException {
      return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
   }

   private static List<String> registered() throws IOException {
      Matcher m = Pattern.compile("reg\\(\"(meteor_rain\\.[a-z_]+)\"\\)").matcher(Files.readString(JAVA.resolve("worldevent/meteor/MeteorRainSounds.java")));
      List<String> names = new ArrayList<>();
      while (m.find()) {
         names.add(m.group(1));
      }
      return names;
   }

   @Test
   void everySoundEventHasOverlayFilesAndSubtitles() throws IOException {
      List<String> names = registered();
      assertEquals(12, names.size());
      JsonObject overlay = read(OVERLAY);
      assertEquals(names.size(), overlay.size(), "overlay and registry list the same events");
      for (String name : names) {
         JsonObject event = overlay.getAsJsonObject(name);
         assertTrue(event != null, name);
         String subtitle = event.get("subtitle").getAsString();
         assertEquals("subtitles.viltrumitecore." + name, subtitle);
         for (JsonElement s : event.getAsJsonArray("sounds")) {
            String file = s.getAsJsonObject().get("name").getAsString().replace("viltrumitecore:", "");
            Path b64 = BIN.resolve("sounds/" + file + ".ogg.b64");
            assertTrue(Files.isRegularFile(b64), b64.toString());
            byte[] ogg = Base64.getMimeDecoder().decode(Files.readString(b64).trim());
            assertEquals("OggS", new String(ogg, 0, 4, StandardCharsets.US_ASCII), b64.toString());
            assertTrue(ogg.length < 48 * 1024, b64 + " stays small");
            assertTrue(!Files.exists(ASSETS.resolve("sounds/" + file + ".ogg")), "no raw twin of " + file);
         }
         for (String locale : LOCALES) {
            JsonObject lang = read(LANG.resolve(locale + ".json"));
            assertTrue(lang.has(subtitle), locale + " " + subtitle);
         }
      }
   }

   @Test
   void everyLocaleNamesTheFireBlock() throws IOException {
      for (String locale : LOCALES) {
         assertTrue(read(LANG.resolve(locale + ".json")).has("block.viltrumitecore.meteor_fire"), locale);
      }
   }

   @Test
   void particlesAndTexturesExist() throws IOException {
      for (String p : List.of("meteor_smoke", "meteor_billow", "meteor_dust", "fire_smoke", "meteor_flame", "fire_flare", "meteor_ember", "meteor_ash")) {
         JsonObject json = read(ASSETS.resolve("particles/" + p + ".json"));
         assertTrue(json.getAsJsonArray("textures").size() > 0, p);
         for (JsonElement t : json.getAsJsonArray("textures")) {
            String tex = t.getAsString().replace("viltrumitecore:", "");
            assertTrue(Files.isRegularFile(BIN.resolve("textures/particle/" + tex + ".png.b64")), p + " -> " + tex);
         }
      }
      assertTrue(Files.isRegularFile(BIN.resolve("textures/entity/meteor_glow.png.b64")));
      assertTrue(Files.isRegularFile(BIN.resolve("textures/entity/meteor_tail.png.b64")));
   }

   @Test
   void fireBlockStateModelsExist() throws IOException {
      String state = Files.readString(ASSETS.resolve("blockstates/meteor_fire.json"));
      Matcher m = Pattern.compile("\"viltrumitecore:block/([a-z0-9_]+)\"").matcher(state);
      int n = 0;
      while (m.find()) {
         n++;
         JsonObject model = read(ASSETS.resolve("models/block/" + m.group(1) + ".json"));
         assertTrue(model.get("parent").getAsString().startsWith("minecraft:block/fire_"), m.group(1));
         assertEquals("minecraft:cutout", model.get("render_type").getAsString());
      }
      assertTrue(n >= 10);
   }

   @Test
   void meteorNoLongerUsesTheFlatSmoke() throws IOException {
      try (Stream<Path> s = Stream.of(JAVA.resolve("entity/MeteorEntity.java"), JAVA.resolve("client/render/MeteorRenderer.java"))) {
         s.forEach(p -> {
            try {
               String code = Files.readString(p);
               assertTrue(!code.contains("GIANT_METEOR_SMOKE"), p.toString());
               assertTrue(!code.contains("METEOR_GHOST_TRAIL"), p.toString());
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         });
      }
   }
}
