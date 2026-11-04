package dev.baranhan.viltrumitecore.city.citizen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Review of PR #149: upstairs occupants use the shared goal clock (no entity-counter parity miss). */
class OccupyGoalClockReviewTest {
   @Test
   void occupantsStrollOnTheSharedClock() throws Exception {
      String s = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/city/citizen/CitizenGoals.java"));
      int occupy = s.indexOf("static final class Occupy extends Goal");
      int canUse = s.indexOf("public boolean canUse()", occupy);
      String body = s.substring(canUse, s.indexOf("CityManager city", canUse));
      assertTrue(body.contains("!due(this.c, 20)"));
      assertFalse(s.contains("(this.c.tickCount + this.c.getId()) % 20"), "no goal is timed by its own counter any more");
   }
}
