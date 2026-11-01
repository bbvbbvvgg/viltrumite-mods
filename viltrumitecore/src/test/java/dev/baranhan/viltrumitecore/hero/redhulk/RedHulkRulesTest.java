package dev.baranhan.viltrumitecore.hero.redhulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Red Hulk's heat system, combo and ability rules (pure functions of {@link RedHulkRules} and {@link RedHulkMove}). */
class RedHulkRulesTest {
   private static final float EPS = 1.0E-4F;

   @Test
   void heatIsClampedAndTiered() {
      assertEquals(0, RedHulkRules.clampHeat(-50));
      assertEquals(RedHulkRules.HEAT_MAX, RedHulkRules.clampHeat(5000));
      assertEquals(0, RedHulkRules.tier(0));
      assertEquals(0, RedHulkRules.tier(RedHulkRules.WARM - 1));
      assertEquals(1, RedHulkRules.tier(RedHulkRules.WARM));
      assertEquals(1, RedHulkRules.tier(RedHulkRules.HOT - 1));
      assertEquals(2, RedHulkRules.tier(RedHulkRules.HOT));
      assertEquals(2, RedHulkRules.tier(RedHulkRules.BLAZING - 1));
      assertEquals(3, RedHulkRules.tier(RedHulkRules.BLAZING));
      assertEquals(3, RedHulkRules.tier(RedHulkRules.HEAT_MAX));
      assertTrue(RedHulkRules.WARM < RedHulkRules.HOT && RedHulkRules.HOT < RedHulkRules.BLAZING && RedHulkRules.BLAZING < RedHulkRules.HEAT_MAX);
   }

   @Test
   void heatRaisesDamageUpToTheCap() {
      assertEquals(1.0F, RedHulkRules.damageFactor(0, false), EPS);
      assertEquals(1.15F, RedHulkRules.damageFactor(500, false), EPS);
      assertEquals(1.30F, RedHulkRules.damageFactor(RedHulkRules.HEAT_MAX, false), EPS);
      assertEquals(1.30F, RedHulkRules.damageFactor(99999, false), EPS, "heat is clamped");
      assertEquals(1.15F, RedHulkRules.damageFactor(0, true), EPS);
      assertEquals(RedHulkRules.MAX_DAMAGE_FACTOR, RedHulkRules.damageFactor(RedHulkRules.HEAT_MAX, true), EPS, "fury at full heat hits the cap");
      float last = 0;
      for (int h = 0; h <= RedHulkRules.HEAT_MAX; h += 50) {
         float f = RedHulkRules.damageFactor(h, true);
         assertTrue(f >= last && f <= RedHulkRules.MAX_DAMAGE_FACTOR + EPS, "monotone and capped at " + h);
         last = f;
      }
   }

   @Test
   void stateChangesIncomingDamage() {
      assertEquals(1.0F, RedHulkRules.damageTakenFactor(false, false, false, false), EPS);
      assertEquals(0.85F, RedHulkRules.damageTakenFactor(true, false, false, false), EPS);
      assertEquals(1.15F, RedHulkRules.damageTakenFactor(false, true, false, false), EPS, "overheat: +15% damage taken");
      assertEquals(0.5F, RedHulkRules.damageTakenFactor(false, false, true, false), EPS);
      assertEquals(1.2F, RedHulkRules.damageTakenFactor(false, false, false, true), EPS);
   }

