package dev.baranhan.viltrumitecore.city.citizen;

/**
 * How panic passes through a crowd (pure, unit tested). Someone who sees or feels a danger
 * panics at hearsay depth 0; a neighbour catching it from a runner is one deeper. Only shallow
 * and fresh panics spread, so a dense crowd calms down once the danger is gone instead of
 * keeping itself running forever.
 */
public final class PanicRules {
   /** Panic passes on at most this many times from the one who saw the danger. */
   public static final int MAX_HOPS = 2;
   /** A panic older than this (since the danger was last seen first hand) no longer spreads. */
   public static final int RUMOR_TICKS = 160;

   private PanicRules() {
   }

   /** Does a panic of this hearsay depth, first-hand at {@code sightedAt}, still spread at {@code now}? */
   public static boolean spreads(int hops, long sightedAt, long now) {
      return hops < MAX_HOPS && sightedAt != Long.MIN_VALUE && now - sightedAt < RUMOR_TICKS;
   }
}
