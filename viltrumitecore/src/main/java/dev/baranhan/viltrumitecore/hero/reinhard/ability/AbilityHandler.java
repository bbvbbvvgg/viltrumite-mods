package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardActions;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import net.minecraft.server.level.ServerPlayer;

/**
 * One Reinhard ability (stage 3+), driven by {@code ReinhardActions}: the router
 * gates the press (control, mode, cooldown, busy, timeline) and then calls
 * {@link #tryStart}; a false answer is a refusal (sound, no cooldown). Server only.
 */
public interface AbilityHandler {
   /** Starts the ability after the gate passed; false = refused (the ability spent nothing). */
   boolean tryStart(ServerPlayer player, ReinhardState state);

   /**
    * The press was refused (gate or {@link #tryStart}); {@code refusal} NONE = tryStart said no.
    * For predicted abilities that must undo the client's prediction (Step Dash).
    */
   default void refused(ServerPlayer player, ReinhardState state, ReinhardActions.Refusal refusal) {
   }

   /** Every server tick while Reinhard is the hero (before the timeline advances). */
   default void tick(ServerPlayer player, ReinhardState state) {
   }

   /** Stops everything the ability owns (mode change, control, cleanup); {@code reason} null = not a cleanup. */
   default void cancel(ServerPlayer player, ReinhardState state, CleanupReason reason) {
   }

   /** A press that belongs to an ability already running (grab → throw); skips the gate except control. */
   default boolean claimsPress(ReinhardState state) {
      return false;
   }

   default void secondPress(ServerPlayer player, ReinhardState state) {
   }
}
