package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import javax.annotation.Nullable;

/**
 * Thor's six physical inputs. Each one resolves to a different move by state
 * (ThorControls); the HeroAction is only the wire name of the key.
 */
public enum ThorInput {
   /** LMB. */
   STRIKE(HeroAction.PRIMARY_ATTACK),
   /** RMB. */
   STANCE(HeroAction.SECONDARY_USE),
   /** MMB (the heart key mapping, default middle mouse). */
   HAMMER(HeroAction.TOOL_CYCLE),
   /** Slot key 1 (default R). */
   POWER(HeroAction.CONTEXT_POWER),
   /** Slot key 2 (default Y). */
   MOBILITY(HeroAction.CONTEXT_MOBILITY),
   /** Slot key 3 (default Z): the separate ultimate key. */
   ULTIMATE(HeroAction.ULTIMATE);

   private final HeroAction action;

   ThorInput(HeroAction action) {
      this.action = action;
   }

   public HeroAction action() {
      return this.action;
   }

   @Nullable
   public static ThorInput of(@Nullable HeroAction action) {
      for (ThorInput input : values()) {
         if (input.action == action) {
            return input;
         }
      }

      return null;
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
