package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** heroFlags bit layout for every Reinhard stage. */
class ReinhardFlagsTest {
   @Test
   void flagsDoNotOverlap() {
      int[] bits = {ReinhardFlags.REID_DRAWN, ReinhardFlags.REID_DRAWING, ReinhardFlags.REID_SHEATHING, ReinhardFlags.PARRY_POSE,
         ReinhardFlags.CATCH_WINDOW, ReinhardFlags.REFLECT_WINDOW, ReinhardFlags.AIR_STEP_PULSE, ReinhardFlags.GRAB_HOLDING,
         ReinhardFlags.HIT_STOP, ReinhardFlags.ERASURE_CASTER, ReinhardFlags.CHOP_LEFT_FORCED};
      int seen = ReinhardFlags.MODE_MASK;
      for (int bit : bits) {
         assertTrue(bit >= 2 && bit <= 12, "bit " + bit);
         assertEquals(0, seen & (1 << bit), "overlap at " + bit);
         seen |= 1 << bit;
      }
   }

   @Test
   void withModeKeepsOtherBits() {
      int flags = (1 << ReinhardFlags.REID_DRAWN) | (1 << ReinhardFlags.HIT_STOP);
      for (ReinhardMode mode : ReinhardMode.values()) {
         int next = ReinhardFlags.withMode(flags, mode);
         assertEquals(mode.ordinal(), ReinhardFlags.mode(next));
         assertTrue(ReinhardFlags.has(next, ReinhardFlags.REID_DRAWN));
         assertTrue(ReinhardFlags.has(next, ReinhardFlags.HIT_STOP));
      }
   }

   @Test
   void extraIndexesAfterAbilities() {
      assertEquals(14, ReinhardFlags.EXTRA_MODE_CD);
      assertEquals(15, ReinhardFlags.EXTRA_PHOENIX);
   }
}
