package dev.baranhan.viltrumitecore.entity.chitauri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ChariotSteeringTest {
   @Test
   void seekCruisesThenArrives() {
      Vec3 pos = new Vec3(0, 80, 0);
      Vec3 far = ChariotSteering.seek(pos, new Vec3(100, 80, 0), ChariotSteering.CRUISE, 12.0);
      assertEquals(ChariotSteering.CRUISE, far.length(), 1.0E-9);
      assertTrue(far.x > 0.0);
      Vec3 near = ChariotSteering.seek(pos, new Vec3(6, 80, 0), ChariotSteering.CRUISE, 12.0);
      assertEquals(ChariotSteering.CRUISE * 0.5, near.length(), 1.0E-9);
      Vec3 at = ChariotSteering.seek(pos, new Vec3(0.5, 80, 0), ChariotSteering.CRUISE, 12.0);
      assertEquals(ChariotSteering.CRUISE * 0.15, at.length(), 1.0E-9, "never stalls completely");
      assertEquals(Vec3.ZERO, ChariotSteering.seek(pos, pos, 1.0, 5.0));
   }

   @Test
   void steeringForceAndSpeedAreLimited() {
      Vec3 vel = new Vec3(ChariotSteering.CRUISE, 0, 0);
      Vec3 desired = new Vec3(-ChariotSteering.CRUISE, 0, 0);
      Vec3 next = ChariotSteering.steer(vel, desired, ChariotSteering.MAX_ACCEL, ChariotSteering.ATTACK);
      assertEquals(ChariotSteering.MAX_ACCEL, vel.subtract(next).length(), 1.0E-9);
      Vec3 fast = ChariotSteering.steer(new Vec3(0.9, 0, 0), new Vec3(5, 0, 0), 1.0, ChariotSteering.ATTACK);
      assertEquals(ChariotSteering.ATTACK, fast.length(), 1.0E-9);
      assertTrue(ChariotSteering.CRUISE < ChariotSteering.ATTACK && ChariotSteering.ATTACK < ChariotSteering.EVADE);
   }

   @Test
   void bankLeansIntoTheTurnAndIsClamped() {
      assertTrue(ChariotSteering.bank(3.0F) < 0.0F);
      assertTrue(ChariotSteering.bank(-3.0F) > 0.0F);
      assertEquals(0.0F, ChariotSteering.bank(0.0F), 0.0F);
      assertEquals(-ChariotSteering.MAX_BANK, ChariotSteering.bank(40.0F));
      assertEquals(ChariotSteering.MAX_BANK, ChariotSteering.bank(-40.0F));
   }

   @Test
   void attackRunsComeFromAFreshSide() {
      Vec3 target = new Vec3(5, 64, -3);
      for (double az = 0.0; az < 360.0; az += 30.0) {
         Vec3 p = ChariotSteering.approachPoint(target, az, 40.0, 18.0);
         assertEquals(40.0, Math.hypot(p.x - target.x, p.z - target.z), 1.0E-9);
         assertEquals(target.y + 18.0, p.y, 1.0E-9);
         for (double r = 0.0; r <= 1.0; r += 0.25) {
            double next = ChariotSteering.nextAzimuth(az, r);
            assertTrue(next >= 0.0 && next < 360.0);
            double diff = Math.abs(((next - az) % 360.0 + 540.0) % 360.0 - 180.0);
            // 90..180 degrees away from the last pass
            assertTrue(diff >= 90.0 - 1.0E-9 && diff <= 180.0 + 1.0E-9, "az " + az + " r " + r + " diff " + diff);
         }
      }
   }

   @Test
   void yawRotatesAroundUp() {
      Vec3 v = ChariotSteering.yawed(new Vec3(1, 0.5, 0), 90.0);
      assertEquals(0.0, v.x, 1.0E-9);
      assertEquals(0.5, v.y, 1.0E-9);
      assertEquals(1.0, v.z, 1.0E-9);
   }

   @Test
   void separationPushesApartOnlyWhenClose() {
      Vec3 a = new Vec3(0, 80, 0);
      assertEquals(Vec3.ZERO, ChariotSteering.separation(a, new Vec3(10, 80, 0), 8.0, 0.1));
      assertEquals(Vec3.ZERO, ChariotSteering.separation(a, a, 8.0, 0.1));
      Vec3 push = ChariotSteering.separation(a, new Vec3(2, 80, 0), 8.0, 0.1);
      assertTrue(push.x < 0.0);
      Vec3 harder = ChariotSteering.separation(a, new Vec3(1, 80, 0), 8.0, 0.1);
      assertTrue(harder.length() > push.length());
   }
}
