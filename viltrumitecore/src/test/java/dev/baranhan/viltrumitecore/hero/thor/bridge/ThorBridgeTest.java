package dev.baranhan.viltrumitecore.hero.thor.bridge;

import dev.baranhan.viltrumitecore.hero.thor.ThorStateFixtures;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The no-op session bridge: hammer access, read-only state, grab window, scene lock, camera lease, events, dev registry. */
class ThorBridgeTest {
   private static final UUID THOR = UUID.fromString("00000000-0000-0000-0000-000000000001");

   @AfterEach
   void reset() {
      ThorEvents.resetForTests();
      ThorMoves.resetForTests();
      ThorBridge.resetForTests();
      ThorDevCommands.resetForTests();
   }

   private static ThorEvent event(long id, ThorEventType type) {
      return new ThorEvent(id, type, THOR, null, ThorMove.HAMMER_COMBO, 1, true, 4.0F, false);
   }

   @Test
   void foundationHammerAccessIsASafeReadOnlyDefault() {
      assertNull(ThorBridge.hammerAccessOwner());
      ThorState state = new ThorState();
      assertEquals(new HammerView(null, THOR, HammerState.HOLSTERED, false, null, null), ThorBridge.hammer(THOR, state));
      ThorStateFixtures.hammer(state, HammerState.IN_HAND);
      HammerView inHand = ThorBridge.hammer(THOR, state);
      assertTrue(inHand.inHand());
      assertNull(inHand.logicalId(), "no world hammer before A");
      assertSame(HammerState.HOLSTERED, ThorBridge.hammer(THOR, null).phase(), "unknown state reads as on the belt");
      ThorStateFixtures.hammer(state, HammerState.THROWN);
      assertTrue(ThorBridge.hammer(THOR, state).flying());
      assertFalse(inHand.flying(), "an old view never changes");
   }

   @Test
   void onlyOneSessionInstallsTheHammerAccess() {
      UUID logical = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
      ThorBridge.installHammerAccess("A", (thor, state) -> new HammerView(logical, thor, HammerState.THROWN, false, null, null));
      assertEquals("A", ThorBridge.hammerAccessOwner());
      assertEquals(logical, ThorBridge.hammer(THOR, new ThorState()).logicalId());
      assertThrows(IllegalStateException.class, () -> ThorBridge.installHammerAccess("B", HammerAccess.FOUNDATION));
      assertThrows(IllegalArgumentException.class, () -> new HammerView(null, THOR, null, false, null, null));
   }

   @Test
   void stateViewIsAReadOnlyCopy() {
      ThorState state = new ThorState();
      ThorStateView idle = ThorBridge.state(state);
      assertEquals(ThorStateView.idle(ThorMode.NORMAL), idle);

      ThorActions.start(state, ThorMove.DRAW, true);
      ThorActions.advance(state, true, false);
      state.setMode(ThorMode.ELECTRIC);
      ThorStateView running = ThorBridge.state(state);
      assertSame(ThorMove.DRAW, running.move());
      assertSame(MovePhase.INTRO, running.phase());
      assertEquals(1, running.moveElapsed());
      assertSame(ThorMode.ELECTRIC, running.mode());
      assertEquals(ThorStateView.idle(ThorMode.NORMAL), idle);
   }

   @Test
   void grabWindowOpensExtendsAndCloses() {
      ThorState state = new ThorState();
      assertFalse(ThorBridge.grabWindowActive(state));
      ThorBridge.openGrabWindow(state, 5);
      ThorBridge.openGrabWindow(state, 3);
      assertEquals(5, ThorBridge.state(state).grabWindowTicks(), "a shorter open never shortens the window");
      ThorBridge.closeGrabWindow(state);
      assertFalse(ThorBridge.state(state).grabWindowActive());
      assertThrows(IllegalArgumentException.class, () -> ThorBridge.openGrabWindow(state, 0));
   }

   @Test
   void sceneLockAndCameraLeaseHaveOneOwnerEachAndDropOnStop() {
      ThorState state = new ThorState();
      assertTrue(ThorBridge.lockScene(state, "C"));
      assertTrue(ThorBridge.lockScene(state, "C"), "re-entrant for its owner");
      assertFalse(ThorBridge.lockScene(state, "A"));
      assertFalse(ThorBridge.unlockScene(state, "A"), "only the owner unlocks");
      assertTrue(ThorBridge.acquireCamera(state, "C"));
      assertFalse(ThorBridge.acquireCamera(state, "B"));
      assertFalse(ThorBridge.releaseCamera(state, "B"));
      assertEquals("C", ThorBridge.state(state).cameraLeaseOwner());
      assertTrue(ThorBridge.releaseCamera(state, "C"));
      assertTrue(ThorBridge.acquireCamera(state, "C"));

      ThorActions.stop(state);
      assertFalse(ThorBridge.sceneLocked(state), "logout / death / dimension / hero change drop the lock");
      assertFalse(ThorBridge.cameraLeased(state), "and the camera lease");
      assertThrows(IllegalArgumentException.class, () -> ThorBridge.lockScene(state, " "));
      assertThrows(IllegalArgumentException.class, () -> ThorBridge.acquireCamera(state, null));
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
      ThorEvents.subscribe("C", ThorEventType.MELEE_HIT, e -> seen.add(e.eventId()));
      long id = ThorEvents.nextId();
      assertTrue(ThorEvents.post(event(id, ThorEventType.MELEE_HIT)));
      assertFalse(ThorEvents.post(event(id, ThorEventType.MELEE_HIT)), "duplicate");
      assertFalse(ThorEvents.post(event(id, ThorEventType.LETHAL_HIT)), "the id is the key, not the type");
      assertTrue(ThorEvents.post(event(ThorEvents.nextId(), ThorEventType.MELEE_HIT)));
      assertEquals(2, seen.size());
      assertTrue(ThorEvents.nextId() > id, "ids grow");
   }

