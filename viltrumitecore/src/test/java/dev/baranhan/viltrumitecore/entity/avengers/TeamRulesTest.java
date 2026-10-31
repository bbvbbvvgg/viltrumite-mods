package dev.baranhan.viltrumitecore.entity.avengers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Joint moves (rework §15): readiness, the block check, the shockwave falloff and the scheduler. */
class TeamRulesTest {
   @Test
   void vaultFitsUnderTheHeadroomItChecks() {
      double apex = TeamRules.apex(TeamRules.VAULT_LAUNCH);
      assertTrue(apex > 5.5 && apex < TeamRules.VAULT_HEADROOM - 1.0, "apex " + apex);
      assertTrue(TeamRules.riseTicks(TeamRules.VAULT_LAUNCH) <= TeamRules.VAULT_RISE_MAX);
      int last = TeamRules.VAULT_SHOTS[TeamRules.VAULT_SHOTS.length - 1];
      assertTrue(last < TeamRules.VAULT_HANG, "every arrow leaves while he hangs");
      assertTrue(TeamRules.VAULT_SHOTS.length >= 3, "several shots in the air");
      assertTrue(TeamRules.VAULT_SHOTS[0] - TeamRules.VAULT_AIR_RELEASE >= 1, "the first draw starts inside the hang (stageT counts from 1)");
      double sink = TeamRules.VAULT_HANG * TeamRules.VAULT_HANG_SINK;
      assertTrue(sink < 2.0, "hangs, does not glide down: " + sink);
   }

   @Test
   void onlyAFreshBlockTowardThorCounts() {
      assertTrue(TeamRules.blockCounts(true, true, true, 5, 30, 3.5));
      assertFalse(TeamRules.blockCounts(true, true, true, 60, 30, 3.5), "guard held before the call: no accidental strike");
      assertFalse(TeamRules.blockCounts(true, false, true, 5, 30, 3.5), "the shield is in flight");
      assertFalse(TeamRules.blockCounts(true, true, false, 5, 30, 3.5), "Thor behind Steve");
      assertFalse(TeamRules.blockCounts(false, true, true, 0, 30, 3.5), "no guard");
      assertFalse(TeamRules.blockCounts(true, true, true, 5, 30, TeamRules.SHIELD_MAX_DISTANCE + 0.5), "too far");
   }

   @Test
   void shockwaveIsLimited() {
      assertEquals(TeamRules.SHOCK_DAMAGE, TeamRules.shockDamage(0), 1.0E-4);
      assertEquals(TeamRules.SHOCK_DAMAGE * 0.5F, TeamRules.shockDamage(TeamRules.SHOCK_RADIUS), 1.0E-4);
      assertEquals(0.0F, TeamRules.shockDamage(TeamRules.SHOCK_RADIUS + 0.1), 1.0E-4);
      assertTrue(TeamRules.shockDamage(4) < TeamRules.shockDamage(2));
      assertTrue(TeamRules.SHOCK_MAX_TARGETS <= 10 && TeamRules.SHOCK_STUN <= 60, "short stun, a capped crowd");
   }

   @Test
   void schedulerRotatesAndRespectsCooldownsAndTheGap() {
      boolean[] all = {true, true, true};
      int[] ready = {0, 0, 0};
      assertEquals(1, TeamRules.pick(ready, 0, false, 1, all));
      assertEquals(2, TeamRules.pick(new int[] {0, 50, 0}, 0, false, 1, all), "cooling move skipped");
      assertEquals(-1, TeamRules.pick(ready, 10, false, 0, all), "gap between moves");
      assertEquals(-1, TeamRules.pick(ready, 0, true, 0, all), "one at a time");
      assertEquals(0, TeamRules.pick(ready, 0, false, 2, new boolean[] {true, false, false}));
      assertEquals(-1, TeamRules.pick(ready, 0, false, 0, new boolean[] {false, false, false}));
      for (int i = 0; i < TeamRules.MOVES; i++) {
         assertTrue(TeamRules.COOLDOWN[i] >= 600, "no constant repetition");
         assertTrue(TeamRules.CANCEL_COOLDOWN[i] > TeamRules.GAP);
      }
   }

   @Test
   void movesNeedACrowdOrATankyEnemy() {
      assertFalse(TeamRules.worthIt(1, 20));
      assertTrue(TeamRules.worthIt(2, 20));
      assertTrue(TeamRules.worthIt(1, 100));
   }
}
