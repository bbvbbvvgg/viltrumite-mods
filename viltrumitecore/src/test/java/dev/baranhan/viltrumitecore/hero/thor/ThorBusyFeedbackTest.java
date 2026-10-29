package dev.baranhan.viltrumitecore.hero.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveHandler;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** B-m2: fast clicking a chain neither overflows its steps nor spams "Not now"; a real block still says so. */
class ThorBusyFeedbackTest {
   @AfterEach
   void reset() {
      ThorMoves.resetForTests();
   }

   private static void author() {
      ThorMoves.register("B", ThorMove.HAMMER_COMBO, new ThorMoveDefinition(2, 1, 3, MovePhase.FINISH, 8), ThorMoveHandler.NONE);
      ThorMoves.register("B", ThorMove.WRATH, new ThorMoveDefinition(4, 2, 4, null, 1), ThorMoveHandler.NONE);
   }

   @Test
   void aThirdClickOfTheSameChainIsQuiet() {
      author();
      ThorState state = new ThorState();
      assertEquals(ThorActions.Refusal.NONE, ThorActions.start(state, ThorMove.HAMMER_COMBO, true));
      assertEquals(ThorActions.Refusal.NONE, ThorActions.start(state, ThorMove.HAMMER_COMBO, true), "buffered");
      assertEquals(ThorActions.Refusal.BUSY, ThorActions.start(state, ThorMove.HAMMER_COMBO, true));
      assertTrue(ThorActions.quietBusy(state, ThorMove.HAMMER_COMBO), "no 'Not now' for clicking faster than the buffer");
   }

   @Test
   void aDifferentBlockedMoveStillTellsThePlayer() {
      author();
      ThorState state = new ThorState();
      ThorActions.start(state, ThorMove.WRATH, true);
      assertEquals(ThorActions.Refusal.BUSY, ThorActions.start(state, ThorMove.HAMMER_COMBO, true));
      assertFalse(ThorActions.quietBusy(state, ThorMove.HAMMER_COMBO));
   }

   @Test
   void aLongChainNeverRunsOutOfSteps() {
      author();
      ThorState state = new ThorState();
      ThorActions.start(state, ThorMove.HAMMER_COMBO, true);
      for (int press = 0; press < 40; press++) {
         assertEquals(ThorActions.Refusal.NONE, ThorActions.start(state, ThorMove.HAMMER_COMBO, true), "press " + press);
         for (int t = 0; t < 6 && state.timeline.queued() != null; t++) {
            ThorActions.advance(state, true, false);
         }
      }

      assertTrue(state.timeline.step() >= 0 && state.timeline.step() < ThorMove.HAMMER_COMBO.maxSteps());
   }
}
