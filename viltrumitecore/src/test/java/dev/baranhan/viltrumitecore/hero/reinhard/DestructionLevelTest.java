package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroDebris;
import org.junit.jupiter.api.Test;

/** Spec §5: destruction per mode. */
class DestructionLevelTest {
   private static final HeroDebris.Eruption BIG = new HeroDebris.Eruption(6.0, 3.0, 1.0, 120, 0.8, 1.0);

   @Test
   void restrainedNeverBreaks() {
      DestructionLevel level = DestructionLevel.of(ReinhardMode.RESTRAINED);
      assertEquals(DestructionLevel.NONE, level);
      assertFalse(level.mayBreak());
      assertNull(level.scaled(BIG));
   }

   @Test
   void knightModerate() {
      DestructionLevel level = DestructionLevel.of(ReinhardMode.KNIGHT);
      assertEquals(DestructionLevel.MODERATE, level);
      assertTrue(level.mayBreak());
      HeroDebris.Eruption spec = level.scaled(BIG);
      assertNotNull(spec);
      assertTrue(spec.depth() <= DestructionLevel.MODERATE_MAX_DEPTH, "top layers only");
      assertTrue(spec.maxFlying() <= DestructionLevel.MODERATE_MAX_FLYING, "few flying blocks");
      assertTrue(spec.radius() <= DestructionLevel.MODERATE_MAX_RADIUS);
   }

   @Test
   void holySwordMassive() {
      DestructionLevel level = DestructionLevel.of(ReinhardMode.HOLY_SWORD);
      assertEquals(DestructionLevel.MASSIVE, level);
      assertTrue(level.mayBreak());
      assertEquals(BIG, level.scaled(BIG));
   }
}
