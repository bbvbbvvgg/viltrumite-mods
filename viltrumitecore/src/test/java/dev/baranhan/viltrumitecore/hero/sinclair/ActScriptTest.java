package dev.baranhan.viltrumitecore.hero.sinclair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.sinclair.act.ActScript;
import org.junit.jupiter.api.Test;

/** §6.1 key ticks of the six numbers. */
class ActScriptTest {
   @Test
   void moveLengthsMatchScripts() {
      for (ActShape shape : ActShape.values()) {
         assertEquals(ActScript.of(shape).length(), SinclairMove.act(shape).length(), shape.key());
         assertTrue(SinclairMove.act(shape).isAct());
         assertTrue(SinclairMove.act(shape).locksCasting());
      }
   }

   @Test
   void tablesFollowTheSpec() {
      assertEquals(12, ActScript.BEAM.reveal());
      assertEquals(70, ActScript.BEAM.length());
      assertEquals(50, ActScript.BEAM.bowStart());
      assertEquals(24, ActScript.NOVA.reveal());
      assertEquals(64, ActScript.NOVA.length());
      assertEquals(44, ActScript.NOVA_CATCH);
      assertEquals(20, ActScript.CUT.reveal());
      assertEquals(60, ActScript.CUT.length());
      assertEquals(62, ActScript.BARRAGE.length());
      assertEquals(50, ActScript.AEGIS.reveal());
      assertEquals(78, ActScript.AEGIS.length());
      assertEquals(44, ActScript.FINALE.reveal());
      assertEquals(72, ActScript.FINALE.length());
   }

   @Test
   void beamPulsesEveryFourTicks() {
      int pulses = 0;
      for (int t = 0; t < 80; t++) {
         if (ActScript.beamPulse(t)) {
            pulses++;
            assertTrue(t >= 12 && t < 42);
         }
      }

      assertEquals(8, pulses);
      // ≈ 9 damage in total at nominal power (spec table).
      assertEquals(9.6F, pulses * ActScript.BEAM_DAMAGE, 1.0E-4F);
   }

   @Test
   void barrageAndFinaleBeats() {
      assertTrue(ActScript.barrageVolley(10) && ActScript.barrageVolley(18) && ActScript.barrageVolley(26));
      assertFalse(ActScript.barrageVolley(11));
      assertEquals(9, ActScript.BARRAGE_VOLLEYS.length * ActScript.BARRAGE_CARDS_PER_VOLLEY);
      assertEquals(16.2F, 9 * ActScript.BARRAGE_DAMAGE, 1.0E-4F);
      assertTrue(ActScript.finaleSword(18) && ActScript.finaleSword(26) && ActScript.finaleSword(34));
      assertFalse(ActScript.finaleSword(19));
   }

   @Test
   void pauseAndBowAreOrdered() {
      for (ActShape shape : ActShape.values()) {
         ActScript s = ActScript.of(shape);
         assertTrue(s.hushStart() <= s.reveal(), shape.key());
         assertTrue(s.pauseStart() <= s.pauseEnd(), shape.key());
         assertTrue(s.pauseEnd() <= s.bowStart(), shape.key());
         assertTrue(s.bowStart() < s.length(), shape.key());
         assertTrue(s.bowing(s.length() - 1));
         assertFalse(s.bowing(s.length()));
      }
   }
}
