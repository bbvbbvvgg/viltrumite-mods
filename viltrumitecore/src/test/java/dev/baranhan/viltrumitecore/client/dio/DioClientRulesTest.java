package dev.baranhan.viltrumitecore.client.dio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Pure client rules: stand placement, the roller staging and the time-stop screen timing. */
class DioClientRulesTest {
   @Test
   void idleStandFloatsBehindTheRightShoulder() {
      // Yaw 0 faces +Z; DIO's right is -X.
      double[] o = StandMotion.offset(StandMotion.Mode.IDLE, 0.0F);
      assertEquals(-0.55, o[0], 1.0E-9);
      assertEquals(-0.65, o[2], 1.0E-9);
      assertTrue(o[1] > 0.0);
      double[] attack = StandMotion.offset(StandMotion.Mode.ATTACK, 90.0F);
      assertEquals(-0.85, attack[0], 1.0E-9, "yaw 90 faces -X");
      assertEquals(0.0, attack[2], 1.0E-9);
      assertTrue(StandMotion.Mode.ATTACK.rate > StandMotion.Mode.IDLE.rate, "strikes snap, idle lags");
   }

   @Test
   void springIsFrameRateIndependent() {
      float one = StandMotion.follow(9.0F, 0.05F);
      float half = StandMotion.follow(9.0F, 0.025F);
      assertEquals(one, 1.0F - (1.0F - half) * (1.0F - half), 1.0E-5);
      assertEquals(0.0F, StandMotion.follow(9.0F, -1.0F));
      assertTrue(StandMotion.follow(28.0F, 0.1F) < 1.0F);
   }

   @Test
   void summonFadesInAndDismissFadesOut() {
      assertEquals(0.0F, StandMotion.phaseAlpha(DioRules.STAND_SUMMONING, 0));
      assertEquals(1.0F, StandMotion.phaseAlpha(DioRules.STAND_SUMMONING, DioRules.SUMMON_TICKS), 1.0E-6);
      assertEquals(1.0F, StandMotion.phaseAlpha(DioRules.STAND_DISMISSING, 0), 1.0E-6);
      assertEquals(0.0F, StandMotion.phaseAlpha(DioRules.STAND_DISMISSING, DioRules.DISMISS_TICKS), 1.0E-6);
      assertEquals(0.0F, StandMotion.phaseAlpha(DioRules.STAND_NONE, 5));
      float prev = -1.0F;
      for (int t = 0; t <= DioRules.SUMMON_TICKS; t++) {
         float a = StandMotion.phaseAlpha(DioRules.STAND_SUMMONING, t);
         assertTrue(a >= prev);
         prev = a;
      }

      assertTrue(StandMotion.cameraFade(0.0) > 0.0F && StandMotion.cameraFade(0.0) < 0.2F);
      assertEquals(1.0F, StandMotion.cameraFade(3.0));
   }

   @Test
   void rollerStaging() {
      assertSame(RollerChoreo.Pose.HIDDEN, RollerChoreo.at(RollerChoreo.APPEAR - 1));
      assertTrue(RollerChoreo.at(RollerChoreo.APPEAR).visible());
      assertFalse(RollerChoreo.at(DioRules.ROLLER_SLAM - 0.5).landed());
      assertTrue(RollerChoreo.at(DioRules.ROLLER_SLAM).landed());
      assertTrue(RollerChoreo.at(DioRules.ROLLER_SLAM).squash() < 0.85F, "squash on the slam tick");
      assertTrue(RollerChoreo.at(DioRules.ROLLER_SLAM - 4).pitchDeg() < -10.0F, "nose down in the fall");
      assertEquals(0.0F, RollerChoreo.at(DioRules.ROLLER_SLAM).dent());
      assertEquals(1.0F, RollerChoreo.at(DioRules.ROLLER_BARRAGE_END).dent(), 1.0E-6);
      assertEquals(DioRules.rollerPulseCount(), RollerChoreo.pulsesUpTo(DioRules.ROLLER_LENGTH));
      assertTrue(RollerChoreo.at(DioRules.ROLLER_RESUME - 1).tremble() > 0.8F);
      assertFalse(RollerChoreo.at(DioRules.ROLLER_RESUME).visible(), "gone on the resume tick: the server blast takes over");
      float prevDent = 0.0F;
      for (double t = RollerChoreo.APPEAR; t < DioRules.ROLLER_RESUME; t += 0.5) {
         RollerChoreo.Pose p = RollerChoreo.at(t);
         assertTrue(p.dent() >= prevDent);
         assertTrue(p.grow() > 0.5F && p.grow() <= 1.0F);
         prevDent = p.dent();
      }
   }

