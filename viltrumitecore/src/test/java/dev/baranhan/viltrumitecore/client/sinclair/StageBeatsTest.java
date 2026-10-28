package dev.baranhan.viltrumitecore.client.sinclair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.sinclair.act.ActScript;
import org.junit.jupiter.api.Test;

class StageBeatsTest {
   @Test
   void everyActRevealShakesTheCamera() {
      for (ActShape shape : ActShape.values()) {
         ActScript script = ActScript.of(shape);
         float strongest = 0.0F;
         for (int t = 0; t < script.length(); t++) {
            strongest = Math.max(strongest, StageBeats.beat(shape, t)[0]);
         }

         assertTrue(strongest >= 0.25F, shape.name());
      }

      assertTrue(StageBeats.beat(ActShape.NOVA, ActScript.NOVA.reveal())[1] > 0.0F);
      assertTrue(StageBeats.beat(ActShape.FINALE, ActScript.FINALE.reveal())[1] > 0.0F);
      assertEquals(0.0F, StageBeats.beat(ActShape.CUT, 2)[0], 1.0E-6F);
   }

   @Test
   void silenceCoversHushAndPauseButNotTheBow() {
      ActScript cut = ActScript.CUT;
      assertTrue(StageBeats.quiet(cut, cut.hushStart()));
      assertTrue(StageBeats.quiet(cut, cut.pauseStart()));
      assertFalse(StageBeats.quiet(cut, cut.bowStart()));
      assertFalse(StageBeats.quiet(cut, 0));
   }
}
