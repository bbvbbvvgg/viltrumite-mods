package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.homelander.HomelanderHero;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardFlags;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardHero;
import org.junit.jupiter.api.Test;

class ChopStyleTest {
   @Test
   void defaultEqualsLegacyConstants() {
      ChopStyle d = ChopStyle.DEFAULT;
      assertFalse(d.forceLeft());
      assertEquals(1.0F, d.arcScale());
      assertEquals(0xFFFFFF, d.trailInner());
      assertEquals(0xFFC8C8, d.trailOuter());
      assertFalse(d.ripple());
      assertEquals(1.0F, d.soundVolume());
      assertEquals(1.0F, d.soundPitch());
      // Today's sounds (PlayerEntityCoreMixin.tickChop sweep, ChopImpactManager crit).
      assertEquals(1.5F, d.sweepVolume());
      assertEquals(0.5F, d.sweepPitch());
      assertEquals(1.2F, d.critVolume());
      assertEquals(0.8F, d.critPitch());
   }

   @Test
   void homelanderUsesDefault() {
      assertSame(ChopStyle.DEFAULT, new HomelanderHero().chopStyle(null));
   }

   @Test
   void reinhardStyleValues() {
      ChopStyle sheathed = ReinhardHero.chopStyle(0);
      assertFalse(sheathed.forceLeft());
      assertEquals(2.0F, sheathed.arcScale());
      assertEquals(0xFFFFFF, sheathed.trailInner());
      assertEquals(0xB0142A, sheathed.trailOuter());
      assertTrue(sheathed.ripple());
      assertEquals(1.4F, sheathed.soundVolume(), 1.0E-6F);
      assertEquals(0.8F, sheathed.soundPitch(), 1.0E-6F);
      assertTrue(ReinhardHero.chopStyle(1 << ReinhardFlags.REID_DRAWN).forceLeft());
      assertSame(ReinhardHero.chopStyle(0), ReinhardHero.chopStyle(0), "no allocation per lookup");
   }

   @Test
   void serverForcesLeft() {
      ChopStyle drawn = ReinhardHero.chopStyle(1 << ReinhardFlags.REID_DRAWN);
      assertTrue(ChopStyle.resolveLeft(false, drawn), "a forged right chop becomes left");
      assertTrue(ChopStyle.resolveLeft(true, drawn));
      assertFalse(ChopStyle.resolveLeft(false, ChopStyle.DEFAULT));
      assertTrue(ChopStyle.resolveLeft(true, ChopStyle.DEFAULT));
   }
}
