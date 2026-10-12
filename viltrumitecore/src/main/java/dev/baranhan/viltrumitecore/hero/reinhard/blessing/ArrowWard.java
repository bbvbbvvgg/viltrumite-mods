package dev.baranhan.viltrumitecore.hero.reinhard.blessing;

import dev.baranhan.viltrumitecore.hero.ProjectileResponse;

/**
 * Divine Protection of Arrow Protection (spec §4.3): no projectile of any kind
 * hits him — it is turned aside (DEFLECT). The Restrained palm windows (stage 3)
 * win over the passive: reflect beats catch. Mode-independent; the response
 * never depends on the projectile class.
 */
public final class ArrowWard {
   private ArrowWard() {
   }

   public static ProjectileResponse response(boolean catchOpen, boolean reflectOpen) {
      if (reflectOpen) {
         return ProjectileResponse.REFLECT;
      }

      return catchOpen ? ProjectileResponse.CATCH : ProjectileResponse.DEFLECT;
   }
}
