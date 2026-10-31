package dev.baranhan.viltrumitecore.entity.avengers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.captain.CaptainRules;
import org.junit.jupiter.api.Test;

/**
 * Out-of-reach targets are filtered out, not only scored lower (the old rule only added a malus for "not
 * visible", so the team still aggroed on mobs behind walls and under the floor).
 */
class AvengerReachTest {
   private static boolean walker(double d, double dySelf, double dyOwner, boolean self, boolean owner) {
      return AvengerBrain.reachable(d, dySelf, dyOwner, self, owner, false);
   }

   @Test
   void behindASolidWallForBothIsNeverACandidate() {
      assertFalse(walker(6, 0, 0, false, false));
      assertFalse(AvengerBrain.reachable(6, 0, 0, false, false, true));
   }

   @Test
   void oneClearLineIsEnough() {
      assertTrue(walker(8, 0, 0, true, false), "the Avenger sees it");
      assertTrue(walker(8, 0, 0, false, true), "Steve sees it round the corner");
   }

   @Test
   void cavesUnderSteveAreOutOfReach() {
      assertFalse(walker(9, -9, -9, true, true), "a ravine mob 9 blocks down, even when a ray finds it");
      assertFalse(AvengerBrain.reachable(9, -9, -9, true, true, true), "fliers keep Steve's window");
      assertTrue(walker(5, -3, -3, true, true), "a mob just down the slope");
   }

   @Test
   void cliffTopsAreOutOfReachForWalkersOnly() {
      assertFalse(walker(8, 7, 2, true, true), "a walker cannot climb 7 blocks");
      assertTrue(AvengerBrain.reachable(8, 7, 2, true, true, true), "Iron Man and Thor fly up");
      assertFalse(walker(8, 2, CaptainRules.TARGET_RISE + 1, true, true), "above Steve's window");
   }

   @Test
   void searchRadiusIsBounded() {
      assertFalse(walker(CaptainRules.TARGET_SEARCH + 0.5, 0, 0, true, true));
      assertTrue(walker(CaptainRules.TARGET_SEARCH - 0.5, 0, 0, true, true));
   }
}
