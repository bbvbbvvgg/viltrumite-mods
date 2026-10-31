package dev.baranhan.viltrumitecore.hero.ironman.mark;

/**
 * Where the single instance of a mark is (plan stage 4, Review Focus 0).
 * Append only: NBT stores the name, the wire uses the ordinal.
 */
public enum MarkLocation {
   /** In Veronica's storage (default, also on cooldown). */
   STORED,
   /** On Tony (worn or partial). */
   WORN,
   /** Standing in the world as the empty suit. */
   EMPTY,
   /** Parts flying to Tony. */
   IN_DELIVERY,
   /** Flying with the Iron Legion (stage 6). */
   LEGION;

   /** A world instance that never survives a relog, death or dimension change. */
   public boolean inWorld() {
      return this == EMPTY || this == IN_DELIVERY || this == LEGION;
   }
}
