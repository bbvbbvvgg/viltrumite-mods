package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ReinhardReleaseTest {
   @Test void overhaulIsAFeatureRelease() throws Exception {
      var version = Pattern.compile("(?m)^version = '([^']+)'").matcher(Files.readString(Path.of("../viltrumitecore/build.gradle")));
      assertTrue(version.find(), "Core release version must be declared");
      assertEquals("1.43.0", version.group(1), "the redrawn Reinhard ability icons are a feature release over 1.42.0");
   }
   @Test void snapshotCapacityUpgradeRejectsOldNetworkPeers() throws Exception {
      String source = Files.readString(Path.of("../viltrumitecore/src/main/java/dev/baranhan/viltrumitecore/network/CoreMessages.java"));
      assertTrue(source.contains("PROTOCOL_VERSION = \"6\""));
      assertTrue(source.contains("networkProtocolVersion(() -> PROTOCOL_VERSION)"));
      assertTrue(source.contains("clientAcceptedVersions(PROTOCOL_VERSION::equals)"));
      assertTrue(source.contains("serverAcceptedVersions(PROTOCOL_VERSION::equals)"));
   }
}
