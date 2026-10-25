package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import dev.baranhan.viltrumitecore.hero.reinhard.ActionTimeline;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeMoves;

/** Chain bookkeeping shared by the LMB strikes (fists outside Knight, drawn Reid). Pure, unit tested. */
public final class MeleeChain {
   private MeleeChain() {
   }

   /** Every server tick before the timeline advances: idle count and chain break; true = start the buffered press now. */
   public static boolean tick(ReinhardState state, ReinhardAbility ability) {
      if (state.meleeAbility != ability) {
         return false;
      }

      ActionTimeline action = state.action;
      if (action.running()) {
         if (!action.is(ability)) {
            reset(state);
         }

         return false;
      }

      if (state.meleeQueued) {
         state.meleeQueued = false;
         return true;
      }

      if (++state.meleeIdle > MeleeMoves.CHAIN_WINDOW) {
         reset(state);
      }

      return false;
   }

   /** A press during a running strike: buffered once the strike passed its chain tick, else dropped (no spam). */
   public static boolean buffer(ReinhardState state, MeleeMoves.Move move) {
      if (state.action.elapsed() >= move.chainFrom()) {
         state.meleeQueued = true;
         return true;
      }

      return false;
   }

   public static void reset(ReinhardState state) {
      state.meleeAbility = null;
      state.meleeStep = -1;
      state.meleeIdle = 0;
      state.meleeQueued = false;
      state.meleeHit.clear();
   }
}
