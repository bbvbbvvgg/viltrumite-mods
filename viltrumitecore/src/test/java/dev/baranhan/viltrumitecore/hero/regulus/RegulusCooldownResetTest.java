package dev.baranhan.viltrumitecore.hero.regulus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Final integration: PR #87's Evangelium fixes (named refusal, re-activation after a cancel, /hero cooldown reset)
 * carried over to Regulus Reborn (#89), whose ultimate is Kingdom of One and whose Evangelium is an inert keepsake.
 */
class RegulusCooldownResetTest {
   @Test
   void resetEndsEveryTimerButKeepsTheRest() {
      RegulusState state = new RegulusState();
      state.lionCooldown = 11;
      state.sandCooldown = 22;
      state.severCooldown = 33;
      state.trampleCooldown = 44;
      state.replyCooldown = 55;
      state.ultCooldown = 1999;
      state.strain = 640;
      state.lionLock = 77;
      state.clearCooldowns();
      for (int i = RegulusFlags.COOLDOWN_LION; i <= RegulusFlags.COOLDOWN_ULT; i++) {
         assertEquals(0, state.cooldown(i), "cooldown " + i);
      }

      assertEquals(640, state.strain, "strain is not a cooldown");
      assertEquals(77, state.lionLock, "the Lion lock is not a cooldown");
      assertEquals(0, KingdomOfOne.refusalSeconds(state), "the ult opens again at once");
   }

   @Test
   void refusalNamesTheRemainingRecharge() {
      RegulusState state = new RegulusState();
      assertEquals(0, KingdomOfOne.refusalSeconds(state));
      state.ultCooldown = 1;
      assertEquals(1, KingdomOfOne.refusalSeconds(state));
      state.ultCooldown = 20;
      assertEquals(1, KingdomOfOne.refusalSeconds(state));
      state.ultCooldown = 21;
      assertEquals(2, KingdomOfOne.refusalSeconds(state));
   }

   @Test
   void anAbortedJudgementRechargesAndOpensAgain() {
      // abort(early) caps the cooldown; with the scene gone it ticks down to 0 and the ult may be pressed again
      RegulusState state = new RegulusState();
      state.ultCooldown = Math.min(RegulusRules.ULT_COOLDOWN, KingdomOfOne.ABORT_EARLY_COOLDOWN);
      assertTrue(KingdomOfOne.refusalSeconds(state) > 0);
      for (int t = 0; t < KingdomOfOne.ABORT_EARLY_COOLDOWN; t++) {
         state.tickCooldowns();
      }

      assertEquals(0, state.ultCooldown);
      assertEquals(0, KingdomOfOne.refusalSeconds(state));
   }
}
