package dev.baranhan.viltrumitecore.hero.combat;

/**
 * The three authored segments of a staged move (wind-up, the effective part,
 * recovery). A move may leave a phase unauthored (0 ticks); the timeline then
 * skips it. Stable order: the ordinal travels in the snapshot variant.
 */
public enum MovePhase {
   /** Wind-up / reach / grab entry. */
   INTRO,
   /** The effective part: hit frames, the state flip (draw/holster), the paired scene body. */
   MAIN,
   /** Recovery / follow-through; the usual window for chains and cancels. */
   FINISH;

   private static final MovePhase[] VALUES = values();

   /** Phase by ordinal, INTRO for anything out of range (snapshot decode). */
   public static MovePhase byOrdinal(int ordinal) {
      return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : INTRO;
   }

   /** True when this phase is at or after {@code other} (null = never). */
   public boolean atLeast(MovePhase other) {
      return other != null && this.ordinal() >= other.ordinal();
   }
}
