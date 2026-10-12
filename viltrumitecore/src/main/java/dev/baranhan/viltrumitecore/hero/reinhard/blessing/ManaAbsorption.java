package dev.baranhan.viltrumitecore.hero.reinhard.blessing;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;

/**
 * Blessing of Mana Absorption (spec §4.7): the world's mana flows to him, so
 * any non-allied player within {@link ReinhardRules#MANA_RADIUS} refills his own
 * hero resource at half speed (shared seam HeroResourceRegen: the strongest
 * source wins, two Reinhards are still ×0.5). Mode-independent.
 */
public final class ManaAbsorption {
   private ManaAbsorption() {
   }

   /** Pure factor for another player at {@code distanceSqr}. */
   public static float factor(boolean sameEntity, double distanceSqr, boolean allied) {
      if (sameEntity || allied || distanceSqr > ReinhardRules.MANA_RADIUS * ReinhardRules.MANA_RADIUS) {
         return 1.0F;
      }

      return ReinhardRules.MANA_REGEN_FACTOR;
   }
}
