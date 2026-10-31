package dev.baranhan.viltrumitecore.interaction;

/**
 * One contextual interaction: a reaction that only exists because two specific powers met (a blade and a guard,
 * lightning and armour, a summon and a world event). Implementations are stateless and live in
 * {@code interaction/builtin}; every id must have an entry in {@code docs/contextual-interactions.json}
 * ({@code ContextualInteractionCatalogTest}). Server main thread.
 */
public interface ContextualInteraction {
   InteractionSpec spec();

   /** Cheap, side-effect free check of the trigger conditions. */
   boolean matches(InteractionContext ctx);

   /** Applies the interaction (state, pushes, FX, sounds). Runs inside the re-entrancy guard. */
   InteractionOutcome apply(InteractionContext ctx);

   /** One-time registration hook (shared combat rules). */
   default void onRegister() {
   }
}
