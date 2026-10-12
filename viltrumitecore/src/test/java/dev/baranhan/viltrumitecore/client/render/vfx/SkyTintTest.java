package dev.baranhan.viltrumitecore.client.render.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyTintTest {
   private static final SkyTint.Tint DARK = new SkyTint.Tint(0.5F, 0.5F, 0.6F, 0.75F, 0.0F);
   private static final SkyTint.Tint GOLD = new SkyTint.Tint(1.0F, 0.9F, 0.5F, 0.1F, 1.0F);

   @Test
   void strongestTintWins() {
      SkyTint sky = new SkyTint();
      Object low = new Object();
      Object high = new Object();
      sky.push(low, 1, () -> GOLD);
      sky.push(high, 5, () -> DARK);
      sky.advance(10.0F);
      SkyTint.Tint now = sky.current();
      assertEquals(0.75F, now.darken(), 0.01F);
      assertEquals(0.0F, now.rain(), 0.01F);
      sky.remove(high);
      sky.advance(10.0F);
      assertEquals(0.1F, sky.current().darken(), 0.01F);
   }

   @Test
   void tintFadesOut() {
      SkyTint sky = new SkyTint();
      Object owner = new Object();
      sky.push(owner, 1, () -> DARK);
      sky.advance(10.0F);
      assertTrue(sky.active());
      sky.remove(owner);
      sky.advance(0.05F);
      float darken = sky.current().darken();
      assertTrue(darken > 0.0F && darken < 0.75F, "fades, not snaps: " + darken);
      sky.advance(10.0F);
      assertEquals(0.0F, sky.current().darken(), 1.0E-4F);
      assertFalse(sky.active());
   }

   @Test
   void mixIsLinearAndApplyDarkens() {
      SkyTint.Tint half = SkyTint.mix(SkyTint.NONE, DARK, 0.5F);
      assertEquals(0.75F, half.r(), 1.0E-4F);
      assertEquals(0.375F, half.darken(), 1.0E-4F);
      double[] rgb = SkyTint.apply(1.0, 1.0, 1.0, DARK);
      assertEquals(0.125, rgb[0], 1.0E-4);
      assertEquals(0.15, rgb[2], 1.0E-4);
   }
}
