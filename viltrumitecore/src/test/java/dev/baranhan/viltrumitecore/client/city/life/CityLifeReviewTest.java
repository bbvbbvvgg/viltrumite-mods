package dev.baranhan.viltrumitecore.client.city.life;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regressions for the review of PR #147: the city soundscape plays only around a city. */
class CityLifeReviewTest {
   private static final Path AMBIENCE = Path.of("src/main/java/dev/baranhan/viltrumitecore/client/city/life/CityLifeAmbience.java");

   @Test
   void anOrdinaryWorldOrAnotherDimensionHasNoCitySoundscape() {
      // no grid packet and no citizen ever heard: an ordinary world, the Nether, the End
      assertFalse(CityLifeMixer.cityPresent(false, 0, Long.MIN_VALUE, 5000L));
      // a city grid or people around: the street plays
      assertTrue(CityLifeMixer.cityPresent(true, 0, Long.MIN_VALUE, 5000L));
      assertTrue(CityLifeMixer.cityPresent(false, 3, 5000L, 5000L));
      // a quiet street between two scans of people keeps the city for a while, then it fades
      assertTrue(CityLifeMixer.cityPresent(false, 0, 5000L, 5000L + CityLifeMixer.PRESENCE_MEMORY - 1));
      assertFalse(CityLifeMixer.cityPresent(false, 0, 5000L, 5000L + CityLifeMixer.PRESENCE_MEMORY));
      // a clock that went back (another world) never counts as recent
      assertFalse(CityLifeMixer.cityPresent(false, 0, 9000L, 100L));
   }

   @Test
   void theAmbienceIsGatedOnCityPresence() throws IOException {
      String s = Files.readString(AMBIENCE);
      assertTrue(s.contains("present = CityLifeMixer.cityPresent(dev.baranhan.viltrumitecore.client.city.power.CityPowerClient.known(), n, citizensSeenAt, level.getGameTime());"));
      assertTrue(s.contains("if (!present) {\n         // an ordinary world"), "beds fade to zero outside a city");
      assertTrue(s.contains("if (!present || indoor || cooldown[GAP] > 0) {"), "no bells, dogs or pigeons outside a city");
      assertTrue(s.contains("citizensSeenAt = Long.MIN_VALUE;\n         present = false;"), "presence resets on a level change");
   }
}
