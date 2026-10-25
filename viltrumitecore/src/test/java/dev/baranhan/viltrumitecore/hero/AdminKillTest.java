package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** /kill scope: hero HP floors step aside only inside it. */
class AdminKillTest {
   @Test
   void inactiveByDefault() {
      assertFalse(AdminKill.active());
   }

   @Test
   void scopeNests() {
      AdminKill.run(() -> {
         assertTrue(AdminKill.active());
         AdminKill.run(() -> assertTrue(AdminKill.active()));
         assertTrue(AdminKill.active());
      });
      assertFalse(AdminKill.active());
   }

   /** AGENTS.md: members a mixin adds are {@code @Unique}, including the /kill redirect handler. */
   @Test
   void killRedirectHandlerIsUnique() throws java.io.IOException {
      String source = java.nio.file.Files.readString(java.nio.file.Path.of(
         "src/main/java/dev/baranhan/viltrumitecore/mixin/KillCommandMixin.java"));
      int handler = source.indexOf("viltrumitecore$adminKill(");
      assertTrue(handler > 0);
      String annotations = source.substring(source.lastIndexOf("}", handler) + 1, handler);
      assertTrue(annotations.contains("@Unique"), annotations);
      assertTrue(annotations.contains("@Redirect"), annotations);
   }

   @Test
   void scopeClosesOnThrow() {
      assertThrows(IllegalStateException.class, () -> AdminKill.run(() -> {
         throw new IllegalStateException("boom");
      }));
      assertFalse(AdminKill.active());
   }
}
