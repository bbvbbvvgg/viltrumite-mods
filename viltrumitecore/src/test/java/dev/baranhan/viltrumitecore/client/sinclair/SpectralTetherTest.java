package dev.baranhan.viltrumitecore.client.sinclair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SpectralTetherTest {
   private static final double EPS = 1.0E-9;

   @Test
   void threadsArePinnedAtBothChests() {
      Vec3 a = new Vec3(0.0, 65.2, 0.0);
      Vec3 b = new Vec3(6.0, 65.5, 3.0);
      for (int strand = 0; strand < 2; strand++) {
         for (double s : new double[]{0.0, 1.3, 17.7}) {
            assertEquals(0.0, SpectralTether.point(a, b, 0.0, strand, s).distanceTo(a), EPS);
            assertEquals(0.0, SpectralTether.point(a, b, 1.0, strand, s).distanceTo(b), EPS);
         }
      }
   }

   @Test
   void threadsStayCloseToTheLineAndSag() {
      Vec3 a = new Vec3(0.0, 0.0, 0.0);
      Vec3 b = new Vec3(10.0, 0.0, 0.0);
      double bound = SpectralTether.WIND + SpectralTether.MAX_SAG + 1.0E-6;
      for (int i = 0; i <= 100; i++) {
         double u = i / 100.0;
         Vec3 straight = a.add(b.subtract(a).scale(u));
         for (int strand = 0; strand < 2; strand++) {
            assertTrue(SpectralTether.point(a, b, u, strand, 3.0).distanceTo(straight) <= bound, "u=" + u);
         }
      }

      // the middle hangs below the straight line (the winding is smaller than the sag here)
      double sag = Math.min(SpectralTether.MAX_SAG, 10.0 * SpectralTether.SAG_PER_BLOCK);
      double midY = (SpectralTether.point(a, b, 0.5, 0, 0.0).y + SpectralTether.point(a, b, 0.5, 1, 0.0).y) / 2.0;
      assertEquals(-sag, midY, 1.0E-6);
   }

   @Test
   void twoDistinctThreadsAtTheEnds() {
      Vec3 feet = new Vec3(0.0, 64.0, 0.0);
      Vec3 right = SpectralTether.end(feet, SpectralTether.SINCLAIR_CHEST, 0.0F, 0);
      Vec3 left = SpectralTether.end(feet, SpectralTether.SINCLAIR_CHEST, 0.0F, 1);
      assertEquals(2.0 * SpectralTether.SPREAD, right.distanceTo(left), EPS);
      assertEquals(64.0 + SpectralTether.SINCLAIR_CHEST, right.y, EPS);
      // yaw 0 looks south (+z): his right is west (-x)
      assertTrue(right.x < left.x);
   }

   @Test
   void dotCountIsBounded() {
      assertEquals(12, SpectralTether.dots(0.1));
      assertEquals(220, SpectralTether.dots(500.0));
      assertTrue(SpectralTether.dots(10.0) > 12);
   }
}
