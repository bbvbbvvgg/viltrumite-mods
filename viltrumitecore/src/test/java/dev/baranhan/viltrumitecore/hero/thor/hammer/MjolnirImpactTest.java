package dev.baranhan.viltrumitecore.hero.thor.hammer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** №186/№195 crater bowl and the lift gate. */
class MjolnirImpactTest {
   @Test
   void theBowlIsDeepestInTheCentreAndEndsAtTheRim() {
      int radius = 6;
      int centre = MjolnirImpact.bowlDepth(radius, 0, 0);
      assertEquals((int)Math.round(radius * MjolnirTuning.CRATER_DEPTH), centre);
      assertEquals(-1, MjolnirImpact.bowlDepth(radius, radius, 1), "outside the rim");
      int previous = centre;
      for (int dx = 1; dx <= radius; dx++) {
         int depth = MjolnirImpact.bowlDepth(radius, dx, 0);
         assertTrue(depth <= previous && depth >= 0, "shallower outwards at " + dx);
         assertEquals(depth, MjolnirImpact.bowlDepth(radius, 0, dx), "round");
         previous = depth;
      }
   }

   @Test
   void aLiftAttemptWaitsForTheLastOneAndItsPause() {
      assertTrue(MjolnirLift.mayBegin(false, 0L, 10L));
      assertTrue(!MjolnirLift.mayBegin(true, 0L, 10L), "one attempt at a time");
      assertTrue(!MjolnirLift.mayBegin(false, 30L, 10L), "the pause after a failed attempt");
      assertTrue(MjolnirLift.mayBegin(false, 30L, 30L));
   }
}
