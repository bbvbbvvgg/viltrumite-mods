package dev.baranhan.viltrumitecore.worldevent.thanos;

/**
 * How one server tick of the fight resolves (pure). Called at ServerTickEvent END, after every
 * entity tick of that tick, so all damage of the tick has landed: the defeat check comes first
 * and therefore a kill on the very tick the clock runs out is a victory ("defeat wins ties").
 * Once a non-FIGHT outcome is returned the event stops the clock and never asks again.
 */
public enum ThanosOutcome {
   FIGHT,
   VICTORY,
   SNAP;

   /**
    * @param defeated Thanos is dead or at 0 health after this tick's damage
    * @param expired  the clock reached its duration (after this tick's advance)
    */
   public static ThanosOutcome resolve(boolean defeated, boolean expired) {
      if (defeated) {
         return VICTORY;
      }

      return expired ? SNAP : FIGHT;
   }
}
