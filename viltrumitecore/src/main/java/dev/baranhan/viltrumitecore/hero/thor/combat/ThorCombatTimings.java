package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition;
import java.util.EnumMap;
import java.util.Map;

/**
 * Phase ticks of session B's moves (intro, main, finish, cancel window,
 * combo steps). All PLACEHOLDER: the author has not approved windows,
 * priorities of cancels or recovery (spec §6 "[ОТКРЫТО]"). Constants, not
 * config: the client clips are keyed to the same lengths.
 */
public final class ThorCombatTimings {
   /** Combo steps on the timeline; the routes (ComboBook) are shorter, a finished route starts a new one. */
   public static final int COMBO_STEPS = 8;
   /** Ticks a combo chain stays open after its step (the window to continue it). */
   public static final int COMBO_GRACE = 6;
   /** Ticks of the storm's main phase (its maximum growth time). */
   public static final int STORM_MAX_TICKS = 100;

   public static final Map<ThorMove, ThorMoveDefinition> DEFINITIONS = new EnumMap<>(ThorMove.class);

   static {
      DEFINITIONS.put(ThorMove.HAMMER_COMBO, new ThorMoveDefinition(5, 3, 8, MovePhase.FINISH, COMBO_STEPS));
      DEFINITIONS.put(ThorMove.FIST_COMBO, new ThorMoveDefinition(3, 2, 6, MovePhase.FINISH, COMBO_STEPS));
      DEFINITIONS.put(ThorMove.HAMMER_STRIKE, new ThorMoveDefinition(6, 3, 9, MovePhase.FINISH, 1));
      DEFINITIONS.put(ThorMove.ELECTRIC_MODE, new ThorMoveDefinition(0, 4, 0, MovePhase.MAIN, 1));
      DEFINITIONS.put(ThorMove.ELECTRIC_COMBO, new ThorMoveDefinition(3, 2, 7, MovePhase.FINISH, COMBO_STEPS));
      // The intro is the hold threshold: RMB still down when it ends grows the wave into the storm.
      DEFINITIONS.put(ThorMove.ELECTRIC_WAVE, new ThorMoveDefinition(7, 3, 8, MovePhase.FINISH, COMBO_STEPS));
      DEFINITIONS.put(ThorMove.ELECTRIC_STORM, new ThorMoveDefinition(0, STORM_MAX_TICKS, 12, null, 1));
      DEFINITIONS.put(ThorMove.WRATH, new ThorMoveDefinition(24, 8, 20, null, 1));
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
