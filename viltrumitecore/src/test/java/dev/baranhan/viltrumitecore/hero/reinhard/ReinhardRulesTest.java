package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/** Stage 1 rules: resist, HP floor, mode cycle (spec §3.1, §5). */
class ReinhardRulesTest {
   @Test
   void resistLeavesOneThousandth() {
      assertEquals(1.0F, ReinhardRules.resist(1000.0F), 1.0E-4F);
   }

   @Test
   void floorKeepsOneHp() {
      assertEquals(19.0F, ReinhardRules.floor(20.0F, 50.0F), 1.0E-6F);
   }

   @Test
   void floorAtOneHpIsZero() {
      assertEquals(0.0F, ReinhardRules.floor(1.0F, 5.0F), 1.0E-6F);
   }

   @Test
   void floorHandlesMaxValue() {
      assertEquals(19.0F, ReinhardRules.floor(20.0F, Float.MAX_VALUE), 1.0E-6F);
      assertEquals(19.0F, ReinhardRules.floor(20.0F, Float.POSITIVE_INFINITY), 1.0E-6F);
      assertEquals(0.0F, ReinhardRules.floor(20.0F, Float.NaN), 1.0E-6F);
   }

   @Test
   void modeCycles() {
      assertSame(ReinhardMode.KNIGHT, ReinhardMode.RESTRAINED.next());
      assertSame(ReinhardMode.HOLY_SWORD, ReinhardMode.KNIGHT.next());
      assertSame(ReinhardMode.RESTRAINED, ReinhardMode.HOLY_SWORD.next());
   }

   @Test
   void modeKeyRoundTrip() {
      for (ReinhardMode mode : ReinhardMode.values()) {
         assertSame(mode, ReinhardMode.fromKey(mode.key()));
         assertEquals(mode.ordinal(), mode.page());
      }
   }

   @Test
   void unknownModeKeyIsRestrained() {
      assertSame(ReinhardMode.RESTRAINED, ReinhardMode.fromKey("nope"));
      assertSame(ReinhardMode.RESTRAINED, ReinhardMode.fromKey(null));
   }
}
