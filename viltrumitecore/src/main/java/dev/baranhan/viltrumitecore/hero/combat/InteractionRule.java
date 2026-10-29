package dev.baranhan.viltrumitecore.hero.combat;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * One tag/trait rule: when a presence matching {@link #first()} meets one
 * matching {@link #second()} (in either order), the interaction is
 * {@link #kind()}. Rules name tags and stances only, never a hero or a
 * weapon id, so no rule is ever written for a concrete pair.
 */
public record InteractionRule(String id, Side first, Side second, InteractionKind kind, int priority) {
   public InteractionRule {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(first, "first");
      Objects.requireNonNull(second, "second");
      Objects.requireNonNull(kind, "kind");
   }

   /** Sum of both sides' specificity: deeper tags and fixed stances win ties of priority. */
   public int specificity() {
      return this.first.specificity() + this.second.specificity();
   }

   /** One side of a rule: a tag query and an optional stance (null = any stance). */
   public record Side(String query, @Nullable CombatStance stance) {
      public Side {
         CombatTags.require(query);
      }

      public static Side of(String query) {
         return new Side(query, null);
      }

      public static Side of(String query, CombatStance stance) {
         return new Side(query, Objects.requireNonNull(stance, "stance"));
      }

      public boolean matches(CombatPresence presence) {
         return presence.has(this.query) && (this.stance == null || presence.stance() == this.stance);
      }

      public int specificity() {
         return CombatTags.depth(this.query) * 2 + (this.stance == null ? 0 : 1);
      }
   }
}
