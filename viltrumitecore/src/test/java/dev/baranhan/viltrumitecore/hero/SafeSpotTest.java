package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Shared safe-spot search (Reinhard stage 3 Task 8, hero-neutral). */
class SafeSpotTest {
   private static final Vec3 WANTED = new Vec3(10.0, 64.0, 10.0);

   /** A fake world: each predicate decides one check for a spot relative to {@link #WANTED}. */
   private record Fake(Predicate<Vec3> fits, Predicate<Vec3> border, Predicate<Vec3> ground, int[] lastDrop) implements SafeSpot.Probe {
      Fake(Predicate<Vec3> fits, Predicate<Vec3> border, Predicate<Vec3> ground) {
         this(fits, border, ground, new int[] {-1});
      }

      @Override
      public boolean fits(Vec3 feet) {
         return this.fits.test(feet.subtract(WANTED));
      }

      @Override
      public boolean insideBorder(Vec3 feet) {
         return this.border.test(feet.subtract(WANTED));
      }

      @Override
      public boolean groundBelow(Vec3 feet, int maxDrop) {
         this.lastDrop[0] = maxDrop;
         return this.ground.test(feet.subtract(WANTED));
      }
   }

   private static Optional<Vec3> find(SafeSpot.Probe probe) {
      return SafeSpot.find(WANTED, probe, 3.0, 0.5);
   }

   @Test
   void wantedSpotFirst() {
      Optional<Vec3> spot = find(new Fake(v -> true, v -> true, v -> true));
      assertEquals(Optional.of(WANTED), spot);
   }

   @Test
   void blockedSearchesNearest() {
      // A wall fills everything with x < 2 (relative): the nearest free spot is 2 blocks along +x.
      Optional<Vec3> spot = find(new Fake(v -> v.x >= 2.0, v -> true, v -> true));
      assertTrue(spot.isPresent());
      assertEquals(2.0, spot.get().distanceTo(WANTED), 1.0E-9, "nearest first");
      assertEquals(new Vec3(12.0, 64.0, 10.0), spot.get());
   }

   @Test
   void outsideBorderRejected() {
      Optional<Vec3> spot = find(new Fake(v -> true, v -> v.z <= -1.0, v -> true));
      assertEquals(Optional.of(new Vec3(10.0, 64.0, 9.0)), spot, "the first spot inside the border");
      assertTrue(find(new Fake(v -> true, v -> false, v -> true)).isEmpty(), "all outside: none");
   }

   @Test
   void overVoidRejected() {
      Fake probe = new Fake(v -> true, v -> true, v -> v.z >= 3.0);
      Optional<Vec3> spot = find(probe);
      assertEquals(Optional.of(new Vec3(10.0, 64.0, 13.0)), spot, "the only spot with ground below");
      assertEquals(SafeSpot.MAX_DROP, probe.lastDrop()[0]);
      assertEquals(6, SafeSpot.MAX_DROP);
   }

   @Test
   void nothingWithin3Empty() {
      assertTrue(find(new Fake(v -> v.x >= 3.5, v -> true, v -> true)).isEmpty());
   }
}
