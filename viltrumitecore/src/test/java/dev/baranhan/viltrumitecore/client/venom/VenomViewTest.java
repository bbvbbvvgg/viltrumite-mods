package dev.baranhan.viltrumitecore.client.venom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.venom.VenomMove;
import dev.baranhan.viltrumitecore.hero.venom.VenomRules;
import org.junit.jupiter.api.Test;

/** The staged transformation and the living surface (spec §3, §5.1). */
class VenomViewTest {
   @Test
   void theCoatSpreadsThenTheBodyGrowsThenTheFaceForms() {
      VenomView.Look start = VenomView.look(false, VenomMove.TRANSFORM, 0);
      assertFalse(start.geo());
      assertEquals(0.0F, start.coat());
      float coat = -1;
      float growth = -1;
      float marks = -1;
      int stage = -1;
      for (double t = 0; t <= VenomRules.TRANSFORM_TICKS; t += 0.5) {
         VenomView.Look l = VenomView.look(false, VenomMove.TRANSFORM, t);
         assertTrue(l.coat() >= coat - 1.0E-6F && l.growth() >= growth - 1.0E-6F && l.marks() >= marks - 1.0E-6F, "never backwards at " + t);
         assertTrue(l.stage() >= stage);
         assertEquals(t >= VenomRules.STAGE_GROW, l.geo(), "the body takes over at the grow stage, t=" + t);
         coat = l.coat();
         growth = l.growth();
         marks = l.marks();
         stage = l.stage();
      }
      assertEquals(1.0F, VenomView.look(false, VenomMove.TRANSFORM, VenomRules.STAGE_FACE).growth(), 1.0E-6F);
      assertEquals(0.0F, VenomView.look(false, VenomMove.TRANSFORM, VenomRules.STAGE_FACE).marks(), 1.0E-6F);
      assertEquals(1.0F, VenomView.look(false, VenomMove.TRANSFORM, VenomRules.STAGE_POSE).marks(), 1.0E-6F);
      assertEquals(4, VenomView.look(false, VenomMove.TRANSFORM, VenomRules.STAGE_POSE).stage());
   }

   @Test
   void theRevertRunsItBackwardsToTheBareHost() {
      VenomView.Look first = VenomView.look(true, VenomMove.REVERT, 0);
      assertTrue(first.geo());
      assertEquals(1.0F, first.marks(), 1.0E-6F);
      VenomView.Look end = VenomView.look(true, VenomMove.REVERT, VenomRules.REVERT_TICKS);
      assertFalse(end.geo());
      assertEquals(0.0F, end.coat(), 1.0E-6F, "nothing of the coat stays");
      assertFalse(VenomView.look(true, VenomMove.REVERT, VenomView.REVERT_GEO_END).geo());
   }

   @Test
   void outsideTheMovesTheFormDecides() {
      assertEquals(VenomView.Look.VENOM, VenomView.look(true, null, 0));
      assertEquals(VenomView.Look.HUMAN, VenomView.look(false, null, 0));
      assertEquals(VenomView.Look.VENOM, VenomView.look(true, VenomMove.CLAW_1, 3));
   }

   @Test
   void coatFramesAndTheirTexturesAreClamped() {
      assertEquals(0, VenomView.coatFrame(-1.0F));
      assertEquals(VenomView.COAT_FRAMES, VenomView.coatFrame(2.0F));
      assertEquals(VenomView.COAT_FRAMES / 2, VenomView.coatFrame(0.5F));
      assertEquals("textures/entity/venom/coat/coat_0.png", VenomView.coat(-5).getPath());
      assertEquals("textures/entity/venom/coat/coat_" + VenomView.COAT_FRAMES + ".png", VenomView.coat(99).getPath());
   }

   @Test
   void theSurfaceBeatsHarderInRageAndBoilsWhenHurt() {
      double calm = 0;
      double rage = 0;
      for (double s = 0; s < 10; s += 0.02) {
         float p = VenomView.pulse(s, false, 0);
         float r = VenomView.pulse(s, true, 0);
         assertTrue(p >= 0 && p <= 1 && r >= 0 && r <= 1);
         calm += p;
         rage += r;
         assertTrue(Math.abs(VenomView.swell(s, false)) <= 0.022F + 1.0E-6F);
         assertTrue(Math.abs(VenomView.swell(s, true)) <= 0.035F + 1.0E-6F);
      }
      assertTrue(rage > calm, "rage is brighter");
      assertTrue(VenomView.pulse(0.5, false, 10) >= 0.9F, "a fresh hit boils");
   }
}
