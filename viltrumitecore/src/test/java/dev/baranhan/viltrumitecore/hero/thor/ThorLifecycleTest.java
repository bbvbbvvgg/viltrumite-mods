package dev.baranhan.viltrumitecore.hero.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.combat.InterruptReason;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ReleaseCause;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvents;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorLifecycle;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveContext;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveHandler;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Round-1 lifecycle fixes (F-B1, F-B2, F-M3, F-m2, F-M4): one start path
 * (ThorBridge.start → ThorActions.begin) with a fixed handler order, a safe
 * interrupt, onStop never lost by the tick, the lifecycle stop order, and
 * forced releases.
 */
class ThorLifecycleTest {
   @AfterEach
   void reset() {
      ThorMoves.resetForTests();
      ThorEvents.resetForTests();
   }

   /** Records every handler call as "MOVE:call". */
   private static final class Recorder implements ThorMoveHandler {
      final List<String> calls;
      boolean admit = true;
      Consumer<ThorMoveContext> onStartAction = c -> { };

      Recorder(List<String> calls) {
         this.calls = calls;
      }

      @Override
      public boolean admits(ThorMoveContext context) {
         this.calls.add(context.move() + ":admits");
         return this.admit;
      }

      @Override
      public void onStart(ThorMoveContext context) {
         this.calls.add(context.move() + ":start");
         this.onStartAction.accept(context);
      }

      @Override
      public void onQueued(ThorMoveContext context) {
         this.calls.add(context.move() + ":queued:" + context.input());
      }

      @Override
      public void onPhase(ThorMoveContext context, StagedTimeline.Event event) {
         this.calls.add(context.move() + ":" + event);
      }

      @Override
      public void onRelease(ThorMoveContext context, int heldTicks) {
         this.calls.add(context.move() + ":release:" + heldTicks);
      }

      @Override
      public void onStop(ThorMoveContext context) {
         this.calls.add(context.move() + ":stop");
      }
   }

   private static Recorder register(List<String> calls, ThorMove move, ThorMoveDefinition definition) {
      Recorder recorder = new Recorder(calls);
      ThorMoves.register("T", move, definition, recorder);
      return recorder;
   }

   private static ThorActions.Refusal begin(ThorState state, ThorMove move) {
      return ThorActions.begin(null, state, move, -1, null, null, null);
   }

   private static void tick(ThorState state) {
      ThorActions.tick(null, state, true, false);
   }

   @Test
   void anIntroLessStartHearsOnStartThenItsEntryPhase() {
      List<String> calls = new ArrayList<>();
      register(calls, ThorMove.ELECTRIC_MODE, new ThorMoveDefinition(0, 4, 0, MovePhase.MAIN, 1));
      assertSame(ThorActions.Refusal.NONE, begin(new ThorState(), ThorMove.ELECTRIC_MODE));
      assertEquals(List.of("ELECTRIC_MODE:admits", "ELECTRIC_MODE:start", "ELECTRIC_MODE:ENTER_MAIN"), calls);
   }

   @Test
   void aBufferedPressHearsOnlyOnQueuedAndItsStepBeginsWithChainThenEntry() {
      List<String> calls = new ArrayList<>();
      // Intro 0 per step: the buffered step starts in MAIN, so CHAIN is followed by ENTER_MAIN.
      register(calls, ThorMove.FIST_COMBO, new ThorMoveDefinition(0, 2, 2, MovePhase.FINISH, 4));
      ThorState state = new ThorState();
      ThorActions.begin(null, state, ThorMove.FIST_COMBO, -1, ThorInput.STRIKE, null, null);
      calls.clear();
      ThorActions.begin(null, state, ThorMove.FIST_COMBO, -1, ThorInput.STANCE, null, null);
      assertEquals(List.of("FIST_COMBO:admits", "FIST_COMBO:queued:STANCE"), calls, "QUEUE never reaches onStart (F-M3)");
      calls.clear();
      for (int i = 0; i < 3; i++) {
         tick(state);
      }

      assertEquals(List.of("FIST_COMBO:ENTER_FINISH", "FIST_COMBO:CHAIN", "FIST_COMBO:ENTER_MAIN"), calls);
      assertEquals(1, state.timeline.step());
   }

   @Test
   void anImmediateChainIsAStartAndASameMoveRestartStopsFirst() {
      List<String> calls = new ArrayList<>();
      register(calls, ThorMove.FIST_COMBO, new ThorMoveDefinition(1, 1, 3, MovePhase.FINISH, 2));
      ThorState state = new ThorState();
      begin(state, ThorMove.FIST_COMBO);
      tick(state);
      tick(state);
      assertSame(MovePhase.FINISH, state.timeline.phase());
      calls.clear();
      begin(state, ThorMove.FIST_COMBO);
      assertEquals(List.of("FIST_COMBO:admits", "FIST_COMBO:start"), calls, "a chain step in the window: onStart, no onStop");
      assertEquals(1, state.timeline.step());

      tick(state);
      tick(state);
      calls.clear();
      // Last step in its window: the same move restarts at step 0 (INTERRUPT) — onStop, then onStart.
      begin(state, ThorMove.FIST_COMBO);
      assertEquals(List.of("FIST_COMBO:admits", "FIST_COMBO:stop", "FIST_COMBO:start"), calls);
      assertEquals(0, state.timeline.step());
   }

