package dev.baranhan.viltrumitecore.hero.thor.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.HammerState;
import dev.baranhan.viltrumitecore.hero.thor.ThorActions;
import dev.baranhan.viltrumitecore.hero.thor.ThorMode;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.dev.ThorDevCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The no-op session bridge: read-only view, grab window, scene lock, typed events with dedupe, dev gate. */
class ThorBridgeTest {
   private static final UUID THOR = UUID.fromString("00000000-0000-0000-0000-000000000001");

   @AfterEach
   void reset() {
      ThorEvents.resetForTests();
      ThorMoves.resetForTests();
   }

   private static ThorEvent event(long id, ThorEventType type) {
      return new ThorEvent(id, type, THOR, null, ThorMove.HAMMER_COMBO, 0);
   }

   @Test
   void viewIsAReadOnlyCopyOfTheState() {
      ThorState state = new ThorState();
      HammerView idle = ThorBridge.view(state);
      assertEquals(new HammerView(HammerState.HOLSTERED, ThorMode.NORMAL, null, null, 0, 0, null), idle);

      ThorActions.start(state, ThorMove.DRAW, true);
      ThorActions.advance(state, true, false);
      state.mode = ThorMode.ELECTRIC;
      HammerView running = ThorBridge.view(state);
      assertSame(ThorMove.DRAW, running.move());
      assertSame(MovePhase.INTRO, running.phase());
      assertEquals(1, running.moveElapsed());
      assertSame(ThorMode.ELECTRIC, running.mode());
      assertEquals(idle, new HammerView(HammerState.HOLSTERED, ThorMode.NORMAL, null, null, 0, 0, null), "an old view never changes");
   }

   @Test
   void grabWindowOpensExtendsAndCloses() {
      ThorState state = new ThorState();
      assertFalse(ThorBridge.grabWindowOpen(state));
      ThorBridge.openGrabWindow(state, 5);
      ThorBridge.openGrabWindow(state, 3);
      assertEquals(5, ThorBridge.view(state).grabWindowTicks(), "a shorter open never shortens the window");
      ThorBridge.closeGrabWindow(state);
      assertFalse(ThorBridge.view(state).grabWindowOpen());
      assertThrows(IllegalArgumentException.class, () -> ThorBridge.openGrabWindow(state, 0));
   }

   @Test
   void sceneLockHasOneOwner() {
      ThorState state = new ThorState();
      assertTrue(ThorBridge.lockScene(state, "A"));
      assertTrue(ThorBridge.lockScene(state, "A"), "re-entrant for its owner");
      assertFalse(ThorBridge.lockScene(state, "B"));
      assertFalse(ThorBridge.unlockScene(state, "B"), "only the owner unlocks");
      assertEquals("A", ThorBridge.view(state).sceneLockOwner());
      assertTrue(ThorBridge.unlockScene(state, "A"));
      assertFalse(ThorBridge.sceneLocked(state));
      assertThrows(IllegalArgumentException.class, () -> ThorBridge.lockScene(state, " "));
   }

   @Test
   void postingWithoutListenersIsANoOp() {
      for (ThorEventType type : ThorEventType.values()) {
         assertTrue(ThorEvents.owners(type).isEmpty(), type + ": the foundation listens to nothing");
         assertTrue(ThorEvents.post(event(ThorEvents.nextId(), type)));
      }
   }

   @Test
   void theSameEventIdIsDeliveredOnce() {
      List<Long> seen = new ArrayList<>();
      ThorEvents.subscribe("B", ThorEventType.HIT, e -> seen.add(e.id()));
      long id = ThorEvents.nextId();
      assertTrue(ThorEvents.post(event(id, ThorEventType.HIT)));
      assertFalse(ThorEvents.post(event(id, ThorEventType.HIT)), "duplicate");
      assertFalse(ThorEvents.post(event(id, ThorEventType.LETHAL)), "the id is the key, not the type");
      assertTrue(ThorEvents.post(event(ThorEvents.nextId(), ThorEventType.HIT)));
      assertEquals(2, seen.size());
      assertTrue(ThorEvents.nextId() > id, "ids grow");
   }

   @Test
   void dedupeForgetsOnlyBeyondItsWindow() {
      long first = ThorEvents.nextId();
      ThorEvents.post(event(first, ThorEventType.HIT));
      for (int i = 0; i < ThorEvents.DEDUPE_WINDOW - 1; i++) {
         ThorEvents.post(event(ThorEvents.nextId(), ThorEventType.HIT));
      }

      assertFalse(ThorEvents.post(event(first, ThorEventType.HIT)), "still inside the window");
      ThorEvents.post(event(ThorEvents.nextId(), ThorEventType.HIT));
      assertTrue(ThorEvents.post(event(first, ThorEventType.HIT)), "evicted after the window");
   }

   @Test
   void ownersListenIndependently() {
      List<String> calls = new ArrayList<>();
      ThorEvents.subscribe("A", ThorEventType.CATCH, e -> calls.add("A"));
      ThorEvents.subscribe("C", ThorEventType.CATCH, e -> {
         throw new IllegalStateException("C broke");
      });
      ThorEvents.subscribe("B", ThorEventType.CATCH, e -> calls.add("B"));
      ThorEvents.subscribe("B", ThorEventType.COMBO_END, e -> calls.add("B-combo"));
      assertThrows(IllegalStateException.class, () -> ThorEvents.subscribe("B", ThorEventType.CATCH, e -> { }), "one subscription per owner and type");
      assertEquals(List.of("A", "C", "B"), ThorEvents.owners(ThorEventType.CATCH));

      assertThrows(IllegalStateException.class, () -> ThorEvents.post(event(ThorEvents.nextId(), ThorEventType.CATCH)));
      assertEquals(List.of("A", "B"), calls, "a failing listener does not stop the others; other types are not called");
   }

   @Test
   void eventsAreValidated() {
      assertThrows(IllegalArgumentException.class, () -> new ThorEvent(1, null, THOR, null, null, 0));
      assertThrows(IllegalArgumentException.class, () -> new ThorEvent(1, ThorEventType.HIT, null, null, null, 0));
      assertThrows(IllegalArgumentException.class, () -> ThorEvents.subscribe("", ThorEventType.HIT, e -> { }));
      assertNull(new ThorEvent(1, ThorEventType.THROW, THOR, null, null, 0).target());
   }

   @Test
   void eventTypesAreAppendOnly() {
      assertEquals(List.of("HIT", "LETHAL", "COMBO_END", "THROW", "RECALL", "CATCH"),
         java.util.Arrays.stream(ThorEventType.values()).map(Enum::name).toList());
   }

   @Test
   void devCommandsOnlyOutsideProduction() {
      assertFalse(ThorDevCommands.enabled(true));
      assertTrue(ThorDevCommands.enabled(false));
      assertEquals(2, ThorDevCommands.PERMISSION, "operators only");
   }
}
