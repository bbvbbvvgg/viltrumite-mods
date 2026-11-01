package dev.baranhan.viltrumitecore.city.links;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathCause;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CityDisasterLinksTest {
   @Test
   void collapseCausesKeepTheirAftermathCause() {
      assertEquals(AftermathCause.METEOR, CityDisasterLinks.aftermathCause("meteor"));
      assertEquals(AftermathCause.LEVIATHAN, CityDisasterLinks.aftermathCause("leviathan"));
      assertEquals(AftermathCause.ULTRON, CityDisasterLinks.aftermathCause("ultron"));
      assertEquals(AftermathCause.COLLAPSE, CityDisasterLinks.aftermathCause("command"));
      assertEquals(AftermathCause.COLLAPSE, CityDisasterLinks.aftermathCause("secondary"));
   }

   @Test
   void outageRadiusGrowsWithDarkCells() {
      assertEquals(24.0F, CityDisasterLinks.outageRadius(0), 1.0E-3F);
      assertTrue(CityDisasterLinks.outageRadius(9) > CityDisasterLinks.outageRadius(1));
      assertEquals(Math.sqrt(9 * 48.0 * 48.0 / Math.PI), CityDisasterLinks.outageRadius(9), 1.0E-2);
   }

   @Test
   void systemsAreWiredTogether() throws Exception {
      Path root = Path.of("src/main/java/dev/baranhan/viltrumitecore");
      String grid = Files.readString(root.resolve("city/power/PowerGrid.java"));
      String city = Files.readString(root.resolve("city/CityManager.java"));
      assertTrue(grid.contains("CityDisasterLinks.collapseOwns"), "collapse bursts must not hit the grid twice");
      assertTrue(city.contains("CityAftermath.onCityReset") && city.contains("CityDisasterLinks.onCityReset")
         && city.contains("this.power.onCityReset()"), "/city reset clears grid, ruins and grid outages together");
   }
}
