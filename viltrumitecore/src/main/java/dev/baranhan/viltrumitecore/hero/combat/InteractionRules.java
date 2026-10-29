package dev.baranhan.viltrumitecore.hero.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Ordered rule set resolving what happens when two combat presences meet.
 * Symmetric: (a, b) and (b, a) resolve to the same rule. The best match wins
 * by priority, then specificity, then registration order. {@link #defaults()}
 * holds only generic tag fallbacks (weapon clash, block); special matchups are
 * added later as more specific tag rules, never as hero-pair checks.
 *
 * The mod's one instance lives in {@link HeroInteractions}; heroes publish
 * their presence through {@code HeroDefinition.combatPresence}. What a
 * resolved CLASH / BLOCK does to a hit is per-matchup design (OPEN).
 */
public final class InteractionRules {
   private final List<InteractionRule> rules = new ArrayList<>();

   /** Resolved rule; {@code swapped} = the rule's first side matched the second argument. */
   public record Resolution(InteractionRule rule, boolean swapped) {
      public InteractionKind kind() {
         return this.rule.kind();
      }

      /** The presence that matched the rule's first side. */
      public CombatPresence first(CombatPresence a, CombatPresence b) {
         return this.swapped ? b : a;
      }

      /** The presence that matched the rule's second side. */
      public CombatPresence second(CombatPresence a, CombatPresence b) {
         return this.swapped ? a : b;
      }
   }

   /** Generic fallbacks only: active weapons clash, active weapons and fists are blocked by a guarding weapon. */
   public static InteractionRules defaults() {
      return new InteractionRules()
         .register(new InteractionRule("generic.clash",
            InteractionRule.Side.of(CombatTags.WEAPON, CombatStance.ACTIVE), InteractionRule.Side.of(CombatTags.WEAPON, CombatStance.ACTIVE),
            InteractionKind.CLASH, 0))
         .register(new InteractionRule("generic.block",
            InteractionRule.Side.of(CombatTags.WEAPON, CombatStance.ACTIVE), InteractionRule.Side.of(CombatTags.WEAPON, CombatStance.GUARD),
            InteractionKind.BLOCK, 0))
         .register(new InteractionRule("generic.block_bare",
            InteractionRule.Side.of(CombatTags.BODY, CombatStance.ACTIVE), InteractionRule.Side.of(CombatTags.WEAPON, CombatStance.GUARD),
            InteractionKind.BLOCK, 0));
   }

   /** Adds a rule; ids are unique. */
   public InteractionRules register(InteractionRule rule) {
      for (InteractionRule existing : this.rules) {
         if (existing.id().equals(rule.id())) {
            throw new IllegalArgumentException("duplicate interaction rule " + rule.id());
         }
      }

      this.rules.add(rule);
      return this;
   }

   public List<InteractionRule> rules() {
      return Collections.unmodifiableList(this.rules);
   }

   /** Best rule for the pair in either order, or empty when nothing applies. */
   public Optional<Resolution> resolve(CombatPresence a, CombatPresence b) {
      Resolution best = null;
      for (InteractionRule rule : this.rules) {
         Resolution candidate = null;
         if (rule.first().matches(a) && rule.second().matches(b)) {
            candidate = new Resolution(rule, false);
         } else if (rule.first().matches(b) && rule.second().matches(a)) {
            candidate = new Resolution(rule, true);
         }

         if (candidate != null && (best == null || better(rule, best.rule()))) {
            best = candidate;
         }
      }

      return Optional.ofNullable(best);
   }

   /** Earlier registration keeps the tie: strictly better only. */
   private static boolean better(InteractionRule rule, InteractionRule best) {
      if (rule.priority() != best.priority()) {
         return rule.priority() > best.priority();
      }

      return rule.specificity() > best.specificity();
   }
}
