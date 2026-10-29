package dev.baranhan.viltrumitecore.hero.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvents;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorLifecycle;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorStateView;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Foundation m1 / A m6: no session can write another owner's Thor state behind its back. */
class ThorStateEncapsulationTest {
   @AfterEach
   void reset() {
      ThorMoves.resetForTests();
      ThorEvents.resetForTests();
      ThorLifecycle.resetForTests();
   }

   @Test
   void noPublicMutableField() {
      for (Field field : ThorState.class.getDeclaredFields()) {
         int mods = field.getModifiers();
         if (Modifier.isPublic(mods)) {
            assertTrue(Modifier.isFinal(mods), field.getName() + " is public and mutable");
         }
      }
   }

   @Test
   void hammerAndBridgeWritersAreClaimedOnce() {
      try {
         ThorState.claimHammerWriter("test-first");
      } catch (IllegalStateException alreadyHeld) {
         // The world store (MjolnirWorld) holds it when its class was loaded first.
      }

      assertThrows(IllegalStateException.class, () -> ThorState.claimHammerWriter("intruder"));
      ThorBridge.sceneLocked(new ThorState());
      assertThrows(IllegalStateException.class, () -> ThorState.claimBridgeWriter("intruder"));
      assertThrows(IllegalArgumentException.class, () -> ThorState.claimHammerWriter(" "));
   }

   @Test
   void bridgeOwnerChecksGuardEveryWrite() {
      ThorState state = new ThorState();
      assertTrue(ThorBridge.lockScene(state, "C"));
      assertFalse(ThorBridge.lockScene(state, "B"), "a foreign owner cannot take a held lock");
      assertFalse(ThorBridge.unlockScene(state, "B"), "nor drop it");
      assertTrue(ThorBridge.sceneLockedBy(state, "C"));
      assertFalse(ThorBridge.sceneLockedBy(state, "B"));
      assertTrue(ThorBridge.acquireCamera(state, "C"));
      assertFalse(ThorBridge.releaseCamera(state, "A"));
      assertEquals("C", state.cameraLease());
   }

   @Test
   void windowsCountDownAndForgetTheirTarget() {
      ThorState state = new ThorState();
      ThorBridge.openGrabWindow(state, 2, 42);
      ThorBridge.openComboWindow(state, ThorMove.HAMMER_COMBO, 1);
      state.tickWindows();
      assertEquals(42, ThorBridge.grabTarget(state));
      assertNull(ThorBridge.comboWindow(state), "the one-tick combo window closed");
      assertNull(state.comboMove());
      state.tickWindows();
      assertEquals(-1, ThorBridge.grabTarget(state));
      assertEquals(-1, state.grabTarget(), "an expired window forgets its target");
   }

   @Test
   void extendingAnUntargetedWindowNeverInventsATarget() {
      ThorState state = new ThorState();
      ThorBridge.openGrabWindow(state, 3);
      assertEquals(-1, ThorBridge.grabTarget(state));
      ThorBridge.openGrabWindow(state, 5, 7);
      ThorBridge.openGrabWindow(state, 2);
      assertEquals(7, ThorBridge.grabTarget(state), "extending keeps the target");
      assertEquals(5, state.grabWindowTicks(), "never shortened");
   }

   @Test
   void devResetGoesThroughOwnersBeforeFlagsDrop() {
      List<String> heard = new ArrayList<>();
      ThorLifecycle.subscribe("C", (player, s, cause) -> heard.add(cause + " lock=" + s.sceneLock() + " lease=" + s.cameraLease()));
      ThorState state = new ThorState();
      ThorBridge.lockScene(state, "C");
      ThorBridge.acquireCamera(state, "C");
      ThorBridge.openGrabWindow(state, 9, 3);
      ThorActions.stop(null, state, ThorLifecycle.Cause.DEV_RESET);
      assertEquals(List.of("DEV_RESET lock=C lease=C"), heard, "the owner hears the reset while it still holds its flags");
      assertNull(state.sceneLock());
      assertNull(state.cameraLease());
      assertEquals(0, state.grabWindowTicks());
   }

   @Test
   void readModelCarriesStepPartnerQueuedAndDetached() {
      ThorState state = new ThorState();
      ThorStateFixtures.detached(state, true);
      ThorStateView view = ThorBridge.state(state);
      assertTrue(view.hammerDetached());
      assertNull(view.queued());
      assertEquals(state.timeline.step(), view.step());
      assertEquals(state.timeline.partnerId(), view.partnerId());
      assertEquals(ThorStateView.idle(ThorMode.NORMAL).partnerId(), new ThorState().timeline.partnerId(), "idle() matches a fresh timeline");
   }

   @Test
   void setModeNeverStoresNull() {
      ThorState state = new ThorState();
      state.setMode(ThorMode.ELECTRIC);
      state.setMode(null);
      assertEquals(ThorMode.NORMAL, state.mode());
   }
}
