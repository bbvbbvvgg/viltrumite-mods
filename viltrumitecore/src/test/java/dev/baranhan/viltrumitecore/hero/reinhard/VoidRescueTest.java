package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Void rescue search (spec §3.1): pure column scan and nearest-column search. */
class VoidRescueTest {
   @Test
   void findsTopBlock() {
      OptionalInt y = VoidRescue.surfaceY(-64, 319, h -> h <= 70 && h >= 10);
      assertTrue(y.isPresent());
      assertEquals(70, y.getAsInt());
   }

   @Test
   void emptyColumnReturnsEmpty() {
      assertFalse(VoidRescue.surfaceY(-64, 319, h -> false).isPresent());
      assertFalse(VoidRescue.surfaceY(10, 5, h -> true).isPresent());
   }

   @Test
   void searchesNearestColumn() {
      // Islands at (+12, 0) and (-8, -8): the second one is nearer (11.3 < 12).
      Optional<int[]> found = VoidRescue.nearestColumn(64, 4, (dx, dz) -> dx == 12 && dz == 0 || dx == -8 && dz == -8);
      assertTrue(found.isPresent());
      assertArrayEquals(new int[]{-8, -8}, found.get());
   }

   @Test
   void nearestColumnRespectsRadius() {
      assertFalse(VoidRescue.nearestColumn(64, 4, (dx, dz) -> dx == 68 && dz == 0).isPresent());
      assertFalse(VoidRescue.nearestColumn(64, 4, (dx, dz) -> false).isPresent());
   }

   @Test
   void rescueDepthBelowWorld() {
      assertTrue(VoidRescue.needsRescue(-73.0, -64));
      assertFalse(VoidRescue.needsRescue(-71.0, -64));
      assertEquals(8, ReinhardRules.VOID_RESCUE_DEPTH);
      assertEquals(64, ReinhardRules.VOID_SEARCH_RADIUS);
   }
}
