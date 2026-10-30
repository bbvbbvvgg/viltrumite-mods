package dev.baranhan.viltrumitecore.hero.sinclair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.sinclair.act.ActScript;
import org.junit.jupiter.api.Test;

/** Key ticks of the six numbers (design 2026-11-02 §3: tension → main → climax → finish; balance unchanged). */
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
   void tablesFollowTheDesign() {
      assertEquals(28, ActScript.BEAM.main());
      assertEquals(78, ActScript.BEAM.reveal());
      assertEquals(96, ActScript.BEAM.bowStart());
      assertEquals(120, ActScript.BEAM.length());
      assertEquals(52, ActScript.NOVA.reveal());
      assertEquals(104, ActScript.NOVA.length());
      assertEquals(74, ActScript.NOVA_CATCH);
      assertEquals(50, ActScript.CUT.reveal());
      assertEquals(100, ActScript.CUT.length());
      assertEquals(100, ActScript.BARRAGE.length());
      assertEquals(50, ActScript.AEGIS.reveal());
      assertEquals(96, ActScript.AEGIS.length());
      assertEquals(58, ActScript.FINALE.reveal());
      assertEquals(100, ActScript.FINALE.length());
      for (ActShape shape : ActShape.values()) {
         // every number is longer than its pass-1 version (60-78 ticks) and at most six seconds
         assertTrue(ActScript.of(shape).length() >= 96 && ActScript.of(shape).length() <= 120, shape.key());
      }
   }

   @Test
   void everyActHasFourParts() {
      for (ActShape shape : ActShape.values()) {
         ActScript s = ActScript.of(shape);
         assertEquals(ActScript.Part.TENSION, s.part(0), shape.key());
         assertEquals(ActScript.Part.MAIN, s.part(s.main()), shape.key());
         assertEquals(ActScript.Part.CLIMAX, s.part(s.hushStart()), shape.key());
         assertEquals(ActScript.Part.CLIMAX, s.part(s.reveal()), shape.key());
         assertEquals(ActScript.Part.FINISH, s.part(s.pauseEnd()), shape.key());
         assertEquals(ActScript.Part.FINISH, s.part(s.length() - 1), shape.key());
         assertTrue(s.main() >= 12, shape.key() + ": a real tension phase");
         assertTrue(s.hushStart() - s.main() >= 20, shape.key() + ": a real main phase");
      }
   }

   @Test
   void beamKeepsItsDamageBudget() {
      int pulses = 0;
      for (int t = 0; t < 130; t++) {
         if (ActScript.beamPulse(t)) {
            pulses++;
            assertTrue(t >= ActScript.BEAM_START && t < ActScript.BEAM_END);
         }
      }

      assertEquals(9, pulses);
      // pass 1: 8 × 1.2 = 9.6; pass 2: 9 × 0.75 + overdrive 2.85 = 9.6
      assertEquals(9.6F, pulses * ActScript.BEAM_DAMAGE + ActScript.BEAM_BURST_DAMAGE, 1.0E-4F);
      assertTrue(ActScript.beamLit(ActScript.BEAM_START) && ActScript.beamLit(ActScript.BEAM_BURST));
      assertFalse(ActScript.beamLit(ActScript.BEAM_START - 1) || ActScript.beamLit(ActScript.BEAM_BURST_END));
      assertTrue(ActScript.BEAM_END <= ActScript.BEAM.hushStart() && ActScript.BEAM_BURST == ActScript.BEAM.reveal());
   }

   @Test
   void barrageCutAndFinaleKeepTheirBudgets() {
      assertTrue(ActScript.barrageVolley(20) && ActScript.barrageVolley(28) && ActScript.barrageVolley(36) && ActScript.barrageVolley(50));
      assertFalse(ActScript.barrageVolley(21));
      assertTrue(ActScript.barrageFinale(50));
      assertFalse(ActScript.barrageFinale(36));
      assertEquals(16.2F, ActScript.BARRAGE_VOLLEYS.length * ActScript.BARRAGE_CARDS_PER_VOLLEY * ActScript.BARRAGE_DAMAGE, 1.0E-4F);
      assertEquals(ActScript.BARRAGE.reveal(), ActScript.BARRAGE_VOLLEYS[ActScript.BARRAGE_VOLLEYS.length - 1]);
      assertTrue(ActScript.cutSaw(30) && ActScript.cutSaw(38));
      assertFalse(ActScript.cutSaw(50));
      // pass 1: one 8.0 cut; pass 2: two 1.0 saw strokes + the 6.0 grand cut
      assertEquals(8.0F, ActScript.CUT_SAWS.length * ActScript.CUT_SAW_DAMAGE + ActScript.CUT_DAMAGE, 1.0E-4F);
      assertTrue(ActScript.finaleSword(30) && ActScript.finaleSword(38) && ActScript.finaleSword(46));
      assertFalse(ActScript.finaleSword(31));
      assertTrue(ActScript.FINALE_LAND < ActScript.FINALE_SWORDS[0]);
      assertTrue(ActScript.NOVA_HAT_OFF < ActScript.NOVA_THROW && ActScript.NOVA_THROW < ActScript.NOVA_ARRIVE
         && ActScript.NOVA_ARRIVE < ActScript.NOVA.reveal() && ActScript.NOVA.reveal() < ActScript.NOVA_CATCH
         && ActScript.NOVA_CATCH <= ActScript.NOVA.bowStart());
   }

   @Test
   void pauseAndBowAreOrdered() {
      for (ActShape shape : ActShape.values()) {
         ActScript s = ActScript.of(shape);
         assertTrue(s.main() <= s.hushStart(), shape.key());
         assertTrue(s.hushStart() <= s.reveal(), shape.key());
         assertTrue(s.pauseStart() <= s.pauseEnd(), shape.key());
         assertTrue(s.pauseEnd() <= s.bowStart(), shape.key());
         assertTrue(s.bowStart() < s.length(), shape.key());
         assertTrue(s.bowing(s.length() - 1));
         assertFalse(s.bowing(s.length()));
      }
   }
}
