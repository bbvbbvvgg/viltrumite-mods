package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.baranhan.viltrumitecore.hero.reinhard.blessing.ManaAbsorption;
import org.junit.jupiter.api.Test;

/** Resource regen seam + Mana Absorption (spec §4.7). */
class HeroResourceRegenTest {
   @Test
   void strongestWins() {
      // Two Reinhards near one Iron Man: x0.5, not x0.25.
      float reinhard = ManaAbsorption.factor(false, 10.0 * 10.0, false);
      assertEquals(0.5F, HeroResourceRegen.combine(new float[]{reinhard, reinhard}));
      assertEquals(0.3F, HeroResourceRegen.combine(new float[]{0.5F, 0.3F, 1.0F}));
      assertEquals(0.0F, HeroResourceRegen.combine(new float[]{-2.0F}));
   }

   @Test
   void noReinhardIsOne() {
      assertEquals(1.0F, HeroResourceRegen.combine(new float[0]));
      assertEquals(1.0F, HeroResourceRegen.combine(new float[]{1.0F, 1.0F}));
      assertEquals(1.0F, new HumanHero().foreignResourceRegenFactor(null, null));
   }

   @Test
   void alliesUnaffected() {
      assertEquals(1.0F, ManaAbsorption.factor(false, 4.0, true));
      // Reinhard himself is never his own source.
      assertEquals(1.0F, ManaAbsorption.factor(true, 0.0, false));
   }

   @Test
   void outsideRadiusUnaffected() {
      assertEquals(0.5F, ManaAbsorption.factor(false, 24.0 * 24.0, false));
      assertEquals(1.0F, ManaAbsorption.factor(false, 24.1 * 24.1, false));
      assertEquals(1.0F, ManaAbsorption.factor(false, 30.0 * 30.0, false));
   }
}
