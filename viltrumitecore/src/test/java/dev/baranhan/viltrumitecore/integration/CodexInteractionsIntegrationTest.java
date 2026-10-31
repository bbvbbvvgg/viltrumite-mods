package dev.baranhan.viltrumitecore.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.codex.CodexRecord;
import dev.baranhan.viltrumitecore.codex.catalog.CatalogInteraction;
import dev.baranhan.viltrumitecore.codex.catalog.CatalogParser;
import dev.baranhan.viltrumitecore.codex.catalog.InteractionCatalog;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Final integration #107 + #108: the encyclopedia reads the one interaction catalog of #107
 * (docs/contextual-interactions.json, shipped as assets/viltrumitecore/codex/contextual_interactions.json),
 * shows all 21 entries with their texts, and a real firing opens the entry for its participants.
 */
class CodexInteractionsIntegrationTest {
   private static final Path CATALOG = Path.of("../docs/contextual-interactions.json");
   private static final Path SRC = Path.of("src/main/java/dev/baranhan/viltrumitecore");

   private static List<String> rawIds() throws IOException {
      JsonObject root = JsonParser.parseString(Files.readString(CATALOG, StandardCharsets.UTF_8)).getAsJsonObject();
      JsonArray arr = root.getAsJsonArray("interactions");
      List<String> ids = new ArrayList<>();
      for (JsonElement e : arr) {
         ids.add(e.getAsJsonObject().get("id").getAsString());
      }
      return ids;
   }

   private static InteractionCatalog parsed() throws IOException {
      try (Reader r = Files.newBufferedReader(CATALOG, StandardCharsets.UTF_8)) {
         return CatalogParser.parse(r, CATALOG.toString(), false);
      }
   }

   @Test
   void encyclopediaShowsAllTwentyOneInteractionsOfTheOneCatalog() throws IOException {
      List<String> raw = rawIds();
      assertEquals(21, raw.size(), "#107 ships 21 contextual interactions");
      InteractionCatalog c = parsed();
      assertEquals(InteractionCatalog.Status.LOADED, c.status(), c.problems().toString());
      assertTrue(c.problems().isEmpty(), "no catalog entry may be skipped: " + c.problems());
      List<String> shown = c.interactions().stream().map(CatalogInteraction::id).toList();
      assertEquals(raw, shown, "the encyclopedia lists exactly the catalog, in order");
      Function<String, String> keys = k -> k;
      for (CatalogInteraction i : c.interactions()) {
         assertFalse(i.name().resolve("ru_ru", keys).isBlank(), i.id() + ": Russian title");
         assertFalse(i.name().resolve("en_us", keys).isBlank(), i.id() + ": English title");
         assertFalse(i.participants().isEmpty(), i.id() + ": participants");
         assertFalse(i.description().resolve("ru_ru", keys).isBlank(), i.id() + ": description");
         assertFalse(i.mechanic().isEmpty(), i.id() + ": gameplay result");
         assertFalse(i.secret(), i.id() + ": the catalog marks nothing secret, so the encyclopedia invents no secrecy");
         assertTrue(CodexRecord.validId(i.id()), i.id() + ": discoverable id (CodexRecord)");
      }
   }

   @Test
   void noSecondCatalogIsShipped() throws IOException {
      try (var files = Files.walk(Path.of("src/main/resources"))) {
         List<Path> dupes = files.filter(p -> p.getFileName().toString().matches("contextual[-_]interactions\\.json")).toList();
         assertTrue(dupes.isEmpty(), "the catalog is packed from docs/ by processResources only: " + dupes);
      }
      String gradle = Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8);
      assertTrue(gradle.contains("include 'contextual-interactions.json'") && gradle.contains("into 'assets/viltrumitecore/codex'"),
         "build.gradle packs docs/contextual-interactions.json into the jar");
   }

   @Test
   void aRealFiringOpensTheEntryForItsParticipants() throws IOException {
      String dispatch = Files.readString(SRC.resolve("interaction/ContextualInteractions.java"), StandardCharsets.UTF_8);
      int guard = dispatch.indexOf("GUARD.fired(spec, a, b, ctx.now());");
      int discovery = dispatch.indexOf("InteractionDiscovery.fired(spec, ctx);");
      assertTrue(guard > 0 && discovery > guard, "discovery follows the real firing in ContextualInteractions.run");
      String disc = Files.readString(SRC.resolve("interaction/InteractionDiscovery.java"), StandardCharsets.UTF_8);
      assertTrue(disc.contains("CodexProgress.discoverInteraction(p, spec.id())"), "discovery goes through the codex progress API");
      assertTrue(disc.contains("SummonOwners.ownerOf"), "a summon's owner discovers too");
   }
}
