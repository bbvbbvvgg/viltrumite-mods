package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Projectile seam math (stage 2 Task 3). */
class HeroProjectilesTest {
   private static final Vec3 ARROW = new Vec3(0.3, -0.05, 2.8);
   private static final Vec3 CENTRED = new Vec3(0.0, 0.0, 1.0);

   @Test
   void deflectTurnsSideways() {
      for (double roll : new double[]{0.0, 0.5, 0.999}) {
         for (boolean left : new boolean[]{true, false}) {
            Vec3 next = ProjectileMath.deflected(ARROW, CENTRED, left, ProjectileMath.deflectDegrees(roll));
            double angle = ProjectileMath.angleDeg(ARROW, next);
            assertTrue(angle >= 60.0 - 1.0E-6 && angle <= 90.0 + 1.0E-6, "angle " + angle);
            // Sideways: the vertical part is not amplified into a lob.
            assertTrue(Math.abs(next.y) <= Math.abs(ARROW.y) + 1.0E-6, "y " + next.y);
         }
      }
   }

   @Test
   void deflectSlower() {
      Vec3 next = ProjectileMath.deflected(ARROW, CENTRED, true);
      assertEquals(ARROW.length() * 0.6, next.length(), 1.0E-9);
   }

   @Test
   void deflectGoesAwayFromTheBodyCentre() {
      // Arrow along +z, the body centre 0.4 to the +x side of its line: it must turn toward -x.
      Vec3 v = new Vec3(0.0, 0.0, 2.0);
      Vec3 next = ProjectileMath.deflected(v, new Vec3(0.4, 0.0, 0.5), true, 75.0);
      assertTrue(next.x < -0.5, "x " + next.x);
      Vec3 other = ProjectileMath.deflected(v, new Vec3(-0.4, 0.0, 0.5), true, 75.0);
      assertTrue(other.x > 0.5, "x " + other.x);
      // Dead centre: the random side decides.
      assertTrue(ProjectileMath.deflected(v, new Vec3(0.0, 0.0, 0.5), true, 75.0).x * ProjectileMath.deflected(v, new Vec3(0.0, 0.0, 0.5), false, 75.0).x < 0.0);
   }

   @Test
   void verticalProjectileStillTurns() {
      Vec3 falling = new Vec3(0.0, -1.0, 0.0);
      Vec3 next = ProjectileMath.deflected(falling, new Vec3(0.0, -1.0, 0.0), true, 80.0);
      assertEquals(80.0, ProjectileMath.angleDeg(falling, next), 1.0E-6);
   }

   @Test
   void reflectAimsAtOwner() {
      Vec3 from = new Vec3(0.0, 1.5, 0.0);
      Vec3 owner = new Vec3(10.0, 2.5, -20.0);
      Vec3 next = ProjectileMath.reflected(ARROW, from, owner);
      assertEquals(ARROW.length(), next.length(), 1.0E-9);
      assertEquals(0.0, ProjectileMath.angleDeg(next, owner.subtract(from)), 1.0E-6);
   }

   @Test
   void reflectWithoutOwnerGoesBack() {
      Vec3 next = ProjectileMath.reflected(ARROW, Vec3.ZERO, null);
      assertEquals(180.0, ProjectileMath.angleDeg(ARROW, next), 1.0E-6);
      assertEquals(ARROW.length(), next.length(), 1.0E-9);
   }

   @Test
   void deflectRotatesPowerVector() {
      // Ghast fireball: velocity and power along its line (AbstractHurtingProjectile.tick: v = (v + power) * 0.95).
      Vec3 velocity = new Vec3(0.2, 0.0, 1.0);
      Vec3 power = new Vec3(0.02, 0.0, 0.1);
      Vec3 axis = ProjectileMath.sideAxis(velocity);
      double yaw = ProjectileMath.deflectYaw(velocity, CENTRED, true, 70.0);
      Vec3 v2 = ProjectileMath.rotateAndScale(velocity, axis, yaw, ProjectileMath.DEFLECT_SCALE);
      Vec3 p2 = ProjectileMath.rotateAndScale(power, axis, yaw, ProjectileMath.DEFLECT_SCALE);
      assertEquals(ProjectileMath.angleDeg(velocity, power), ProjectileMath.angleDeg(v2, p2), 1.0E-6);
      Vec3 v = v2;
      for (int tick = 0; tick < 20; tick++) {
         v = v.add(p2).scale(0.95);
      }

      // No curve back onto the old line.
      assertTrue(ProjectileMath.angleDeg(velocity, v) >= 60.0, "after 20 ticks " + ProjectileMath.angleDeg(velocity, v));
      // Without turning the power it would have curved back.
      Vec3 naive = v2;
      for (int tick = 0; tick < 20; tick++) {
         naive = naive.add(power).scale(0.95);
      }

      assertTrue(ProjectileMath.angleDeg(velocity, naive) < 60.0);
   }

   @Test
   void reflectReaimsPowerVector() {
      Vec3 power = new Vec3(0.0, 0.0, 0.1);
      Vec3 back = new Vec3(0.0, 0.0, -1.0);
      Vec3 next = ProjectileMath.alongVelocity(power, back);
      assertEquals(0.1, next.length(), 1.0E-9);
      assertEquals(0.0, ProjectileMath.angleDeg(next, back), 1.0E-6);
   }

   @Test
   void catchSkeletonArrow() {
      // Mob arrows are DISALLOWED: still caught as an item.
      assertEquals("arrow", ProjectileMath.caughtItem(AbstractArrow.Pickup.DISALLOWED, () -> "arrow"));
      assertEquals("arrow", ProjectileMath.caughtItem(AbstractArrow.Pickup.ALLOWED, () -> "arrow"));
   }

   @Test
   void catchInfinityArrowGivesNothing() {
      assertNull(ProjectileMath.caughtItem(AbstractArrow.Pickup.CREATIVE_ONLY, () -> "arrow"));
   }

   @Test
   void catchTippedKeepsPotion() {
      // The arrow's own pickup item is handed over untouched (tipped / spectral keep their stack).
      String tipped = "tipped_arrow{Potion:minecraft:poison}";
      assertEquals(tipped, ProjectileMath.caughtItem(AbstractArrow.Pickup.DISALLOWED, () -> tipped));
   }
}
