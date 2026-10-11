package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HeroPassiveEffectTest {
   @Test
   void appliesWhenMissingOrWeaker() {
      assertTrue(HeroPassiveEffect.needsRefresh(0, 0, false, 1));
      assertTrue(HeroPassiveEffect.needsRefresh(0, 6000, true, 1), "a Regeneration I potion is upgraded");
   }

   @Test
   void neverPinsTheDuration() {
      // Regeneration heals on duration % interval: refreshing every tick would freeze it.
      for (int duration = HeroPassiveEffect.WINDOW; duration > HeroPassiveEffect.REFRESH_AT; duration--) {
         assertFalse(HeroPassiveEffect.needsRefresh(1, duration, true, 1), "no refresh at " + duration);
      }

      assertTrue(HeroPassiveEffect.needsRefresh(1, HeroPassiveEffect.REFRESH_AT, true, 1));
   }

   @Test
   void strongerEffectWins() {
      assertFalse(HeroPassiveEffect.needsRefresh(2, 5, true, 1));
   }
}
