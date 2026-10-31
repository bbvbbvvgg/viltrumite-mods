package dev.baranhan.viltrumitecore.balance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Vanilla formulas and the pure steps of the global balance model. */
class BalanceRulesTest {
   @Test
   void armorStepMatchesVanilla() {
      // CombatRules.getDamageAfterAbsorb(20, 20, 0): clamp(20 - 20/2, 4, 20) = 10 -> 20 * (1 - 10/25) = 12
      assertEquals(12.0F, BalanceRules.afterArmor(20.0F, 20.0F, 0.0F), 1.0E-4F);
      // armor 28 / toughness 8 against 30: clamp(28 - 30/4, 5.6, 20) = 20 -> 80 % off
      assertEquals(6.0F, BalanceRules.afterArmor(30.0F, 28.0F, 8.0F), 1.0E-4F);
      // huge hits fall to the 20 % floor of the armor value
      assertEquals(160.0F * (1.0F - 5.6F / 25.0F), BalanceRules.afterArmor(160.0F, 28.0F, 8.0F), 1.0E-3F);
      assertEquals(7.0F, BalanceRules.afterArmor(7.0F, 0.0F, 0.0F), 1.0E-6F);
   }

   @Test
   void resistanceAndDifficultyMatchVanilla() {
      assertEquals(8.0F, BalanceRules.afterResistance(10.0F, 0), 1.0E-5F);
      assertEquals(10.0F, BalanceRules.afterResistance(10.0F, -1), 1.0E-5F);
      assertEquals(0.0F, BalanceRules.afterResistance(10.0F, 4), 1.0E-5F);
      assertEquals(3.0F, BalanceRules.difficultyScaled(4.0F, 1), 1.0E-5F);
      assertEquals(4.0F, BalanceRules.difficultyScaled(4.0F, 2), 1.0E-5F);
      assertEquals(6.0F, BalanceRules.difficultyScaled(4.0F, 3), 1.0E-5F);
      assertEquals(0.0F, BalanceRules.difficultyScaled(4.0F, 0), 1.0E-5F);
   }

   @Test
   void heroHitAppliesResilienceThenCap() {
      assertEquals(4.0F, BalanceRules.heroHit(20.0F, 40.0F, 0.8F, 1.0F), 1.0E-5F);
      assertEquals(10.0F, BalanceRules.heroHit(100.0F, 40.0F, 0.0F, 0.25F), 1.0E-5F);
      assertEquals(0.0F, BalanceRules.heroHit(0.0F, 40.0F, 0.8F, 0.25F), 1.0E-5F);
      // never invulnerability: a cap leaves at least 1 HP per hit
      assertEquals(1.0F, BalanceRules.heroHit(50.0F, 2.0F, 0.0F, 0.1F), 1.0E-5F);
      // vanilla profile passes everything
      assertEquals(37.0F, BalanceRules.heroHit(37.0F, 40.0F, 0.0F, 1.0F), 1.0E-5F);
   }

   @Test
   void viltrumiteChassisIgnoresOnlyChipDamage() {
      assertEquals(0.0F, BalanceRules.viltrumiteChassis(2.0F, 97.5F, BalanceTable.CONQUEST.ignoreThreshold()), 1.0E-6F);
      assertTrue(BalanceRules.viltrumiteChassis(4.0F, 97.5F, BalanceTable.CONQUEST.ignoreThreshold()) > 0.0F);
      assertEquals(800.0F, BalanceRules.viltrumiteEffectiveHealth(20.0F, 97.5F), 0.01F);
   }

   @Test
   void sustainHealsLikeRegenerationIPlusNaturalRegen() {
      assertEquals(0.4F + 0.25F, BalanceRules.sustainHealPerSecond(), 1.0E-5F);
      assertEquals(Float.POSITIVE_INFINITY, BalanceRules.secondsToDie(20.0F, 0.5F, 0.65F));
      assertEquals(10.0F, BalanceRules.secondsToDie(20.0F, 2.65F, 0.65F), 1.0E-4F);
   }

   @Test
   void summonGroupsGrowButNeverLinearly() {
      assertEquals(1.0F, BalanceRules.groupFactor(4, 4), 1.0E-6F);
      assertEquals((float)Math.sqrt(4.0 / 12.0), BalanceRules.groupFactor(12, 4), 1.0E-6F);
      float four = BalanceRules.groupDamage(4, 4, 5.0F);
      float twelve = BalanceRules.groupDamage(12, 4, 5.0F);
      assertTrue(twelve > four, "a bigger army still hits harder");
      assertTrue(twelve < 12 * 5.0F * 0.6F, "but twelve shadows are not twelve times one");
      assertEquals(1.0F, BalanceRules.groupFactor(5, 0), 1.0E-6F);
   }

   @Test
   void hitsToKillRoundsUp() {
      assertEquals(5, BalanceRules.hitsToKill(20.0F, 4.0F));
      assertEquals(4, BalanceRules.hitsToKill(20.0F, 5.0F));
      assertEquals(Integer.MAX_VALUE, BalanceRules.hitsToKill(20.0F, 0.0F));
   }
}
