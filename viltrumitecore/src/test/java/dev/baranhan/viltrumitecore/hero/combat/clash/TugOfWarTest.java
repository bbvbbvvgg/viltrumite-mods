package dev.baranhan.viltrumitecore.hero.combat.clash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The collision point: grace, pushing, wins, time-out. */
class TugOfWarTest {
   @Test
   void pointHoldsStillDuringGrace() {
      TugOfWar tug = new TugOfWar(0.5);
      for (int i = 0; i < ClashRules.GRACE_TICKS; i++) {
         assertEquals(TugOfWar.Result.RUNNING, tug.tick(5.0, 1.0));
         assertTrue(tug.inGrace());
      }

      assertEquals(0.5, tug.point(), 1.0E-9);
   }

   @Test
   void strongerSidePushesThePointTowardsTheWeakerOne() {
      TugOfWar tug = new TugOfWar(0.5);
      for (int i = 0; i < ClashRules.GRACE_TICKS + 5; i++) {
         tug.tick(2.0, 1.0);
      }

      assertTrue(tug.point() > 0.5, "A is stronger, so the point moves towards B");
      assertTrue(tug.balance() > 0.0);
   }

   @Test
   void strongerSideWinsBeforeTimeOut() {
      TugOfWar tug = new TugOfWar(0.5);
      TugOfWar.Result result = TugOfWar.Result.RUNNING;
      while (result == TugOfWar.Result.RUNNING) {
         result = tug.tick(1.0, 2.5);
      }

      assertEquals(TugOfWar.Result.B_WINS, result);
      assertTrue(tug.age() < ClashRules.MAX_TICKS, "an idle side loses well before the time-out");
      assertEquals(ClashRules.WIN_MARGIN, tug.point(), 1.0E-9);
      assertEquals(-1.0, tug.balance(), 1.0E-9);
   }

   @Test
   void evenStruggleEndsInADraw() {
      TugOfWar tug = new TugOfWar(0.5);
      TugOfWar.Result result = TugOfWar.Result.RUNNING;
      while (result == TugOfWar.Result.RUNNING) {
         result = tug.tick(1.5, 1.5);
      }

      assertEquals(TugOfWar.Result.DRAW, result);
      assertEquals(ClashRules.MAX_TICKS, tug.age());
   }

   @Test
   void noPressureDoesNotMoveThePoint() {
      TugOfWar tug = new TugOfWar(0.4);
      for (int i = 0; i < 50; i++) {
         tug.tick(0.0, -3.0);
      }

      assertEquals(0.4, tug.point(), 1.0E-9);
   }

   @Test
   void startIsClampedIntoPlay() {
      assertEquals(ClashRules.WIN_MARGIN, new TugOfWar(-1.0).point(), 1.0E-9);
      assertEquals(1.0 - ClashRules.WIN_MARGIN, new TugOfWar(3.0).point(), 1.0E-9);
   }
}
