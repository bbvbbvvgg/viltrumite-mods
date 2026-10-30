package dev.baranhan.viltrumitecore.client.dio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Road Roller cinematic: 9 s, continuous camera (no cut/teleport/flip), starts and ends on the player's own view. */
class RollerCameraTest {
   private static final Vec3 START = new Vec3(0, 64, 0);
   private static final Vec3 LAND = new Vec3(3, 62, 14);

   private static RollerCamera.View at(double t) {
      Vec3 f = RollerCamera.facing(START, LAND, 0.0F);
      Vec3 dio = RollerChoreo.dioFeet(t, START, LAND);
      Vec3 eye = dio.add(0, 1.62, 0);
      return RollerCamera.view(t, dio, START, LAND, f, eye, 12.0F, 5.0F);
   }

   @Test
   void ultIsNineSecondsWithOrderedBeats() {
      assertEquals(180, DioRules.ROLLER_LENGTH);
      int[] beats = {DioRules.ROLLER_CAPTURE, DioRules.ROLLER_CROUCH, DioRules.ROLLER_LEAP, DioRules.ROLLER_APPEAR, DioRules.ROLLER_APEX, DioRules.ROLLER_HANG,
         DioRules.ROLLER_SLAM, DioRules.ROLLER_BARRAGE_END, DioRules.ROLLER_RESUME, DioRules.ROLLER_LAND, DioRules.ROLLER_LENGTH};
      for (int i = 1; i < beats.length; i++) {
         assertTrue(beats[i - 1] < beats[i], "beat " + i);
      }

      assertTrue(DioRules.rollerHeight(DioRules.ROLLER_APEX, 64, 62) - 64 >= 15.0, "a leap visible from far away");
   }

   @Test
   void startsAndEndsOnThePlayersOwnView() {
      RollerCamera.View first = at(0.0);
      assertEquals(START.add(0, 1.62, 0), first.pos());
      assertEquals(12.0F, first.yaw());
      RollerCamera.View last = at(RollerCamera.EXIT_END + 0.01);
      Vec3 eye = RollerChoreo.dioFeet(RollerCamera.EXIT_END + 0.01, START, LAND).add(0, 1.62, 0);
      assertTrue(last.pos().distanceTo(eye) < 1.0E-6);
      assertEquals(0.0, RollerCamera.weight(DioRules.ROLLER_LENGTH));
   }

   @Test
   void cameraNeverJumpsOrFlips() {
      RollerCamera.View prev = at(0.0);
      for (double t = 0.05; t <= DioRules.ROLLER_LENGTH; t += 0.05) {
         RollerCamera.View v = at(t);
         assertTrue(v.pos().distanceTo(prev.pos()) < 0.35, "camera jump at t=" + t + ": " + v.pos().distanceTo(prev.pos()));
         float dy = ((v.yaw() - prev.yaw()) % 360.0F + 540.0F) % 360.0F - 180.0F;
         assertTrue(Math.abs(dy) < 8.0F, "yaw flip at t=" + t + ": " + dy);
         assertTrue(Math.abs(v.pitch() - prev.pitch()) < 6.0F, "pitch jump at t=" + t);
         assertTrue(v.pitch() > -89.0F && v.pitch() < 89.0F);
         prev = v;
      }
   }

   @Test
   void lettersboxFollowsTheScene() {
      assertEquals(0.0F, RollerCamera.bars(0.0), 1.0E-6);
      assertTrue(RollerCamera.bars(DioRules.ROLLER_SLAM) > 0.99F);
      assertEquals(0.0F, RollerCamera.bars(RollerCamera.EXIT_END), 1.0E-6);
   }
}
