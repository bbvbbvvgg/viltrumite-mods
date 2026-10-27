package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import javax.annotation.Nullable;

/**
 * Context controls: one key, a move id by state. Pure; the single resolver
 * for the server press, the client mouse claim and the panel grey-out. The
 * order of the checks is the priority of the contexts. It only routes: a
 * move does something only once a session registers it (ThorMoves).
 * Retired ids (guard, air strike, dash, teleport strike, dive slam, manual
 * lightning call, grab slam) are never returned.
 */
public final class ThorControls {
   private ThorControls() {
   }

   /** The move an input means right now, or null when it means nothing for Thor. */
   @Nullable
   public static ThorMove resolve(ThorInput input, ThorContext context) {
      return switch (input) {
         // No attacks in full flight.
         case STRIKE -> context.flying() ? null
            : context.mode() == ThorMode.ELECTRIC ? ThorMove.ELECTRIC_COMBO
            : context.hammer() == HammerState.IN_HAND ? ThorMove.HAMMER_COMBO : ThorMove.FIST_COMBO;
         // RMB routing is not part of the bridge: vanilla until its session defines it.
         case STANCE -> null;
         case HAMMER -> hammer(context);
         case POWER -> ThorMove.ELECTRIC_MODE;
         case MOBILITY -> context.hammer() == HammerState.THROWN ? ThorMove.HAMMER_PULL : null;
         case TELEPORT -> ThorMove.TELEPORT_SELECT;
         case ULTIMATE -> ThorMove.WRATH;
      };
   }

   /** MMB, the one decision point: grab window &gt; recall &gt; melee (enemy within 4 blocks) &gt; throw. */
   public static ThorMove hammer(ThorContext context) {
      if (context.grabWindow()) {
         return ThorMove.GRAB;
      } else if (context.hammer().away()) {
         return ThorMove.RECALL;
      }

      return context.enemyNear() ? ThorMove.HAMMER_STRIKE : ThorMove.HAMMER_THROW;
   }

   /** Mouse claim: only a button whose resolved move is authored is taken from vanilla. */
   @Nullable
   public static HeroAction claimedMouse(MouseButton button, ThorContext context) {
      ThorInput input = ThorInput.of(button);
      if (input == null) {
         return null;
      }

      ThorMove move = resolve(input, context);
      return move != null && move.authored() ? input.action() : null;
   }

   /** Can this input start any authored move in some context (panel slot lit)? */
   public static boolean anyAuthored(ThorInput input) {
      for (ThorMove move : candidates(input)) {
         if (move.authored()) {
            return true;
         }
      }

      return false;
   }

   /** Every move an input can resolve to, in resolver order (tests, grey-out). */
   public static ThorMove[] candidates(ThorInput input) {
      return switch (input) {
         case STRIKE -> new ThorMove[]{ThorMove.ELECTRIC_COMBO, ThorMove.HAMMER_COMBO, ThorMove.FIST_COMBO};
         case STANCE -> new ThorMove[0];
         case HAMMER -> new ThorMove[]{ThorMove.GRAB, ThorMove.RECALL, ThorMove.HAMMER_STRIKE, ThorMove.HAMMER_THROW};
         case POWER -> new ThorMove[]{ThorMove.ELECTRIC_MODE};
         case MOBILITY -> new ThorMove[]{ThorMove.HAMMER_PULL};
         case TELEPORT -> new ThorMove[]{ThorMove.TELEPORT_SELECT};
         case ULTIMATE -> new ThorMove[]{ThorMove.WRATH};
      };
   }
}
