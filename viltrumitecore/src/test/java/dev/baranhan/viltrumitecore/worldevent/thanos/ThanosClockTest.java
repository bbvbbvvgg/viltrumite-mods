package dev.baranhan.viltrumitecore.worldevent.thanos;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ThanosClockTest {
   @Test
   void sixMinutesOfActiveTicks() {
      assertEquals(7200, ThanosRules.DURATION_TICKS);
      ThanosClock clock = new ThanosClock(ThanosRules.DURATION_TICKS);
      for (int i = 0; i < 7199; i++) {
         assertTrue(clock.advance(false));
      }

      assertFalse(clock.expired());
      assertEquals(1, clock.remaining());
      assertTrue(clock.advance(false));
      assertTrue(clock.expired());
      assertFalse(clock.advance(false), "never past the end");
      assertEquals(7200, clock.elapsed());
   }

   @Test
   void aStoppedWorldDoesNotCostTheHeroesTime() {
      ThanosClock clock = new ThanosClock(100);
      clock.advance(false);
      for (int i = 0; i < 50; i++) {
         assertFalse(clock.advance(true));
      }

      assertEquals(1, clock.elapsed());
   }

   @Test
   void stopFreezesTheValue() {
      ThanosClock clock = new ThanosClock(100);
      clock.advance(false);
      clock.stop();
      assertFalse(clock.advance(false));
      assertEquals(99, clock.remaining());
      clock.skipTo(5);
      assertEquals(99, clock.remaining(), "a stopped clock ignores skips");
   }

   @Test
   void finalMinuteIsTheLastSixtySeconds() {
      ThanosClock clock = new ThanosClock(ThanosRules.DURATION_TICKS);
      clock.skipTo(1201);
      assertFalse(clock.finalMinute());
      clock.advance(false);
      assertTrue(clock.finalMinute());
      clock.skipTo(-5);
      assertTrue(clock.expired());
      clock = new ThanosClock(ThanosRules.DURATION_TICKS);
      clock.skipTo(99999);
      assertEquals(0, clock.elapsed(), "skip never goes before the start");
   }

   @Test
   void hudClockRoundsUp() {
      assertEquals("6:00", ThanosRules.clock(7200));
      assertEquals("5:59", ThanosRules.clock(7180));
      assertEquals("0:01", ThanosRules.clock(1));
      assertEquals("0:00", ThanosRules.clock(0));
      assertEquals("1:00", ThanosRules.clock(1200));
      assertEquals("0:09", ThanosRules.clock(161));
   }

   @Test
   void defeatWinsTheLastTick() {
      assertEquals(ThanosOutcome.VICTORY, ThanosOutcome.resolve(true, true));
      assertEquals(ThanosOutcome.VICTORY, ThanosOutcome.resolve(true, false));
      assertEquals(ThanosOutcome.SNAP, ThanosOutcome.resolve(false, true));
      assertEquals(ThanosOutcome.FIGHT, ThanosOutcome.resolve(false, false));
   }

   @Test
   void lastTickOrderIsDeterministic() {
      // The event's END tick: defeat check, then advance, then expiry; a kill landing in the
      // same server tick as the 7200th advance is a victory.
      ThanosClock clock = new ThanosClock(10);
      for (int i = 0; i < 9; i++) {
         clock.advance(false);
      }

      boolean killedThisTick = true;
      clock.advance(false);
      assertTrue(clock.expired());
      assertEquals(ThanosOutcome.VICTORY, ThanosOutcome.resolve(killedThisTick, clock.expired()));
   }
}
