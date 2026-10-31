package dev.baranhan.viltrumitecore.hero.ironman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroId;
import org.junit.jupiter.api.Test;

class IronManHeroTest {
   private final IronManHero hero = new IronManHero();

   @Test
   void fromKeyIronman() {
      assertSame(HeroId.IRON_MAN, HeroId.fromKey("ironman"));
      assertEquals("ironman", HeroId.IRON_MAN.key());
      assertEquals(HeroId.IRON_MAN, this.hero.id());
   }

   @Test
   void ownsOnlyOwnIds() {
      assertTrue(this.hero.ownsAbility(IronManAbilities.SUIT));
      for (String id : new String[]{"viltrumite:punch", "viltrumite:dash", "homelander:lasers", "regulus:mania", "ironman:unknown", "", null}) {
         assertFalse(this.hero.ownsAbility(id), String.valueOf(id));
      }
   }

   @Test
   void noLegacyKit() {
      assertFalse(this.hero.allowsLegacyAbilities(null));
      assertFalse(this.hero.allowsLegacyAbility(null, "viltrumite:punch"));
      assertTrue(this.hero.hasAbilityPanel(null));
      assertTrue(this.hero.allowsAbilityPages(null));
   }

   @Test
   void loadoutPutsSuitOnPage2SlotB() {
      String[] loadout = this.hero.defaultLoadout();
      assertEquals(18, loadout.length);
      // Page 2 starts at slot 6; the fifth key (B) is index 4 of the page.
      assertEquals(IronManAbilities.SUIT, loadout[IronManAbilities.SUIT_SLOT]);
      // Page 1 (spec §6.2): Unibeam, missiles, nano arsenal.
      assertEquals(IronManAbilities.UNIBEAM, loadout[0]);
      assertEquals(IronManAbilities.MISSILES, loadout[1]);
      assertEquals(IronManAbilities.NANO_ARSENAL, loadout[2]);
      // Page 2 (spec §6.2): scan, countermeasures, Veronica, helmet, suit, Iron Legion (stage 6).
      // Page 1 also has the countermeasures; page 2: scan, Veronica, helmet, suit, (free), Legion.
      assertEquals(IronManAbilities.COUNTERMEASURES, loadout[3]);
      assertEquals(IronManAbilities.SCAN, loadout[6]);
      assertEquals(IronManAbilities.VERONICA, loadout[7]);
      assertEquals(IronManAbilities.HELMET, loadout[8]);
      assertEquals(IronManAbilities.SUIT, loadout[9]);
      assertEquals(IronManAbilities.LEGION, loadout[11]);
      assertEquals(11, IronManAbilities.LEGION_SLOT);
      for (int i = 4; i < 18; i++) {
         if ((i < 6 || i > 9) && i != 11) {
            assertEquals("", loadout[i], "slot " + i);
         }
      }
   }

   @Test
   void suitSlotSendsSuitAction() {
      assertEquals(9, this.hero.heroInputSlots().length);
      assertTrue(java.util.Arrays.asList(this.hero.heroInputSlots()).contains(IronManAbilities.SUIT));
      assertSame(HeroAction.SUIT, this.hero.heroActionFor(IronManAbilities.SUIT));
      assertSame(HeroAction.UNIBEAM, this.hero.heroActionFor(IronManAbilities.UNIBEAM));
      assertSame(HeroAction.MISSILES, this.hero.heroActionFor(IronManAbilities.MISSILES));
      assertSame(HeroAction.NANO_ARSENAL, this.hero.heroActionFor(IronManAbilities.NANO_ARSENAL));
      assertSame(HeroAction.SCAN, this.hero.heroActionFor(IronManAbilities.SCAN));
      assertSame(HeroAction.COUNTERMEASURES, this.hero.heroActionFor(IronManAbilities.COUNTERMEASURES));
      assertSame(HeroAction.HELMET, this.hero.heroActionFor(IronManAbilities.HELMET));
      assertSame(HeroAction.VERONICA, this.hero.heroActionFor(IronManAbilities.VERONICA));
      assertSame(HeroAction.LEGION, this.hero.heroActionFor(IronManAbilities.LEGION));
      assertEquals(null, this.hero.heroActionFor("viltrumite:punch"));
   }

   @Test
   void noFlightWithoutSuit() {
      assertFalse(this.hero.allowsFlight(null));
   }
}
