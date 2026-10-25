package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.reinhard.blessing.Detox;
import net.minecraft.world.effect.MobEffectCategory;
import org.junit.jupiter.api.Test;

/** Blessing of Detoxification (spec §4.6). */
class DetoxTest {
   @Test
   void harmfulBlocked() {
      // Poison, wither, darkness, nausea, slowness, hunger (pufferfish, stew, Warden) are all HARMFUL in 1.20.1.
      assertTrue(Detox.blocks(MobEffectCategory.HARMFUL, false));
      assertTrue(Detox.blocks(MobEffectCategory.HARMFUL, true));
   }

   @Test
   void beneficialPasses() {
      // Speed potion; the hidden hero passives (HeroPassiveEffect) are beneficial too.
      assertFalse(Detox.blocks(MobEffectCategory.BENEFICIAL, false));
   }

   @Test
   void neutralPasses() {
      // Glowing from a spectral arrow is NEUTRAL: other players' outlines still work.
      assertFalse(Detox.blocks(MobEffectCategory.NEUTRAL, false));
   }

   @Test
   void poisonTaggedModEffectBlocked() {
      assertTrue(Detox.blocks(MobEffectCategory.NEUTRAL, true));
      assertTrue(Detox.blocks(MobEffectCategory.BENEFICIAL, true));
   }
}
