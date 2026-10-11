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

   @Test
   void scopeClosesOnThrow() {
      assertThrows(IllegalStateException.class, () -> AdminKill.run(() -> {
         throw new IllegalStateException("boom");
      }));
      assertFalse(AdminKill.active());
   }
}
