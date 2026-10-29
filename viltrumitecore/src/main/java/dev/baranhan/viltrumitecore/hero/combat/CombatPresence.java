package dev.baranhan.viltrumitecore.hero.combat;

import java.util.Objects;
import java.util.Set;

/**
 * What one side brings into a combat interaction: a weapon id (for logs and
 * future per-weapon effects, never for rule matching), its tags, the current
 * stance and a priority for clash outcomes. Rules see only tags and stances.
 */
public record CombatPresence(String weaponId, Set<String> tags, CombatStance stance, int priority) {
   /** No weapon, no tags: no rule ever matches. */
   public static final CombatPresence NONE = new CombatPresence("none", Set.of(), CombatStance.NONE, 0);

   public CombatPresence {
      Objects.requireNonNull(weaponId, "weaponId");
      Objects.requireNonNull(stance, "stance");
      tags = Set.copyOf(tags);
      for (String tag : tags) {
         CombatTags.require(tag);
      }
   }

   public boolean has(String query) {
      return CombatTags.has(this.tags, query);
   }

   public CombatPresence withStance(CombatStance next) {
      return next == this.stance ? this : new CombatPresence(this.weaponId, this.tags, next, this.priority);
   }
}
