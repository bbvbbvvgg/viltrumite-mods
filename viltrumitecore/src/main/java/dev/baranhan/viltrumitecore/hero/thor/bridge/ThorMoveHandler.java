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

   /** The press was admitted on the timeline (started, chained or buffered). */
   default void onStart(ThorMoveContext context) {
   }

   /** A timeline event of this move: ENTER_MAIN, ENTER_FINISH, CHAIN or END. */
   default void onPhase(ThorMoveContext context, StagedTimeline.Event event) {
   }

   /**
    * The key that started this move was released after {@code heldTicks}
    * server ticks (mouse buttons through HeldInputs). The release always
    * reaches the move chosen on the press, e.g. a charged MMB throw.
    */
   default void onRelease(ThorMoveContext context, int heldTicks) {
   }

   /** Stopped before its end (interrupt, death, dimension, hero change). */
   default void onStop(ThorMoveContext context) {
   }
}
