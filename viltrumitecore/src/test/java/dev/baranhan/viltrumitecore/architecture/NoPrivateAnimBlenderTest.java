package dev.baranhan.viltrumitecore.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * AGENTS.md section 3: clip sampling and pose composition live in the shared animation core (client/anim/).
 * An entity renderer may consume it and add a head turn, but must not sample keyframe channels itself
 * (regression for the private Scp096Blend found in review of PR #129).
 */
class NoPrivateAnimBlenderTest {
   private static final Path ROOT = Path.of("src/main/java/dev/baranhan/viltrumitecore/client");
   private static final Pattern SAMPLES_CHANNEL = Pattern.compile("\\b(rot|pos|scale)[XYZ]\\(\\)\\.sample\\(");

   @Test
   void scp096UsesTheSharedQuaternionBlend() throws IOException {
      assertTrue(Files.exists(ROOT.resolve("anim/pose/QuatPoseBlend.java")));
      assertTrue(Files.notExists(ROOT.resolve("scp096/Scp096Blend.java")));
      assertTrue(Files.readString(ROOT.resolve("scp096/Scp096Renderer.java")).contains("QuatPoseBlend"));
   }

   @Test
   void scp096ClientDoesNotSampleClipChannels() throws IOException {
      List<String> bad;
      try (Stream<Path> files = Files.walk(ROOT.resolve("scp096"))) {
         bad = files.filter(p -> p.toString().endsWith(".java")).filter(p -> {
            try {
               return SAMPLES_CHANNEL.matcher(Files.readString(p)).find();
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }).map(Path::toString).toList();
      }
      assertTrue(bad.isEmpty(), "keyframe sampling outside client/anim: " + bad);
   }
}
