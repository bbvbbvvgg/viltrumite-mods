package dev.baranhan.viltrumitecore.hero.thor.combat;

import java.util.List;
import java.util.UUID;

/**
 * One action seen by the electric combo recognizer: what was done, how the
 * player moved, the context, and who the action itself hit (for the sky
 * lightning target). The three MMB results are different actions (#147).
 */
public record ElectricAction(Kind kind, MoveDirection direction, boolean airborne, boolean hammerInHand, long tick, List<UUID> primaryHits,
   List<UUID> allHits) {
   public enum Kind {
      /** LMB discharge. */
      LMB,
      /** RMB wave (tap). */
      RMB,
      /** RMB held into the storm. */
      HOLD_RMB,
      /** MMB melee branch (session B's HAMMER_STRIKE). */
      MMB_STRIKE,
      /** MMB throw (session A's THROW event). */
      MMB_THROW,
      /** MMB recall (session A's RECALL event). */
      MMB_RECALL
   }

   public ElectricAction {
      primaryHits = List.copyOf(primaryHits);
      allHits = List.copyOf(allHits);
   }

   public static ElectricAction of(Kind kind, MoveDirection direction, boolean airborne, boolean hammerInHand, long tick) {
      return new ElectricAction(kind, direction, airborne, hammerInHand, tick, List.of(), List.of());
   }
}