   @Test
   void dedupeForgetsOnlyBeyondItsWindow() {
      long first = ThorEvents.nextId();
      ThorEvents.post(event(first, ThorEventType.MELEE_HIT));
      for (int i = 0; i < ThorEvents.DEDUPE_WINDOW - 1; i++) {
         ThorEvents.post(event(ThorEvents.nextId(), ThorEventType.MELEE_HIT));
      }

      assertFalse(ThorEvents.post(event(first, ThorEventType.MELEE_HIT)), "still inside the window");
      ThorEvents.post(event(ThorEvents.nextId(), ThorEventType.MELEE_HIT));
      assertTrue(ThorEvents.post(event(first, ThorEventType.MELEE_HIT)), "evicted after the window");
   }

   @Test
   void ownersListenIndependently() {
      List<String> calls = new ArrayList<>();
      ThorEvents.subscribe("B", ThorEventType.CATCH, e -> calls.add("B"));
      ThorEvents.subscribe("X", ThorEventType.CATCH, e -> {
         throw new IllegalStateException("X broke");
      });
      ThorEvents.subscribe("C", ThorEventType.CATCH, e -> calls.add("C"));
      ThorEvents.subscribe("C", ThorEventType.COMBO_END, e -> calls.add("C-combo"));
      assertThrows(IllegalStateException.class, () -> ThorEvents.subscribe("C", ThorEventType.CATCH, e -> { }), "one subscription per owner and type");
      assertEquals(List.of("B", "X", "C"), ThorEvents.owners(ThorEventType.CATCH));

      // F-M10: a failing listener is logged, never rethrown into the poster (damage path, hammer store transition).
      assertTrue(assertDoesNotThrow(() -> ThorEvents.post(event(ThorEvents.nextId(), ThorEventType.CATCH))));
      assertEquals(List.of("B", "C"), calls, "a failing listener does not stop the others; other types are not called");
   }

   @Test
   void hitEventsCarryTheHitTimeSnapshot() {
      List<ThorEvent> seen = new ArrayList<>();
      ThorEvents.subscribe("C", ThorEventType.LETHAL_HIT, seen::add);
      UUID victim = UUID.fromString("00000000-0000-0000-0000-000000000002");
      ThorEvents.post(new ThorEvent(ThorEvents.nextId(), ThorEventType.LETHAL_HIT, THOR, victim, ThorMove.HAMMER_COMBO, 3, true, 20.0F, false));
      assertEquals(1, seen.size());
      assertEquals(victim, seen.get(0).targetId());
      assertEquals(3, seen.get(0).attackId());
      assertTrue(seen.get(0).hammerInHand());
      assertThrows(IllegalArgumentException.class, () -> new ThorEvent(1, null, THOR, null, null, 0, false, 0, false));
      assertThrows(IllegalArgumentException.class, () -> new ThorEvent(1, ThorEventType.THROW, null, null, null, 0, false, 0, false));
      assertThrows(IllegalArgumentException.class, () -> ThorEvents.subscribe("", ThorEventType.THROW, e -> { }));
   }

   @Test
   void eventTypesAreAppendOnly() {
      assertEquals(List.of("MELEE_HIT", "LETHAL_HIT", "COMBO_END", "THROW", "RECALL", "CATCH", "HAMMER_STATE_CHANGED", "HAMMER_OWNER_CHANGED",
         "STRIKE_START", "STRIKE_END", "SCENE_START", "SCENE_END"),
         Arrays.stream(ThorEventType.values()).map(Enum::name).toList());
   }

   @Test
   void devRootIsSharedChildrenAreUniqueAndOnlyOutsideProduction() {
      assertFalse(ThorDevCommands.enabled(true));
      assertTrue(ThorDevCommands.enabled(false));
      assertEquals(2, ThorDevCommands.PERMISSION, "operators only");
      List<String> foundation = ThorDevCommands.paths();
      assertTrue(foundation.stream().allMatch(path -> path.startsWith("foundation ")), "the foundation only adds its fixtures");

      ThorDevCommands.registerChild("A", "debug hammer", node -> node);
      ThorDevCommands.registerChild("B", "debug  Combat", node -> node);
      ThorDevCommands.registerChild("C", "grab", node -> node);
      assertEquals("B", ThorDevCommands.owner("debug combat"));
      assertThrows(IllegalStateException.class, () -> ThorDevCommands.registerChild("C", "debug hammer", node -> node), "one owner per path");
      assertThrows(IllegalArgumentException.class, () -> ThorDevCommands.registerChild("C", "bad/path", node -> node));
      assertEquals(foundation.size() + 3, ThorDevCommands.paths().size());
   }
}
