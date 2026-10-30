package dev.baranhan.viltrumitecore.hero.ironman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterMotion;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterRules;
import dev.baranhan.viltrumitecore.hero.thor.ThorHero;
import org.junit.jupiter.api.Test;

/** Spec item 4 (2026-10-28): heavy jump >= 1.25 blocks, step 1.0, jump prep <= 4 ticks. */
class HulkbusterJumpTest {
   /** LivingEntity.travel: move, then gravity 0.08, then drag 0.98 (vanilla 1.20.1). */
   private static double apex(double vy) {
      double y = 0.0;
      double max = 0.0;
      for (int tick = 0; tick < 200 && (vy > 0.0 || y > 0.0); tick++) {
         y += vy;
         max = Math.max(max, y);
         vy = (vy - 0.08) * 0.98;
      }

      return max;
   }

   @Test
   void heavyJumpClearsMoreThanABlock() {
      assertEquals(1.2522, apex(0.42), 0.001, "model check: vanilla jump");
      double apex = apex(HulkbusterMotion.HEAVY_JUMP);
      assertTrue(apex >= 1.25, "heavy jump apex " + apex);
      assertTrue(apex < 2.0, "still a low heavy jump, not a jet: " + apex);
   }

   @Test
   void stepIsOneBlockLikeThor() {
      assertEquals(1.0, 0.6 + HulkbusterRules.STEP_BONUS, 1.0E-9);
      assertEquals(ThorHero.BODY_STEP_BONUS, HulkbusterRules.STEP_BONUS, 1.0E-9);
   }

   @Test
   void jumpPrepIsAtMostFourTicks() {
      assertTrue(HulkbusterMotion.worstJumpPrepTicks() <= 4, "longest tap " + HulkbusterMotion.worstJumpPrepTicks());
      assertTrue(HulkbusterMotion.jumpAtPress(false), "no jet charge possible: jump on the press (0 ticks)");
      assertFalse(HulkbusterMotion.jumpAtPress(true), "jet possible: a tap is told from a hold on release");
   }
}
