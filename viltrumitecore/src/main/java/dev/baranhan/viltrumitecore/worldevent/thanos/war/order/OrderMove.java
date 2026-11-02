package dev.baranhan.viltrumitecore.worldevent.thanos.war.order;

/**
 * One move of a Black Order member (pure data, implemented by {@link MawMove} and {@link CullMove}).
 * Ticks: telegraph (readable wind-up), active, recovery (open to punishment).
 */
public interface OrderMove {
   int telegraph();

   int active();

   int recovery();

   int cooldown();

   double minRange();

   double maxRange();

   /** Base weight (0 = only when {@link #situational} raises it). */
   float weight();

   /** Reaches a flying target. */
   boolean air();

   /** Holds the target (the control ledger must allow it). */
   boolean control();

   /** Usable without a clear line to the target. */
   boolean throughCover();

   /** Damage of one hit (before the hero scale). */
   float damage();

   /** Multiplier of the member's own rules for this situation (1 = neutral, 0 = never). */
   double situational(OrderBrain.Situation s);

   default int length() {
      return this.telegraph() + this.active() + this.recovery();
   }

   int ordinal();

   String name();
}
