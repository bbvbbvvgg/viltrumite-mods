package dev.baranhan.viltrumitecore.hero.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import org.junit.jupiter.api.Test;

/** The pure server flow behind MMB: gate → timeline → Mjölnir changes place at MAIN. */
class ThorActionsTest {
   private static void advance(ThorState state, int ticks, boolean handFree) {
      for (int i = 0; i < ticks; i++) {
         ThorActions.advance(state, handFree, false);
      }
   }

   @Test
   void gateRefusesLaterStagesAndABusyHand() {
      assertSame(ThorActions.Refusal.NONE, ThorActions.gate(ThorMove.DRAW, true));
      assertSame(ThorActions.Refusal.HANDS_BUSY, ThorActions.gate(ThorMove.DRAW, false));
      assertSame(ThorActions.Refusal.NONE, ThorActions.gate(ThorMove.HOLSTER, false), "holstering never needs a free hand");
      assertSame(ThorActions.Refusal.NOT_READY, ThorActions.gate(ThorMove.HAMMER_THROW, true));
      assertSame(ThorActions.Refusal.NOT_READY, ThorActions.gate(ThorMove.WRATH, true));
   }

   @Test
   void drawPutsMjolnirInTheHandWhenTheGripLands() {
      ThorState state = new ThorState();
      assertSame(ThorActions.Refusal.NONE, ThorActions.start(state, ThorMove.DRAW, true));
      assertTrue(state.timeline.is(ThorMove.DRAW));

      advance(state, ThorRules.DRAW_INTRO - 1, true);
      assertSame(HammerState.HOLSTERED, state.hammer, "still reaching");
      advance(state, 1, true);
      assertSame(MovePhase.MAIN, state.timeline.phase());
      assertSame(HammerState.IN_HAND, state.hammer, "grip at the start of MAIN");

      advance(state, ThorRules.DRAW_MAIN + ThorRules.DRAW_FINISH, true);
      assertFalse(state.timeline.running());
      assertSame(HammerState.IN_HAND, state.hammer);
   }

   @Test
   void holsterReturnsMjolnirToTheBelt() {
      ThorState state = new ThorState();
      state.hammer = HammerState.IN_HAND;
      assertSame(ThorActions.Refusal.NONE, ThorActions.start(state, ThorMove.HOLSTER, true));
      advance(state, ThorRules.HOLSTER_INTRO, true);
      assertSame(HammerState.HOLSTERED, state.hammer);
      advance(state, ThorRules.HOLSTER_MAIN + ThorRules.HOLSTER_FINISH, true);
      assertFalse(state.timeline.running());
   }

   @Test
   void aPressDuringTheReachIsRefusedButTheSettleGivesWay() {
      ThorState state = new ThorState();
      ThorActions.start(state, ThorMove.DRAW, true);
      assertSame(ThorActions.Refusal.BUSY, ThorActions.start(state, ThorMove.HOLSTER, true));

      advance(state, ThorRules.DRAW_INTRO + ThorRules.DRAW_MAIN, true);
      assertSame(MovePhase.FINISH, state.timeline.phase());
      assertSame(ThorActions.Refusal.NONE, ThorActions.start(state, ThorMove.HOLSTER, true), "FINISH is the cancel window");
      assertTrue(state.timeline.is(ThorMove.HOLSTER));
   }

   @Test
   void anItemTakenBeforeTheGripCancelsTheDraw() {
      ThorState state = new ThorState();
      ThorActions.start(state, ThorMove.DRAW, true);
      advance(state, 1, false);
      assertFalse(state.timeline.running());
      advance(state, ThorRules.DRAW_INTRO + 1, false);
      assertSame(HammerState.HOLSTERED, state.hammer);
   }

   @Test
   void mjolnirNeverSharesTheHandWithAnItem() {
      ThorState state = new ThorState();
      state.hammer = HammerState.IN_HAND;
      ThorActions.advance(state, false, false);
      assertSame(HammerState.HOLSTERED, state.hammer);

      assertSame(HammerState.HOLSTERED, ThorActions.settle(HammerState.IN_HAND, false));
      assertSame(HammerState.IN_HAND, ThorActions.settle(HammerState.IN_HAND, true));
      assertSame(HammerState.THROWN, ThorActions.settle(HammerState.THROWN, false), "a thrown hammer is not in the hand");
   }

   @Test
   void controlAndLifecycleStopTheMove() {
      ThorState state = new ThorState();
      ThorActions.start(state, ThorMove.DRAW, true);
      ThorActions.advance(state, true, true);
      assertFalse(state.timeline.running());
      assertSame(HammerState.HOLSTERED, state.hammer);

      state.hammer = HammerState.IN_HAND;
      ThorActions.start(state, ThorMove.HOLSTER, true);
      ThorActions.stop(state);
      assertFalse(state.timeline.running());
      assertSame(HammerState.IN_HAND, state.hammer, "stopping never teleports the hammer");
   }

   @Test
   void unshippedMovesNeverTouchTheTimeline() {
      ThorState state = new ThorState();
      for (ThorMove move : ThorMove.values()) {
         if (!move.shipped()) {
            assertSame(ThorActions.Refusal.NOT_READY, ThorActions.start(state, move, true), move.name());
            assertFalse(state.timeline.running(), move.name());
         }
      }
   }

   @Test
   void afterMainOnlyMovesTheHammerFromTheExpectedPlace() {
      assertSame(HammerState.IN_HAND, ThorActions.afterMain(ThorMove.DRAW, HammerState.HOLSTERED));
      assertSame(HammerState.THROWN, ThorActions.afterMain(ThorMove.DRAW, HammerState.THROWN));
      assertSame(HammerState.HOLSTERED, ThorActions.afterMain(ThorMove.HOLSTER, HammerState.IN_HAND));
      assertSame(HammerState.HOLSTERED, ThorActions.afterMain(ThorMove.HOLSTER, HammerState.HOLSTERED));
      assertSame(HammerState.IN_HAND, ThorActions.afterMain(ThorMove.GUARD, HammerState.IN_HAND));
   }

   @Test
   void theMmbPressMatchesTheHammerPlace() {
      // The same resolver the server press and the client claim use.
      ThorState state = new ThorState();
      ThorMove first = ThorControls.resolve(ThorInput.HAMMER, ThorContext.grounded(state.hammer, state.mode));
      assertSame(ThorMove.DRAW, first);
      ThorActions.start(state, first, true);
      advance(state, ThorMove.DRAW.length(), true);
      ThorMove second = ThorControls.resolve(ThorInput.HAMMER, ThorContext.grounded(state.hammer, state.mode));
      assertSame(ThorMove.HOLSTER, second);
      assertEquals(ThorActions.Refusal.NONE, ThorActions.start(state, second, true));
   }
}
