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
      var protocol = Pattern.compile("PROTOCOL_VERSION = \"(\\d+)\"").matcher(source);
      assertTrue(protocol.find(), "the network protocol version must be declared");
      // 7: the snapshot capacity upgrade; later wire changes (8: Thor scene packets) only raise it.
      assertTrue(Integer.parseInt(protocol.group(1)) >= 7, "peers older than the snapshot capacity upgrade stay rejected");
      assertTrue(source.contains("networkProtocolVersion(() -> PROTOCOL_VERSION)"));
      assertTrue(source.contains("clientAcceptedVersions(PROTOCOL_VERSION::equals)"));
      assertTrue(source.contains("serverAcceptedVersions(PROTOCOL_VERSION::equals)"));
   }
}
