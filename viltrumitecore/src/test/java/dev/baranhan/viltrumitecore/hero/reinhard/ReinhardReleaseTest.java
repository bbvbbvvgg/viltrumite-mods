package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ReinhardReleaseTest {
   @Test void qualityReworkContentUsesNewCoreMinorVersion() throws Exception {
      var version = Pattern.compile("(?m)^version = '([^']+)'").matcher(Files.readString(Path.of("../viltrumitecore/build.gradle")));
      assertTrue(version.find(), "Core release version must be declared");
      assertEquals("1.32.0", version.group(1), "New Reinhard art/input content requires a new core minor; flight stays unchanged");
   }
}
