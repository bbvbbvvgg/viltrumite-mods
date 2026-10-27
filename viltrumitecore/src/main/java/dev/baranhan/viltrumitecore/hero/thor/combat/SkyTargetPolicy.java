package dev.baranhan.viltrumitecore.hero.thor.combat;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Who the automatic sky lightning (#129) strikes after a successful
 * electric combo. OPEN (#75, #130, #149): "one target" / "the last struck
 * target" is not defined for chain and area hits. Every option is a
 * PLACEHOLDER, configurable; none is the author's decision.
 */
public enum SkyTargetPolicy {
   /** The last enemy struck directly (not by a chain jump or an area). */
   LAST_PRIMARY_HIT,
   /** The last enemy struck by anything of the combo. */
   LAST_ANY_HIT,
   /** No entity: where the player aims. */
   AIM_POINT;

   /** Pure: the target id from the combo's hits (oldest first), or null for the aim point. */
   @Nullable
   public UUID select(List<UUID> primaryHits, List<UUID> allHits) {
      return switch (this) {
         case LAST_PRIMARY_HIT -> primaryHits.isEmpty() ? last(allHits) : last(primaryHits);
         case LAST_ANY_HIT -> allHits.isEmpty() ? last(primaryHits) : last(allHits);
         case AIM_POINT -> null;
      };
   }

   @Nullable
   private static UUID last(List<UUID> ids) {
      return ids.isEmpty() ? null : ids.get(ids.size() - 1);
   }
}
