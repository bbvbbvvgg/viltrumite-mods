package dev.baranhan.viltrumitecore.balance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.balance.BalanceModel.HeroSheet;
import org.junit.jupiter.api.Test;

/**
 * The acceptance numbers of the global rebalance (docs/balance/2026-10-31-global-rebalance.md),
 * computed from the real constants through the real formulas. Static model: no AI, movement
 * or randomness (the document lists the assumptions).
 */
class BalanceModelTest {
   private static final HeroTier[] EXPECTED = {
      HeroTier.S_PLUS, HeroTier.S, HeroTier.A_PLUS, HeroTier.A_PLUS, HeroTier.A, HeroTier.A, HeroTier.A, HeroTier.A, HeroTier.A_ON_ULTIMATE, HeroTier.B_PLUS,
      HeroTier.B
   };

   @Test
   void tierListMatchesTheSpec() {
      for (int i = 0; i < EXPECTED.length; i++) {
         assertEquals(EXPECTED[i], BalanceModel.HEROES.get(i).profile().tier(), BalanceModel.HEROES.get(i).name());
      }

      assertTrue(HeroTier.S_PLUS.above(HeroTier.S));
      assertTrue(HeroTier.S.above(HeroTier.A_PLUS));
      assertTrue(HeroTier.A.above(HeroTier.A_ON_ULTIMATE));
      assertTrue(HeroTier.B_PLUS.above(HeroTier.B));
      assertFalse(HeroTier.B.above(HeroTier.B_PLUS));
   }

   @Test
   void chitauriRifleKillsAHumanInFourToSevenHits() {
      for (int difficulty = 1; difficulty <= 3; difficulty++) {
         float raw = BalanceRules.difficultyScaled(BalanceTable.CHITAURI_RIFLE, difficulty);
         int hits = BalanceModel.HUMAN.hitsToKill(raw, IncomingKind.PROJECTILE);
         assertTrue(hits >= 4 && hits <= 7, "difficulty " + difficulty + ": " + hits + " hits");
      }

      assertEquals(5, BalanceModel.HUMAN.hitsToKill(BalanceTable.CHITAURI_RIFLE, IncomingKind.PROJECTILE));
   }

   @Test
   void chitauriFireStaysAThreatToEveryMortalHero() {
      for (HeroSheet hero : BalanceModel.HEROES) {
         if (hero.hpFloor()) {
            continue;
         }

         // a rifle bolt must still outpace the sustain if two land per second (vanilla hit cooldown)
         float perSecond = hero.hpPerHit(BalanceTable.CHITAURI_RIFLE, IncomingKind.PROJECTILE) * 2.0F;
         assertTrue(perSecond > BalanceRules.sustainHealPerSecond(), hero.name() + " outheals Chitauri fire");
         assertTrue(hero.hitsToKill(BalanceTable.CHITAURI_RIFLE, IncomingKind.PROJECTILE) < 100, hero.name());
      }
   }

   @Test
   void anOrdinaryViltrumitePunchNeverOneShotsAnIntactSuit() {
      HeroSheet suit = BalanceModel.IRON_MAN_SUIT;
      for (BalanceTable.ViltrumiteNpc npc : new BalanceTable.ViltrumiteNpc[]{BalanceTable.THULA, BalanceTable.LUCAN, BalanceTable.VIDOR, BalanceTable.CONQUEST}) {
         float punch = BalanceModel.viltrumitePunch(npc.baseDamage(), 0.0F);
         float chop = BalanceModel.viltrumiteChop(npc.baseDamage());
         float charged = BalanceModel.viltrumitePunch(npc.baseDamage(), 1.0F);
         assertTrue(suit.hitsToKill(punch, kind(punch)) >= 3, "several ordinary punches, base " + npc.baseDamage());
         assertTrue(suit.hitsToKill(chop, kind(chop)) >= 3, "several chops, base " + npc.baseDamage());
         assertTrue(suit.hitsToKill(charged, kind(charged)) >= 2, "a charged punch is worse but still not a one-shot");
      }

      // without the suit Tony is a human: a Viltrumite kills him at once
      assertEquals(1, BalanceModel.TONY.hitsToKill(BalanceModel.viltrumitePunch(BalanceTable.THULA.baseDamage(), 0.0F), IncomingKind.MELEE));
   }

   private static IncomingKind kind(float raw) {
      return raw >= BalanceRules.HEAVY_RAW ? IncomingKind.HEAVY : IncomingKind.MELEE;
   }

