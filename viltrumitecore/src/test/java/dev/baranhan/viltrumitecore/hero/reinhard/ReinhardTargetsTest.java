package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** One target rule for every Reinhard hit and AoE (stage 3 Task 1). */
class ReinhardTargetsTest {
   /** A plain hostile mob. */
   private static ReinhardTargets.Facts mob() {
      return new ReinhardTargets.Facts(true, true, false, false, false, false, false, false);
   }

   @Test
   void hitsMobs() {
      assertTrue(ReinhardTargets.canHit(mob()));
   }

   @Test
   void skipsSelfAlliesSpectators() {
      assertFalse(ReinhardTargets.canHit(new ReinhardTargets.Facts(true, true, true, false, false, false, false, false)), "self");
      assertFalse(ReinhardTargets.canHit(new ReinhardTargets.Facts(true, true, false, true, false, false, false, false)), "spectator");
      assertFalse(ReinhardTargets.canHit(new ReinhardTargets.Facts(true, true, false, false, false, true, false, false)), "ally");
      assertFalse(ReinhardTargets.canHit(new ReinhardTargets.Facts(false, true, false, false, false, false, false, false)), "not living");
      assertFalse(ReinhardTargets.canHit(new ReinhardTargets.Facts(true, false, false, false, false, false, false, false)), "dead");
   }

   @Test
   void playersOnlyWithPvp() {
      assertTrue(ReinhardTargets.canHit(new ReinhardTargets.Facts(true, true, false, false, false, false, true, true)));
      assertFalse(ReinhardTargets.canHit(new ReinhardTargets.Facts(true, true, false, false, false, false, true, false)), "PvP off");
      assertFalse(ReinhardTargets.canHit(new ReinhardTargets.Facts(true, true, false, false, true, false, true, true)), "creative");
   }
}
