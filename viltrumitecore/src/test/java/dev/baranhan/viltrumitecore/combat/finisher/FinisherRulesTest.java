package dev.baranhan.viltrumitecore.combat.finisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Boundary behaviour of the finisher rules (wave1-22 T6). {@link FinisherRules#judge} is the single
 * place the verdict is decided — the server and the client hint both call it — so these cases are the
 * acceptance table of the mechanic.
 */
class FinisherRulesTest {
   private static FinisherRules.Verdict judge(boolean alive, boolean sameLevel, boolean lineOfSight, double distance,
                                             float health, float maxHealth, boolean boss,
                                             boolean player, boolean pvp, boolean ally) {
      return FinisherRules.judge(new FinisherRules.Facts(alive, sameLevel, lineOfSight, distance, health, maxHealth, boss, player, pvp, ally));
   }

   /** The ALLOW shape for an ordinary body; every test moves one fact away from it. */
   private static FinisherRules.Verdict mob(double distance, float health, float maxHealth, boolean boss) {
      return judge(true, true, true, distance, health, maxHealth, boss, false, true, false);
   }

   @Test
   void reachIsInclusiveAtFourAndAHalfBlocks() {
      assertTrue(FinisherRules.inRange(FinisherRules.RANGE));
      assertTrue(FinisherRules.inRange(0.0));
      assertFalse(FinisherRules.inRange(4.5000001));
      assertFalse(FinisherRules.inRange(-0.1));
      assertFalse(FinisherRules.inRange(Double.NaN));
      assertFalse(FinisherRules.inRange(Double.POSITIVE_INFINITY));
      assertEquals(FinisherRules.Verdict.TOO_FAR, mob(4.6, 1.0F, 20.0F, false));
      assertEquals(FinisherRules.Verdict.ALLOW, mob(4.5, 1.0F, 20.0F, false));
   }

   @Test
   void healthUsesTheFloorOrTheFractionWhicheverIsBigger() {
      assertEquals(20.0F, FinisherRules.threshold(100.0F, false), 0.0F);
      assertEquals(4.0F, FinisherRules.threshold(10.0F, false), 0.0F, "20 % of 10 is under the floor");
      assertEquals(4.0F, FinisherRules.threshold(0.0F, false), 0.0F, "a broken max health keeps the floor");
      assertTrue(FinisherRules.healthAllows(20.0F, 100.0F, false));
      assertFalse(FinisherRules.healthAllows(20.1F, 100.0F, false));
      assertTrue(FinisherRules.healthAllows(4.0F, 10.0F, false));
      assertFalse(FinisherRules.healthAllows(4.1F, 10.0F, false));
      assertFalse(FinisherRules.healthAllows(Float.NaN, 100.0F, false));
      assertFalse(FinisherRules.healthAllows(-1.0F, 100.0F, false));
      assertEquals(FinisherRules.Verdict.TOO_HEALTHY, mob(1.0, 21.0F, 100.0F, false));
   }

   @Test
   void bossesNeedTheirLowEightPercent() {
      assertEquals(8.0F, FinisherRules.threshold(100.0F, true), 0.0F);
      assertTrue(FinisherRules.healthAllows(8.0F, 100.0F, true));
      assertFalse(FinisherRules.healthAllows(8.1F, 100.0F, true));
      assertEquals(FinisherRules.Verdict.TOO_HEALTHY, mob(1.0, 12.0F, 100.0F, true), "12 % is fine for a body, not for a boss");
      assertEquals(FinisherRules.Verdict.ALLOW, mob(1.0, 12.0F, 100.0F, false));
   }

   @Test
   void refusalsReadInOrder() {
      assertEquals(FinisherRules.Verdict.DEAD, judge(false, false, false, 99.0, Float.NaN, 0.0F, true, true, false, true));
      assertEquals(FinisherRules.Verdict.OTHER_LEVEL, judge(true, false, false, 99.0, 999.0F, 100.0F, false, true, false, false));
      assertEquals(FinisherRules.Verdict.TOO_FAR, judge(true, true, false, 99.0, 999.0F, 100.0F, false, true, false, false));
      assertEquals(FinisherRules.Verdict.NO_LINE_OF_SIGHT, judge(true, true, false, 1.0, 999.0F, 100.0F, false, true, false, false));
      assertEquals(FinisherRules.Verdict.TOO_HEALTHY, judge(true, true, true, 1.0, 999.0F, 100.0F, false, true, false, false));
   }

   @Test
   void playersNeedPvpAndMustNotBeAllies() {
      assertEquals(FinisherRules.Verdict.ALLOW, judge(true, true, true, 1.0, 1.0F, 20.0F, false, true, true, false));
      assertEquals(FinisherRules.Verdict.PVP_FORBIDDEN, judge(true, true, true, 1.0, 1.0F, 20.0F, false, true, false, false));
      assertEquals(FinisherRules.Verdict.ALLY, judge(true, true, true, 1.0, 1.0F, 20.0F, false, true, true, true));
      assertEquals(FinisherRules.Verdict.ALLOW, judge(true, true, true, 1.0, 1.0F, 20.0F, false, false, false, true), "a mob is not gated by the PvP rule");
   }

   @Test
   void antiSpamGateAndBlowSize() {
      assertFalse(FinisherRules.onCooldown(0));
      assertFalse(FinisherRules.onCooldown(-3));
      assertTrue(FinisherRules.onCooldown(1));
      assertEquals(FinisherRules.COOLDOWN_TICKS, 20);
      assertEquals((10.0F + 0.0F + 1.0F) * FinisherRules.ARMOR_HEADROOM, FinisherRules.finishingDamage(10.0F, 0.0F), 0.0F);
      assertEquals((0.0F + 4.0F + 1.0F) * FinisherRules.ARMOR_HEADROOM, FinisherRules.finishingDamage(0.0F, 4.0F), 0.0F);
      assertEquals((0.0F + 0.0F + 1.0F) * FinisherRules.ARMOR_HEADROOM, FinisherRules.finishingDamage(Float.NaN, -2.0F), 0.0F);
      assertTrue(FinisherRules.finishingDamage(20.0F, 0.0F) > 20.0F / 0.2F, "the blow survives the worst armor mitigation");
   }
}
