package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ReinhardReleaseTest {
   @Test void combatReworkUsesNewMinorVersion() throws Exception {
      var version = Pattern.compile("(?m)^version = '([^']+)'").matcher(Files.readString(Path.of("../viltrumitecore/build.gradle")));
      assertTrue(version.find(), "Core release version must be declared");
      assertEquals("1.36.0", version.group(1), "Combat fluidity is a core patch; flight stays unchanged");
   }
   @Test void snapshotCapacityUpgradeRejectsOldNetworkPeers() throws Exception {
      String source = Files.readString(Path.of("../viltrumitecore/src/main/java/dev/baranhan/viltrumitecore/network/CoreMessages.java"));
      assertTrue(source.contains("PROTOCOL_VERSION = \"4\""));
      assertTrue(source.contains("networkProtocolVersion(() -> PROTOCOL_VERSION)"));
      assertTrue(source.contains("clientAcceptedVersions(PROTOCOL_VERSION::equals)"));
      assertTrue(source.contains("serverAcceptedVersions(PROTOCOL_VERSION::equals)"));
   }
}
