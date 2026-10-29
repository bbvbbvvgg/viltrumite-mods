package dev.baranhan.viltrumitecore.hero.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Hierarchical tags and the presence record the interaction rules read. */
class CombatTagsTest {
   @Test
   void queryMatchesItselfAndEverythingBelow() {
      assertTrue(CombatTags.matches(CombatTags.WEAPON_HAMMER, CombatTags.WEAPON));
      assertTrue(CombatTags.matches(CombatTags.WEAPON_HAMMER, CombatTags.WEAPON_HAMMER));
      assertFalse(CombatTags.matches(CombatTags.WEAPON, CombatTags.WEAPON_HAMMER), "a parent tag is not a child");
      assertFalse(CombatTags.matches("weaponry", CombatTags.WEAPON), "prefix without a dot is another tag");
      assertFalse(CombatTags.matches(CombatTags.WEAPON_AXE, CombatTags.WEAPON_HAMMER));
      assertFalse(CombatTags.matches(null, CombatTags.WEAPON));
      assertFalse(CombatTags.matches(CombatTags.WEAPON, null));
   }

   @Test
   void tagsAreDottedLowercase() {
      assertTrue(CombatTags.valid("weapon.hammer"));
      assertTrue(CombatTags.valid("trait.divine_2"));
      for (String bad : new String[]{null, "", "Weapon", "weapon.", ".weapon", "weapon..hammer", "weapon hammer", "weapon:hammer"}) {
         assertFalse(CombatTags.valid(bad), String.valueOf(bad));
      }

      assertThrows(IllegalArgumentException.class, () -> CombatTags.require("Weapon.Hammer"));
      assertEquals("element.frost", CombatTags.require("element.frost"));
   }

   @Test
   void deeperQueriesAreMoreSpecific() {
      assertEquals(0, CombatTags.depth(null));
      assertEquals(0, CombatTags.depth(""));
      assertEquals(1, CombatTags.depth(CombatTags.WEAPON));
      assertEquals(2, CombatTags.depth(CombatTags.WEAPON_HAMMER));
      assertEquals(3, CombatTags.depth("weapon.hammer.mjolnir"));
   }

   @Test
   void anyTagMatches() {
      List<String> tags = List.of(CombatTags.ELEMENT_LIGHTNING, CombatTags.WEAPON_HAMMER);
      assertTrue(CombatTags.has(tags, CombatTags.ELEMENT));
      assertTrue(CombatTags.has(tags, CombatTags.WEAPON));
      assertFalse(CombatTags.has(tags, CombatTags.BODY));
      assertFalse(CombatTags.has(List.of(), CombatTags.WEAPON));
   }

   @Test
   void presenceValidatesAndFreezesItsTags() {
      assertThrows(IllegalArgumentException.class, () -> new CombatPresence("x", Set.of("Bad Tag"), CombatStance.NONE, 0));
      assertThrows(NullPointerException.class, () -> new CombatPresence(null, Set.of(), CombatStance.NONE, 0));

      Set<String> source = new HashSet<>(Set.of(CombatTags.WEAPON_SWORD));
      CombatPresence sword = new CombatPresence("test:sword", source, CombatStance.NONE, 1);
      source.add(CombatTags.ELEMENT_FROST);
      assertFalse(sword.has(CombatTags.ELEMENT), "the presence copies its tags");
      assertThrows(UnsupportedOperationException.class, () -> sword.tags().add(CombatTags.BODY));

      assertSame(sword, sword.withStance(CombatStance.NONE));
      CombatPresence guarding = sword.withStance(CombatStance.GUARD);
      assertEquals(CombatStance.GUARD, guarding.stance());
      assertEquals(sword.tags(), guarding.tags());
      assertEquals(1, guarding.priority());
      assertFalse(CombatPresence.NONE.has(CombatTags.WEAPON));
   }
}
