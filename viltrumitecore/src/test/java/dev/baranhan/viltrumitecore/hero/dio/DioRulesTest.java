package dev.baranhan.viltrumitecore.hero.dio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** DioRules numbers and pure functions against the spec summary (§8). */
class DioRulesTest {
   @Test
   void timeStopLastsThreeToNineSeconds() {
      assertEquals(60, DioRules.timeStopDuration(0));
      assertEquals(60, DioRules.timeStopDuration(DioRules.TIME_STOP_MIN_BLOOD));
      assertEquals(180, DioRules.timeStopDuration(DioRules.BLOOD_MAX));
      assertEquals(180, DioRules.timeStopDuration(500));
      int previous = 0;
      for (int b = 25; b <= 100; b++) {
         int d = DioRules.timeStopDuration(b);
         assertTrue(d >= previous, "monotonic in blood");
         previous = d;
      }
   }

   @Test
   void comboChainsAndRecovers() {
      assertEquals(0, DioRules.nextComboStep(-1, -1));
      assertEquals(1, DioRules.nextComboStep(0, 0));
      assertEquals(2, DioRules.nextComboStep(1, DioRules.COMBO_WINDOW));
      assertEquals(0, DioRules.nextComboStep(2, 1));
      assertEquals(0, DioRules.nextComboStep(1, DioRules.COMBO_WINDOW + 1), "window missed: new chain");
      assertFalse(DioRules.comboReady(2, DioRules.COMBO_RECOVER - 1), "pause after the hook");
      assertTrue(DioRules.comboReady(2, DioRules.COMBO_RECOVER));
      assertTrue(DioRules.comboReady(0, 0));
      assertEquals(3, DioRules.COMBO_LENGTH.length);
      for (int i = 0; i < 3; i++) {
         assertTrue(DioRules.COMBO_CONTACT[i] > 0 && DioRules.COMBO_CONTACT[i] < DioRules.COMBO_LENGTH[i], "contact inside the strike");
      }
   }

   @Test
   void conesAndGuard() {
      assertTrue(DioRules.inCone(0, 0, 1, 0, 0, 3, DioRules.STAND_CONE_DEG));
      assertTrue(DioRules.inCone(0, 0, 1, 1, 0, 3, DioRules.STAND_CONE_DEG), "18° off axis");
      assertFalse(DioRules.inCone(0, 0, 1, 3, 0, 1, DioRules.STAND_CONE_DEG));
      assertFalse(DioRules.inCone(0, 0, 1, 0, 0, -1, 90.0 - 1.0E-6));
      assertTrue(DioRules.guardCovers(0, 1, 0.5, 1));
      assertFalse(DioRules.guardCovers(0, 1, 0, -1), "behind is open");
      assertFalse(DioRules.guardCovers(0, 1, 1, 0), "the side (90°) is open");
   }

   @Test
   void ledgerCapsPlayersOnly() {
      assertEquals(0.0F, DioRules.ledgerPayout(-3.0F, 20.0F, true));
      assertEquals(12.0F, DioRules.ledgerPayout(50.0F, 20.0F, true), 1.0E-6);
      assertEquals(5.0F, DioRules.ledgerPayout(5.0F, 20.0F, true), 1.0E-6);
      assertEquals(50.0F, DioRules.ledgerPayout(50.0F, 20.0F, false), 1.0E-6);
   }

   @Test
   void sunWeakensTheStand() {
      assertEquals(5.0F * 0.75F, DioRules.standDamage(5.0F, true), 1.0E-6);
      assertEquals(5.0F, DioRules.standDamage(5.0F, false), 1.0E-6);
      assertEquals(0, DioRules.clampBlood(-4));
      assertEquals(100, DioRules.clampBlood(140));
   }

