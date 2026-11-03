package dev.baranhan.viltrumitecore.city.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The beats of citizen goals fall on ticks when Mob.serverAiStep really runs the goal selector (#136). */
class GoalClockTest {
   /** How often a goal timed like this would be tried over {@code ticks}, for an entity added at server tick {@code spawn}. */
   private static int tries(int spawn, int id, int period, int ticks, boolean ownCounter) {
      int n = 0;
      for (int server = spawn; server < spawn + ticks; server++) {
         int tickCount = server - spawn + 1; // ServerLevel.tickNonPassenger counts before Mob.tick
         boolean selecting = (server + id) % 2 == 0 || tickCount <= 1; // Mob.serverAiStep
         boolean beat = ownCounter ? (tickCount + id) % period == 0 : GoalClock.due(server, id, period);
         if (selecting && beat) {
            n++;
         }
      }
      return n;
   }

   @Test
   void everyCitizenGetsItsBeatWhateverTickItWasAddedOn() {
      for (int period : new int[]{10, 20, 40}) {
         for (int spawn = 0; spawn < 4; spawn++) {
            for (int id = 0; id < 12; id++) {
               assertEquals(2000 / period, tries(spawn, id, period, 2000, false), "period " + period + " spawn " + spawn + " id " + id);
            }
         }
      }
   }

   @Test
   void theEntityCounterMissedHalfTheCitizens() {
      // the old check: added on a tick of the wrong parity, the goal was never tried at all
      int never = 0;
      for (int spawn = 0; spawn < 2; spawn++) {
         for (int id = 0; id < 12; id++) {
            if (tries(spawn, id, 40, 4000, true) == 0) {
               never++;
            }
         }
      }
      assertEquals(12, never);
      assertTrue(GoalClock.due(-40, 0, 40) && !GoalClock.due(-39, 0, 40), "negative ticks wrap cleanly");
   }
}
