package dev.baranhan.viltrumitecore.client.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EffectQualityTest {
   @Test
   void parsesTheConfigValueLeniently() {
      assertEquals(EffectQuality.ULTRA, EffectQuality.parse(" Ultra ", EffectQuality.HIGH));
      assertEquals(EffectQuality.HIGH, EffectQuality.parse(null, EffectQuality.HIGH));
      assertEquals(EffectQuality.MEDIUM, EffectQuality.parse("potato", EffectQuality.MEDIUM));
      assertEquals("low", EffectQuality.LOW.id());
   }

   @Test
   void highIsTheGameAsDesignedAndUltraNeverAdapts() {
      assertEquals(1.0, EffectQuality.HIGH.spawn);
      assertEquals(1.0, EffectQuality.HIGH.lod);
      assertFalse(EffectQuality.HIGH.lodAlways);
      assertTrue(EffectQuality.LOW.lodAlways);
      assertFalse(EffectQuality.ULTRA.adaptive);
      assertTrue(EffectQuality.LOW.spawn < EffectQuality.MEDIUM.spawn);
   }
}