   @Test
   void barrageHitsEveryTwoTicksAfterTheWindup() {
      int hits = 0;
      for (int t = 0; t < DioRules.BARRAGE_WINDUP; t++) {
         assertFalse(DioRules.barrageHitTick(t));
      }

      for (int t = 0; t <= DioRules.BARRAGE_MAX; t++) {
         if (DioRules.barrageHitTick(t)) {
            hits++;
            assertEquals(0, (t - DioRules.BARRAGE_WINDUP) % DioRules.BARRAGE_HIT_INTERVAL);
         }
      }

      assertEquals((DioRules.BARRAGE_MAX - DioRules.BARRAGE_WINDUP) / 2 + 1, hits);
   }

   @Test
   void knifeFanIsSymmetric() {
      int n = DioRules.KNIFE_COUNT;
      for (int i = 0; i < n; i++) {
         assertEquals(-DioRules.knifeYawOffset(i, n), DioRules.knifeYawOffset(n - 1 - i, n), 1.0E-9);
      }

      assertEquals(-DioRules.KNIFE_SPREAD_DEG, DioRules.knifeYawOffset(0, n), 1.0E-9);
      assertEquals(DioRules.KNIFE_SPREAD_DEG, DioRules.knifeYawOffset(n - 1, n), 1.0E-9);
      assertEquals(0.0, DioRules.knifeYawOffset(0, 1));
   }

   @Test
   void rollerTimelineMatchesTheSpec() {
      assertEquals(13, DioRules.rollerPulseCount(), "13 shoves on the roller");
      assertFalse(DioRules.rollerPulseTick(DioRules.ROLLER_SLAM), "the slam is not a pulse");
      assertTrue(DioRules.rollerPulseTick(DioRules.ROLLER_SLAM + DioRules.ROLLER_PULSE));
      assertTrue(DioRules.rollerPulseTick(DioRules.ROLLER_BARRAGE_END));
      assertTrue(DioRules.ROLLER_CAPTURE < DioRules.ROLLER_LEAP && DioRules.ROLLER_LEAP < DioRules.ROLLER_APEX && DioRules.ROLLER_APEX < DioRules.ROLLER_SLAM
         && DioRules.ROLLER_SLAM < DioRules.ROLLER_BARRAGE_END && DioRules.ROLLER_BARRAGE_END < DioRules.ROLLER_RESUME
         && DioRules.ROLLER_RESUME < DioRules.ROLLER_LENGTH);
   }

   @Test
   void rollerArcIsContinuous() {
      double start = 64.0, land = 60.0;
      assertEquals(start, DioRules.rollerHeight(0, start, land));
      assertEquals(start, DioRules.rollerHeight(DioRules.ROLLER_LEAP, start, land), 1.0E-9);
      assertEquals(start + DioRules.ROLLER_APEX_HEIGHT, DioRules.rollerHeight(DioRules.ROLLER_APEX, start, land), 1.0E-9);
      assertEquals(land + DioRules.ROLLER_SEAT, DioRules.rollerHeight(DioRules.ROLLER_SLAM, start, land), 1.0E-9);
      assertEquals(land + DioRules.ROLLER_SEAT, DioRules.rollerHeight(DioRules.ROLLER_RESUME, start, land), 1.0E-9);
      assertEquals(land, DioRules.rollerHeight(DioRules.ROLLER_LENGTH, start, land), 1.0E-9);
      double prev = DioRules.rollerHeight(0, start, land);
      for (double t = 0.25; t <= DioRules.ROLLER_LENGTH; t += 0.25) {
         double y = DioRules.rollerHeight(t, start, land);
         assertTrue(Math.abs(y - prev) < 1.2, "no teleport at t=" + t);
         prev = y;
      }

      double travel = 0.0;
      for (double t = 0; t <= DioRules.ROLLER_LENGTH; t += 0.5) {
         double u = DioRules.rollerTravel(t);
         assertTrue(u >= travel - 1.0E-12 && u <= 1.0);
         travel = u;
      }

      assertEquals(0.0, DioRules.rollerTravel(DioRules.ROLLER_LEAP));
      assertEquals(1.0, DioRules.rollerTravel(DioRules.ROLLER_SLAM));
   }
}
