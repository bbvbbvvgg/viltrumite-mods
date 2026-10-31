package dev.baranhan.viltrumitecore.entity.avengers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Team pacing: the whole team on one evil iron golem (100 HP) needs at least ~15 s; small mobs still drop fast. */
class TeamDamageTest {
   /** Ticks until {@code health} is gone under the five Avengers hitting as often as their melee cooldowns allow. */
   private static int ticksToKill(float health) {
      TeamDamage.Bucket b = new TeamDamage.Bucket(health, 0);
      // raw hit, period in ticks: Hulk, Thor, Iron Man punch + repulsor, Widow, Hawkeye (all melee/aimed shots)
      float[] raw = {14, 11, 11, 7, 6, 7};
      int[] period = {14, 12, 30, 20, 14, 22};
      float left = health;
      for (int t = 0; t < 20 * 120; t++) {
         for (int i = 0; i < raw.length; i++) {
            if (t % period[i] == i) {
               left -= b.spend(t, raw[i]);
            }
         }
         if (left <= 0) {
            return t;
         }
      }
      return Integer.MAX_VALUE;
   }

   @Test
   void evilGolemHoldsOutFifteenSecondsAgainstTheWholeTeam() {
      int t = ticksToKill(100.0F);
      assertTrue(t >= 15 * 20, "golem died after " + t / 20.0 + " s");
      assertTrue(t <= 30 * 20, "golem must still fall: " + t / 20.0 + " s");
   }

   @Test
   void smallMobsStillDropInSeconds() {
      int t = ticksToKill(20.0F);
      assertTrue(t <= 50, "zombie took " + t / 20.0 + " s");
   }

   @Test
   void aSingleHitWithinBudgetIsOnlyScaled() {
      TeamDamage.Bucket b = new TeamDamage.Bucket(100.0F, 0);
      assertEquals(5.0F * TeamDamage.SCALE, b.spend(0, 5.0F), 1.0E-4);
   }

   @Test
   void anEmptyBucketGivesAGlanceAndRefillsOverTime() {
      TeamDamage.Bucket b = new TeamDamage.Bucket(100.0F, 0);
      b.spend(0, 100.0F);
      assertEquals(TeamDamage.GLANCE, b.spend(0, 10.0F), 1.0E-4);
      float later = b.spend(40, 10.0F); // 2 s later: about two seconds of budget back
      assertTrue(later > 5.0F && later <= 7.0F, "refilled " + later);
   }
}
