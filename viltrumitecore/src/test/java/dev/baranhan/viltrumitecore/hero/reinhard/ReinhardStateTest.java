package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Per-player Reinhard state: cooldown ticking and NBT round trip. */
class ReinhardStateTest {
   @Test
   void stateSaveLoadRoundTrip() {
      ReinhardState state = new ReinhardState();
      state.mode = ReinhardMode.HOLY_SWORD;
      state.modeCooldown = 33;
      state.cooldowns[3] = 120;
      state.cooldowns[13] = 7;
      CompoundTag nbt = new CompoundTag();
      state.save(nbt);
      ReinhardState loaded = new ReinhardState();
      loaded.load(nbt);
      assertSame(ReinhardMode.HOLY_SWORD, loaded.mode);
      assertEquals(33, loaded.modeCooldown);
      assertArrayEquals(state.cooldowns, loaded.cooldowns);
   }

   @Test
   void loadWithoutKeyKeepsDefaults() {
      ReinhardState loaded = new ReinhardState();
      loaded.load(new CompoundTag());
      assertSame(ReinhardMode.RESTRAINED, loaded.mode);
      assertEquals(0, loaded.modeCooldown);
      assertEquals(ReinhardState.COOLDOWN_SLOTS, loaded.cooldowns.length);
   }

   @Test
   void cooldownsTickDown() {
      ReinhardState state = new ReinhardState();
      state.modeCooldown = 2;
      state.cooldowns[0] = 1;
      state.cooldowns[5] = 3;
      state.tick();
      assertEquals(1, state.modeCooldown);
      assertEquals(0, state.cooldowns[0]);
      assertEquals(2, state.cooldowns[5]);
      state.tick();
      state.tick();
      assertEquals(0, state.modeCooldown);
      assertEquals(0, state.cooldowns[0]);
      assertEquals(0, state.cooldowns[5]);
   }
}
