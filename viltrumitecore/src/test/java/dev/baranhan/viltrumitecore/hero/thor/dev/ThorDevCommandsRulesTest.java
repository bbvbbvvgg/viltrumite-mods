package dev.baranhan.viltrumitecore.hero.thor.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.thor.ThorMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Foundation m11: dev commands refuse unknown words, guard owner paths, and can be enabled for QA builds. */
class ThorDevCommandsRulesTest {
   @AfterEach
   void reset() {
      ThorDevCommands.resetForTests();
   }

   @Test
   void unknownModeIsRefusedNotSilentlyNormal() {
      assertNull(ThorDevCommands.parseMode("lightning"));
      assertNull(ThorDevCommands.parseMode(""));
      assertEquals(ThorMode.ELECTRIC, ThorDevCommands.parseMode("ELECTRIC"));
      assertEquals(ThorMode.NORMAL, ThorDevCommands.parseMode("normal"));
      assertEquals(ThorMode.values().length, ThorDevCommands.modeKeys().size());
   }

   @Test
   void foundationRootIsReserved() {
      assertThrows(IllegalStateException.class, () -> ThorDevCommands.registerChild("B", "foundation reset", n -> n));
      assertThrows(IllegalStateException.class, () -> ThorDevCommands.registerChild("B", "foundation", n -> n));
   }

   @Test
   void oneOwnersLeafCannotShadowAnotherOwnersSubtree() {
      ThorDevCommands.registerChild("A", "debug hammer throw", n -> n);
      assertThrows(IllegalStateException.class, () -> ThorDevCommands.registerChild("B", "debug hammer", n -> n));
      assertThrows(IllegalStateException.class, () -> ThorDevCommands.registerChild("B", "debug hammer throw now", n -> n));
      ThorDevCommands.registerChild("B", "debug combat", n -> n);
      ThorDevCommands.registerChild("A", "debug hammer recall", n -> n);
      assertEquals("A", ThorDevCommands.owner("debug hammer recall"));
      assertNotNull(ThorDevCommands.owner("debug combat"));
   }

   @Test
   void productionNeedsTheQaProperty() {
      assertTrue(ThorDevCommands.enabled(false, false));
      assertFalse(ThorDevCommands.enabled(true, false));
      assertTrue(ThorDevCommands.enabled(true, true));
   }
}
