package dev.baranhan.viltrumitecore.worldevent.thanos;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ThanosDamageCapTest {
   static final float MAX = 4000.0F;

   @Test
   void oneShotIsClampedToTheHitCap() {
      ThanosDamageCap cap = new ThanosDamageCap();
      assertEquals(100.0F, cap.allow(Float.MAX_VALUE, MAX), 0.01F);
      assertEquals(10.0F, cap.allow(10.0F, MAX), 0.01F);
      assertEquals(0.0F, cap.allow(-3.0F, MAX));
      assertEquals(0.0F, cap.allow(Float.NaN, MAX));
   }

   @Test
   void theSecondWindowLimitsBursts() {
      ThanosDamageCap cap = new ThanosDamageCap();
      float second = ThanosRules.secondCap(MAX);
      float taken = 0.0F;
      for (int i = 0; i < 10; i++) {
         float a = cap.allow(100.0F, MAX);
         cap.record(a);
         taken += a;
      }

      assertEquals(second, taken, 0.01F);
      assertEquals(0.0F, cap.allow(50.0F, MAX));
      for (int i = 0; i < ThanosRules.TPS; i++) {
         cap.tick();
      }

      assertTrue(cap.allow(50.0F, MAX) > 0.0F, "the window rolls over after a second");
   }

   @Test
   void minimumKillTimeIsLong() {
      // Even perfect damage needs max / secondCap seconds: 40 s solo.
      assertTrue(MAX / ThanosRules.secondCap(MAX) >= 40.0F);
   }

   @Test
   void recentDamageFeedsTheRewind() {
      ThanosDamageCap cap = new ThanosDamageCap();
      cap.record(100.0F);
      for (int i = 0; i < ThanosRules.REWIND_WINDOW - 1; i++) {
         cap.tick();
      }

      assertEquals(100.0F, cap.recent(), 0.01F);
      cap.tick();
      assertEquals(0.0F, cap.recent(), 0.01F);
      assertEquals(50.0F, ThanosRules.rewindHeal(100.0F, MAX), 0.01F);
      assertEquals(240.0F, ThanosRules.rewindHeal(10000.0F, MAX), 0.01F);
   }

   @Test
   void trailRewindsThreeSeconds() {
      ThanosTrail trail = new ThanosTrail(ThanosRules.REWIND_BACK + 1);
      assertNull(trail.back(3));
      for (int i = 0; i < 100; i++) {
         trail.push(i, 64, -i);
      }

      assertArrayEquals(new double[]{99, 64, -99}, trail.back(0));
      assertArrayEquals(new double[]{39, 64, -39}, trail.back(ThanosRules.REWIND_BACK));
      assertArrayEquals(new double[]{39, 64, -39}, trail.back(500), "clamped to the oldest sample");
   }


   /** The windows keep server time: two calls in one tick count once, a held body still lets old hits go. */
   @Test
   void windowsFollowServerTime() {
      ThanosDamageCap cap = new ThanosDamageCap();
      float max = 3000.0F;
      cap.advanceTo(100);
      cap.record(cap.allow(1000.0F, max));
      float first = cap.lastSecond();
      assertTrue(first > 0.0F);
      assertEquals(0.0F, cap.allow(10.0F, max), 1.0E-6F, "the second is full");
      cap.advanceTo(101);
      cap.advanceTo(101);
      cap.advanceTo(101);
      assertEquals(first, cap.lastSecond(), 1.0E-6F, "repeated calls in one tick do not roll the window");
      cap.advanceTo(100 + ThanosRules.TPS);
      assertEquals(0.0F, cap.lastSecond(), 1.0E-6F, "one second later the hit left the window");
      assertTrue(cap.recent() > 0.0F, "but it is still recent for the rewind");
      // a long freeze (time stop holding the body, then one big jump): no lock, both windows empty
      cap.advanceTo(100 + 60 * ThanosRules.TPS);
      assertEquals(0.0F, cap.recent(), 1.0E-6F);
      assertTrue(cap.allow(1000.0F, max) > 0.0F, "the caps never lock him");
      // a clock that goes back (a new level time) only re-anchors
      cap.advanceTo(5);
      cap.record(cap.allow(50.0F, max));
      cap.advanceTo(6);
      assertEquals(50.0F, cap.lastSecond(), 1.0E-6F);
   }
}
