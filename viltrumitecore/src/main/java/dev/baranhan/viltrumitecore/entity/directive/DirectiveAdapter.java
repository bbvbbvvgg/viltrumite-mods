package dev.baranhan.viltrumitecore.entity.directive;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Bridge for bodies whose brain the goal-based layer cannot reach (a custom state machine like SCP-096, another mod's
 * boss). The layer still vetoes targets and damage for every entity; an adapter adds what only the entity itself can do
 * (calm a rage, drop an internal target list). Registered with {@link MobDirectives#registerAdapter}; the first adapter
 * whose {@link #handles} is true is used.
 */
public interface DirectiveAdapter {
   boolean handles(LivingEntity entity);

   /** Does this adapter implement an effect only it can (SCP-096: FORGET_FACES)? */
   default boolean supports(LivingEntity entity, DirectiveEffect effect) {
      return false;
   }

   /** A directive was just written into the body. */
   default void onApply(LivingEntity entity, Directive directive) {
   }

   /** Once per server tick while the body carries directives. */
   default void tick(LivingEntity entity, List<Directive> active) {
   }

   /** Extra target veto (true = the body must not target {@code target}). */
   default boolean forbidsTarget(LivingEntity entity, Entity target, List<Directive> active) {
      return false;
   }
}
