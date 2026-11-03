package dev.baranhan.viltrumitecore.t1000;

/**
 * The visible body of the T-1000 (synced as one byte with its start tick). HUMAN is the citizen disguise; every
 * other form shows (part of) the liquid metal. The client derives every blend from {@link #metal} and the time
 * since the form started, so a late joiner sees the same frame as everyone else.
 */
public enum T1000Form {
   /** Disguised as a citizen: the citizen renderer only (wounds may still show metal under the skin). */
   HUMAN,
   /** Chrome spreads from the origin over the skin ({@link T1000Rules#REVEAL_TICKS}). */
   REVEALING,
   /** The liquid-metal body. */
   LIQUID,
   /** Integrity lost: collapses into a puddle ({@link T1000Rules#MELT_TICKS}). */
   MELTING,
   /** A puddle on the ground: crawls, regenerates, is vulnerable. */
   PUDDLE,
   /** Rises out of the puddle ({@link T1000Rules#REFORM_TICKS}). */
   REFORMING,
   /** Metal flows back into skin and clothes ({@link T1000Rules#CONCEAL_TICKS}). */
   CONCEALING,
   /** The final destruction (core gone): throes, shatter, the puddle boils away. */
   DYING;

   private static final T1000Form[] ALL = values();

   public static T1000Form of(int ordinal) {
      return ordinal >= 0 && ordinal < ALL.length ? ALL[ordinal] : HUMAN;
   }

   /** 0 = only skin, 1 = only metal, at {@code elapsed} ticks into this form. */
   public float metal(float elapsed) {
      return switch (this) {
         case HUMAN -> 0.0F;
         case REVEALING -> T1000Rules.clamp01(elapsed / T1000Rules.REVEAL_TICKS);
         case CONCEALING -> 1.0F - T1000Rules.clamp01(elapsed / T1000Rules.CONCEAL_TICKS);
         default -> 1.0F;
      };
   }

   /** 0 = standing body, 1 = flat puddle. */
   public float melt(float elapsed) {
      return switch (this) {
         case MELTING -> T1000Rules.smooth(T1000Rules.clamp01(elapsed / T1000Rules.MELT_TICKS));
         case PUDDLE -> 1.0F;
         case REFORMING -> 1.0F - T1000Rules.smooth(T1000Rules.clamp01(elapsed / T1000Rules.REFORM_TICKS));
         case DYING -> T1000Rules.smooth(T1000Rules.clamp01((elapsed - T1000Rules.DEATH_MELT_START) / (T1000Rules.DEATH_TICKS - T1000Rules.DEATH_MELT_START)));
         default -> 0.0F;
      };
   }

   /** The body is a puddle or on its way there/back: low hitbox, no attacks. */
   public boolean grounded() {
      return this == MELTING || this == PUDDLE || this == REFORMING;
   }

   /** Metal shows: a target for heroes, no citizen behaviour. */
   public boolean revealed() {
      return this != HUMAN;
   }
}
