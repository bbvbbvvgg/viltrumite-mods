package dev.baranhan.viltrumitecore.hero.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveContext;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveHandler;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The pure server flow: gate → timeline → press / release bookkeeping; technical DRAW / HOLSTER timings. */
class ThorActionsTest {
   @AfterEach
   void reset() {
      ThorMoves.resetForTests();
   }

   private static void advance(ThorState state, int ticks, boolean handFree) {
      for (int i = 0; i < ticks; i++) {
         ThorActions.advance(state, handFree, false);
      }
   }

   @Test
   void gateRefusesUnauthoredMovesABusyHandAndASceneLock() {
      ThorState state = new ThorState();
      assertSame(ThorActions.Refusal.NONE, ThorActions.gate(state, ThorMove.DRAW, true));
      assertSame(ThorActions.Refusal.HANDS_BUSY, ThorActions.gate(state, ThorMove.DRAW, false));
      assertSame(ThorActions.Refusal.NONE, ThorActions.gate(state, ThorMove.HOLSTER, false), "holstering never needs a free hand");
      assertSame(ThorActions.Refusal.NOT_READY, ThorActions.gate(state, ThorMove.HAMMER_THROW, true));
      assertSame(ThorActions.Refusal.NOT_READY, ThorActions.gate(state, ThorMove.WRATH, true));

      assertTrue(ThorBridge.lockScene(state, "A"));
      assertSame(ThorActions.Refusal.BUSY, ThorActions.gate(state, ThorMove.DRAW, true), "a scene lock refuses every press");
      assertSame(ThorActions.Refusal.BUSY, ThorActions.start(state, ThorInput.HAMMER, ThorMove.DRAW, true));
      assertFalse(state.timeline.running());
   }

   @Test
   void technicalDrawPutsMjolnirInTheHandWhenTheGripLands() {
      ThorState state = new ThorState();
      assertSame(ThorActions.Refusal.NONE, ThorActions.start(state, ThorMove.DRAW, true));
      advance(state, ThorRules.DRAW_INTRO - 1, true);
      assertSame(HammerState.HOLSTERED, state.hammer, "still reaching");
      assertSame(StagedTimeline.Event.ENTER_MAIN, ThorActions.advance(state, true, false));
      assertSame(HammerState.IN_HAND, state.hammer, "grip at the start of MAIN");
      advance(state, ThorRules.DRAW_MAIN + ThorRules.DRAW_FINISH - 1, true);
      assertSame(StagedTimeline.Event.END, ThorActions.advance(state, true, false));
      assertFalse(state.timeline.running());
   }

   @Test
   void technicalHolsterReturnsMjolnirToTheBelt() {
      ThorState state = new ThorState();
      state.hammer = HammerState.IN_HAND;
      assertSame(ThorActions.Refusal.NONE, ThorActions.start(state, ThorMove.HOLSTER, true));
      advance(state, ThorRules.HOLSTER_INTRO, true);
      assertSame(HammerState.HOLSTERED, state.hammer);
   }

   @Test
   void anItemTakenBeforeTheGripCancelsTheDraw() {
      ThorState state = new ThorState();
      ThorActions.start(state, ThorMove.DRAW, true);
      advance(state, 1, false);
      assertFalse(state.timeline.running());
      assertSame(HammerState.HOLSTERED, state.hammer);
   }

   @Test
   void mjolnirNeverSharesTheHandWithAnItem() {
      ThorState state = new ThorState();
      state.hammer = HammerState.IN_HAND;
      ThorActions.advance(state, false, false);
      assertSame(HammerState.HOLSTERED, state.hammer);
      assertSame(HammerState.THROWN, ThorActions.settle(HammerState.THROWN, false), "a thrown hammer is not in the hand");
   }