   @Test
   void aCutInStopsThePreviousMoveBeforeTheNewOneStarts() {
      List<String> calls = new ArrayList<>();
      register(calls, ThorMove.ELECTRIC_WAVE, new ThorMoveDefinition(7, 3, 8, MovePhase.FINISH, 1));
      register(calls, ThorMove.ELECTRIC_STORM, new ThorMoveDefinition(0, 100, 12, null, 1));
      ThorState state = new ThorState();
      begin(state, ThorMove.ELECTRIC_WAVE);
      calls.clear();
      // ElectricMoves.startStorm: the storm replaces the wave through the one start path (WAVE used to miss onStop).
      assertSame(ThorActions.Refusal.NONE, begin(state, ThorMove.ELECTRIC_STORM));
      assertEquals(List.of("ELECTRIC_STORM:admits", "ELECTRIC_WAVE:stop", "ELECTRIC_STORM:start", "ELECTRIC_STORM:ENTER_MAIN"), calls);
   }

   @Test
   void theGateRunsBeforeTheOwnersAdmitsAndADeclineIsQuiet() {
      List<String> calls = new ArrayList<>();
      Recorder wrath = register(calls, ThorMove.WRATH, new ThorMoveDefinition(2, 2, 2, null, 1));
      ThorState state = new ThorState();
      ThorBridge.lockScene(state, "C");
      assertSame(ThorActions.Refusal.BUSY, begin(state, ThorMove.WRATH));
      assertTrue(calls.isEmpty(), "a scene lock refuses before the owner's cooldown message (F-M7)");
      ThorBridge.unlockScene(state, "C");
      wrath.admit = false;
      assertSame(ThorActions.Refusal.DECLINED, begin(state, ThorMove.WRATH));
      assertEquals(List.of("WRATH:admits"), calls);
      assertFalse(state.timeline.running());
   }

   @Test
   void cuttingStopsAnyRunningMoveWhateverItsPriority() {
      List<String> calls = new ArrayList<>();
      register(calls, ThorMove.WRATH, new ThorMoveDefinition(24, 8, 20, null, 1));
      register(calls, ThorMove.TELEPORT_SELECT, new ThorMoveDefinition(0, 1200, 0, null, 1));
      ThorState state = new ThorState();
      begin(state, ThorMove.WRATH);
      assertSame(ThorActions.Refusal.BUSY, begin(state, ThorMove.TELEPORT_SELECT), "lower priority without a cut");
      calls.clear();
      assertSame(ThorActions.Refusal.NONE, ThorActions.begin(null, state, ThorMove.TELEPORT_SELECT, -1, null, null, InterruptReason.INTERACTION));
      assertEquals(List.of("TELEPORT_SELECT:admits", "WRATH:stop", "TELEPORT_SELECT:start", "TELEPORT_SELECT:ENTER_MAIN"), calls);
   }

   @Test
   void theSafeInterruptCallsOnStopAndLeavesOtherOwnersLocks() {
      List<String> calls = new ArrayList<>();
      register(calls, ThorMove.GRAB, new ThorMoveDefinition(0, 1200, 0, null, 1));
      ThorState state = new ThorState();
      ThorActions.begin(null, state, ThorMove.GRAB, 42, null, null, null);
      ThorBridge.lockScene(state, "C");
      ThorBridge.acquireCamera(state, "C");
      calls.clear();
      assertTrue(ThorBridge.interrupt(null, state, InterruptReason.HIT));
      assertEquals(List.of("GRAB:stop"), calls);
      assertTrue(ThorBridge.sceneLocked(state), "the lock belongs to its owner");
      assertTrue(ThorBridge.cameraLeased(state), "the lease belongs to its owner");
      assertFalse(ThorBridge.interrupt(null, state, InterruptReason.HIT), "nothing runs: no second onStop");
      tick(state);
      assertEquals(List.of("GRAB:stop"), calls, "the tick owes nothing");
   }

   @Test
   void aStopThatBypassedTheApiStillHearsOnStopOnceOnTheNextTick() {
      List<String> calls = new ArrayList<>();
      register(calls, ThorMove.HAMMER_PULL, new ThorMoveDefinition(2, 20, 6, MovePhase.MAIN, 1));
      ThorState state = new ThorState();
      begin(state, ThorMove.HAMMER_PULL);
      calls.clear();
      state.timeline.interrupt(InterruptReason.HIT);
      tick(state);
      assertEquals(List.of("HAMMER_PULL:stop"), calls, "F-B2: the remembered move is compared, not the pre-advance read");
      tick(state);
      assertEquals(List.of("HAMMER_PULL:stop"), calls);
   }

