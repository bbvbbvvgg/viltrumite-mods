package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReinhardAttributionTest {
   @Test void processedResourcesCarryCcByNoticesForStandaloneJar() throws Exception {
      String credits = resource("META-INF/credits/reinhard-credits.md");
      String sources = resource("META-INF/credits/reinhard-sources.md");
      for (String id : new String[]{"398218", "248150", "331620", "275809"}) {
         assertTrue(credits.contains("/sounds/" + id + "/"), id + " source link");
         assertTrue(sources.contains(id), id + " provenance");
      }
      for (String author : new String[]{"klankbeeld", "hykenfreak", "dingus23"}) assertTrue(credits.contains(author), author);
      assertTrue(credits.contains("https://creativecommons.org/licenses/by/4.0/"));
      assertTrue(credits.contains("trimmed / layered / normalized"));
      assertTrue(credits.contains("Freesound.org"));
      assertTrue(resource("META-INF/mods.toml").contains("META-INF/credits/reinhard-credits.md"), "Audio license exception must be discoverable");
   }
   private String resource(String name) throws Exception {
      try (var stream = getClass().getClassLoader().getResourceAsStream(name)) {
         assertNotNull(stream, name + " must be packaged, not just present at repo root");
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}
