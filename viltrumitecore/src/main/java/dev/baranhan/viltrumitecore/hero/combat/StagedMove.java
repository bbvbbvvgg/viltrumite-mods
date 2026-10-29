package dev.baranhan.viltrumitecore.hero.combat;

import javax.annotation.Nullable;

/**
 * One move a {@link StagedTimeline} can play: intro → main → finish with a
 * priority, an optional cancel window, combo steps and an optional partner
 * (two-character scene). Implemented by hero move enums; carries data only,
 * the hero owns the effects.
 */
public interface StagedMove {
   /** Stable id on the wire (snapshot actionId); unique within the hero, never reused. */
   int wireId();

   /** Higher priority interrupts a lower running move; equal priority never interrupts outside the cancel window. */
   int priority();

   /** Ticks of one phase; 0 = phase not authored (skipped). */
   int ticks(MovePhase phase);

   /**
    * First phase in which a new press may cancel this move into another move,
    * or chain the next combo step; null = the move always plays to its end.
    */
   @Nullable
   MovePhase cancellableFrom();

   /** Needs a partner entity for its whole run (grab, fatality). */
   boolean paired();

   /** Combo steps (1 = single move). A step replays intro → main → finish with step + 1. */
   int maxSteps();

   /** May the move keep running through this interrupt in this phase (hyper armour)? LIFECYCLE is never resisted. */
   default boolean resists(InterruptReason reason, MovePhase phase) {
      return false;
   }

   /** Total ticks of one step. 0 = nothing authored: the move can never start. */
   default int length() {
      int total = 0;
      for (MovePhase phase : MovePhase.values()) {
         total += Math.max(0, this.ticks(phase));
      }
      return total;
   }

   /**
    * Ticks of one phase of combo step {@code step} (0-based). Default: every
    * step plays the same phases ({@link #ticks(MovePhase)}). A move whose steps
    * differ (a three-strike combo of 24 / 26 / 36 ticks) overrides this.
    */
   default int ticks(MovePhase phase, int step) {
      return this.ticks(phase);
   }

   /** Total ticks of combo step {@code step}. */
   default int length(int step) {
      int total = 0;
      for (MovePhase phase : MovePhase.values()) {
         total += Math.max(0, this.ticks(phase, step));
      }
      return total;
   }
}