   @Test
   void aHandlerEndingItselfInOnStartHearsOneStopAndNoEntryPhase() {
      List<String> calls = new ArrayList<>();
      Recorder storm = register(calls, ThorMove.ELECTRIC_STORM, new ThorMoveDefinition(0, 100, 12, null, 1));
      storm.onStartAction = context -> ThorBridge.end(context.player(), context.state(), context.move());
      ThorState state = new ThorState();
      begin(state, ThorMove.ELECTRIC_STORM);
      tick(state);
      assertEquals(List.of("ELECTRIC_STORM:admits", "ELECTRIC_STORM:start", "ELECTRIC_STORM:stop"), calls);
   }

   @Test
   void theOverlayHearsOnlyOnStartBesideTheRunningMove() {
      List<String> calls = new ArrayList<>();
      register(calls, ThorMove.RECALL, new ThorMoveDefinition(0, 1, 0, MovePhase.INTRO, 1));
      register(calls, ThorMove.FIST_COMBO, new ThorMoveDefinition(3, 2, 6, MovePhase.FINISH, 8));
      ThorState state = new ThorState();
      begin(state, ThorMove.FIST_COMBO);
      calls.clear();
      assertSame(ThorActions.Refusal.NONE, ThorActions.begin(null, state, ThorMove.RECALL, -1, ThorInput.HAMMER, null, null));
      assertEquals(List.of("RECALL:admits", "RECALL:start"), calls);
      assertSame(ThorMove.FIST_COMBO, state.timeline.move());
      assertNull(ThorActions.release(state, ThorInput.HAMMER));
   }

   @Test
   void theLifecycleStopTellsTheMoveThenTheOwnersThenDropsTheFlags() {
      List<String> calls = new ArrayList<>();
      ThorState state = new ThorState();
      ThorMoves.register("T", ThorMove.GRAB, new ThorMoveDefinition(0, 1200, 0, null, 1), new ThorMoveHandler() {
         @Override
         public void onStop(ThorMoveContext context) {
            calls.add("onStop lock=" + context.state().sceneLock);
         }
      });
      ThorLifecycle.subscribe("B", (player, s, cause) -> calls.add("B " + cause + " lock=" + s.sceneLock + " running=" + s.timeline.running()));
      ThorActions.begin(null, state, ThorMove.GRAB, 7, null, null, null);
      ThorBridge.lockScene(state, "C");
      ThorBridge.openGrabWindow(state, 10, 7);
      ThorActions.stop(null, state, ThorLifecycle.Cause.DIMENSION_CHANGE);
      assertEquals(List.of("onStop lock=C", "B DIMENSION_CHANGE lock=C running=false"), calls, "F-m2: onStop before the flags drop");
      assertFalse(ThorBridge.sceneLocked(state));
      assertFalse(ThorBridge.grabWindowActive(state));
      tick(state);
      assertEquals(2, calls.size(), "no second onStop from the tick");
   }

   @Test
   void aThrowingLifecycleListenerNeverStopsTheOthers() {
      List<String> calls = new ArrayList<>();
      ThorLifecycle.subscribe("X", (player, s, cause) -> {
         throw new IllegalStateException("X broke");
      });
      ThorLifecycle.subscribe("B", (player, s, cause) -> calls.add("B"));
      ThorActions.stop(null, new ThorState(), ThorLifecycle.Cause.DEATH);
      assertEquals(List.of("B"), calls);
   }

   @Test
   void aForcedReleaseNeverReachesThePlayersRelease() {
      List<String> calls = new ArrayList<>();
      register(calls, ThorMove.HAMMER_THROW, new ThorMoveDefinition(4, 40, 8, MovePhase.FINISH, 1));
      ThorState state = new ThorState();
      ThorActions.begin(null, state, ThorMove.HAMMER_THROW, -1, ThorInput.HAMMER, null, null);
      tick(state);
      calls.clear();
      ThorActions.release(null, state, ThorInput.HAMMER, ReleaseCause.FORCED);
      assertTrue(calls.isEmpty(), "F-M4: the default drops a forced release (no throw from a corpse)");
      assertNull(ThorActions.release(state, ThorInput.HAMMER), "the press is consumed either way");

      ThorState other = new ThorState();
      ThorActions.begin(null, other, ThorMove.HAMMER_THROW, -1, ThorInput.HAMMER, null, null);
      calls.clear();
      ThorActions.release(null, other, ThorInput.HAMMER, ReleaseCause.PLAYER);
      assertEquals(List.of("HAMMER_THROW:release:0"), calls);
   }

   @Test
   void theCloneKeepsTheHammerProjectionAndNothingElse() {
      ThorState state = new ThorState();
      state.hammer = HammerState.IN_HAND;
      state.mode = ThorMode.ELECTRIC;
      ThorBridge.lockScene(state, "C");
      ThorState copy = state.respawnCopy();
      assertSame(HammerState.IN_HAND, copy.hammer, "F-M11: an End return never strands Mjölnir on the belt");
      assertFalse(copy.hammerDetached);
      assertSame(ThorMode.NORMAL, copy.mode);
      assertFalse(ThorBridge.sceneLocked(copy));
      state.hammer = HammerState.THROWN;
      state.hammerDetached = true;
      assertTrue(state.respawnCopy().hammerDetached);
   }
}
