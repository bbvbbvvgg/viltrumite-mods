package dev.baranhan.viltrumitecore.client.scp106;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Review of PR #148/#149: a bed dropped by a sound-engine reload comes back whatever its target volume. */
class PocketAmbienceReviewTest {
   @Test
   void aDroppedBedIsReplacedRegardlessOfItsTarget() throws Exception {
      assertTrue(PocketClient.abandoned(true, false, 0), "stopped");
      assertTrue(PocketClient.abandoned(false, false, PocketClient.START_GRACE_TICKS + 1), "inactive past the grace (F3+T)");
      assertFalse(PocketClient.abandoned(false, false, 5), "just started: the engine has not picked it up yet");
      assertFalse(PocketClient.abandoned(false, true, 10_000), "playing");
      String s = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/client/scp106/PocketClient.java"));
      int bed = s.indexOf("private static void bed(");
      String body = s.substring(bed, s.indexOf("static boolean abandoned(", bed));
      assertFalse(body.contains("s.target <= 0.0F"), "the restart no longer depends on the old target");
      assertTrue(body.contains("RETRY_TICKS"), "restarts are spaced out");
   }
}
