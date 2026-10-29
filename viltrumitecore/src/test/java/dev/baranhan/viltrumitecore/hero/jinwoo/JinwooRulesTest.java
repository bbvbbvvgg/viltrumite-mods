package dev.baranhan.viltrumitecore.hero.jinwoo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Spec §9 numbers and the pure rule functions. */
class JinwooRulesTest {
   @Test
   void specNumbers() {
      assertEquals(20.0, JinwooRules.HEALTH_BONUS);
      assertEquals(6.0, JinwooRules.ARMOR);
      assertEquals(2.0, JinwooRules.TOUGHNESS);
      assertEquals(0.15, JinwooRules.SPEED_BONUS, 1e-9);
      assertEquals(100.0F, JinwooRules.MANA_MAX);
      assertEquals(5, JinwooRules.MANA_REGEN_INTERVAL);
      assertEquals(3.0F, JinwooRules.MANA_PER_HIT);
      assertArrayEquals(new float[]{6, 6, 5, 8, 12}, JinwooRules.COMBO_DAMAGE);
      assertArrayEquals(new int[]{6, 6, 10, 9, 14}, JinwooRules.COMBO_LENGTH);
      assertArrayEquals(new int[]{3, 3, 5, 5, 9}, JinwooRules.COMBO_CONTACT);
      assertEquals(12, JinwooRules.COMBO_WINDOW);
      assertEquals(4, JinwooRules.COMBO_BUFFER);
      assertEquals(3.4, JinwooRules.REACH);
      assertEquals(110.0, JinwooRules.ARC_DEGREES);
      assertEquals(8.0, JinwooRules.STEP_DISTANCE);
      assertEquals(4, JinwooRules.STEP_TICKS);
      assertEquals(4.0F, JinwooRules.STEP_DAMAGE);
      assertEquals(50, JinwooRules.STEP_COOLDOWN);
      assertEquals(10.0F, JinwooRules.STEP_MANA);
      assertEquals(2.5F, JinwooRules.RUSH_DAMAGE);
      assertEquals(8, JinwooRules.RUSH_HITS);
      assertEquals(10.0F, JinwooRules.RUSH_FINAL);
      assertEquals(240, JinwooRules.RUSH_COOLDOWN);
      assertEquals(30.0F, JinwooRules.RUSH_MANA);
      assertEquals(6, JinwooRules.AUTHORITY_TARGETS);
      assertEquals(14.0, JinwooRules.AUTHORITY_RANGE);
      assertEquals(60, JinwooRules.AUTHORITY_HOLD);
      assertEquals(8.0F, JinwooRules.AUTHORITY_DAMAGE);
      assertEquals(300, JinwooRules.AUTHORITY_COOLDOWN);
      assertEquals(35.0F, JinwooRules.AUTHORITY_MANA);
      assertEquals(10.0, JinwooRules.EXTRACT_RADIUS);
      assertEquals(30, JinwooRules.EXTRACT_CHANNEL);
      assertEquals(60, JinwooRules.EXTRACT_COOLDOWN);
      assertEquals(20.0F, JinwooRules.EXTRACT_MANA);
      assertEquals(3, JinwooRules.EXTRACT_ATTEMPTS);
      assertEquals(600, JinwooRules.REMNANT_LIFE);
      assertEquals(16, JinwooRules.REMNANT_MAX);
      assertEquals(64.0, JinwooRules.EXCHANGE_RANGE);
      assertEquals(400, JinwooRules.EXCHANGE_COOLDOWN);
      assertEquals(25.0F, JinwooRules.EXCHANGE_MANA);
      assertEquals(12, JinwooRules.ARMY_ACTIVE);
      assertEquals(24, JinwooRules.ARMY_ROSTER);
      assertEquals(64, JinwooRules.ARMY_LEVEL);
      assertEquals(600, JinwooRules.SHADOW_REVIVE);
      assertEquals(14, JinwooRules.CHARGE_LENGTH_BLOCKS);
      assertEquals(4.0, JinwooRules.CHARGE_WIDTH);
      assertEquals(9.0F, JinwooRules.CHARGE_DAMAGE);
      assertEquals(400, JinwooRules.CHARGE_COOLDOWN);
      assertEquals(40.0F, JinwooRules.CHARGE_MANA);
      assertEquals(80.0F, JinwooRules.ULT_MANA);
      assertEquals(100, JinwooRules.ULT_LENGTH);
      assertEquals(22.0, JinwooRules.ULT_RADIUS);
      assertEquals(14.0F, JinwooRules.ULT_DAMAGE);
      assertEquals(1800, JinwooRules.ULT_COOLDOWN);
      assertEquals(400, JinwooRules.ULT_DOMAIN);
   }

   @Test
   void regenEveryFiveTicksDoubledInDomain() {
      assertEquals(50.0F, JinwooRules.regen(50.0F, 3, false));
      assertEquals(51.0F, JinwooRules.regen(50.0F, 5, false));
      assertEquals(52.0F, JinwooRules.regen(50.0F, 10, true));
      assertEquals(100.0F, JinwooRules.regen(99.5F, 5, true));
   }

