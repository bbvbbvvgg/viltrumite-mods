package dev.baranhan.viltrumitecore.client.sinclair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.sinclair.SinclairMove;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import org.junit.jupiter.api.Test;

/** The copied-act card stays up long enough to read (pure timing of {@link ActCardPopup}). */
class ActCardPopupTest {
   @Test
   void opensWhenTheCardComesOutOrIsHeld() {
      assertFalse(ActCardPopup.cardOut(SinclairMove.CAPTURE, SinclairRules.ULT_CARD_TICK - 1, false, true));
      assertTrue(ActCardPopup.cardOut(SinclairMove.CAPTURE, SinclairRules.ULT_CARD_TICK, false, true));
      assertTrue(ActCardPopup.cardOut(null, 0, true, true));
      assertFalse(ActCardPopup.cardOut(null, 0, true, false), "no act synced yet");
      assertFalse(ActCardPopup.cardOut(null, 0, false, true));
   }

   @Test
   void holdsAtLeastSixSecondsFullyVisible() {
      assertEquals(0.0F, ActCardPopup.alpha(-1.0F));
      assertEquals(0.5F, ActCardPopup.alpha(ActCardPopup.FADE_IN / 2.0F), 1.0E-4F);
      for (int t = ActCardPopup.FADE_IN; t < ActCardPopup.FADE_IN + ActCardPopup.HOLD; t++) {
         assertEquals(1.0F, ActCardPopup.alpha(t), "tick " + t);
      }

      assertTrue(ActCardPopup.HOLD >= 120, "at least 6 s to read");
      assertTrue(ActCardPopup.alpha(ActCardPopup.TOTAL - 1) > 0.0F);
      assertEquals(0.0F, ActCardPopup.alpha(ActCardPopup.TOTAL));
   }

   @Test
   void performingClosesIntoTheFadeOut() {
      assertEquals(ActCardPopup.FADE_IN + ActCardPopup.HOLD, ActCardPopup.closeEarly(10));
      assertEquals(ActCardPopup.TOTAL - 2, ActCardPopup.closeEarly(ActCardPopup.TOTAL - 2));
   }

   @Test
   void darkActColoursAreLiftedForTheName() {
      assertEquals(0xF2C14E, ActCardPopup.brighten(0xF2C14E));
      int lifted = ActCardPopup.brighten(0x3A1C8F);
      assertTrue(Math.max(lifted >> 16 & 0xFF, Math.max(lifted >> 8 & 0xFF, lifted & 0xFF)) >= 0xC0);
   }
}
