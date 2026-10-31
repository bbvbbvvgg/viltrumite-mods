package dev.baranhan.viltrumitecore.entity.avengers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AvengerGolemRuleTest {
   @Test
   void peacefulGolemIsSpared() {
      assertFalse(AvengerBrain.hostileToTeam(false, false, false, false));
   }

   @Test
   void golemThatTurnedOnSteveIsFairGame() {
      assertTrue(AvengerBrain.hostileToTeam(true, false, false, false), "hunts Steve / an Avenger");
      assertTrue(AvengerBrain.hostileToTeam(false, true, false, false), "angry at Steve");
      assertTrue(AvengerBrain.hostileToTeam(false, false, true, false), "hit Steve");
      assertTrue(AvengerBrain.hostileToTeam(false, false, false, true), "Steve struck it");
   }
}
