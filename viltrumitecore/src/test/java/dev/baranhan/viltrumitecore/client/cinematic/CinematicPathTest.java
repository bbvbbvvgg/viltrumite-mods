package dev.baranhan.viltrumitecore.client.cinematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** The built-in one-shot orbit: endpoints, look target and the reduced-motion scaling (wave1-22). */
class CinematicPathTest {
   private static final Vec3 CENTRE = new Vec3(10.0, 64.0, -20.0);

   private static double horizontal(Vec3 p, Vec3 c) {
      return Math.sqrt((p.x - c.x) * (p.x - c.x) + (p.z - c.z) * (p.z - c.z));
   }

   @Test
   void normalShotIsThreeSeconds() {
      assertEquals(60, CinematicPath.around(CENTRE, false).ticks());
   }

   @Test
   void startAndEndAreOnTheOrbitRing() {
      CinematicPath path = CinematicPath.around(CENTRE, false);
      Vec3 start = path.position(0.0F);
      Vec3 end = path.position(path.ticks());
      assertEquals(CinematicPath.BASE_RADIUS, horizontal(start, CENTRE), 1.0E-6);
      assertTrue(horizontal(end, CENTRE) > CinematicPath.BASE_RADIUS * 0.8, "the shot stays on the ring");
      assertTrue(start.distanceTo(end) > 1.0, "the camera actually travels");
   }

   @Test
   void progressIsClampedToOne() {
      CinematicPath path = CinematicPath.around(CENTRE, false);
      assertEquals(1.0F, path.progress(path.ticks() * 5.0F), 1.0E-6F);
      assertEquals(0.0F, path.progress(-3.0F), 1.0E-6F);
   }

   @Test
   void lookTargetSitsAboveTheFocusFeet() {
      CinematicPath path = CinematicPath.around(CENTRE, false);
      assertTrue(path.look().y > CENTRE.y);
      assertEquals(CENTRE.x, path.look().x, 1.0E-6);
      assertEquals(CENTRE.z, path.look().z, 1.0E-6);
   }

   @Test
   void reducedMotionSlowsDownAndShrinksTheOrbit() {
      CinematicPath normal = CinematicPath.around(CENTRE, false);
      CinematicPath reduced = CinematicPath.around(CENTRE, true);
      assertTrue(reduced.ticks() > normal.ticks(), "reduced motion is slower");
      assertTrue(reduced.radius() < normal.radius(), "reduced motion has a smaller amplitude");
      assertTrue(reduced.height() < normal.height());
      assertTrue(horizontal(reduced.position(0.0F), CENTRE) < horizontal(normal.position(0.0F), CENTRE));
   }

   @Test
   void aNullCentreFallsBackToTheOrigin() {
      CinematicPath path = CinematicPath.around(null, false);
      assertEquals(Vec3.ZERO, path.centre());
   }
}