   @Test
   void coolingWaitsForQuietAndWaterSteamsHimDown() {
      assertEquals(500, RedHulkRules.cool(500, RedHulkRules.HEAT_IDLE - 1, false, false, false), "no cooling right after a gain");
      assertEquals(500 - RedHulkRules.HEAT_DECAY, RedHulkRules.cool(500, RedHulkRules.HEAT_IDLE, false, false, false));
      assertEquals(500, RedHulkRules.cool(500, 1000, true, false, false), "fury keeps the heat");
      assertEquals(500, RedHulkRules.cool(500, 1000, false, true, false), "overheat keeps the heat");
      assertEquals(500 - RedHulkRules.HEAT_DECAY * RedHulkRules.WET_COOLING, RedHulkRules.cool(500, 0, false, false, true), "water cools at once, x3");
      assertEquals(0, RedHulkRules.cool(3, 1000, false, false, false), "never below zero");
      assertEquals(0, RedHulkRules.cool(3, 0, false, false, true));
      // From full heat to cold in a plausible time: 100 quiet ticks + 1000 / 5 = 300 ticks (15 s).
      int heat = RedHulkRules.HEAT_MAX;
      int ticks = 0;
      while (heat > 0 && ticks < 2000) {
         heat = RedHulkRules.cool(heat, ticks, false, false, false);
         ticks++;
      }
      assertEquals(0, heat);
      assertTrue(ticks >= 260 && ticks <= 340, "full -> cold in ~15 s, was " + ticks);
   }

   @Test
   void hitsAndFlamesFeedTheHeatWithCaps() {
      assertEquals(0, RedHulkRules.heatFromHit(0.0F));
      assertEquals(15, RedHulkRules.heatFromHit(5.0F));
      assertEquals(RedHulkRules.HEAT_HIT_TAKEN_CAP, RedHulkRules.heatFromHit(1000.0F));
      assertEquals(0, RedHulkRules.heatFromHit(-3.0F));
      assertEquals(20, RedHulkRules.heatFromAbsorbed(2.0F));
      assertEquals(RedHulkRules.HEAT_ABSORB_CAP, RedHulkRules.heatFromAbsorbed(100.0F));
      assertEquals(0.60F, RedHulkRules.FIRE_ABSORB, EPS, "fire is 60% absorbed");
   }

   @Test
   void comboCyclesThroughFiveBlowsAndResetsWhenIdle() {
      assertEquals(RedHulkRules.COMBO_LENGTH, RedHulkRules.COMBO_DAMAGE.length);
      assertEquals(RedHulkRules.COMBO_LENGTH, RedHulkRules.COMBO_HEAT.length);
      int step = -1;
      List<Integer> seen = new ArrayList<>();
      for (int i = 0; i < 7; i++) {
         step = RedHulkRules.nextStep(step, 3);
         seen.add(step);
      }
      assertEquals(List.of(0, 1, 2, 3, 4, 0, 1), seen);
      assertEquals(3, RedHulkRules.nextStep(2, RedHulkRules.COMBO_WINDOW));
      assertEquals(0, RedHulkRules.nextStep(2, RedHulkRules.COMBO_WINDOW + 1), "the window closed");
      for (int s = 0; s < RedHulkRules.COMBO_LENGTH; s++) {
         RedHulkMove m = RedHulkMove.combo(s);
         assertTrue(m.isCombo());
         assertEquals(s, m.comboStep());
      }
      assertEquals(RedHulkMove.JAB_L, RedHulkMove.combo(-4));
      assertEquals(RedHulkMove.HAMMER, RedHulkMove.combo(99));
      assertTrue(RedHulkRules.COMBO_DAMAGE[4] > RedHulkRules.COMBO_DAMAGE[0], "the finisher hits hardest");
   }

   @Test
   void inputIsBufferedNearTheEndOfAMove() {
      int len = RedHulkMove.HOOK.length;
      assertFalse(RedHulkRules.buffers(len - RedHulkRules.BUFFER - 1, len));
      assertTrue(RedHulkRules.buffers(len - RedHulkRules.BUFFER, len));
      assertTrue(RedHulkRules.buffers(len, len));
   }

   @Test
   void falloffAndFallDamage() {
      assertEquals(12.0F, RedHulkRules.falloff(0.0, 6.0, 12.0F, 6.0F), EPS);
      assertEquals(9.0F, RedHulkRules.falloff(3.0, 6.0, 12.0F, 6.0F), EPS);
      assertEquals(6.0F, RedHulkRules.falloff(6.0, 6.0, 12.0F, 6.0F), EPS);
      assertEquals(6.0F, RedHulkRules.falloff(60.0, 6.0, 12.0F, 6.0F), EPS, "clamped at the edge");
      assertEquals(0.0F, RedHulkRules.fallDamage(RedHulkRules.SAFE_FALL), EPS, "no damage up to 24 blocks");
      assertEquals(0.0F, RedHulkRules.fallDamage(10.0F), EPS);
      assertEquals((40.0F - RedHulkRules.SAFE_FALL) * RedHulkRules.FALL_FACTOR, RedHulkRules.fallDamage(40.0F), EPS);
   }

