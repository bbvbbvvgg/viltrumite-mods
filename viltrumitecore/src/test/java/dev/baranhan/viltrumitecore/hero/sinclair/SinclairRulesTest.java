package dev.baranhan.viltrumitecore.hero.sinclair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.sinclair.ability.Prestige;
import org.junit.jupiter.api.Test;

/** Spec §9 numbers and the pure rules. */
class SinclairRulesTest {
   @Test
   void specNumbers() {
      assertEquals(6.0, SinclairRules.BONUS_HEALTH);
      assertEquals(4.0, SinclairRules.BONUS_ARMOR);
      assertEquals(2.0F, SinclairRules.SPARK_DAMAGE);
      assertEquals(3.2F, SinclairRules.SPARK_SPEED);
      assertEquals(48.0F, SinclairRules.SPARK_RANGE);
      assertEquals(12, SinclairRules.MAGAZINE);
      assertEquals(32, SinclairRules.RELOAD_TICKS);
      assertEquals(160, SinclairRules.BOLT_COOLDOWN);
      assertEquals(600, SinclairRules.ASSIST_COOLDOWN);
      assertEquals(700, SinclairRules.HEX_COOLDOWN);
      assertEquals(400, SinclairRules.MIS_COOLDOWN);
      assertEquals(1400, SinclairRules.ULT_COOLDOWN);
      assertEquals(600, SinclairRules.ULT_UNUSED_COOLDOWN);
      assertEquals(240, SinclairRules.ULT_WINDOW);
      assertEquals(60, SinclairRules.OVATION_REFUND);
      assertEquals(3.0F, SinclairRules.VEIL_STRIKE_BONUS);
   }

   @Test
   void burstTiming() {
      // 2 sparks 3 t apart, then 9 t to the next burst.
      assertEquals(12, Prestige.BURST_PERIOD);
   }

   @Test
   void boltDamageRamps() {
      assertEquals(3.0F, SinclairRules.boltDamage(0), 1.0E-6F);
      assertEquals(5.0F, SinclairRules.boltDamage(20), 1.0E-6F);
      assertEquals(7.0F, SinclairRules.boltDamage(40), 1.0E-6F);
      assertEquals(7.0F, SinclairRules.boltDamage(44), 1.0E-6F);
      assertEquals(3.0F, SinclairRules.boltDamage(-5), 1.0E-6F);
   }

   @Test
   void swapRules() {
      assertFalse(SinclairRules.swapAllowed(0, 9, 999));
      assertTrue(SinclairRules.swapAllowed(0, 10, 999));
      assertFalse(SinclairRules.swapAllowed(1, 60, 39));
      assertTrue(SinclairRules.swapAllowed(1, 60, 40));
      assertFalse(SinclairRules.swapAllowed(2, 150, 100));
   }

   @Test
   void hexArea() {
      assertEquals(50, SinclairRules.hexTicks(true));
      assertEquals(70, SinclairRules.hexTicks(false));
      assertTrue(SinclairRules.inHexArea(3.4, 0.0, 0.0));
      assertTrue(SinclairRules.inHexArea(2.4, 2.5, 2.4));
      assertFalse(SinclairRules.inHexArea(2.5, 0.0, 2.5));
      assertFalse(SinclairRules.inHexArea(0.0, 2.6, 0.0));
      assertFalse(SinclairRules.inHexArea(0.0, -2.6, 0.0));
   }

   @Test
   void ovationNeverBelowZero() {
      assertEquals(1340, SinclairRules.ovation(1400));
      assertEquals(0, SinclairRules.ovation(30));
      assertEquals(0, SinclairRules.ovation(0));
   }

   @Test
   void actPowerClamp() {
      assertEquals(0.6F, SinclairRules.actPower(0.1F));
      assertEquals(1.2F, SinclairRules.actPower(5.0F));
      assertEquals(1.0F, SinclairRules.actPower(Float.NaN));
      assertEquals(0.9F, SinclairRules.actPower(0.9F));
   }

   @Test
   void cone() {
      assertTrue(SinclairRules.inCone(0, 0, 1, 0, 0, 10, 6.0));
      assertTrue(SinclairRules.inCone(0, 0, 1, Math.tan(Math.toRadians(5.9)), 0, 1, 6.0));
      assertFalse(SinclairRules.inCone(0, 0, 1, Math.tan(Math.toRadians(6.5)), 0, 1, 6.0));
      assertFalse(SinclairRules.inCone(0, 0, 1, 0, 0, -1, 6.0));
      assertFalse(SinclairRules.inCone(0, 0, 0, 0, 0, 1, 6.0));
   }
}
