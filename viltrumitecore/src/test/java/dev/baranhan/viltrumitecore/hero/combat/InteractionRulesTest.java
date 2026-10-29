package dev.baranhan.viltrumitecore.hero.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tag/trait interaction rules: symmetric, priority → specificity → registration order, no pair checks. */
class InteractionRulesTest {
   private static final CombatPresence HAMMER = new CombatPresence("test:hammer",
      Set.of(CombatTags.WEAPON_HAMMER, CombatTags.ELEMENT_LIGHTNING, CombatTags.TRAIT_DIVINE), CombatStance.NONE, 0);
   private static final CombatPresence AXE = new CombatPresence("test:axe",
      Set.of(CombatTags.WEAPON_AXE, CombatTags.ELEMENT_FROST, CombatTags.TRAIT_DIVINE), CombatStance.NONE, 0);
   private static final CombatPresence FISTS = new CombatPresence("test:fists", Set.of(CombatTags.BODY_BARE), CombatStance.NONE, 0);

   private static String rule(InteractionRules rules, CombatPresence a, CombatPresence b) {
      return rules.resolve(a, b).map(resolution -> resolution.rule().id()).orElse("none");
   }

   @Test
   void activeWeaponsClashWhateverTheirKind() {
      InteractionRules rules = InteractionRules.defaults();
      CombatPresence hammer = HAMMER.withStance(CombatStance.ACTIVE);
      CombatPresence axe = AXE.withStance(CombatStance.ACTIVE);
      assertEquals("generic.clash", rule(rules, hammer, axe));
      assertEquals("generic.clash", rule(rules, axe, hammer), "symmetric");
      assertEquals(InteractionKind.CLASH, rules.resolve(hammer, axe).orElseThrow().kind());
   }

   @Test
   void guardBlocksStrikesAndReportsTheSides() {
      InteractionRules rules = InteractionRules.defaults();
      CombatPresence strike = HAMMER.withStance(CombatStance.ACTIVE);
      CombatPresence guard = AXE.withStance(CombatStance.GUARD);

      InteractionRules.Resolution straight = rules.resolve(strike, guard).orElseThrow();
      assertEquals(InteractionKind.BLOCK, straight.kind());
      assertFalse(straight.swapped());
      assertSame(strike, straight.first(strike, guard));
      assertSame(guard, straight.second(strike, guard));

      InteractionRules.Resolution reversed = rules.resolve(guard, strike).orElseThrow();
      assertSame(straight.rule(), reversed.rule());
      assertTrue(reversed.swapped());
      assertSame(strike, reversed.first(guard, strike), "the striking side stays the rule's first side");

      assertEquals("generic.block_bare", rule(rules, FISTS.withStance(CombatStance.ACTIVE), guard));
   }

   @Test
   void carriedWeaponsAndEmptyPresencesNeverInteract() {
      InteractionRules rules = InteractionRules.defaults();
      assertEquals("none", rule(rules, HAMMER, AXE));
      assertEquals("none", rule(rules, CombatPresence.NONE, AXE.withStance(CombatStance.ACTIVE)));
      assertEquals("none", rule(rules, FISTS.withStance(CombatStance.ACTIVE), FISTS.withStance(CombatStance.ACTIVE)), "no fist rule yet");
      assertEquals(Optional.empty(), rules.resolve(CombatPresence.NONE, CombatPresence.NONE));
   }

   @Test
   void deeperTagRuleWinsOverTheGenericFallback() {
      // A special matchup is one more tag rule: lightning meets frost, no hero or weapon id named.
      InteractionRules rules = InteractionRules.defaults().register(new InteractionRule("test.storm_meets_frost",
         InteractionRule.Side.of(CombatTags.ELEMENT_LIGHTNING, CombatStance.ACTIVE), InteractionRule.Side.of(CombatTags.ELEMENT_FROST, CombatStance.ACTIVE),
         InteractionKind.DEFLECT, 0));
      CombatPresence hammer = HAMMER.withStance(CombatStance.ACTIVE);
      CombatPresence axe = AXE.withStance(CombatStance.ACTIVE);
      assertEquals("test.storm_meets_frost", rule(rules, hammer, axe));
      assertEquals("test.storm_meets_frost", rule(rules, axe, hammer));

      CombatPresence sword = new CombatPresence("test:sword", Set.of(CombatTags.WEAPON_SWORD), CombatStance.ACTIVE, 0);
      assertEquals("generic.clash", rule(rules, hammer, sword), "other weapons keep the fallback");
   }

   @Test
   void priorityBeatsSpecificityAndOrderKeepsTies() {
      InteractionRule broad = new InteractionRule("test.divine_disarm",
         InteractionRule.Side.of(CombatTags.TRAIT), InteractionRule.Side.of(CombatTags.TRAIT), InteractionKind.DISARM, 1);
      InteractionRules rules = InteractionRules.defaults().register(broad);
      assertEquals("test.divine_disarm", rule(rules, HAMMER.withStance(CombatStance.ACTIVE), AXE.withStance(CombatStance.ACTIVE)));

      InteractionRules tie = new InteractionRules()
         .register(new InteractionRule("test.first", InteractionRule.Side.of(CombatTags.WEAPON), InteractionRule.Side.of(CombatTags.WEAPON), InteractionKind.CLASH, 0))
         .register(new InteractionRule("test.second", InteractionRule.Side.of(CombatTags.WEAPON), InteractionRule.Side.of(CombatTags.WEAPON), InteractionKind.PARRY, 0));
      assertEquals("test.first", rule(tie, HAMMER, AXE));
   }

   @Test
   void ruleIdsAreUniqueAndTheSetIsReadOnly() {
      InteractionRules rules = InteractionRules.defaults();
      assertThrows(IllegalArgumentException.class, () -> rules.register(new InteractionRule("generic.clash",
         InteractionRule.Side.of(CombatTags.BODY), InteractionRule.Side.of(CombatTags.BODY), InteractionKind.CLASH, 0)));
      assertThrows(UnsupportedOperationException.class, () -> rules.rules().clear());
      assertEquals(3, rules.rules().size());
      assertThrows(IllegalArgumentException.class, () -> InteractionRule.Side.of("Not A Tag"));
   }

   @Test
   void specificityCountsDepthAndStance() {
      assertEquals(2, InteractionRule.Side.of(CombatTags.WEAPON).specificity());
      assertEquals(3, InteractionRule.Side.of(CombatTags.WEAPON, CombatStance.GUARD).specificity());
      assertEquals(5, InteractionRule.Side.of(CombatTags.WEAPON_HAMMER, CombatStance.ACTIVE).specificity());
   }

   @Test
   void defaultRulesNameTagsOnly() {
      for (InteractionRule rule : InteractionRules.defaults().rules()) {
         for (InteractionRule.Side side : new InteractionRule.Side[]{rule.first(), rule.second()}) {
            assertTrue(side.query().equals(CombatTags.WEAPON) || side.query().equals(CombatTags.BODY), rule.id() + " must stay generic");
         }
      }
   }
}
