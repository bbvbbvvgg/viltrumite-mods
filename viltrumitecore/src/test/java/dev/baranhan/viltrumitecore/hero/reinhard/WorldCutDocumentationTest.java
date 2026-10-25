package dev.baranhan.viltrumitecore.hero.reinhard;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

/** Stage 5 shipping/documentation contract: no claim that pure tests measured real TPS. */
class WorldCutDocumentationTest {
   static String read(String path) throws IOException { return Files.readString(Path.of("..",path)); }
   @Test void coreVersion1290() throws IOException {
      assertTrue(read("viltrumitecore/build.gradle").contains("version = '1.29.0'"));
   }
   @Test void sharedQueueHasRunnableExample() throws IOException {
      assertTrue(read(".agents/skills/add_hero/references/shared-toolkit.md").contains("```java\nCarveJobs.submit(level,"));
   }
   @Test void memoryExplainsCarveFlagsAndDetachedLifetime() throws IOException {
      String text=read(".memory-bank/project.md");
      assertTrue(text.contains("## Reinhard stage 5")); assertTrue(text.contains("WorldCutManager"));
      assertTrue(text.contains("CLEAR_FLAGS")); assertTrue(text.contains("external neighbour updates"));
   }
   @Test void sessionSeparatesBudgetProofFromTpsMeasurement() throws IOException {
      String text=read("SESSION.md");
      assertTrue(text.contains("## 2026-10-13 — reinhard-stage5-world-cut"));
      assertTrue(text.contains("TPS ≥ 15")); assertTrue(text.contains("runServer"));
   }
   @Test void planRecordsManualTpsInsteadOfCheckingIt() throws IOException {
      assertTrue(read("docs/plans/2026-10-11-reinhard-stage5-world-cut.md").contains("Manual TPS ≥ 15"));
   }
}
