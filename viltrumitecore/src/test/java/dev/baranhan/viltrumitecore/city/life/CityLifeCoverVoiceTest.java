package dev.baranhan.viltrumitecore.city.life;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.city.citizen.CitizenState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regression for the review of PR #147: a citizen hiding in cover still gets its periodic voice. */
class CityLifeCoverVoiceTest {
   @Test
   void coverIsPanickedAndStillVoiced() throws IOException {
      assertTrue(CitizenState.COVER.panicked(), "precondition of the bug: cover counts as panicked");
      assertTrue(CityLife.periodicVoice(CitizenState.COVER));
      assertTrue(CityLife.periodicVoice(CitizenState.IDLE));
      assertTrue(CityLife.periodicVoice(CitizenState.RECOVERING));
      assertFalse(CityLife.periodicVoice(CitizenState.FLEEING));
      String s = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/city/life/CityLife.java"));
      assertFalse(s.contains("} else if ((s == CitizenState.IDLE || s == CitizenState.RECOVERING || s == CitizenState.COVER) && r.nextInt(6) == 0) {"),
         "the voice check must not be the else branch of the panic count");
      assertTrue(s.contains("if (periodicVoice(s) && r.nextInt(6) == 0) {"));
   }
}
