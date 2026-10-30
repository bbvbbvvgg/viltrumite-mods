package dev.baranhan.viltrumitecore.client.sinclair.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.sinclair.act.ActScript;
import org.junit.jupiter.api.Test;

/** Pure parts of the act camera and the card layout (design 2026-11-02 §3.2, §2.4). */
class StageCameraTest {
   @Test
   void actCameraBlendsAndNeverJumps() {
      for (ActShape shape : ActShape.values()) {
         ActScript s = ActScript.of(shape);
         assertEquals(0.0, StageCamera.actWeight(s, 0), 1.0E-9);
         assertEquals(1.0, StageCamera.actWeight(s, s.main()), 1.0E-9);
         assertEquals(0.0, StageCamera.actWeight(s, s.length()), 1.0E-9);
         double prevD = StageCamera.actDistance(s, 0);
         double prevW = 0.0;
         for (double t = 0.0; t < s.length(); t += 0.25) {
            double d = StageCamera.actDistance(s, t);
            double w = StageCamera.actWeight(s, t);
            assertTrue(d >= 2.0 && d <= 5.0, shape + " distance " + d + " at " + t);
            assertTrue(Math.abs(d - prevD) < 0.35, shape + " distance jump at " + t);
            assertTrue(Math.abs(w - prevW) < 0.06, shape + " weight jump at " + t);
            prevD = d;
            prevW = w;
         }

         // push in through the hush, pull back on the reveal
         assertTrue(StageCamera.actDistance(s, s.reveal() - 0.01) < StageCamera.actDistance(s, s.hushStart()), shape.key());
         assertTrue(StageCamera.actDistance(s, s.reveal() + 3) > StageCamera.actDistance(s, s.reveal() - 0.01), shape.key());
         assertEquals(0.0, StageCamera.bowTurn(s, s.bowStart()), 1.0E-9);
         assertEquals(1.0, StageCamera.bowTurn(s, s.bowStart() + 8), 1.0E-9);
      }
   }

   @Test
   void cardFitsEveryGuiScale() {
      int[][] sizes = {{320, 240}, {427, 240}, {640, 360}, {960, 540}, {1920, 1080}, {240, 320}};
      for (int[] size : sizes) {
         float h = CopyCardRenderer.cardHeight(size[0], size[1]);
         assertTrue(h >= 60.0F, "readable minimum");
         assertTrue(h * 0.72F <= Math.max(size[0] * 0.5F, 60.0F * 0.72F) + 1.0E-3F, "never wider than half the screen");
         assertTrue(h <= size[1] * 0.62F + 1.0E-3F || h == 60.0F, "leaves room for chat and subtitles");
         assertTrue(h <= 240.0F);
      }
   }
}
