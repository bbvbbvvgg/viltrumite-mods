package dev.baranhan.viltrumitecore.hero.thor.bridge;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import javax.annotation.Nullable;

/**
 * Authored timing of one Thor move: ticks of the INTRO / MAIN / FINISH
 * phases, the phase from which any press may cut it, and the combo steps.
 * A move only exists for the timeline once its definition is registered in
 * {@link ThorMoves}; there is no global "shipped stage".
 */
public record ThorMoveDefinition(int intro, int main, int finish, @Nullable MovePhase cancellableFrom, int maxSteps) {
   public ThorMoveDefinition {
      if (intro < 0 || main < 0 || finish < 0) {
         throw new IllegalArgumentException("negative phase ticks");
      }

      if (intro + main + finish <= 0) {
         throw new IllegalArgumentException("a move needs at least one tick");
      }

      if (maxSteps < 1) {
         throw new IllegalArgumentException("maxSteps < 1");
      }
   }

   public int ticks(MovePhase phase) {
      return switch (phase) {
         case INTRO -> this.intro;
         case MAIN -> this.main;
         case FINISH -> this.finish;
      };
   }

   public int length() {
      return this.intro + this.main + this.finish;
   }
}
