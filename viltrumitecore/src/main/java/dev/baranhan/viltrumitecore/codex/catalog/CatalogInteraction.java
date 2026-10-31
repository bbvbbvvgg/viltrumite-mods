package dev.baranhan.viltrumitecore.codex.catalog;

import java.util.List;

/**
 * One contextual interaction of docs/contextual-interactions.json as the encyclopedia shows it. Every text
 * is optional except the id; {@code secret} entries stay closed (only the hint) until the player discovers
 * them (CodexProgress).
 */
public record CatalogInteraction(
   String id,
   LocalText name,
   List<Participant> participants,
   LocalText description,
   LocalText lore,
   List<LocalText> conditions,
   LocalText mechanic,
   LocalText visuals,
   LocalText hint,
   boolean secret,
   String category,
   List<String> related
) {
   public record Participant(String id, LocalText name, String kind) {
   }

   /** Every participant id plus every related id (events, characters): what the entry is linked to. */
   public boolean involves(java.util.Set<String> aliases) {
      for (Participant p : this.participants) {
         if (aliases.contains(CatalogParser.slug(p.id()))) {
            return true;
         }
      }
      for (String r : this.related) {
         if (aliases.contains(CatalogParser.slug(r))) {
            return true;
         }
      }
      return false;
   }
}
