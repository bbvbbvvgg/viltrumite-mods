package dev.baranhan.viltrumitecore.client.codex;

import com.mojang.logging.LogUtils;
import dev.baranhan.viltrumitecore.codex.catalog.CatalogParser;
import dev.baranhan.viltrumitecore.codex.catalog.InteractionCatalog;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;

/**
 * Loads the contextual-interaction catalog for the encyclopedia. The real one is
 * {@code docs/contextual-interactions.json} of the repository, packed by the build as
 * {@code assets/viltrumitecore/codex/contextual_interactions.json}; until it is integrated the screen says
 * so instead of inventing entries. QA may point {@code -Dviltrumitecore.codex.catalog=<file>} at a test
 * file: that catalog is flagged test data and the screen labels it «TEST DATA».
 */
public final class CodexCatalog {
   public static final ResourceLocation RESOURCE = new ResourceLocation("viltrumitecore", "codex/contextual_interactions.json");
   public static final String TEST_PROPERTY = "viltrumitecore.codex.catalog";
   private static final Logger LOGGER = LogUtils.getLogger();
   private static InteractionCatalog cached;

   private CodexCatalog() {
   }

   /** The catalog, read once per game session (resource reloads clear it). */
   public static synchronized InteractionCatalog get() {
      if (cached == null) {
         cached = load();
         LOGGER.info("[codex] interaction catalog: {} ({} entries, source {}, test data {}, {} problems)", cached.status(),
            cached.interactions().size(), cached.source(), cached.testData(), cached.problems().size());
         for (String problem : cached.problems()) {
            LOGGER.warn("[codex] catalog: {}", problem);
         }
      }
      return cached;
   }

   public static synchronized void invalidate() {
      cached = null;
   }

   private static InteractionCatalog load() {
      String override = System.getProperty(TEST_PROPERTY);
      if (override != null && !override.isBlank()) {
         Path path = Path.of(override.strip());
         try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return CatalogParser.parse(reader, path.toString(), true);
         } catch (Exception e) {
            LOGGER.warn("[codex] test catalog {} unreadable: {}", path, e.toString());
         }
      }
      try {
         Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(RESOURCE);
         if (resource.isEmpty()) {
            return InteractionCatalog.missing();
         }
         try (Reader reader = resource.get().openAsReader()) {
            return CatalogParser.parse(reader, RESOURCE.toString(), false);
         }
      } catch (Exception e) {
         LOGGER.warn("[codex] catalog {} unreadable: {}", RESOURCE, e.toString());
         return new InteractionCatalog(InteractionCatalog.Status.INVALID, java.util.List.of(), java.util.List.of(e.toString()), RESOURCE.toString(), false);
      }
   }
}
