package dev.baranhan.viltrumitecore.hero;

/**
 * What a hero does to a projectile that is about to hit him
 * ({@link HeroDefinition#projectileResponse}). Append only.
 */
public enum ProjectileResponse {
   /** Vanilla hit. */
   PASS,
   /** Turned 60-90° sideways and slowed; keeps flying, no hit. */
   DEFLECT,
   /** Caught: an arrow/trident goes to the inventory, anything else is removed. */
   CATCH,
   /** Sent back at its owner with the same speed; the hero becomes the owner. */
   REFLECT;
}