   @Test
   void rollerSitsUnderDioOnTheArc() {
      Vec3 from = new Vec3(0, 64, 0), land = new Vec3(0, 62, 20);
      assertEquals(from, RollerChoreo.dioFeet(0, from, land));
      Vec3 slam = RollerChoreo.dioFeet(DioRules.ROLLER_SLAM, from, land);
      assertEquals(20.0, slam.z, 1.0E-9);
      assertEquals(62 + DioRules.ROLLER_SEAT, slam.y, 1.0E-9);
      // Body top (39 units) at the roller scale sits at the seat height.
      assertEquals(DioRules.ROLLER_SEAT, 39.0 * RollerChoreo.SCALE / 16.0, 0.05);
   }

   @Test
   void timeStopScreenTiming() {
      double r = DioRules.TIME_STOP_RADIUS;
      // Caster (distance 0): the wave leaves him before the capture.
      float[] cast = TimeStopScreen.screen(-5.0, 1000, false, -1, 0.0, r);
      assertEquals(1.0F, cast[1], "inverted while the ring sweeps");
      assertTrue(cast[2] > 0.0F);
      // Far viewer: nothing until the front reaches him.
      assertArrayEquals(TimeStopScreen.NONE, TimeStopScreen.screen(-5.0, 1000, false, -1, 35.0, r));
      assertTrue(TimeStopScreen.screen(10.0, 100, false, -1, 35.0, r)[0] > 0.9F);
      // Outside the field: never.
      assertArrayEquals(TimeStopScreen.NONE, TimeStopScreen.screen(50.0, 100, false, -1, r + 1.0, r));
      // Held: grey, no inversion.
      float[] hold = TimeStopScreen.screen(40.0, 80, false, -1, 10.0, r);
      assertEquals(1.0F, hold[0]);
      assertEquals(0.0F, hold[1]);
      assertEquals(0.0F, hold[2]);
      // Resume: the reverse front reaches the edge first, the caster last.
      float[] edge = TimeStopScreen.screen(160.0, 10, false, -1, 38.0, r);
      float[] center = TimeStopScreen.screen(160.0, 10, false, -1, 0.0, r);
      assertTrue(edge[0] < 1.0F, "colour returns at the edge");
      assertEquals(1.0F, center[0], "still grey at DIO");
      assertArrayEquals(TimeStopScreen.NONE, TimeStopScreen.screen(180.0, 0, false, TimeStopScreen.RETURN_TICKS, 0.0, r));
      assertTrue(TimeStopScreen.screen(180.0, 0, false, 3, 0.0, r)[0] < 1.0F, "DIO's colour lifts last");
      // Scripted (roller) field: grey until it ends, then a quick return.
      assertEquals(1.0F, TimeStopScreen.screen(60.0, 0, true, -1, 5.0, r)[0]);
      assertTrue(TimeStopScreen.screen(60.0, 0, true, 2, 5.0, r)[0] < 1.0F);
      assertArrayEquals(TimeStopScreen.NONE, TimeStopScreen.screen(60.0, 0, true, TimeStopScreen.RETURN_TICKS, 5.0, r));
      assertEquals(r, TimeStopScreen.front(4.0, r), 1.0E-9);
      assertEquals(0.0, TimeStopScreen.front(-TimeStopScreen.WAVE_LEAD, r), 1.0E-9);
   }

   @Test
   void hudAndPoseHelpers() {
      assertEquals(0, DioHud.fill(0, 80));
      assertEquals(20, DioHud.fill(25, 80));
      assertEquals(80, DioHud.fill(140, 80));
      assertEquals(0.0F, DioPoser.jojoWeight(DioPoser.POSE_AFTER - 1));
      assertTrue(DioPoser.jojoWeight(DioPoser.POSE_AFTER + DioPoser.POSE_HOLD / 2.0) > 0.99F);
      assertEquals(0.0F, DioPoser.jojoWeight(DioPoser.POSE_AFTER + DioPoser.POSE_HOLD + 5));
      assertEquals(0.0F, DioPoser.window(5, 5, 10, 2));
      assertEquals(1.0F, DioPoser.window(7.5, 5, 10, 2), 1.0E-6);
   }
}