   @Test
   void heatBlastPulsesAndSpendsHeat() {
      assertEquals(1.6F, RedHulkRules.blastPulseDamage(0), EPS);
      assertEquals(3.2F, RedHulkRules.blastPulseDamage(RedHulkRules.HEAT_MAX), EPS);
      List<Integer> pulses = new ArrayList<>();
      for (int t = 0; t <= RedHulkMove.BLAST.length; t++) {
         if (RedHulkRules.blastPulse(t)) {
            pulses.add(t);
         }
      }
      assertEquals(List.of(12, 14, 16, 18, 20, 22, 24), pulses);
      assertTrue(RedHulkRules.BLAST_FIRE_END < RedHulkMove.BLAST.length, "the beam ends inside the clip");
      assertEquals(400, RedHulkRules.afterBlast(1000), "the blast spends 60% of the heat");
      assertEquals(120, RedHulkRules.afterBlast(300));
      float full = pulses.size() * RedHulkRules.blastPulseDamage(RedHulkRules.HEAT_MAX);
      assertTrue(full <= 25.0F, "a full-heat blast on one target stays below an ultimate, was " + full);
   }

   @Test
   void heatGatesTheHeatAbilities() {
      assertFalse(RedHulkRules.canBlast(RedHulkRules.BLAST_MIN_HEAT - 1, false));
      assertTrue(RedHulkRules.canBlast(RedHulkRules.BLAST_MIN_HEAT, false));
      assertFalse(RedHulkRules.canBlast(RedHulkRules.HEAT_MAX, true), "overheat locks the blast");
      assertFalse(RedHulkRules.canUlt(RedHulkRules.ULT_MIN_HEAT - 1, false));
      assertTrue(RedHulkRules.canUlt(RedHulkRules.ULT_MIN_HEAT, false));
      assertFalse(RedHulkRules.canUlt(RedHulkRules.HEAT_MAX, true), "overheat locks the ultimate");
      assertTrue(RedHulkRules.BLAST_MIN_HEAT < RedHulkRules.ULT_MIN_HEAT);
   }

   @Test
   void grabLiftsOnlyHumanSizedBodies() {
      assertTrue(RedHulkRules.liftable(0.6F, 1.8F), "a player");
      assertTrue(RedHulkRules.liftable(1.4F, 2.9F), "an iron golem");
      assertFalse(RedHulkRules.liftable(3.0F, 3.0F), "too wide");
      assertFalse(RedHulkRules.liftable(1.0F, 12.0F), "too tall");
   }

   @Test
   void cooldownsAreOrderedAndTheUltimateIsLongest() {
      assertEquals(RedHulkRules.COOLDOWNS, RedHulkRules.COOLDOWN_TICKS.length);
      int ult = RedHulkRules.COOLDOWN_TICKS[RedHulkRules.CD_ULT];
      for (int i = 0; i < RedHulkRules.COOLDOWNS; i++) {
         assertTrue(RedHulkRules.COOLDOWN_TICKS[i] > 0);
         assertTrue(RedHulkRules.COOLDOWN_TICKS[i] <= ult);
      }
      assertTrue(RedHulkRules.COOLDOWN_TICKS[RedHulkRules.CD_FURY] > RedHulkRules.FURY_TICKS, "fury cannot be chained");
   }

