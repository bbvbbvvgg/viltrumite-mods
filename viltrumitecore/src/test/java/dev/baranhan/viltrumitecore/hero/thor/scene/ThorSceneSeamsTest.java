package dev.baranhan.viltrumitecore.hero.thor.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.thor.HammerState;
import dev.baranhan.viltrumitecore.hero.thor.ThorActions;
import dev.baranhan.viltrumitecore.hero.thor.ThorFlags;
import dev.baranhan.viltrumitecore.hero.thor.ThorMode;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveHandler;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Seams C added to the shared Thor surface: the grab window target, paired starts, the scene flag, the single resolver. */
class ThorSceneSeamsTest {
   @AfterEach
   void reset() {
      ThorMoves.resetForTests();
   }

   @Test
   void grabWindowRemembersItsTargetUntilItCloses() {
      ThorState state = new ThorState();
      assertEquals(-1, ThorBridge.grabTarget(state));
      ThorBridge.openGrabWindow(state, 20, 42);
      assertTrue(ThorBridge.grabWindowActive(state));
      assertEquals(42, ThorBridge.grabTarget(state));
      for (int i = 0; i < 20; i++) {
         ThorActions.advance(state, true, false);
      }
      assertFalse(ThorBridge.grabWindowActive(state));
      assertEquals(-1, ThorBridge.grabTarget(state), "an expired window has no target");
      ThorBridge.openGrabWindow(state, 20, 7);
      ThorBridge.closeGrabWindow(state);
      assertEquals(-1, state.grabTarget());
   }

   @Test
   void pairedMovesStartWithTheirPartnerOnly() {
      ThorMoves.register("C", ThorMove.GRAB, new ThorMoveDefinition(0, 100, 0, null, 1), ThorMoveHandler.NONE);
      ThorMoves.register("C", ThorMove.FATALITY, new ThorMoveDefinition(0, 100, 0, null, 1), ThorMoveHandler.NONE);
      ThorState state = new ThorState();
      assertSame(ThorActions.Refusal.BUSY, ThorActions.start(state, ThorMove.GRAB, true), "no window target, no partner");
      ThorBridge.openGrabWindow(state, 20, 42);
      assertSame(ThorActions.Refusal.NONE, ThorActions.start(state, ThorMove.GRAB, true));
      assertEquals(42, state.timeline.partnerId());
      state.timeline.clear();
      assertSame(ThorActions.Refusal.NONE, ThorActions.start(state, null, ThorMove.FATALITY, true, 9));
      assertEquals(9, state.timeline.partnerId());
   }

   @Test
   void sceneLockRefusesEveryPress() {
      ThorMoves.register("C", ThorMove.GRAB, new ThorMoveDefinition(0, 100, 0, null, 1), ThorMoveHandler.NONE);
      ThorState state = new ThorState();
      ThorBridge.lockScene(state, ThorScenes.OWNER);
      assertTrue(ThorScenes.sceneLocked(state));
      assertSame(ThorActions.Refusal.BUSY, ThorActions.start(state, null, ThorMove.GRAB, true, 5));
   }

   @Test
   void sceneFlagIsItsOwnBit() {
      int flags = ThorFlags.compose(HammerState.IN_HAND, ThorMode.ELECTRIC, true, true, false, true);
      assertFalse(ThorFlags.hammerDetached(flags), "the scene bit is not the detached-hammer bit");
      assertTrue(ThorFlags.scene(flags));
      assertTrue(ThorFlags.grabWindow(flags));
      assertTrue(ThorFlags.cameraLease(flags));
      assertSame(HammerState.IN_HAND, ThorFlags.hammer(flags));
      assertSame(ThorMode.ELECTRIC, ThorFlags.mode(flags));
      assertFalse(ThorFlags.scene(ThorFlags.compose(HammerState.IN_HAND, ThorMode.ELECTRIC, true, true)));
   }

   @Test
   void sceneMovesAreTheBridgeMoves() {
      assertSame(ThorMove.FATALITY, ThorScenes.FATALITY);
      assertSame(ThorMove.TELEPORT_SELECT, ThorScenes.TELEPORT);
      assertTrue(ThorScenes.GRAB.paired());
      assertEquals("C", ThorScenes.OWNER);
   }

   @Test
   void scenesNeverResolveMmbThemselves() throws Exception {
      Path dir = Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/thor/scene");
      try (var files = Files.walk(dir)) {
         for (Path file : (Iterable<Path>)files.filter(p -> p.toString().endsWith(".java"))::iterator) {
            String text = Files.readString(file);
            assertFalse(text.contains("ThorControls.hammer("), file + " calls the MMB resolver");
            assertFalse(text.contains("grabWindow ="), file + " writes the window outside the bridge");
         }
      }
   }

   @Test
   void transientGrabTargetIsDroppedOnLoad() {
      ThorState state = new ThorState();
      ThorBridge.openGrabWindow(state, 20, 3);
      state.load(new net.minecraft.nbt.CompoundTag());
      assertEquals(-1, state.grabTarget());
   }
}
