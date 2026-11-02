package dev.baranhan.viltrumitecore.t1000;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The two-pool damage model: what scratches, what hurts, what really kills (design §4). */
class T1000DamageTest {
   private static final float CORE = T1000Damage.MAX_CORE;
   private static final float FULL = T1000Damage.MAX_INTEGRITY;

   @Test
   void immuneAndEmptyHitsDoNothing() {
      T1000Damage.Split s = T1000Damage.split(50.0F, T1000Damage.Kind.IMMUNE, FULL, 0.0F, false, CORE);
      assertEquals(0.0F, s.integrity());
      assertEquals(0.0F, s.core());
      T1000Damage.Split nan = T1000Damage.split(Float.NaN, T1000Damage.Kind.NORMAL, FULL, 0.0F, false, CORE);
      assertEquals(0.0F, nan.core());
      assertEquals(0.0F, T1000Damage.split(-3.0F, T1000Damage.Kind.NORMAL, FULL, 0.0F, false, CORE).core());
   }

   @Test
   void theShellTakesNormalHitsAndOnlyABleedReachesTheCore() {
      T1000Damage.Split s = T1000Damage.split(10.0F, T1000Damage.Kind.NORMAL, FULL, 0.0F, false, CORE);
      assertEquals(10.0F, s.integrity(), 1.0E-4F);
      assertEquals(10.0F * T1000Damage.BLEED, s.core(), 1.0E-4F);
      assertFalse(s.heavy());
      assertFalse(s.shatter());
   }

   @Test
   void noSingleBlowDestroysAHealthyBody() {
      for (T1000Damage.Kind k : new T1000Damage.Kind[]{T1000Damage.Kind.NORMAL, T1000Damage.Kind.HEAVY, T1000Damage.Kind.HEAT, T1000Damage.Kind.COLD}) {
         T1000Damage.Split s = T1000Damage.split(10000.0F, k, FULL, 0.0F, false, CORE);
         assertTrue(s.core() <= CORE * T1000Damage.CORE_CAP + 1.0E-3F, k + " took " + s.core());
      }
      // /kill and the void are absolute
      assertTrue(T1000Damage.split(10000.0F, T1000Damage.Kind.ABSOLUTE, FULL, 0.0F, false, CORE).core() >= CORE);
   }

   @Test
   void heatIsAWeakness() {
      float normal = T1000Damage.split(10.0F, T1000Damage.Kind.NORMAL, FULL, 0.0F, false, CORE).core();
      float heat = T1000Damage.split(10.0F, T1000Damage.Kind.HEAT, FULL, 0.0F, false, CORE).core();
      assertTrue(heat > normal * 4.0F, "heat " + heat + " vs " + normal);
   }

   @Test
   void heavyHitsDeformAndBleedMore() {
      T1000Damage.Split s = T1000Damage.split(T1000Damage.HEAVY_HIT, T1000Damage.Kind.NORMAL, FULL, 0.0F, false, CORE);
      assertTrue(s.heavy());
      assertEquals(T1000Damage.HEAVY_HIT * T1000Damage.HEAVY_BLEED, s.core(), 1.0E-4F);
      assertTrue(T1000Damage.split(5.0F, T1000Damage.Kind.HEAVY, FULL, 0.0F, false, CORE).heavy());
   }

   @Test
   void coldBuildsUpAndAFrozenBodyShatters() {
      T1000Damage.Split cold = T1000Damage.split(10.0F, T1000Damage.Kind.COLD, FULL, 0.0F, false, CORE);
      assertTrue(cold.cold() > 0.0F);
      T1000Damage.Split shatter = T1000Damage.split(T1000Damage.SHATTER_HIT, T1000Damage.Kind.NORMAL, FULL, T1000Damage.FROZEN, false, CORE);
      assertTrue(shatter.shatter());
      assertTrue(shatter.core() >= CORE * 0.4F - 1.0E-3F);
      assertEquals(-T1000Damage.FROZEN, shatter.cold(), 1.0E-4F);
      // a weak tap does not shatter, heat does not shatter (it thaws)
      assertFalse(T1000Damage.split(T1000Damage.SHATTER_HIT - 1.0F, T1000Damage.Kind.NORMAL, FULL, T1000Damage.FROZEN, false, CORE).shatter());
      assertFalse(T1000Damage.split(20.0F, T1000Damage.Kind.HEAT, FULL, T1000Damage.FROZEN, false, CORE).shatter());
   }

   @Test
   void aPuddleHasNoShell() {
      T1000Damage.Split up = T1000Damage.split(10.0F, T1000Damage.Kind.NORMAL, FULL, 0.0F, false, CORE);
      T1000Damage.Split puddle = T1000Damage.split(10.0F, T1000Damage.Kind.NORMAL, FULL, 0.0F, true, CORE);
      assertEquals(0.0F, puddle.integrity());
      assertTrue(puddle.core() > up.core() * 2.0F);
   }

   @Test
   void whatTheShellCannotHoldReachesTheCore() {
      T1000Damage.Split s = T1000Damage.split(20.0F, T1000Damage.Kind.NORMAL, 4.0F, 0.0F, false, CORE);
      assertEquals(4.0F, s.integrity(), 1.0E-4F);
      assertTrue(s.core() > 20.0F * T1000Damage.HEAVY_BLEED);
   }

   @Test
   void regenerationNeedsQuietAndStopsInHeatOrFrost() {
      assertEquals(50.0F, T1000Damage.regen(50.0F, 0, false, false, 0.0F));
      assertTrue(T1000Damage.regen(50.0F, T1000Damage.REGEN_DELAY, false, false, 0.0F) > 50.0F);
      assertEquals(50.0F, T1000Damage.regen(50.0F, 1000, false, true, 0.0F));
      assertEquals(50.0F, T1000Damage.regen(50.0F, 1000, false, false, T1000Damage.FROZEN));
      assertTrue(T1000Damage.regen(50.0F, 0, true, false, 0.0F) > T1000Damage.regen(50.0F, 1000, false, false, 0.0F));
      assertTrue(T1000Damage.regen(FULL, 1000, false, false, 0.0F) <= FULL);
      assertEquals(0.0F, T1000Damage.coreRegen(true, 10));
      assertTrue(T1000Damage.coreRegen(true, 400) > T1000Damage.coreRegen(false, 400));
      assertEquals(0.0F, T1000Damage.thaw(0.001F));
   }
}
