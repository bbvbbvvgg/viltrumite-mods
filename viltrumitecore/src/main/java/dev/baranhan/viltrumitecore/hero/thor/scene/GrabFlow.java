package dev.baranhan.viltrumitecore.hero.thor.scene;

import javax.annotation.Nullable;

/**
 * Pure state of one grab (decisions 60-64, 117-126): HOLD (Thor walks, the
 * victim may resist with its one key) → ACTION (committed: no resistance,
 * plays to the release) → RECOVER (victim out of the hand, Thor finishes)
 * → DONE. An escape or the hold limit ends HOLD directly.
 */
public final class GrabFlow {
   public enum Stage {
      HOLD,
      ACTION,
      RECOVER,
      DONE
   }

   public enum Outcome {
      NONE,
      /** The meter filled: the victim breaks free. */
      ESCAPED,
      /** No action within the hold limit: the victim drops. */
      TIMEOUT,
      /** A barrage punch lands ({@link #punch()}). */
      PUNCH,
      /** The victim leaves the hand: slam impact, throw launch or barrage finisher. */
      RELEASE,
      /** Thor's follow-through is over. */
      END
   }

   @Nullable
   private final ResistKey key;
   private Stage stage = Stage.HOLD;
   @Nullable
   private GrabAction action;
   private int ticks;
   private float meter;
   private int punch = -1;
   private int pressesThisSecond;
   private int secondTicks;

   /** {@code key} null: the victim cannot resist (a mob). */
   public GrabFlow(@Nullable ResistKey key) {
      this.key = key;
   }

   @Nullable
   public ResistKey key() {
      return this.key;
   }

   public Stage stage() {
      return this.stage;
   }

   @Nullable
   public GrabAction action() {
      return this.action;
   }

   /** Ticks in the current stage. */
   public int ticks() {
      return this.ticks;
   }

   public float meter() {
      return this.meter;
   }

   public int punch() {
      return this.punch;
   }

   /** The victim may still resist: holding, with a key, and Thor has not committed. */
   public boolean resistible() {
      return this.stage == Stage.HOLD && this.key != null;
   }

   /** One press of {@code pressed}; true when it counted. Wrong keys and macro-speed presses do nothing. */
   public boolean resist(ResistKey pressed, float perPress, int maxPerSecond) {
      if (!this.resistible() || pressed != this.key || this.pressesThisSecond >= maxPerSecond) {
         return false;
      }

      this.pressesThisSecond++;
      this.meter = Math.min(1.0F, this.meter + perPress);
      return true;
   }

   /** Thor chooses the one action; false once anything was chosen or the hold is over. */
   public boolean commit(GrabAction chosen) {
      if (this.stage != Stage.HOLD || chosen == null) {
         return false;
      }

      this.stage = Stage.ACTION;
      this.action = chosen;
      this.ticks = 0;
      return true;
   }

   /** Ends the grab from outside (death, logout, distance): DONE without outcome. */
   public void abort() {
      this.stage = Stage.DONE;
   }

   public Outcome tick(float decay, int holdMax, int recoverTicks) {
      this.punch = -1;
      if (++this.secondTicks >= 20) {
         this.secondTicks = 0;
         this.pressesThisSecond = 0;
      }

      switch (this.stage) {
         case HOLD -> {
            this.ticks++;
            if (this.meter >= 1.0F) {
               this.stage = Stage.DONE;
               return Outcome.ESCAPED;
            }

            this.meter = Math.max(0.0F, this.meter - decay);
            if (this.ticks >= holdMax) {
               this.stage = Stage.DONE;
               return Outcome.TIMEOUT;
            }

            return Outcome.NONE;
         }
         case ACTION -> {
            this.ticks++;
            if (this.ticks >= this.action.releaseTick()) {
               this.stage = Stage.RECOVER;
               this.ticks = 0;
               return Outcome.RELEASE;
            }

            this.punch = this.action.punchAt(this.ticks);
            return this.punch >= 0 ? Outcome.PUNCH : Outcome.NONE;
         }
         case RECOVER -> {
            if (++this.ticks >= recoverTicks) {
               this.stage = Stage.DONE;
               return Outcome.END;
            }

            return Outcome.NONE;
         }
         default -> {
            return Outcome.NONE;
         }
      }
   }
}
