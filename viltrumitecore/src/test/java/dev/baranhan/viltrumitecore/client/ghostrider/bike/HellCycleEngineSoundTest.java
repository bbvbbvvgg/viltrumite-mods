package dev.baranhan.viltrumitecore.client.ghostrider.bike;

import static dev.baranhan.viltrumitecore.client.ghostrider.bike.HellCycleEngineSound.leaves;
import static dev.baranhan.viltrumitecore.client.ghostrider.bike.HellCycleEngineSound.starts;
import static dev.baranhan.viltrumitecore.client.ghostrider.bike.HellCycleEngineSound.targetVolume;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.ghostrider.bike.HellCycleEngineSound.Layer;
import org.junit.jupiter.api.Test;

/** The Hell Cycle's engine loops play only while heard, so their subtitles name only what sounds. */
class HellCycleEngineSoundTest {
   private static final float IDLE_RPM = 0.12F;

   @Test
   void anIdlingBikeStartsOnlyTheLayersThatAreHeard() {
      assertTrue(starts(targetVolume(Layer.IDLE, IDLE_RPM, 0.0F, false, true)));
      assertTrue(starts(targetVolume(Layer.ROAR, IDLE_RPM, 0.0F, false, true)), "the hellfire roars even at a standstill");
      assertFalse(starts(targetVolume(Layer.LOW, IDLE_RPM, 0.0F, false, true)));
      assertFalse(starts(targetVolume(Layer.HIGH, IDLE_RPM, 0.0F, false, true)), "no 'Hell Cycle roars' at idle");
      assertFalse(starts(targetVolume(Layer.SKID, IDLE_RPM, 0.0F, false, true)), "no 'Tyres screech' while the tyres are still");
   }

   @Test
   void theScreechStartsWithTheSlideAndLeavesOnceFaded() {
      assertTrue(starts(targetVolume(Layer.SKID, 0.6F, 0.8F, true, true)));
      float level = targetVolume(Layer.SKID, 0.6F, 0.8F, true, true);
      int ticks = 0;
      float after = targetVolume(Layer.SKID, 0.6F, 0.8F, false, true);
      while (!leaves(after, level) && ticks < 100) {
         level += (after - level) * 0.2F; // the loop's fade-out rate
         ticks++;
      }
      assertTrue(ticks > 10 && ticks < 40, "the screech fades for about a second, then frees its channel: " + ticks);
      assertFalse(leaves(targetVolume(Layer.ROAR, 0.6F, 0.8F, false, true), 0.0F), "a layer that still sounds never leaves");
   }

   @Test
   void everyRevHasAnAudibleEngineLayer() {
      for (int i = 0; i <= 100; i++) {
         float r = i / 100.0F;
         boolean heard = starts(targetVolume(Layer.IDLE, r, 0.5F, false, true)) || starts(targetVolume(Layer.LOW, r, 0.5F, false, true))
            || starts(targetVolume(Layer.HIGH, r, 0.5F, false, true));
         assertTrue(heard, "the engine falls silent at rpm " + r);
      }
   }
}
