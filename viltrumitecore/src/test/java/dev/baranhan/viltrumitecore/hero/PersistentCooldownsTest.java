package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Cooldowns in persisted player NBT (Reinhard spec §4.2): online ticks only, removed at 0. */
class PersistentCooldownsTest {
   @Test
   void tickRemovesAtZero() {
      CompoundTag store = new CompoundTag();
      PersistentCooldowns.set(store, "a", 2);
      PersistentCooldowns.tickTag(store);
      assertEquals(1, PersistentCooldowns.get(store, "a"));
      PersistentCooldowns.tickTag(store);
      assertEquals(0, PersistentCooldowns.get(store, "a"));
      assertFalse(store.contains("a"), "a finished cooldown leaves no key");
   }

   @Test
   void keysIndependent() {
      CompoundTag store = new CompoundTag();
      PersistentCooldowns.set(store, "a", 1);
      PersistentCooldowns.set(store, "b", 3);
      PersistentCooldowns.tickTag(store);
      assertEquals(0, PersistentCooldowns.get(store, "a"));
      assertEquals(2, PersistentCooldowns.get(store, "b"));
   }

   @Test
   void setZeroClears() {
      CompoundTag store = new CompoundTag();
      PersistentCooldowns.set(store, "a", 5);
      PersistentCooldowns.set(store, "a", 0);
      assertFalse(store.contains("a"));
   }
}
