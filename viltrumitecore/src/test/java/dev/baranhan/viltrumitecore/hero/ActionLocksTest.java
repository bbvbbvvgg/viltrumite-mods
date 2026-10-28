package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Shared action lock (spec docs/design/2026-10-28-sinclair-design.md §7, §11). */
class ActionLocksTest {
   @AfterEach
   void reset() {
      ActionLocks.clearForTests();
   }

   @Test
   void nullEntityIsNeverLocked() {
      ActionLocks.register(entity -> true);
      assertFalse(ActionLocks.locked(null));
   }

   @Test
   void noProvidersNoLock() {
      assertEquals(0, ActionLocks.providerCount());
      assertDoesNotThrow(() -> ActionLocks.register(null));
      assertEquals(0, ActionLocks.providerCount());
   }

   @Test
   void sameProviderRegistersOnce() {
      Predicate<LivingEntity> provider = entity -> false;
      ActionLocks.register(provider);
      ActionLocks.register(provider);
      assertEquals(1, ActionLocks.providerCount());
   }

   @Test
   void anyProviderLocks() {
      assertFalse(ActionLocks.anyLocks(java.util.List.<Predicate<String>>of(s -> false, s -> false), "x"));
      assertTrue(ActionLocks.anyLocks(java.util.List.<Predicate<String>>of(s -> false, "x"::equals), "x"));
      assertFalse(ActionLocks.anyLocks(java.util.List.<Predicate<String>>of(), "x"));
   }

   /** The three shared input paths ask the lock before starting anything (presses only). */
   @Test
   void inputPathsAskTheLock() throws Exception {
      String root = "src/main/java/dev/baranhan/viltrumitecore/";
      String packet = Files.readString(Path.of(root + "network/packet/HeroInputC2SPacket.java"));
      assertTrue(packet.contains("this.pressed && dev.baranhan.viltrumitecore.hero.ActionLocks.locked(player)"));
      assertTrue(Files.readString(Path.of(root + "hero/HeldInputs.java")).contains("!ActionLocks.locked(player)"));
      assertTrue(Files.readString(Path.of(root + "hero/LegacyKit.java")).contains("!ActionLocks.locked(player)"));
   }
}
