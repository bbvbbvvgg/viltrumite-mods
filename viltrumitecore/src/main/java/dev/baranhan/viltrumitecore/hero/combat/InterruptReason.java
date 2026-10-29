package dev.baranhan.viltrumitecore.hero.combat;

/**
 * Why a running staged move stops early. A move may resist some reasons in
 * some phases (hyper armour, a paired scene); {@link #LIFECYCLE} is never
 * resisted.
 */
public enum InterruptReason {
   /** The owner's own input (a cancel or a higher-priority move). */
   INPUT,
   /** The owner was hit (stagger). */
   HIT,
   /** External control took the owner (anchored, grabbed, frozen). */
   CONTROL,
   /** A combat interaction resolved against the move (clash, parry, disarm). */
   INTERACTION,
   /** Death, logout, dimension change, hero change: always stops the move. */
   LIFECYCLE
}
