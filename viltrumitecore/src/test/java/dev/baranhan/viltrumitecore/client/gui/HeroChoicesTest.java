package dev.baranhan.viltrumitecore.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroId;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Issue #134: the hero choice screen lists every hero Od Lagna grants (translations: HeroRosterIntegrationTest). */
class HeroChoicesTest {
   @Test
   void everyPlayableHeroIsOfferedOnce() {
      List<HeroId> heroes = HeroChoices.heroes();
      assertEquals(new HashSet<>(heroes).size(), heroes.size(), "a hero is listed twice: " + heroes);
      assertFalse(heroes.contains(HeroId.HUMAN), "the human is the screen's own «remain human» button");
      assertFalse(heroes.contains(HeroId.VILTRUMITE), "a save alias, not a choice");
      for (HeroId hero : HeroId.values()) {
         if (hero != HeroId.HUMAN && hero != HeroId.VILTRUMITE) {
            assertTrue(heroes.contains(hero), "the choice screen misses " + hero.key());
         }
      }
      assertTrue(heroes.contains(HeroId.RED_HULK));
   }
}
