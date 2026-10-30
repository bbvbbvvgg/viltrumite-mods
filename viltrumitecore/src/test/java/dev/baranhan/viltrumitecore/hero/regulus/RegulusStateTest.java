package dev.baranhan.viltrumitecore.hero.regulus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Persistence (relog / death never dodge cooldowns) and the first-kit migration. */
class RegulusStateTest {

   @Test
   void saveLoadRoundTrip() {
      RegulusState state = new RegulusState();
      state.lionCooldown = 11;
      state.sandCooldown = 22;
      state.severCooldown = 33;
      state.trampleCooldown = 44;
      state.replyCooldown = 55;
      state.ultCooldown = 1999;
      state.strain = 640;
      state.lionLock = 77;
      CompoundTag nbt = new CompoundTag();
      state.save(nbt);
      RegulusState back = new RegulusState();
      back.load(nbt);
      assertEquals(11, back.lionCooldown);
      assertEquals(22, back.sandCooldown);
      assertEquals(33, back.severCooldown);
      assertEquals(44, back.trampleCooldown);
      assertEquals(55, back.replyCooldown);
      assertEquals(1999, back.ultCooldown);
      assertEquals(640, back.strain);
      assertEquals(77, back.lionLock);
   }

   @Test
   void loadClampsHostileValues() {
      CompoundTag tag = new CompoundTag();
      tag.putInt("Lion", -40);
      tag.putInt("Ult", Integer.MAX_VALUE);
      tag.putInt("Strain", 99999);
      CompoundTag nbt = new CompoundTag();
      nbt.put(RegulusState.TAG, tag);
      RegulusState state = new RegulusState();
      state.load(nbt);
      assertEquals(0, state.lionCooldown);
      assertEquals(12000, state.ultCooldown);
      assertEquals(RegulusRules.STRAIN_MAX, state.strain);
   }

   @Test
   void firstKitCooldownsCarryOverToTheirSuccessors() {
      CompoundTag old = new CompoundTag();
      old.putInt("regulus:lions_heart", 5);
      old.putInt("regulus:debris_kick", 60);
      old.putInt("regulus:mania", 70);
      old.putInt("regulus:greeds_embrace", 80);
      old.putInt("regulus:counter", 90);
      old.putInt("regulus:evangelium", 1500);
      old.putInt("madeup:ability", 50);
      CompoundTag nbt = new CompoundTag();
      nbt.put(RegulusState.LEGACY_TAG, old);
      RegulusState state = new RegulusState();
      state.load(nbt);
      assertEquals(5, state.lionCooldown);
      assertEquals(60, state.sandCooldown);
      assertEquals(70, state.severCooldown);
      assertEquals(80, state.trampleCooldown);
      assertEquals(90, state.replyCooldown);
      assertEquals(1500, state.ultCooldown);
      // the next save writes only the new tag
      CompoundTag saved = new CompoundTag();
      saved.put(RegulusState.LEGACY_TAG, old);
      state.save(saved);
      assertFalse(saved.contains(RegulusState.LEGACY_TAG));
      assertTrue(saved.contains(RegulusState.TAG));
   }

   @Test
   void missingTagsLoadAsFresh() {
      RegulusState state = new RegulusState();
      state.load(new CompoundTag());
      assertEquals(0, state.ultCooldown);
      assertEquals(0, state.strain);
   }

   @Test
   void resetTransientKeepsCooldownsStrainAndLock() {
      RegulusState state = new RegulusState();
      state.ultCooldown = 100;
      state.strain = 300;
      state.lionLock = 20;
      state.startMove(RegulusMove.SAND);
      state.lion = true;
      state.comboStep = 2;
      state.subjects.add(UUID.randomUUID());
      state.host = state.subjects.get(0);
      state.replyAttacker = UUID.randomUUID();
      state.resetTransient();
      assertNull(state.move);
      assertFalse(state.lion);
      assertEquals(-1, state.comboStep);
      assertTrue(state.subjects.isEmpty());
      assertNull(state.host);
      assertNull(state.replyAttacker);
      assertEquals(100, state.ultCooldown);
      assertEquals(300, state.strain);
      assertEquals(20, state.lionLock);
   }

   @Test
   void aNewBodyKeepsOnlyTheCooldowns() {
      RegulusState state = new RegulusState();
      state.sandCooldown = 50;
      state.ultCooldown = 1200;
      state.strain = 900;
      state.lionLock = 100;
      state.lion = true;
      state.subjects.add(UUID.randomUUID());
      RegulusState copy = state.respawnCopy();
      assertEquals(50, copy.sandCooldown);
      assertEquals(1200, copy.ultCooldown);
      assertEquals(0, copy.strain);
      assertEquals(0, copy.lionLock);
      assertFalse(copy.lion);
      assertTrue(copy.subjects.isEmpty());
   }

   @Test
   void movesAdvanceTheSeedAndClear() {
      RegulusState state = new RegulusState();
      state.startMove(RegulusMove.SAND);
      int first = state.seed;
      assertTrue(state.busy());
      state.startMove(RegulusMove.AIR_CUT);
      assertTrue(first != state.seed);
      assertTrue(state.seed >= 0 && state.seed <= 0x7FFF);
      state.startMove(RegulusMove.SEIZURE);
      assertTrue(state.seized());
      state.clearMove();
      assertFalse(state.busy());
      assertEquals(0, state.moveTick);
   }

   @Test
   void cooldownIndicesReadTheRightCounters() {
      RegulusState state = new RegulusState();
      state.lionCooldown = 1;
      state.sandCooldown = 2;
      state.severCooldown = 3;
      state.trampleCooldown = 4;
      state.replyCooldown = 5;
      state.ultCooldown = 6;
      for (int i = 0; i < 6; i++) {
         assertEquals(i + 1, state.cooldown(i));
      }
   }

   @Test
   void processionCooldownCarriesIntoTrample() {
      CompoundTag tag = new CompoundTag();
      tag.putInt("Procession", 120);
      CompoundTag nbt = new CompoundTag();
      nbt.put(RegulusState.TAG, tag);
      RegulusState state = new RegulusState();
      state.load(nbt);
      assertEquals(120, state.trampleCooldown);
   }
}
