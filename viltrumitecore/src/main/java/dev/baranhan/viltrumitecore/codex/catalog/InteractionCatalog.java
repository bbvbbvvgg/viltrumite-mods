package dev.baranhan.viltrumitecore.codex.catalog;

import java.util.List;
import javax.annotation.Nullable;

/**
 * The parsed catalog: its interactions in file order, where it came from, and the problems met while
 * reading it (bad entries are skipped, never fatal). {@code testData} marks a QA file loaded through the
 * {@code viltrumitecore.codex.catalog} system property: the screen labels it so it is never taken for
 * real game content.
 */
public record InteractionCatalog(Status status, List<CatalogInteraction> interactions, List<String> problems, @Nullable String source, boolean testData) {
   public enum Status {
      /** The resource is not in the jar (the catalog has not been integrated yet). */
      MISSING,
      /** The file exists but could not be read as JSON. */
      INVALID,
      LOADED
   }

   public static InteractionCatalog missing() {
      return new InteractionCatalog(Status.MISSING, List.of(), List.of(), null, false);
   }

   public boolean isEmpty() {
      return this.interactions.isEmpty();
   }

   @Nullable
   public CatalogInteraction byId(String id) {
      for (CatalogInteraction i : this.interactions) {
         if (i.id().equals(id)) {
            return i;
         }
      }
      return null;
   }
}
