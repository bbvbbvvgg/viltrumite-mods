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
      int[] v = java.util.Arrays.stream(version.group(1).split("\\.")).mapToInt(Integer::parseInt).toArray();
      assertTrue(v[0] > 1 || v[1] >= 45, "two-handed Reinhard strikes, the recorded shatter and the lattice sphere shipped as feature release 1.45.0");
   }
   @Test void snapshotCapacityUpgradeRejectsOldNetworkPeers() throws Exception {
      String source = Files.readString(Path.of("../viltrumitecore/src/main/java/dev/baranhan/viltrumitecore/network/CoreMessages.java"));
      assertTrue(source.contains("PROTOCOL_VERSION = \"7\""));
      assertTrue(source.contains("networkProtocolVersion(() -> PROTOCOL_VERSION)"));
      assertTrue(source.contains("clientAcceptedVersions(PROTOCOL_VERSION::equals)"));
      assertTrue(source.contains("serverAcceptedVersions(PROTOCOL_VERSION::equals)"));
   }
}
