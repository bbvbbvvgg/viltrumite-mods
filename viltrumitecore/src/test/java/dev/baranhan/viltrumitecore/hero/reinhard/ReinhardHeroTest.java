package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import org.junit.jupiter.api.Test;

/** Reinhard skeleton: id, registration, chassis flags (spec §1.2.5, §1.2.7). */
class ReinhardHeroTest {

   @Test
   void reinhardIsLastOrdinal() {
      HeroId[] values = HeroId.values();
      assertSame(HeroId.REINHARD, values[values.length - 1]);
   }

   @Test
   void fromKeyReinhard() {
      assertSame(HeroId.REINHARD, HeroId.fromKey("reinhard"));
   }

   @Test
   void legacySetKeepsReinhard() {
      assertSame(HeroId.REINHARD, HeroId.legacySet(HeroId.REINHARD, true));
      assertSame(HeroId.REINHARD, HeroId.legacySet(HeroId.REINHARD, false));
   }

   @Test
   void noKitNoFlight() {
      ReinhardHero hero = ReinhardHero.INSTANCE;
      assertFalse(hero.allowsLegacyAbilities(null));
      assertFalse(hero.allowsFlight(null));
      assertFalse(hero.grantsFlightAbility(null));
   }

   @Test
   void registered() {
      HeroRegistry.registerDefaults();
      assertSame(ReinhardHero.INSTANCE, HeroRegistry.get(HeroId.REINHARD));
   }
}
