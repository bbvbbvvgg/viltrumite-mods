package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import javax.annotation.Nullable;

/**
 * Thor's seven physical inputs. Each one resolves to a different move by state
 * (ThorControls); the HeroAction is only the wire name of the key.
 */
public enum ThorInput {
   /** LMB. */
   STRIKE(HeroAction.PRIMARY_ATTACK),
   /** RMB. */
   STANCE(HeroAction.SECONDARY_USE),
   /** MMB (the heart key mapping, default middle mouse). */
   HAMMER(HeroAction.TOOL_CYCLE),
   /** Slot key 1 (default R): toggle the electric mode. */
   POWER(HeroAction.CONTEXT_POWER),
   /** Slot key 2 (default Y): pull to the thrown Mjölnir. */
   MOBILITY(HeroAction.CONTEXT_MOBILITY),
   /** Slot key 3 (Z, a temporary foundation default): the separate ultimate key. */
   ULTIMATE(HeroAction.ULTIMATE),
   /** Slot key 4 (default V): choose the teleport point. */
   TELEPORT(HeroAction.CONTEXT_TELEPORT);

   private final HeroAction action;

   ThorInput(HeroAction action) {
      this.action = action;
   }

   public HeroAction action() {
      return this.action;
   }

   private static final ThorInput[] VALUES = values();

   @Nullable
   public static ThorInput of(@Nullable HeroAction action) {
      for (ThorInput input : VALUES) {
         if (input.action == action) {
            return input;
         }
      }

      return null;
   }

   /** The wire action of a mouse input (STRIKE / STANCE / HAMMER): it must come through HeldInputs. */
   public static boolean mouseOnly(@Nullable HeroAction action) {
      ThorInput input = of(action);
      return input == STRIKE || input == STANCE || input == HAMMER;
   }

   /** Mouse button → input; the guard key (F) stays vanilla for Thor. */
   @Nullable
   public static ThorInput of(@Nullable MouseButton button) {
      if (button == null) {
         return null;
      }

      return switch (button) {
         case PRIMARY -> STRIKE;
         case SECONDARY -> STANCE;
         case MIDDLE -> HAMMER;
         default -> null;
      };
   }
}