   @Test
   void viltrumitesStayHugeButTheirSkinIsNotAWall() {
      for (BalanceTable.ViltrumiteNpc npc : new BalanceTable.ViltrumiteNpc[]{BalanceTable.THULA, BalanceTable.LUCAN, BalanceTable.VIDOR, BalanceTable.CONQUEST}) {
         float ignoredBelow = BalanceRules.viltrumiteIgnoredBelow(npc.reductionPercent(), npc.ignoreThreshold());
         assertTrue(ignoredBelow <= 3.5F, "ignores raw hits under " + ignoredBelow);
         float ehp = BalanceRules.viltrumiteEffectiveHealth(20.0F, npc.reductionPercent());
         assertTrue(ehp >= 400.0F, "still a heavy hitter's EHP: " + ehp);
      }

      assertTrue(BalanceRules.viltrumiteEffectiveHealth(20.0F, BalanceTable.CONQUEST.reductionPercent()) > BalanceRules.WARDEN_HEALTH,
         "Conquest outlasts a Warden");
      // the old Conquest ignored every raw hit under 40
      assertTrue(BalanceRules.viltrumiteIgnoredBelow(98.5F, 0.6F) >= 39.9F);
   }

   @Test
   void everyHeroOutlastsAGolemSwingChain() {
      for (HeroSheet hero : BalanceModel.HEROES) {
         // a golem (100 HP) dies in under 30 s to any kit; every hero lasts several golem swings
         assertTrue(hero.hitsToKill(BalanceRules.GOLEM_ATTACK_MEAN, IncomingKind.MELEE) >= 4, hero.name());
      }

      assertTrue(BalanceModel.HOMELANDER.secondsVersusGolem() > BalanceRules.GOLEM_HEALTH / (BalanceTable.HOMELANDER_LASER * 5.0F),
         "Homelander's laser alone (10 DPS) kills a golem before the golem kills him");
   }

   @Test
   void wardenRanksTheTiers() {
      float thor = BalanceModel.THOR.secondsVersusWardenMelee();
      float suit = BalanceModel.IRON_MAN_SUIT.secondsVersusWardenMelee();
      float homelander = BalanceModel.HOMELANDER.secondsVersusWardenMelee();
      float sinclair = BalanceModel.SINCLAIR.secondsVersusWardenMelee();
      // A holds the Warden's melee chain for 8+ seconds of trading, B+ about half that, B barely
      assertTrue(thor >= 8.0F, "Thor " + thor);
      assertTrue(suit >= 8.0F, "suit " + suit);
      assertTrue(homelander < thor && homelander >= 3.0F, "Homelander " + homelander);
      assertTrue(sinclair < homelander, "Sinclair " + sinclair);
      assertTrue(Float.isInfinite(BalanceModel.REINHARD.secondsVersusWardenMelee()));
      // A+ bodies are not tougher than Thor: their edge is time stop and the army
      assertTrue(BalanceModel.DIO.secondsVersusWardenMelee() <= thor);
   }

   @Test
   void bossesHitHarderThanTheOrdinaryCapButNeverDeleteAHero() {
      for (HeroSheet hero : BalanceModel.HEROES) {
         if (hero.hpFloor()) {
            continue;
         }

         float boss = hero.hpPerHit(1000.0F, IncomingKind.BOSS);
         assertTrue(boss < hero.maxHealth(), hero.name() + " one-shot by a boss hit");
         assertTrue(boss >= hero.hpPerHit(1000.0F, IncomingKind.MELEE), hero.name());
      }
   }

   @Test
   void hulkbusterIsAHeavyMachineButNotAnSTierWall() {
      float ehp = BalanceModel.hulkbusterEffectiveHealth(800.0F, BalanceTable.HULKBUSTER_PLATING);
      assertEquals(1333.3F, ehp, 0.5F);
      assertTrue(ehp > BalanceRules.WARDEN_HEALTH * 2.0F, "outweighs two Wardens of raw damage");
      assertTrue(ehp < BalanceModel.hulkbusterEffectiveHealth(800.0F, 0.36F), "lighter than the old 2222");
   }

   @Test
   void exemptAndPvpHitsKeepTheirNumbers() {
      HeroSheet thor = BalanceModel.THOR;
      assertEquals(BalanceRules.afterArmor(30.0F, 8.0F, 4.0F), thor.hpPerHit(30.0F, IncomingKind.PVP), 1.0E-4F);
      assertEquals(30.0F, thor.hpPerHit(30.0F, IncomingKind.EXEMPT), 1.0E-4F);
      assertEquals(1.0F, BalanceTable.THUNDER_GOD.capFor(IncomingKind.PVP));
      assertEquals(0.0F, BalanceTable.THUNDER_GOD.resilienceFor(IncomingKind.EXEMPT));
   }

   @Test
   void profilesAreSane() {
      for (HeroSheet hero : BalanceModel.HEROES) {
         HeroBalanceProfile p = hero.profile();
         assertTrue(p.resilience() < 1.0F, hero.name() + " has no hidden invulnerability");
         assertTrue(p.heavyHitCap() >= p.hitCap() && p.bossHitCap() >= p.hitCap(), hero.name());
      }

      assertEquals(HeroBalanceProfile.VANILLA, BalanceTable.UNARMORED);
   }
}
