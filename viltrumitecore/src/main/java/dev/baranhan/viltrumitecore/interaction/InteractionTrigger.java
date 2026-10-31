package dev.baranhan.viltrumitecore.interaction;

/** Where the server asks the contextual interactions (one dispatcher entry per trigger). */
public enum InteractionTrigger {
   /** {@code LivingAttackEvent}, before every damage layer: the interaction may consume the hit. */
   HIT,
   /** {@code LivingHurtEvent}, before the hero layers: the interaction may scale the hit. */
   HURT,
   /** {@code ProjectileImpactEvent} on an entity, before the hero projectile seam: may consume the impact. */
   PROJECTILE
}
