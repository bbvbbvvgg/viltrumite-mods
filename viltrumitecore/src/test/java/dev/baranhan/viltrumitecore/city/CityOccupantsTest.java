package dev.baranhan.viltrumitecore.city;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Issue #138: people upstairs in tall buildings (so a collapse or a breach has someone to fall). */
class CityOccupantsTest {
   @Test
   void occupants_top_up_slowly_and_respect_the_config() {
      assertEquals(2, CityPopulation.occupantsWanted(6, 0), "two new ones per pass");
      assertEquals(1, CityPopulation.occupantsWanted(6, 5));
      assertEquals(0, CityPopulation.occupantsWanted(6, 6));
      assertEquals(0, CityPopulation.occupantsWanted(6, 9), "never negative");
      assertEquals(0, CityPopulation.occupantsWanted(0, 0), "0 = off");
      assertEquals(0, CityPopulation.occupantsWanted(-3, 0));
      assertEquals(0, CityPopulation.occupantsWanted(1000, CityPopulation.OCCUPANT_MAX), "clamped to the hard maximum");
   }

   @Test
   void only_tall_buildings_and_real_heights() {
      assertNull(CityPopulation.occupantFloors(-51, -51 + CityPopulation.OCCUPANT_TALL - 1), "a low building");
      assertArrayEquals(new int[]{-51 + CityPopulation.OCCUPANT_FLOOR, 147}, CityPopulation.occupantFloors(-51, 150));
      int[] f = CityPopulation.occupantFloors(0, CityPopulation.OCCUPANT_TALL);
      assertEquals(CityPopulation.OCCUPANT_FLOOR, f[0], "at least a real fall above the street");
   }
}
