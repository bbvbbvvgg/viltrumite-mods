package dev.baranhan.viltrumitecore.hero.thor.bridge;

import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;

/**
 * Behaviour of one authored move, owned by exactly one session (A/B/C).
 * Every method defaults to doing nothing; the foundation registers only
 * {@link #NONE}. Server main thread only.
 */
public interface ThorMoveHandler {
   ThorMoveHandler NONE = new ThorMoveHandler() {
   };

   /**
    * Last say before the timeline (cooldowns, conditions of the owning
    * session): false refuses the press quietly, the handler gives its own
    * feedback. Default: always.
    */
   default boolean admits(ThorMoveContext context) {
      return true;
   }

   /**
    * A step of this move runs from now on: a fresh start, a cut-in over
    * another move (that one heard onStop first), a restart of the same move
    * (onStop first, then onStart) or an immediate chain step of a press in the
    * cancel window. Never for a buffered press ({@link #onQueued}). When the
    * step begins past its intro (intro 0), {@link #onPhase} with the entry
    * event (ENTER_MAIN / ENTER_FINISH) follows right after.
    */
   default void onStart(ThorMoveContext context) {
   }

   /**
    * A press of this move was buffered (one press, StagedTimeline QUEUE):
    * nothing started yet. The buffered step later begins with
    * {@code onPhase(CHAIN)} (then the entry event when it starts past its
    * intro). The context carries the press input.
    */
   default void onQueued(ThorMoveContext context) {
   }

   /**
    * A timeline event of this move: ENTER_MAIN, ENTER_FINISH, CHAIN (a
    * buffered step began; its entry event follows when it starts past the
    * intro) or END (ran to its end, no onStop).
    */
   default void onPhase(ThorMoveContext context, StagedTimeline.Event event) {
   }

   /**
    * The key that started this move was released after {@code heldTicks}
    * server ticks (mouse buttons through HeldInputs). The release always
    * reaches the move chosen on the press, e.g. a charged MMB throw.
    */
   default void onRelease(ThorMoveContext context, int heldTicks) {
   }

   /**
    * Release with its cause. Default: the player's release goes to
    * {@link #onRelease(ThorMoveContext, int)}, a forced one is dropped (the
    * lifecycle stop or control interrupt that follows ends the move, so a
    * charged throw never fires from a corpse). Override to react to a forced
    * release (e.g. a quiet cancel).
    */
   default void onRelease(ThorMoveContext context, int heldTicks, ReleaseCause cause) {
      if (cause == ReleaseCause.PLAYER) {
         this.onRelease(context, heldTicks);
      }
   }

   /**
    * Stopped before its end (cut by another move, ThorBridge.interrupt /
    * end, control, death, dimension, hero change). Delivered once, also for a
    * stop that bypassed the API (the next tick notices). Must be idempotent:
    * the timeline is already off this move when it runs.
    */
   default void onStop(ThorMoveContext context) {
   }
}
