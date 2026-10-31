package dev.baranhan.viltrumitecore.entity.avengers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Target priorities of the Avengers' fight AI: Steve's attackers first, then what hunts him; the team spreads out. */
class AvengerTargetScoreTest {
   private static double score(double d, boolean hitSteve, boolean huntsSteve, int claimed, int threats) {
      return AvengerBrain.threatScore(d, hitSteve, huntsSteve, false, false, false, claimed, threats, true);
   }

   @Test
   void whatHitsSteveBeatsANearerIdleMob() {
      assertTrue(score(12, true, false, 0, 3) < score(3, false, false, 0, 3));
   }

   @Test
   void whatHuntsSteveBeatsANearerIdleMob() {
      assertTrue(score(10, false, true, 0, 3) < score(3, false, false, 0, 3));
   }

   @Test
   void teamSpreadsOverSeveralThreats() {
      // equal threats: the one nobody fights yet wins even if slightly farther
      assertTrue(score(7, false, false, 0, 2) < score(5, false, false, 1, 2));
   }

   @Test
   void aLoneThreatIsFoughtByEveryone() {
      // one threat only: claims don't push it away
      assertTrue(score(5, false, false, 4, 1) == score(5, false, false, 0, 1));
   }

   @Test
   void unseenTargetsAndStickiness() {
      double seen = AvengerBrain.threatScore(8, false, false, false, false, false, 0, 2, true);
      double unseen = AvengerBrain.threatScore(8, false, false, false, false, false, 0, 2, false);
      double current = AvengerBrain.threatScore(8, false, false, false, false, true, 0, 2, true);
      assertTrue(unseen > seen);
      assertTrue(current < seen);
   }
}
