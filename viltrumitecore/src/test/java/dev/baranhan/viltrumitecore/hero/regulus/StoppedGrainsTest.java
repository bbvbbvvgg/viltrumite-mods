package dev.baranhan.viltrumitecore.hero.regulus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** The grain fan and the severance plane are pure: the client draws exactly what the server hits. */
class StoppedGrainsTest {

   @Test
   void firstGrainFliesStraight() {
      Vec3 look = new Vec3(0.3, -0.2, 0.9);
      Vec3 d = StoppedGrains.direction(look, 0, RegulusRules.SAND_GRAINS, RegulusRules.SAND_SPREAD_DEGREES, 1234);
      assertEquals(0.0, d.subtract(look.normalize()).length(), 1.0E-9);
   }

   @Test
   void fanStaysInsideTheSpreadAndIsUnitLength() {
      for (Vec3 look : new Vec3[]{new Vec3(0, 0, 1), new Vec3(1, 0.4, -0.2), new Vec3(0, 1, 0), new Vec3(0, -1, 0.001)}) {
         Vec3 f = look.normalize();
         for (int i = 0; i < RegulusRules.SAND_GRAINS; i++) {
            Vec3 d = StoppedGrains.direction(look, i, RegulusRules.SAND_GRAINS, RegulusRules.SAND_SPREAD_DEGREES, 77);
            assertEquals(1.0, d.length(), 1.0E-9);
            double angle = Math.toDegrees(Math.acos(Math.min(1.0, d.dot(f))));
            assertTrue(angle <= RegulusRules.SAND_SPREAD_DEGREES + 1.0E-6, "grain " + i + " at " + angle);
            assertTrue(Double.isFinite(d.x) && Double.isFinite(d.y) && Double.isFinite(d.z));
         }
      }
   }

   @Test
   void directionsAreDistinctAndDeterministic() {
      Vec3 look = new Vec3(0, 0, 1);
      for (int i = 0; i < RegulusRules.SAND_GRAINS; i++) {
         Vec3 a = StoppedGrains.direction(look, i, RegulusRules.SAND_GRAINS, RegulusRules.SAND_SPREAD_DEGREES, 4242);
         Vec3 b = StoppedGrains.direction(look, i, RegulusRules.SAND_GRAINS, RegulusRules.SAND_SPREAD_DEGREES, 4242);
         assertEquals(a, b);
         for (int j = 0; j < i; j++) {
            Vec3 c = StoppedGrains.direction(look, j, RegulusRules.SAND_GRAINS, RegulusRules.SAND_SPREAD_DEGREES, 4242);
            assertTrue(a.subtract(c).length() > 0.02, i + " vs " + j);
         }
      }
   }

   @Test
   void ultGrainsStayFiniteForAnyCount() {
      for (int i = 0; i < RegulusRules.ULT_GRAINS; i++) {
         Vec3 d = StoppedGrains.direction(new Vec3(0, 0, 1), i, RegulusRules.ULT_GRAINS, 40.0, 9);
         assertEquals(1.0, d.length(), 1.0E-9);
      }
   }

   @Test
   void muzzleIsInFrontAndToTheRight() {
      Vec3 eye = new Vec3(0, 65.62, 0);
      Vec3 m = StoppedGrains.muzzle(eye, new Vec3(0, 0, 1));
      assertTrue(m.z > 0.5);
      assertTrue(m.y < eye.y);
      assertTrue(Double.isFinite(StoppedGrains.muzzle(eye, new Vec3(0, 1, 0)).x));
   }

   @Test
   void severancePlaneHitsOnlyWhatItCrosses() {
      Vec3 origin = new Vec3(0, 64, 0);
      Vec3 forward = new Vec3(0, 0, 1);
      Vec3 longAxis = new Vec3(1, 0, 0);
      Vec3 thinAxis = new Vec3(0, 1, 0);
      AABB inside = new AABB(-0.3, 63.8, 4.7, 0.3, 65.6, 5.3);
      AABB beside = new AABB(5.0, 63.8, 4.7, 5.6, 65.6, 5.3);
      AABB above = new AABB(-0.3, 66.0, 4.7, 0.3, 67.8, 5.3);
      AABB behind = new AABB(-0.3, 63.8, -2.0, 0.3, 65.6, -1.4);
      double half = RegulusRules.severWidth(5.0) / 2.0;
      double thin = RegulusRules.SEVER_THICKNESS / 2.0;
      assertTrue(AirSeverance.overlaps(inside, origin, forward, longAxis, thinAxis, 4.0, 6.0, half, thin));
      assertTrue(!AirSeverance.overlaps(beside, origin, forward, longAxis, thinAxis, 4.0, 6.0, half, thin));
      assertTrue(!AirSeverance.overlaps(above, origin, forward, longAxis, thinAxis, 4.0, 6.0, half, thin));
      assertTrue(!AirSeverance.overlaps(behind, origin, forward, longAxis, thinAxis, 4.0, 6.0, half, thin));
   }
}
