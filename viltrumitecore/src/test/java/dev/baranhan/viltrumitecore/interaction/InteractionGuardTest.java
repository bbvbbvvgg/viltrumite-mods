package dev.baranhan.viltrumitecore.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Cooldowns, aftermath and reentrancy of the contextual interactions: no spam, no recursion. */
class InteractionGuardTest {
   static final InteractionSpec SPEC = new InteractionSpec("test_combo", InteractionTrigger.HIT, 10, 40, 10);

   @Test
   void pairKeyIsOrderFree() {
      assertEquals(InteractionGuard.pair(3, 7), InteractionGuard.pair(7, 3));
      assertTrue(InteractionGuard.pair(3, 7) != InteractionGuard.pair(3, 8));
   }

   @Test
   void cooldownIsPerInteractionAndPair() {
      InteractionGuard g = new InteractionGuard();
      g.fired(SPEC, 1, 2, 100);
      assertTrue(g.coolingDown("test_combo", 2, 1, 139));
      assertFalse(g.coolingDown("test_combo", 2, 1, 140));
      assertFalse(g.coolingDown("other_combo", 1, 2, 120));
      assertFalse(g.coolingDown("test_combo", 1, 3, 120));
   }

   @Test
   void aftermathSwallowsTheRestOfTheStroke() {
      InteractionGuard g = new InteractionGuard();
      g.fired(SPEC, 1, 2, 100);
      assertTrue(g.inAftermath(2, 1, 105));
      assertFalse(g.inAftermath(1, 2, 110));
      g.fired(new InteractionSpec("short", InteractionTrigger.HIT, 0, 0, 2), 1, 2, 100);
      assertTrue(g.inAftermath(1, 2, 109), "a shorter aftermath never cuts a longer one");
   }

   @Test
   void reentrancyDepthAndClear() {
      InteractionGuard g = new InteractionGuard();
      assertFalse(g.reentrant());
      g.enter();
      g.enter();
      g.exit();
      assertTrue(g.reentrant());
      g.fired(SPEC, 1, 2, 0);
      g.clear();
      assertFalse(g.reentrant());
      assertEquals(0, g.size());
      g.exit();
      assertFalse(g.reentrant(), "exit never goes below zero");
   }

   @Test
   void throttleAndPrune() {
      InteractionGuard g = new InteractionGuard();
      assertTrue(g.throttle("fx", 0, 20));
      assertFalse(g.throttle("fx", 19, 20));
      assertTrue(g.throttle("fx", 20, 20));
      g.fired(SPEC, 1, 2, 0);
      g.prune(1000);
      assertEquals(0, g.size());
   }

   @Test
   void specValidatesIdAndTimings() {
      assertThrows(IllegalArgumentException.class, () -> new InteractionSpec("Bad-Id", InteractionTrigger.HIT, 0, 0, 0));
      assertThrows(IllegalArgumentException.class, () -> new InteractionSpec("ok_id", InteractionTrigger.HIT, 0, -1, 0));
      assertThrows(IllegalArgumentException.class, () -> new InteractionSpec("ok_id", InteractionTrigger.HIT, 0, 0, -1));
   }

   @Test
   void outcomes() {
      assertFalse(InteractionOutcome.PASS.fired());
      assertTrue(InteractionOutcome.CONSUMED.cancel());
      InteractionOutcome s = InteractionOutcome.scaled(1.5F);
      assertTrue(s.fired());
      assertFalse(s.cancel());
      assertEquals(1.5F, s.scale());
   }
}