   @Test
   void movesHaveConsistentTimelines() {
      for (RedHulkMove m : RedHulkMove.values()) {
         assertTrue(m.length > 0, m.name());
         assertTrue(m.contact <= m.length, m + " contacts inside the move");
         assertTrue(m.cancelFrom < m.length, m + " cancels inside the move");
         if (m.contact >= 0 && m.cancelFrom >= 0) {
            assertTrue(m.cancelFrom >= m.contact, m + " cannot be cancelled before its hit lands");
         }
         assertEquals(m, RedHulkMove.byId(m.ordinal()));
         assertFalse(m.cancellable(-1));
         if (m.locks()) {
            assertEquals(-1, m.cancelFrom, m + " locks: never cancelled");
         }
      }
      assertNull(RedHulkMove.byId(-1));
      assertNull(RedHulkMove.byId(RedHulkMove.values().length));
      assertTrue(RedHulkMove.HOLD.loops() && RedHulkMove.LEAP_AIR.loops());
      assertFalse(RedHulkMove.HAMMER.loops());
      assertTrue(RedHulkMove.JAB_L.cancellable(RedHulkMove.JAB_L.cancelFrom));
      assertFalse(RedHulkMove.JAB_L.cancellable(RedHulkMove.JAB_L.cancelFrom - 1));
      assertTrue(RedHulkMove.ULT_CHARGE.ultimate() && RedHulkMove.ULT_CHARGE.locks() && RedHulkMove.ULT_CHARGE.unstoppable());
      assertTrue(RedHulkMove.VENT.locks(), "the overheat vent cannot be skipped");
      assertTrue(RedHulkRules.VENT_BURST_TICK < RedHulkMove.VENT.length);
      assertNotNull(RedHulkMove.combo(0));
   }

   @Test
   void overheatIsAShortPunishment() {
      assertTrue(RedHulkRules.OVERHEAT_TICKS == 120, "6 s");
      assertTrue(RedHulkRules.HEAT_AFTER_VENT < RedHulkRules.ULT_MIN_HEAT, "after venting the ultimate needs fresh heat");
      assertTrue(RedHulkRules.HEAT_AFTER_VENT >= RedHulkRules.BLAST_MIN_HEAT);
      assertTrue(RedHulkRules.OVERHEAT_SLOW < 0);
   }

   @Test
   void slotsGreyOutOnCooldownShortHeatAndOverheat() {
      int none = 0;
      int ready = RedHulkFlags.set(RedHulkFlags.set(0, RedHulkFlags.BLAST_READY, true), RedHulkFlags.ULT_READY, true);
      for (RedHulkAbility a : RedHulkAbility.values()) {
         assertTrue(RedHulkAbilities.slotGrey(a, 5, ready), a + " on cooldown");
      }
      assertFalse(RedHulkAbilities.slotGrey(RedHulkAbility.CLAP, 0, none), "punch abilities need no heat");
      assertFalse(RedHulkAbilities.slotGrey(RedHulkAbility.SEISMIC, 0, none));
      assertFalse(RedHulkAbilities.slotGrey(RedHulkAbility.LEAP, 0, none));
      assertTrue(RedHulkAbilities.slotGrey(RedHulkAbility.BLAST, 0, none), "blast needs 300 heat");
      assertTrue(RedHulkAbilities.slotGrey(RedHulkAbility.OVERHEAT, 0, none), "ultimate needs 700 heat");
      assertFalse(RedHulkAbilities.slotGrey(RedHulkAbility.BLAST, 0, ready));
      assertFalse(RedHulkAbilities.slotGrey(RedHulkAbility.OVERHEAT, 0, ready));
      assertFalse(RedHulkAbilities.slotGrey(RedHulkAbility.FURY, 0, none));
      assertTrue(RedHulkAbilities.slotGrey(RedHulkAbility.FURY, 0, RedHulkFlags.set(0, RedHulkFlags.OVERHEAT, true)), "no fury while overheated");
      assertTrue(RedHulkAbilities.slotGrey(RedHulkAbility.FURY, 0, RedHulkFlags.set(0, RedHulkFlags.FURY, true)), "fury does not stack");
      assertEquals(RedHulkAbility.values().length, RedHulkAbilities.inputSlots().length);
      for (RedHulkAbility a : RedHulkAbility.values()) {
         assertTrue(RedHulkAbilities.owns(a.id()));
         assertEquals(a.action(), RedHulkAbilities.actionFor(a.id()));
      }
      assertFalse(RedHulkAbilities.owns("thor:wrath"));
   }
}
