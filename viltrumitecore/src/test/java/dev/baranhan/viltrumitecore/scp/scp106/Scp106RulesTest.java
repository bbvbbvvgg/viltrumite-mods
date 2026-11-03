package dev.baranhan.viltrumitecore.scp.scp106;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Scp106RulesTest {
   @Test
   void corrosionDecaysBetweenSixAndTenMinutes() {
      assertEquals(7_200, Scp106Rules.corrosionLifetime(0));
      assertEquals(12_000, Scp106Rules.corrosionLifetime(1));
      assertEquals(12_000, Scp106Rules.corrosionLifetime(5));
   }

   @Test
   void naturalChanceIsRareAndDoubledAtNight() {
      assertEquals(0.0075, Scp106Rules.naturalChance(0.75, false), 1e-9);
      assertEquals(0.015, Scp106Rules.naturalChance(0.75, true), 1e-9);
      assertEquals(1.0, Scp106Rules.naturalChance(80, true), 1e-9);
      assertEquals(0.0, Scp106Rules.naturalChance(-3, true), 1e-9);
      assertEquals(288_000L, Scp106Rules.cooldownTicks(240));
      assertEquals(6_000, Scp106Rules.checkTicks(300));
      assertEquals(200, Scp106Rules.checkTicks(1));
   }

   @Test
   void itRetreatsOnlyAfterHeavyDamageInShortTime() {
      long[] t = {0, 50, 100, 150};
      float[] d = {30, 30, 30, 0};
      assertTrue(Scp106Rules.shouldRetreat(150, t, d, 3));
      assertFalse(Scp106Rules.shouldRetreat(260, t, d, 3), "the first hit fell out of the window");
      assertFalse(Scp106Rules.shouldRetreat(100, t, d, 2));
   }

   @Test
   void decisionsNeedTheRightDistance() {
      assertEquals(1, Scp106Rules.decide(0.01, 10, true));
      assertEquals(0, Scp106Rules.decide(0.01, 10, false));
      assertEquals(0, Scp106Rules.decide(0.01, 2, true), "never stops right in front of the prey");
      assertEquals(2, Scp106Rules.decide(0.99, 20, false));
      assertEquals(0, Scp106Rules.decide(0.99, 8, false));
      assertEquals(0, Scp106Rules.decide(0.5, 30, true));
   }

   @Test
   void huntsInsideThePocketAreSpacedTwoToFiveMinutes() {
      assertEquals(2_400, Scp106Rules.stalkerDelay(0));
      assertEquals(6_000, Scp106Rules.stalkerDelay(1));
      assertTrue(Scp106Rules.CAPTURE_TRANSFER < Scp106Rules.CAPTURE_TICKS);
      assertEquals(1.0, Scp106Rules.captureProgress(Scp106Rules.CAPTURE_TRANSFER), 1e-9);
   }
}
