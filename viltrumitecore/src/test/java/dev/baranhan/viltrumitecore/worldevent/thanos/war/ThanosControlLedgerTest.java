package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import static dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosControlLedger.IMMUNITY;
import static dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosControlLedger.WINDOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Holds on a hero (time stop, compulsion, Maw's and Cull's grabs...) never chain: full, half, a
 * quarter within a window, then a pause in which nothing holds him.
 */
class ThanosControlLedgerTest {
   private static final UUID A = new UUID(0L, 1L);
   private static final UUID B = new UUID(0L, 2L);

   @Test
   void fullHalfQuarterThenImmune() {
      ThanosControlLedger ledger = new ThanosControlLedger();
      assertTrue(ledger.controllable(A, 0));
      assertEquals(60, ledger.grant(A, 60, 0), "the first hold is whole");
      assertTrue(ledger.controlled(A, 59));
      assertFalse(ledger.controllable(A, 30));
      assertEquals(0, ledger.grant(A, 60, 30), "no hold on top of a hold");
      assertFalse(ledger.controlled(A, 60), "over at its end");
      assertEquals(30, ledger.grant(A, 60, 60), "the second is half");
      assertEquals(15, ledger.grant(A, 60, 100), "the third a quarter");
      assertTrue(ledger.controlled(A, 114));
      assertTrue(ledger.immune(A, 115), "then immune");
      assertFalse(ledger.controllable(A, 115 + IMMUNITY - 1));
      assertEquals(0, ledger.grant(A, 60, 115 + IMMUNITY - 1), "nothing holds him while immune");
      assertTrue(ledger.controllable(A, 115 + IMMUNITY));
      assertEquals(60, ledger.grant(A, 60, 115 + IMMUNITY), "a new window starts whole");
   }

   @Test
   void theWindowForgetsOldHolds() {
      ThanosControlLedger ledger = new ThanosControlLedger();
      assertEquals(40, ledger.grant(A, 40, 0));
      assertEquals(20, ledger.grant(A, 40, WINDOW - 1), "still the same window");
      ThanosControlLedger fresh = new ThanosControlLedger();
      assertEquals(40, fresh.grant(A, 40, 0));
      assertEquals(40, fresh.grant(A, 40, WINDOW), "a window later it is whole again");
   }

   @Test
   void tooShortIsRefusedAndNotCounted() {
      ThanosControlLedger ledger = new ThanosControlLedger();
      assertEquals(0, ledger.grant(A, 0, 0));
      assertEquals(0, ledger.grant(A, -5, 0));
      assertEquals(6, ledger.grant(A, 6, 0));
      assertEquals(0, ledger.grant(A, 6, 10), "half of 6 is under the minimum");
      assertEquals(20, ledger.grant(A, 40, 20), "the refused one did not count: still the second");
      assertEquals(ThanosControlLedger.MIN_TICKS, ledger.grant(A, ThanosControlLedger.MIN_TICKS * 4, 60), "a quarter can still be the minimum");
   }

   @Test
   void breakingFreeEndsTheHoldButNotTheCount() {
      ThanosControlLedger ledger = new ThanosControlLedger();
      assertEquals(80, ledger.grant(A, 80, 0));
      ledger.release(A, 10);
      assertFalse(ledger.controlled(A, 10));
      assertEquals(40, ledger.grant(A, 80, 10), "the next one is still the second");
      ledger.release(B, 10);
      assertFalse(ledger.controlled(B, 10), "releasing a stranger does nothing");
   }

   @Test
   void heroesAreCountedApartAndForgotten() {
      ThanosControlLedger ledger = new ThanosControlLedger();
      assertEquals(30, ledger.grant(A, 30, 0));
      assertEquals(30, ledger.grant(B, 30, 0), "another hero, another count");
      ledger.prune(10);
      assertEquals(2, ledger.size(), "both still held");
      ledger.prune(WINDOW);
      assertEquals(0, ledger.size(), "window, hold and immunity over: forgotten");

      assertEquals(30, ledger.grant(A, 30, 0));
      ledger.clear();
      assertEquals(0, ledger.size());
      assertEquals(30, ledger.grant(A, 30, 1), "cleared: whole again");
   }

   @Test
   void theFactors() {
      assertEquals(1.0F, ThanosControlLedger.factor(1));
      assertEquals(0.5F, ThanosControlLedger.factor(2));
      assertEquals(0.25F, ThanosControlLedger.factor(3));
      assertEquals(0.25F, ThanosControlLedger.factor(7));
   }
}
