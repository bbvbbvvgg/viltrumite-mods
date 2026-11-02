package dev.baranhan.viltrumitecore.compat.spider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/**
 * Binds the adapter against the real Falbiks Heroes JAR when one is given ({@code ./gradlew test -PfalbiksJar=<path>}).
 * The JAR is never in the repository, so CI skips this with the reason. Classes are loaded without initialisation
 * in an isolated loader; nothing of the JAR is changed or copied.
 */
class FalbiksJarSignatureTest {
   @Test
   void theRealJarExposesEveryMemberTheAdapterNeeds() throws Exception {
      String jar = System.getProperty("falbiks.jar", "");
      assumeTrue(!jar.isBlank() && Files.isRegularFile(Path.of(jar)), "no -PfalbiksJar given: the real-JAR check is skipped");
      try (URLClassLoader loader = new URLClassLoader(new URL[]{Path.of(jar).toUri().toURL()}, FalbiksJarSignatureTest.class.getClassLoader())) {
         SpiderBridge b = SpiderCompat.bind(loader, "0.1.0");
         System.out.println("[SpiderCompat real JAR] " + b.id() + " " + b.features() + " " + b.diagnostics());
         assertEquals(EnumSet.allOf(SpiderFeature.class), b.features(), b.diagnostics().toString());
         assertFalse(((FalbiksSpiderV0)b).cutsByFields(), "endSwing is found in 0.1.0");
         assertTrue(b.diagnostics().isEmpty(), b.diagnostics().toString());
      }
   }
}
