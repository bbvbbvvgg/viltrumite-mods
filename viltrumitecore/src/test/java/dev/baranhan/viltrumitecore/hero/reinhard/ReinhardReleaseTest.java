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
      assertEquals("1.50.0", version.group(1), "cinematic hero subtitles are a feature release (1.46.0..1.49.x are taken by parallel Thor branches)");
   }
   @Test void snapshotCapacityUpgradeRejectsOldNetworkPeers() throws Exception {
      String source = Files.readString(Path.of("../viltrumitecore/src/main/java/dev/baranhan/viltrumitecore/network/CoreMessages.java"));
      assertTrue(source.contains("PROTOCOL_VERSION = \"11\""), "HeroLineS2CPacket is a new packet: old peers must be refused");
      assertTrue(source.contains("networkProtocolVersion(() -> PROTOCOL_VERSION)"));
      assertTrue(source.contains("clientAcceptedVersions(PROTOCOL_VERSION::equals)"));
      assertTrue(source.contains("serverAcceptedVersions(PROTOCOL_VERSION::equals)"));
   }
}
