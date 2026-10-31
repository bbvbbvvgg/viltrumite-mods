package dev.baranhan.viltrumitecore.codex.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** The tolerant reader of docs/contextual-interactions.json (bad entries skipped, never fatal). */
class CatalogParserTest {
   private static final Function<String, String> NO_LANG = key -> null;

   private static InteractionCatalog fixture() throws IOException {
      try (Reader r = new InputStreamReader(CatalogParserTest.class.getResourceAsStream("/codex/test_catalog.json"), StandardCharsets.UTF_8)) {
         return CatalogParser.parse(r, "test_catalog.json", true);
      }
   }

   @Test
   void readsTheTestFixture() throws IOException {
      InteractionCatalog c = fixture();
      assertEquals(InteractionCatalog.Status.LOADED, c.status());
      assertTrue(c.testData());
      assertEquals(3, c.interactions().size(), "no-id and duplicate entries are skipped");
      assertEquals(2, c.problems().size(), c.problems().toString());
      CatalogInteraction open = c.byId("test_open_pair");
      assertNotNull(open);
      assertEquals("ТЕСТ: открытое взаимодействие", open.name().resolve("ru_ru", NO_LANG));
      assertEquals("TEST: open interaction", open.name().resolve("en_us", NO_LANG));
      assertEquals("TEST: open interaction", open.name().resolve("de_de", NO_LANG), "other locales fall back to English");
      assertEquals(2, open.participants().size());
      assertEquals(2, open.conditions().size());
      assertFalse(open.secret());
      assertTrue(open.involves(Set.of("conquest")), "namespaced participant ids match");
      assertTrue(open.involves(Set.of("conquest_arrival")), "related events match");
      CatalogInteraction secret = c.byId("test_secret_pair");
      assertTrue(secret.secret());
      assertTrue(secret.hint().resolve("en_us", NO_LANG).startsWith("Test hint"));
      CatalogInteraction synonyms = c.byId("test_unknown_participant");
      assertEquals("TEST: unknown participant", synonyms.name().resolve("ru_ru", NO_LANG));
      assertEquals(2, synonyms.participants().size(), "\"actors\" is read as participants");
   }

   @Test
   void plainArrayAndIdMapForms() {
      InteractionCatalog a = CatalogParser.parse("[{\"id\":\"x\",\"name\":\"X\"}]", "a", false);
      assertEquals(1, a.interactions().size());
      InteractionCatalog m = CatalogParser.parse("{\"interactions\":{\"y\":{\"name\":\"Y\"}}}", "m", false);
      assertEquals("y", m.interactions().get(0).id());
   }

   @Test
   void langKeysResolveThroughTheGame() {
      InteractionCatalog c = CatalogParser.parse("[{\"id\":\"k\",\"name\":\"codex.test.key\"}]", "k", false);
      Map<String, String> lang = Map.of("codex.test.key", "Перевод");
      assertEquals("Перевод", c.interactions().get(0).name().resolve("ru_ru", lang::get));
   }

   @Test
   void brokenFilesAreReportedNotThrown() {
      assertEquals(InteractionCatalog.Status.INVALID, CatalogParser.parse("{not json", "b", false).status());
      assertEquals(InteractionCatalog.Status.INVALID, CatalogParser.parse("42", "n", false).status());
      InteractionCatalog empty = CatalogParser.parse("[]", "e", false);
      assertEquals(InteractionCatalog.Status.LOADED, empty.status());
      assertTrue(empty.isEmpty());
   }

   @Test
   void slugs() {
      assertEquals("thor", CatalogParser.slug("viltrumitecore:thor"));
      assertEquals("iron_man", CatalogParser.slug(" Iron-Man "));
      assertEquals("black_widow", CatalogParser.slug("Black Widow"));
   }

   /** The real catalog, when the contextual-interactions work has landed in this checkout. */
   @Test
   void realCatalogParsesWhenPresent() throws IOException {
      Path real = Path.of("../docs/contextual-interactions.json");
      if (!Files.isRegularFile(real)) {
         return;
      }
      try (Reader r = Files.newBufferedReader(real, StandardCharsets.UTF_8)) {
         InteractionCatalog c = CatalogParser.parse(r, real.toString(), false);
         assertEquals(InteractionCatalog.Status.LOADED, c.status(), c.problems().toString());
         assertFalse(c.isEmpty());
      }
   }
}
