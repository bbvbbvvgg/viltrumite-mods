package dev.baranhan.viltrumitecore.hero.homelander;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EyeLasersTest {

   @Test
   void chargeDelays4Ticks() {
      for (int age = 0; age < 4; age++) {
         assertFalse(EyeLasers.damageTick(age), "age " + age);
      }
      assertTrue(EyeLasers.damageTick(4));
   }

   @Test
   void damageEvery4Ticks() {
      int hits = 0;
      for (int age = 0; age < 4 + 20; age++) {
         if (EyeLasers.damageTick(age)) {
            hits++;
         }
      }
      // 20 beam ticks = 1 s -> 5 hits x 1.5 = 7.5 health (3.75 hearts) per second.
      assertEquals(5, hits);
      assertFalse(EyeLasers.damageTick(5));
      assertTrue(EyeLasers.damageTick(8));
      assertFalse(EyeLasers.damageTick(-1));
   }

   /** A player-sized box 10 blocks ahead; the eye ray at chest height must hit the chest, not the feet. */
   @Test
   void contactIsWhereTheRayEntersTheHitbox() {
      AABB body = new AABB(-0.3, 0.0, 9.7, 0.3, 1.8, 10.3);
      Vec3 eye = new Vec3(0, 1.3, 0);
      Vec3 contact = EyeLasers.contactPoint(body, eye, eye.add(0, 0, 64));
      assertEquals(9.7, contact.z, 1.0E-6);
      assertEquals(1.3, contact.y, 1.0E-6);
   }

   @Test
   void rayStartingInsideHitsAtTheEye() {
      AABB body = new AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3);
      Vec3 eye = new Vec3(0, 1.0, 0);
      assertEquals(eye, EyeLasers.contactPoint(body, eye, eye.add(0, 0, 10)));
   }

   @Test
   void missReturnsNull() {
      AABB body = new AABB(5, 0, 5, 6, 2, 6);
      Vec3 eye = new Vec3(0, 1.0, 0);
      assertNull(EyeLasers.contactPoint(body, eye, eye.add(0, 0, 10)));
   }
}
