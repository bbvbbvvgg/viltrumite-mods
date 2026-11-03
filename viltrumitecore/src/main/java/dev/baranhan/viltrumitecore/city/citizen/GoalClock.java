package dev.baranhan.viltrumitecore.city.citizen;

/**
 * When a citizen's goal looks for something to do. {@code Mob.serverAiStep} only lets the goal selector try new
 * goals on server ticks where (server tick + entity id) is even. A goal that also waits for
 * {@code (tickCount + id) % n == 0} on the entity's own tick counter is never tried when the entity was added on an
 * odd server tick (its counter and the server's then differ by an odd number): a whole {@code /city populate} batch
 * could never start a chat (issue #136), never follow a companion and never walk back to the street grid. This clock
 * counts server ticks, so its beats always fall on a tick when the selector really looks.
 */
public final class GoalClock {
   private GoalClock() {
   }

   /** Pure: true on every {@code period}-th server tick for the entity {@code id} ({@code period} even). */
   public static boolean due(long serverTick, int id, int period) {
      return Math.floorMod(serverTick + id, (long)period) == 0L;
   }
}
