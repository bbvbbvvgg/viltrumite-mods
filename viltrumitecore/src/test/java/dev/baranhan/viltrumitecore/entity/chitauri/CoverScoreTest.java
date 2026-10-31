package dev.baranhan.viltrumitecore.entity.chitauri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoverScoreTest {
   @Test
   void halfCoverBeatsOpenGroundBeatsBlindSpots() {
      double half = CoverScore.score(true, true, 14, 4, 0);
      double open = CoverScore.score(false, true, 14, 4, 0);
      double blind = CoverScore.score(true, false, 14, 4, 0);
      double exposedBlind = CoverScore.score(false, false, 14, 4, 0);
      assertTrue(half > open);
      assertTrue(open > blind);
      assertTrue(blind > exposedBlind);
   }

   @Test
   void preferredRangeLongWalksAndFlankAngle() {
      double best = CoverScore.score(true, true, 14, 0, 0);
      assertTrue(best > CoverScore.score(true, true, 4, 0, 0), "too close");
      assertTrue(best > CoverScore.score(true, true, 34, 0, 0), "too far");
      assertEquals(best, CoverScore.score(true, true, CoverScore.PREFERRED_MIN, 0, 0), 1.0E-9);
      assertEquals(best, CoverScore.score(true, true, CoverScore.PREFERRED_MAX, 0, 0), 1.0E-9);
      assertTrue(best > CoverScore.score(true, true, 14, 12, 0), "long walk");
      assertTrue(best > CoverScore.score(true, true, 14, 0, 1.0), "off the flank");
      assertEquals(CoverScore.score(true, true, 14, 0, 0.7), CoverScore.score(true, true, 14, 0, -0.7), 1.0E-9);
   }

   @Test
   void squadMembersSpreadToAlternatingFlanks() {
      assertEquals(0.0, CoverScore.flankOffset(0));
      assertEquals(0.0, CoverScore.flankOffset(-3));
      for (int m = 1; m < 12; m += 2) {
         assertTrue(CoverScore.flankOffset(m) > 0.0);
         assertEquals(-CoverScore.flankOffset(m), CoverScore.flankOffset(m + 1), 1.0E-9);
         assertTrue(CoverScore.flankOffset(m) <= 2.4);
      }
      assertTrue(CoverScore.flankOffset(3) > CoverScore.flankOffset(1));
   }
}