   @Test
   void comboChainAndWindow() {
      assertEquals(0, JinwooRules.nextComboStep(-1, 0));
      assertEquals(1, JinwooRules.nextComboStep(0, 5));
      assertEquals(4, JinwooRules.nextComboStep(3, 12));
      assertEquals(0, JinwooRules.nextComboStep(3, 13));
      assertEquals(0, JinwooRules.nextComboStep(4, 0));
      assertTrue(JinwooRules.buffers(2, 6));
      assertFalse(JinwooRules.buffers(1, 6));
   }

   @Test
   void arcsAndCones() {
      assertTrue(JinwooRules.inArc(0, 1, 0.5, 1, 110));
      assertFalse(JinwooRules.inArc(0, 1, 1, -0.1, 110));
      assertTrue(JinwooRules.inCone(0, 0, 1, 0.1, 0, 1, 25));
      assertFalse(JinwooRules.inCone(0, 0, 1, 1, 0, 1, 25));
   }

   @Test
   void extractionChances() {
      assertEquals(1.0, JinwooRules.extractChance(JinwooRules.ShadowTier.WEAK, 0), 1e-9);
      assertEquals(0.75, JinwooRules.extractChance(JinwooRules.ShadowTier.NORMAL, 0), 1e-9);
      assertEquals(0.45, JinwooRules.extractChance(JinwooRules.ShadowTier.STRONG, 0), 1e-9);
      assertEquals(0.85, JinwooRules.extractChance(JinwooRules.ShadowTier.STRONG, 2), 1e-9);
      assertEquals(JinwooRules.ShadowTier.WEAK, JinwooRules.tierOf(8, false));
      assertEquals(JinwooRules.ShadowTier.NORMAL, JinwooRules.tierOf(20, false));
      assertEquals(JinwooRules.ShadowTier.STRONG, JinwooRules.tierOf(20, true));
   }

   @Test
   void stepPitchAndDomain() {
      assertEquals(35.0F, JinwooRules.stepPitch(80));
      assertEquals(-35.0F, JinwooRules.stepPitch(-90));
      assertEquals(0.0, JinwooRules.domainRadius(0), 1e-9);
      assertEquals(22.0, JinwooRules.domainRadius(40), 1e-9);
      assertTrue(JinwooRules.domainRadius(20) > 11.0);
   }

   @Test
   void ultTimeline() {
      assertEquals(JinwooRules.UltPhase.CALL, JinwooRules.ultPhase(0));
      assertEquals(JinwooRules.UltPhase.RISE, JinwooRules.ultPhase(20));
      assertEquals(JinwooRules.UltPhase.WAVE, JinwooRules.ultPhase(60));
      assertEquals(JinwooRules.UltPhase.SETTLE, JinwooRules.ultPhase(75));
      assertEquals(JinwooRules.UltPhase.DONE, JinwooRules.ultPhase(100));
      assertEquals(40.0F, JinwooRules.ultRefund());
   }

   @Test
   void mutilationLandsEightHitsInSixteenTicks() {
      int hits = 0;
      for (int t = 0; t < 40; t++) {
         if (JinwooRules.rushHitTick(t)) {
            hits++;
            assertTrue(t >= JinwooRules.RUSH_APPROACH && t < JinwooRules.RUSH_APPROACH + JinwooRules.RUSH_HIT_TICKS);
         }
      }
      assertEquals(JinwooRules.RUSH_HITS, hits);
      assertTrue(JinwooRules.RUSH_FINAL_TICK < JinwooRules.RUSH_LENGTH);
   }

   @Test
   void chargeCoversFourteenBlocks() {
      assertEquals(0.0, JinwooRules.chargeDistance(0), 1e-9);
      assertEquals(14.0, JinwooRules.chargeDistance(JinwooRules.CHARGE_TICKS), 1e-9);
   }

   @Test
   void formationsAreDistinct() {
      java.util.Set<String> seen = new java.util.HashSet<>();
      for (int i = 0; i < JinwooRules.ARMY_ACTIVE; i++) {
         double[] f = JinwooRules.followSlot(i);
         assertTrue(f[1] > 0, "behind the owner");
         assertTrue(seen.add(Math.round(f[0] * 10) + ":" + Math.round(f[1] * 10)));
         double[] w = JinwooRules.wedgeSlot(i);
         assertTrue(w[1] > 0);
         double[] g = JinwooRules.guardSlot(i, JinwooRules.ARMY_ACTIVE);
         assertEquals(JinwooRules.GUARD_RADIUS, Math.hypot(g[0], g[1]), 1e-9);
      }
   }

   @Test
   void gateOrder() {
      assertEquals(JinwooActions.Refusal.NONE, JinwooActions.gate(true, false, 0, 50, 10, false));
      assertEquals(JinwooActions.Refusal.MANA, JinwooActions.gate(true, false, 0, 5, 10, false));
      assertEquals(JinwooActions.Refusal.COOLDOWN, JinwooActions.gate(true, false, 3, 50, 10, false));
      assertEquals(JinwooActions.Refusal.ANCHORED, JinwooActions.gate(true, true, 0, 50, 10, false));
      assertEquals(JinwooActions.Refusal.BUSY, JinwooActions.gate(true, false, 0, 50, 10, true));
   }
}
