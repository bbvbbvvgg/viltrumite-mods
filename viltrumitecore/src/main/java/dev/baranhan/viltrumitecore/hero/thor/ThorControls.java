package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import javax.annotation.Nullable;

/**
 * Context controls (spec §5): one key, different moves by state. Pure; the
 * single source for the server press, the client mouse claim and the panel
 * grey-out. Order of the checks is the priority of the contexts.
 */
public final class ThorControls {
   private ThorControls() {
   }

   /** The move an input means right now, or null when it means nothing for Thor. */
   @Nullable
   public static ThorMove resolve(ThorInput input, ThorContext context) {
      boolean inHand = context.hammer() == HammerState.IN_HAND;
      return switch (input) {
         case STRIKE -> {
            if (context.grabbing()) {
               yield context.sneaking() ? ThorMove.FATALITY : ThorMove.GRAB_SLAM;
            } else if (context.guarding()) {
               yield ThorMove.HAMMER_THROW;
            } else if (context.flying()) {
               yield ThorMove.AIR_STRIKE;
            } else if (context.mode() == ThorMode.ELECTRIC) {
               yield ThorMove.ELECTRIC_COMBO;
            }

            yield inHand ? ThorMove.HAMMER_COMBO : ThorMove.FIST_COMBO;
         }
         case STANCE -> inHand ? ThorMove.GUARD : context.hasTarget() ? ThorMove.GRAB : null;
         case HAMMER -> switch (context.hammer()) {
            case IN_HAND -> ThorMove.HOLSTER;
            case HOLSTERED -> ThorMove.DRAW;
            case THROWN, RETURNING -> ThorMove.RECALL;
         };
         case POWER -> context.mode() == ThorMode.ELECTRIC && inHand && !context.sneaking() ? ThorMove.LIGHTNING_CALL : ThorMove.ELECTRIC_MODE;
         case MOBILITY -> context.airborne() ? ThorMove.DIVE_SLAM : context.hasTarget() ? ThorMove.TELEPORT_STRIKE : ThorMove.LIGHTNING_DASH;
         case ULTIMATE -> ThorMove.WRATH;
      };
   }

   /**
    * Mouse claim: only a button whose move is shipped is taken from vanilla.
    * LMB / RMB keep vanilla attack and use until their moves ship (stage 2).
    */
   @Nullable
   public static HeroAction claimedMouse(MouseButton button, ThorContext context) {
      ThorInput input = ThorInput.of(button);
      if (input == null) {
         return null;
      }

      ThorMove move = resolve(input, context);
      return move != null && move.shipped() ? input.action() : null;
   }

   /** Can this input start any shipped move in some context (panel slot lit)? */
   public static boolean anyShipped(ThorInput input) {
      for (ThorMove move : candidates(input)) {
         if (move.shipped()) {
            return true;
         }
      }

      return false;
   }

   /** Every move an input can resolve to (documentation, tests, grey-out). */
   public static ThorMove[] candidates(ThorInput input) {
      return switch (input) {
         case STRIKE -> new ThorMove[]{ThorMove.FATALITY, ThorMove.GRAB_SLAM, ThorMove.HAMMER_THROW, ThorMove.AIR_STRIKE, ThorMove.ELECTRIC_COMBO,
            ThorMove.HAMMER_COMBO, ThorMove.FIST_COMBO};
         case STANCE -> new ThorMove[]{ThorMove.GUARD, ThorMove.GRAB};
         case HAMMER -> new ThorMove[]{ThorMove.HOLSTER, ThorMove.DRAW, ThorMove.RECALL};
         case POWER -> new ThorMove[]{ThorMove.LIGHTNING_CALL, ThorMove.ELECTRIC_MODE};
         case MOBILITY -> new ThorMove[]{ThorMove.DIVE_SLAM, ThorMove.TELEPORT_STRIKE, ThorMove.LIGHTNING_DASH};
         case ULTIMATE -> new ThorMove[]{ThorMove.WRATH};
      };
   }
}
