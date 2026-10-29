package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/** Homelander replaces the Viltrumite race: saved Viltrumites load as Homelander. */
class HeroIdMigrationTest {

   @Test
   void fromKeyAliasesViltrumite() {
      assertSame(HeroId.HOMELANDER, HeroId.fromKey("viltrumite"));
      assertSame(HeroId.HOMELANDER, HeroId.fromKey("homelander"));
   }

   @Test
   void legacyTrueIsHomelander() {
      assertSame(HeroId.HOMELANDER, HeroId.fromLegacyBoolean(true));
      assertSame(HeroId.HUMAN, HeroId.fromLegacyBoolean(false));
   }

   @Test
   void legacySetKeepsRegulus() {
      assertSame(HeroId.REGULUS, HeroId.legacySet(HeroId.REGULUS, true));
      assertSame(HeroId.REGULUS, HeroId.legacySet(HeroId.REGULUS, false));
   }

   @Test
   void legacySetToggleHumanAndHomelander() {
      assertSame(HeroId.HOMELANDER, HeroId.legacySet(HeroId.HUMAN, true));
      assertSame(HeroId.HUMAN, HeroId.legacySet(HeroId.HOMELANDER, false));
      assertSame(HeroId.HOMELANDER, HeroId.legacySet(HeroId.HOMELANDER, true));
      assertSame(HeroId.HOMELANDER, HeroId.legacySet(HeroId.VILTRUMITE, true));
      assertSame(HeroId.HUMAN, HeroId.legacySet(HeroId.VILTRUMITE, false));
   }

   @Test
   void ironManKeepsItsOrdinal() {
      HeroId[] values = HeroId.values();
      assertSame(HeroId.IRON_MAN, values[4]);
      assertSame(HeroId.HOMELANDER, values[3]);
      assertSame(HeroId.REGULUS, values[2]);
   }

   @Test
   void legacySetKeepsIronMan() {
      assertSame(HeroId.IRON_MAN, HeroId.legacySet(HeroId.IRON_MAN, true));
      assertSame(HeroId.IRON_MAN, HeroId.legacySet(HeroId.IRON_MAN, false));
   }

   @Test
   void reinhardAppendedAfterIronMan() {
      assertSame(HeroId.REINHARD, HeroId.values()[5]);
      assertSame(HeroId.REINHARD, HeroId.fromKey("reinhard"));
   }

   @Test
   void sinclairAppendedAfterReinhard() {
      assertSame(HeroId.SINCLAIR, HeroId.values()[6]);
      assertSame(HeroId.SINCLAIR, HeroId.fromKey("sinclair"));
      assertSame(HeroId.SINCLAIR, HeroId.legacySet(HeroId.SINCLAIR, true));
   }
}
