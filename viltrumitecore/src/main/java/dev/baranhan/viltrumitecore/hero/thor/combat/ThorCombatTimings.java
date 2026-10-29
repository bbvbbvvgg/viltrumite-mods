package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Move-level timings of session B's moves: cancel window and combo steps
 * (mechanics, unchanged) around the phase ticks of {@link ThorAttacks}
 * (rework spec §4). A combo move's definition is its envelope, the row of
 * its opener; each step is retimed to the row of the strike it plays.
 * Constants, not config: the clips are keyed to the same lengths.
 */
public final class ThorCombatTimings {
   /** Combo steps on the timeline; the routes (ComboBook) are shorter, a finished route starts a new one. */
   public static final int COMBO_STEPS = 8;
   /**
    * Ticks a melee chain stays open after its step ends (the window to continue it). Scaled with the
    * rework's step lengths (spec §4 "окна комбо пропорционально"): 6 ticks after a 16-tick hammer step
    * and an 11-tick fist step became 10 after ~26 and 11 after ~20.
    */
   public static final int HAMMER_COMBO_GRACE = 10;
   public static final int FIST_COMBO_GRACE = 11;
   /** Ticks of the storm's main phase (its maximum growth time). */
   public static final int STORM_MAX_TICKS = 100;

   public static final Map<ThorMove, ThorMoveDefinition> DEFINITIONS = new EnumMap<>(ThorMove.class);

   static {
      put(ThorMove.HAMMER_COMBO, MovePhase.FINISH, COMBO_STEPS);
      put(ThorMove.FIST_COMBO, MovePhase.FINISH, COMBO_STEPS);
      put(ThorMove.HAMMER_STRIKE, MovePhase.FINISH, 1);
      put(ThorMove.ELECTRIC_MODE, MovePhase.MAIN, 1);
      put(ThorMove.ELECTRIC_COMBO, MovePhase.FINISH, COMBO_STEPS);
      // The intro is the hold threshold: RMB still down when it ends grows the wave into the storm.
      put(ThorMove.ELECTRIC_WAVE, MovePhase.FINISH, COMBO_STEPS);
      put(ThorMove.ELECTRIC_STORM, null, 1);
      put(ThorMove.WRATH, null, 1);
   }

   private static void put(ThorMove move, @Nullable MovePhase cancellableFrom, int maxSteps) {
      DEFINITIONS.put(move, ThorAttacks.envelope(move).phases().definition(cancellableFrom, maxSteps));
   }

   /** The grace window of a melee chain of {@code move} (0 for other moves). */
   public static int comboGrace(@Nullable ThorMove move) {
      return move == ThorMove.HAMMER_COMBO ? HAMMER_COMBO_GRACE : move == ThorMove.FIST_COMBO ? FIST_COMBO_GRACE : 0;
   }

   /** The definition of one row: its own phases with the move's cancel window and steps. */
   public static ThorMoveDefinition of(ThorAttacks.Attack attack) {
      ThorMoveDefinition move = of(attack.move());
      return attack.phases().definition(move.cancellableFrom(), move.maxSteps());
   }

   private ThorCombatTimings() {
   }

   public static ThorMoveDefinition of(ThorMove move) {
      ThorMoveDefinition definition = DEFINITIONS.get(move);
      if (definition == null) {
         throw new IllegalArgumentException(move + " is not a session B move");
      }

      return definition;
   }
}
