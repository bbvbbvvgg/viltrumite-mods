package dev.baranhan.viltrumitecore.hero.ironman;

import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterMotion;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterRules;
import org.junit.jupiter.api.Test;

class HulkbusterJumpTest {
   private static double apex(double vy) {
      double y = 0.0, max = 0.0;
      for (int tick = 0; tick < 200 && (vy > 0.0 || y > 0.0); tick++) {
         y += vy; max = Math.max(max, y); vy = (vy - 0.08) * 0.98;
      }
      return max;
   }
   @Test void heavyJumpClearsOneBlock() {
      assertTrue(apex(HulkbusterMotion.HEAVY_JUMP) >= 1.25);
      assertTrue(apex(HulkbusterMotion.HEAVY_JUMP) < 2.0);
   }
   @Test void jumpDoesNotWaitWhenJetChargeIsImpossible() {
      assertTrue(HulkbusterMotion.worstJumpPrepTicks() <= 4);
      assertTrue(HulkbusterMotion.jumpAtPress(false));
      assertFalse(HulkbusterMotion.jumpAtPress(true));
      assertEquals(0.4, HulkbusterRules.STEP_BONUS);
   }
}
