package dev.baranhan.viltrumitecore.hero.dio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** /dio blood infinite: every cost goes through spendAllBlood, the mode persists and survives a death. */
class DioBloodDebugTest {
   @Test
   void spendingEmptiesNormallyAndKeepsFullWhenInfinite() {
      DioState state = new DioState();
      state.blood = DioRules.BLOOD_MAX;
      state.spendAllBlood();
      assertEquals(0, state.blood);
      state.infiniteBlood = true;
      state.blood = 12;
      state.spendAllBlood();
      assertEquals(DioRules.BLOOD_MAX, state.blood);
   }

   @Test
   void infiniteModePersistsAndCarriesOverDeath() {
      DioState state = new DioState();
      state.infiniteBlood = true;
      state.blood = 40;
      CompoundTag nbt = new CompoundTag();
      state.save(nbt);
      DioState loaded = new DioState();
      loaded.load(nbt);
      assertTrue(loaded.infiniteBlood);
      DioState respawned = loaded.respawnCopy();
      assertTrue(respawned.infiniteBlood);
      assertEquals(DioRules.BLOOD_MAX, respawned.blood, "infinite mode respawns full, not halved");
      DioState normal = new DioState();
      normal.blood = 40;
      assertFalse(normal.respawnCopy().infiniteBlood);
      assertEquals(20, normal.respawnCopy().blood);
   }

   @Test
   void infiniteFlagDoesNotCollideWithOtherFlags() {
      int[] flags = {DioRules.FLAG_STAND, DioRules.FLAG_TIME_STOP, DioRules.FLAG_SUN, DioRules.FLAG_GUARD, DioRules.FLAG_RESUMING, DioRules.FLAG_ULT,
         DioRules.FLAG_FINISHER, DioRules.FLAG_DRAIN_HOLD};
      for (int f : flags) {
         assertTrue(f != DioRules.FLAG_INFINITE_BLOOD);
      }

      assertTrue(DioRules.FLAG_INFINITE_BLOOD < 31);
   }
}
