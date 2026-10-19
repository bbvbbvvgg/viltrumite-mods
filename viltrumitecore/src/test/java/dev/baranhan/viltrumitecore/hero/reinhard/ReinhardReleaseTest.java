package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ReinhardReleaseTest {
   @Test void featureContentShipsAsCore1320() throws Exception {
      var version = Pattern.compile("(?m)^version = '([^']+)'").matcher(Files.readString(Path.of("../viltrumitecore/build.gradle")));
      assertTrue(version.find(), "Core release version must be declared");
      assertEquals("1.32.0", version.group(1), "The Reinhard rework adds content: minor bump from 1.31.0");
   }
}
