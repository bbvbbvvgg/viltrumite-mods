package dev.baranhan.viltrumitecore.hero.thor.hammer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.thor.HammerState;
import dev.baranhan.viltrumitecore.hero.thor.ThorActions;
import dev.baranhan.viltrumitecore.hero.thor.ThorContext;
import dev.baranhan.viltrumitecore.hero.thor.ThorControls;
import dev.baranhan.viltrumitecore.hero.thor.ThorInput;
import dev.baranhan.viltrumitecore.hero.thor.ThorMode;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The three A moves on the shared registry and their MMB / Y routing. */
class MjolnirModuleTest {
   @AfterEach
   void reset() {
      ThorMoves.resetForTests();
      ThorBridge.resetForTests();
   }

   @Test
   void sessionARegistersExactlyItsMoves() {
      MjolnirModule.registerMoves();
      for (ThorMove move : new ThorMove[]{ThorMove.HAMMER_THROW, ThorMove.RECALL, ThorMove.HAMMER_PULL}) {
         assertTrue(move.authored(), move.name());
         assertEquals(MjolnirWorld.OWNER, ThorMoves.owner(move));
         assertEquals(MjolnirModule.DEFINITIONS.get(move), ThorMoves.definition(move));
      }

      for (ThorMove move : new ThorMove[]{ThorMove.HAMMER_STRIKE, ThorMove.HAMMER_COMBO, ThorMove.FIST_COMBO, ThorMove.GRAB, ThorMove.WRATH}) {
         assertFalse(move.authored(), move + " belongs to B / C");
      }

      assertTrue(ThorActions.overlay(ThorMove.RECALL), "the recall never takes the timeline");
   }

   @Test
   void mmbIsClaimedForTheThrowAndTheRecallButNotForMelee() {
      MjolnirModule.registerMoves();
      ThorContext inHand = ThorContext.grounded(HammerState.IN_HAND, ThorMode.NORMAL);
      assertSame(ThorMove.HAMMER_THROW, ThorControls.resolve(ThorInput.HAMMER, inHand));
      assertTrue(ThorControls.claimedMouse(MouseButton.MIDDLE, inHand) != null);
      ThorContext lying = new ThorContext(HammerState.HOLSTERED, ThorMode.NORMAL, true, false, false, false, false, false, true, false);
      assertSame(ThorMove.RECALL, ThorControls.resolve(ThorInput.HAMMER, lying));
      assertTrue(ThorControls.claimedMouse(MouseButton.MIDDLE, lying) != null);
      ThorContext enemy = new ThorContext(HammerState.IN_HAND, ThorMode.NORMAL, true, false, false, false, true, false);
      assertEquals(null, ThorControls.claimedMouse(MouseButton.MIDDLE, enemy), "melee (B) stays vanilla until B registers it");
   }

   @Test
   void theChargeThresholdAndTheHoldCap() {
      assertFalse(MjolnirModule.charged(MjolnirTuning.CHARGE_TICKS - 1));
      assertTrue(MjolnirModule.charged(MjolnirTuning.CHARGE_TICKS));
      assertTrue(MjolnirTuning.CHARGE_TICKS < MjolnirTuning.THROW_HOLD_MAX, "a charge is reachable before the hold ends");
      assertEquals(MjolnirTuning.THROW_WINDUP + MjolnirTuning.THROW_HOLD_MAX + MjolnirTuning.THROW_FOLLOW,
         MjolnirModule.DEFINITIONS.get(ThorMove.HAMMER_THROW).length());
   }
}
