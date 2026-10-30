package dev.baranhan.viltrumitecore.client.sinclair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairFlags;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairMove;
import dev.baranhan.viltrumitecore.hero.sinclair.act.ActScript;
import org.junit.jupiter.api.Test;

class SinclairPoseRulesTest {
   private static final int HAT_OFF = SinclairFlags.with(0, SinclairFlags.HAT_OFF, true);

   @Test
   void hexCastMovesTheHatToTheHand() {
      assertTrue(SinclairPoseRules.headHat(0, SinclairMove.HEX_CAST, 0));
      assertFalse(SinclairPoseRules.headHat(HAT_OFF, SinclairMove.HEX_CAST, 3));
      assertTrue(SinclairPoseRules.heldHat(HAT_OFF, SinclairMove.HEX_CAST, 3));
   }

   @Test
   void novaFlightHidesBothHats() {
      assertFalse(SinclairPoseRules.headHat(HAT_OFF, SinclairMove.ACT_NOVA, 20));
      assertFalse(SinclairPoseRules.heldHat(HAT_OFF, SinclairMove.ACT_NOVA, 20));
   }

   @Test
   void novaTensionSpinsTheHatInTheHand() {
      assertTrue(SinclairPoseRules.heldHat(HAT_OFF, SinclairMove.ACT_NOVA, ActScript.NOVA_HAT_OFF));
      assertTrue(SinclairPoseRules.heldHat(HAT_OFF, SinclairMove.ACT_NOVA, ActScript.NOVA_THROW - 1));
      assertFalse(SinclairPoseRules.headHat(HAT_OFF, SinclairMove.ACT_NOVA, 10));
   }

   @Test
   void everyBowSweepsTheHatOffAndBack() {
      for (SinclairMove move : SinclairMove.values()) {
         if (!move.isAct()) {
            continue;
         }

         ActScript script = SinclairPoseRules.script(move);
         int mid = (script.bowStart() + script.length()) / 2;
         assertTrue(SinclairPoseRules.heldHat(0, move, mid), move.name());
         assertFalse(SinclairPoseRules.headHat(0, move, mid), move.name());
         assertTrue(SinclairPoseRules.headHat(0, move, script.length() - 1), move.name());
         assertEquals(move.length(), script.length(), "clip and script agree for " + move);
      }
   }

   @Test
   void cardShowsInTheWindowCaptureBeamAndFan() {
      assertTrue(SinclairPoseRules.card(SinclairFlags.with(0, SinclairFlags.CARD_HELD, true), null, 0));
      assertFalse(SinclairPoseRules.card(0, SinclairMove.CAPTURE, 10));
      assertTrue(SinclairPoseRules.card(0, SinclairMove.CAPTURE, 18));
      assertTrue(SinclairPoseRules.card(0, SinclairMove.ACT_BEAM, 20));
      // pass 2: the card stays up through the charge and the overdrive, then is pocketed
      assertTrue(SinclairPoseRules.card(0, SinclairMove.ACT_BEAM, ActScript.BEAM_END));
      assertFalse(SinclairPoseRules.card(0, SinclairMove.ACT_BEAM, ActScript.BEAM_BURST_END));
      assertTrue(SinclairPoseRules.card(0, SinclairMove.COPY, 0));
      assertTrue(SinclairPoseRules.card(0, SinclairMove.COPY, 30));
      assertFalse(SinclairPoseRules.card(0, SinclairMove.ACT_CUT, 5));
      assertFalse(SinclairPoseRules.card(0, null, 0));
   }

   @Test
   void actsOwnTheBodyFromTheFirstFrame() {
      assertEquals(1.0F, SinclairPoseRules.moveWeight(SinclairMove.ACT_FINALE, 0.0, 3.6), 1.0E-6F);
      assertEquals(0.0F, SinclairPoseRules.moveWeight(SinclairMove.BOLT_CAST, 0.0, 0.7), 1.0E-6F);
      assertEquals(1.0F, SinclairPoseRules.moveWeight(SinclairMove.BOLT_CAST, 0.35, 0.7), 1.0E-6F);
      assertEquals(0.0F, SinclairPoseRules.moveWeight(null, 0.2, 1.0), 1.0E-6F);
      assertEquals(0.0F, SinclairPoseRules.moveWeight(SinclairMove.SNAP, 2.0, 0.4), 1.0E-6F);
   }

   @Test
   void shapesMapOneToOne() {
      assertEquals(ActShape.BEAM, SinclairPoseRules.shape(SinclairMove.ACT_BEAM));
      assertEquals(ActShape.FINALE, SinclairPoseRules.shape(SinclairMove.ACT_FINALE));
      for (ActShape shape : ActShape.values()) {
         assertEquals(shape, SinclairPoseRules.shape(SinclairMove.act(shape)));
      }
   }

   @Test
   void aimFadesAfterTheLastShot() {
      assertEquals(1.0F, SinclairPoseRules.aimWeight(true, 999), 1.0E-6F);
      assertEquals(1.0F, SinclairPoseRules.aimWeight(false, 3), 1.0E-6F);
      assertEquals(0.0F, SinclairPoseRules.aimWeight(false, 20), 1.0E-6F);
   }
}
