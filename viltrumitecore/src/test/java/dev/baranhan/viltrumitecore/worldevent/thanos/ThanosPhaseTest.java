package dev.baranhan.viltrumitecore.worldevent.thanos;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ThanosPhaseTest {
   static final int D = ThanosRules.DURATION_TICKS;

   @Test
   void healthPushesThePhase() {
      assertEquals(ThanosPhase.OPENING, ThanosPhase.of(1.0F, 0, D));
      assertEquals(ThanosPhase.OPENING, ThanosPhase.of(0.71F, 0, D));
      assertEquals(ThanosPhase.ESCALATION, ThanosPhase.of(0.70F, 0, D));
      assertEquals(ThanosPhase.ESCALATION, ThanosPhase.of(0.41F, 0, D));
      assertEquals(ThanosPhase.ONSLAUGHT, ThanosPhase.of(0.40F, 0, D));
   }

   @Test
   void timePushesThePhaseAndTheLaterWins() {
      assertEquals(ThanosPhase.ESCALATION, ThanosPhase.of(1.0F, 2400, D));
      assertEquals(ThanosPhase.ONSLAUGHT, ThanosPhase.of(1.0F, 4800, D));
      assertEquals(ThanosPhase.ONSLAUGHT, ThanosPhase.of(0.2F, 2400, D));
      assertEquals(ThanosPhase.FINAL, ThanosPhase.of(1.0F, D - 1200, D));
      assertEquals(ThanosPhase.FINAL, ThanosPhase.of(0.1F, D - 1, D));
      assertEquals(ThanosPhase.ONSLAUGHT, ThanosPhase.of(0.1F, D - 1201, D));
   }

   @Test
   void nanHealthStaysInTheOpening() {
      assertEquals(ThanosPhase.OPENING, ThanosPhase.ofHealth(Float.NaN));
   }

   @Test
   void healthScalesWithTheGroupAndStops() {
      assertEquals(3000.0F, ThanosRules.maxHealth(0));
      assertEquals(3000.0F, ThanosRules.maxHealth(1));
      assertEquals(4800.0F, ThanosRules.maxHealth(2), 0.01F);
      assertEquals(3000.0F * (1.0F + 0.6F * 7.0F), ThanosRules.maxHealth(8), 0.01F);
      assertEquals(ThanosRules.maxHealth(8), ThanosRules.maxHealth(20));
      assertTrue(ThanosRules.damageScale(4) > ThanosRules.damageScale(1));
   }

   @Test
   void finalPhaseSharpensButKeepsTelegraphsReadable() {
      assertEquals(20, ThanosRules.telegraph(20, ThanosPhase.OPENING));
      assertEquals(16, ThanosRules.telegraph(20, ThanosPhase.FINAL));
      assertEquals(ThanosRules.MIN_TELEGRAPH_FINAL, ThanosRules.telegraph(6, ThanosPhase.FINAL));
      assertEquals(ThanosRules.MIN_TELEGRAPH, ThanosRules.telegraph(6, ThanosPhase.OPENING));
      assertEquals(70, ThanosRules.cooldown(100, ThanosPhase.FINAL));
      for (ThanosMove move : ThanosMove.values()) {
         for (ThanosPhase phase : ThanosPhase.values()) {
            assertTrue(ThanosRules.telegraph(move.telegraph, phase) >= 10, move + " telegraph readable in " + phase);
         }
      }
   }
}
