package dev.baranhan.viltrumitecore.hero.combat;

/** What a combat presence is doing right now, as seen by interaction rules. */
public enum CombatStance {
   /** Carried, not used. */
   NONE,
   /** Blocking / guarding. */
   GUARD,
   /** Striking: inside a move's effective part. */
   ACTIVE,
   /** In flight away from its owner (a thrown weapon). */
   THROWN
}