   @Test
   void controlAndLifecycleStopTheMoveAndDropTheBridgeFlags() {
      ThorState state = new ThorState();
      ThorActions.start(state, ThorMove.DRAW, true);
      ThorActions.advance(state, true, true);
      assertFalse(state.timeline.running());

      state.hammer = HammerState.IN_HAND;
      ThorActions.start(state, ThorInput.HAMMER, ThorMove.HOLSTER, true);
      ThorBridge.openGrabWindow(state, 10);
      ThorBridge.lockScene(state, "A");
      ThorActions.stop(state);
      assertFalse(state.timeline.running());
      assertSame(HammerState.IN_HAND, state.hammer, "stopping never teleports the hammer");
      assertFalse(ThorBridge.grabWindowOpen(state));
      assertFalse(ThorBridge.sceneLocked(state));
      assertNull(ThorActions.release(state, ThorInput.HAMMER), "a stop forgets held presses");
   }

   @Test
   void unauthoredMovesNeverTouchTheTimelineOrTheRelease() {
      ThorState state = new ThorState();
      for (ThorMove move : ThorMove.values()) {
         if (!move.authored()) {
            assertSame(ThorActions.Refusal.NOT_READY, ThorActions.start(state, ThorInput.HAMMER, move, true), move.name());
            assertFalse(state.timeline.running(), move.name());
            assertNull(ThorActions.release(state, ThorInput.HAMMER), move + ": a refused press has no release");
         }
      }
   }

   @Test
   void theReleaseReachesTheMoveChosenOnThePressWithItsHeldTicks() {
      ThorMoves.register("A", ThorMove.HAMMER_THROW, new ThorMoveDefinition(20, 2, 2, null, 1), ThorMoveHandler.NONE);
      ThorState state = new ThorState();
      state.hammer = HammerState.IN_HAND;
      ThorContext context = ThorContext.grounded(state.hammer, state.mode);
      ThorMove pressed = ThorControls.resolve(ThorInput.HAMMER, context);
      assertSame(ThorMove.HAMMER_THROW, pressed);
      assertSame(ThorActions.Refusal.NONE, ThorActions.start(state, ThorInput.HAMMER, pressed, true));
      advance(state, 7, true);
      // The context changes while held (an enemy walks in): the release still goes to the throw.
      assertSame(ThorMove.HAMMER_STRIKE, ThorControls.resolve(ThorInput.HAMMER, new ThorContext(state.hammer, state.mode, true, false, false, false, true, false)));
      assertEquals(new ThorActions.Release(ThorMove.HAMMER_THROW, 7), ThorActions.release(state, ThorInput.HAMMER));
      assertNull(ThorActions.release(state, ThorInput.HAMMER), "one release per press");
      assertNull(ThorActions.release(state, ThorInput.STRIKE), "other inputs are independent");
   }

   @Test
   void theReleaseCallsOnlyThePressedMovesHandler() {
      List<String> calls = new ArrayList<>();
      ThorMoves.register("A", ThorMove.HAMMER_THROW, new ThorMoveDefinition(20, 2, 2, null, 1), new ThorMoveHandler() {
         @Override
         public void onRelease(ThorMoveContext context, int heldTicks) {
            calls.add(context.move() + ":" + heldTicks);
         }
      });
      ThorMoves.register("A", ThorMove.HAMMER_STRIKE, new ThorMoveDefinition(1, 1, 1, null, 1), new ThorMoveHandler() {
         @Override
         public void onRelease(ThorMoveContext context, int heldTicks) {
            calls.add("strike must not charge");
         }
      });
      ThorState state = new ThorState();
      ThorActions.start(state, ThorInput.HAMMER, ThorMove.HAMMER_THROW, true);
      advance(state, 3, true);
      ThorActions.release((ServerPlayer)null, state, ThorInput.HAMMER);
      ThorActions.release((ServerPlayer)null, state, ThorInput.HAMMER);
      assertEquals(List.of("HAMMER_THROW:3"), calls);
   }

   @Test
   void theGrabWindowCountsDownAndRoutesMmb() {
      ThorState state = new ThorState();
      ThorBridge.openGrabWindow(state, 2);
      assertSame(ThorMove.GRAB, ThorControls.resolve(ThorInput.HAMMER,
         new ThorContext(state.hammer, state.mode, true, false, false, false, true, ThorBridge.grabWindowOpen(state))));
      advance(state, 1, true);
      assertTrue(ThorBridge.grabWindowOpen(state));
      advance(state, 1, true);
      assertFalse(ThorBridge.grabWindowOpen(state));
   }
}
