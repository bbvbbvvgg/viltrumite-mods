package dev.baranhan.viltrumitecore.hero.thor.combat;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Who the automatic sky lightning (#129) strikes after a successful
 * electric combo. OPEN (#75, #130, #149): "one target" / "the last struck
 * target" is not defined for chain and area hits. The default
 * {@link #SINGLE_TARGET_ONLY} is the one unambiguous case (B-B2 / B-M4):
 * exactly one distinct enemy struck by the combo, else no sky strike at all
 * (never a point, never Thor himself). The other options are dev switches
 * (PLACEHOLDER), not the author's decision.
 */
public enum SkyTargetPolicy {
   /** Default: the one enemy the combo struck; zero or several distinct enemies = no sky strike. */
   SINGLE_TARGET_ONLY,
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
         case SINGLE_TARGET_ONLY -> single(allHits.isEmpty() ? primaryHits : allHits);
         case LAST_PRIMARY_HIT -> primaryHits.isEmpty() ? last(allHits) : last(primaryHits);
         case LAST_ANY_HIT -> allHits.isEmpty() ? last(primaryHits) : last(allHits);
         case AIM_POINT -> null;
      };
   }

   /** Dev switches only: the sky strike may fall on a point without a struck enemy. */
   public boolean allowsPoint() {
      return this == AIM_POINT;
   }

   @Nullable
   private static UUID single(List<UUID> ids) {
      UUID only = null;
      for (UUID id : ids) {
         if (only == null) {
            only = id;
         } else if (!only.equals(id)) {
            return null;
         }
      }

      return only;
   }

   @Nullable
   private static UUID last(List<UUID> ids) {
      return ids.isEmpty() ? null : ids.get(ids.size() - 1);
   }
}
