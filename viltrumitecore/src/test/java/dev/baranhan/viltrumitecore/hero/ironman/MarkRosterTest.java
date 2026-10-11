package dev.baranhan.viltrumitecore.hero.ironman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkLocation;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkRoster;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkSpec;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class MarkRosterTest {
   @Test
   void allSevenMarksHaveSpecs() {
      assertEquals(7, MarkId.values().length);
      for (MarkId id : MarkId.values()) {
         MarkSpec spec = MarkSpec.of(id);
         assertNotNull(spec, id.name());
         assertTrue(spec.durability() > 0.0F);
         assertTrue(spec.parts() == 7 || spec.parts() == 9 || spec.parts() == 14);
      }

      assertEquals(14, MarkSpec.of(MarkId.MARK_42).parts());
      assertEquals(8, MarkSpec.of(MarkId.WAR_MACHINE_MK2).missileMarks());
      assertEquals(10, MarkSpec.of(MarkId.MARK_17).unibeamCharge());
   }

   @Test
   void markIdAppendOnly() {
      String[] order = {"mark_7", "mark_42", "mark_15", "mark_39", "mark_17", "war_machine_mk2", "iron_heart_mk3"};
      for (int i = 0; i < order.length; i++) {
         assertEquals(order[i], MarkId.values()[i].key());
         assertSame(MarkId.values()[i], MarkId.byKey(order[i]));
      }

      assertNull(MarkId.byId(-1));
      assertNull(MarkId.byId(order.length));
   }

   @Test
   void markInWorldOnce() {
      MarkRoster roster = new MarkRoster();
      assertTrue(roster.move(MarkId.MARK_7, MarkLocation.STORED, MarkLocation.IN_DELIVERY));
      // A second choose packet for the same mark finds it no longer in storage.
      assertFalse(roster.move(MarkId.MARK_7, MarkLocation.STORED, MarkLocation.IN_DELIVERY));
      assertFalse(roster.choosable(MarkId.MARK_7));
      assertTrue(roster.move(MarkId.MARK_7, MarkLocation.IN_DELIVERY, MarkLocation.WORN));
      assertSame(MarkId.MARK_7, roster.worn());
      assertFalse(roster.move(MarkId.MARK_7, MarkLocation.EMPTY, MarkLocation.STORED));
      assertSame(MarkLocation.WORN, roster.location(MarkId.MARK_7));
   }

   @Test
   void wornMarkNotChoosableEmptyIs() {
      MarkRoster roster = new MarkRoster();
      roster.move(MarkId.MARK_42, MarkLocation.STORED, MarkLocation.WORN);
      assertFalse(roster.choosable(MarkId.MARK_42));
      roster.setDurability(MarkId.MARK_42, 33.0F);
      roster.move(MarkId.MARK_42, MarkLocation.WORN, MarkLocation.EMPTY);
      assertTrue(roster.choosable(MarkId.MARK_42));
      assertEquals(33.0F, roster.durability(MarkId.MARK_42), 1.0E-5F);
   }

   @Test
   void breakStarts5MinCooldownThenFullDurability() {
      MarkRoster roster = new MarkRoster();
      roster.move(MarkId.MARK_15, MarkLocation.STORED, MarkLocation.WORN);
      roster.breakMark(MarkId.MARK_15);
      assertSame(MarkLocation.STORED, roster.location(MarkId.MARK_15));
      assertEquals(6000, MarkRoster.BREAK_COOLDOWN);
      assertEquals(MarkRoster.BREAK_COOLDOWN, roster.cooldown(MarkId.MARK_15));
      assertFalse(roster.choosable(MarkId.MARK_15));
      for (int i = 0; i < MarkRoster.BREAK_COOLDOWN - 1; i++) {
         roster.tick();
      }

      assertFalse(roster.choosable(MarkId.MARK_15));
      roster.tick();
      assertTrue(roster.choosable(MarkId.MARK_15));
      assertEquals(MarkSpec.of(MarkId.MARK_15).durability(), roster.durability(MarkId.MARK_15), 1.0E-5F);
   }

   @Test
   void saveLoadRoundTripRecallsWorldInstances() {
      MarkRoster roster = new MarkRoster();
      roster.setDurability(MarkId.MARK_39, 41.5F);
      roster.breakMark(MarkId.IRON_HEART_MK3);
      roster.move(MarkId.MARK_7, MarkLocation.STORED, MarkLocation.EMPTY);
      roster.move(MarkId.MARK_17, MarkLocation.STORED, MarkLocation.WORN);
      CompoundTag tag = new CompoundTag();
      roster.save(tag);
      MarkRoster loaded = new MarkRoster();
      loaded.load(tag);
      assertEquals(41.5F, loaded.durability(MarkId.MARK_39), 1.0E-5F);
      assertEquals(MarkRoster.BREAK_COOLDOWN, loaded.cooldown(MarkId.IRON_HEART_MK3));
      // The empty suit never survives a relog; the worn mark is decided by the suit (IronManState.reconcileMark).
      assertSame(MarkLocation.STORED, loaded.location(MarkId.MARK_7));
      assertSame(MarkLocation.WORN, loaded.location(MarkId.MARK_17));
   }

   @Test
   void garbageLoadsDefaults() {
      CompoundTag tag = new CompoundTag();
      CompoundTag roster = new CompoundTag();
      CompoundTag mark = new CompoundTag();
      mark.putFloat("Durability", Float.NaN);
      mark.putInt("Cooldown", -5);
      mark.putString("Location", "NOWHERE");
      roster.put("mark_7", mark);
      tag.put(MarkRoster.KEY, roster);
      MarkRoster loaded = new MarkRoster();
      loaded.load(tag);
      assertEquals(MarkSpec.of(MarkId.MARK_7).durability(), loaded.durability(MarkId.MARK_7), 1.0E-5F);
      assertEquals(0, loaded.cooldown(MarkId.MARK_7));
      assertSame(MarkLocation.STORED, loaded.location(MarkId.MARK_7));
   }

   @Test
   void deliveryDeathAndLogoutReturnToStored() {
      for (CleanupReason reason : new CleanupReason[]{CleanupReason.DEATH, CleanupReason.DISCONNECT}) {
         IronManState state = new IronManState();
         state.roster.move(MarkId.MARK_7, MarkLocation.STORED, MarkLocation.IN_DELIVERY);
         state.suit.startEquip(MarkId.MARK_7, true, 0);
         state.roster.move(MarkId.MARK_42, MarkLocation.STORED, MarkLocation.EMPTY);
         state.onCleanup(reason);
         assertSame(MarkLocation.STORED, state.roster.location(MarkId.MARK_7), reason.name());
         assertSame(MarkLocation.STORED, state.roster.location(MarkId.MARK_42), reason.name());
         assertSame(SuitState.NONE, state.suit.state(), reason.name());
      }
   }

   @Test
   void relogKeepsWornMarkAndDurability() {
      IronManState state = new IronManState();
      state.roster.move(MarkId.WAR_MACHINE_MK2, MarkLocation.STORED, MarkLocation.IN_DELIVERY);
      state.suit.startEquip(MarkId.WAR_MACHINE_MK2, true, 0);
      for (int i = 0; i < 50; i++) {
         if (state.suit.tick() == Suit.Event.MARK_ON) {
            state.roster.move(MarkId.WAR_MACHINE_MK2, MarkLocation.IN_DELIVERY, MarkLocation.WORN);
         }
      }

      state.roster.setDurability(MarkId.WAR_MACHINE_MK2, 77.0F);
      CompoundTag nbt = new CompoundTag();
      state.save(nbt);
      IronManState loaded = new IronManState();
      loaded.load(nbt);
      assertTrue(loaded.suit.markWorn());
      assertSame(MarkId.WAR_MACHINE_MK2, loaded.suit.mark());
      assertSame(MarkLocation.WORN, loaded.roster.location(MarkId.WAR_MACHINE_MK2));
      assertEquals(77.0F, loaded.roster.durability(MarkId.WAR_MACHINE_MK2), 1.0E-5F);
   }

   @Test
   void deathKeepsDurabilityAndCooldownsHeroChangeResets() {
      IronManState state = new IronManState();
      state.roster.setDurability(MarkId.MARK_39, 10.0F);
      state.roster.breakMark(MarkId.MARK_7);
      state.veronicaCooldown = 900;
      state.signature.cooldown = 50;
      java.util.UUID pod = java.util.UUID.randomUUID();
      state.podUuid = pod;
      state.onCleanup(CleanupReason.DEATH);
      IronManState respawned = IronManState.cloneForRespawn(state);
      assertEquals(pod, respawned.podUuid, "death keeps Veronica");
      assertEquals(10.0F, respawned.roster.durability(MarkId.MARK_39), 1.0E-5F);
      assertEquals(MarkRoster.BREAK_COOLDOWN, respawned.roster.cooldown(MarkId.MARK_7));
      assertEquals(900, respawned.veronicaCooldown);
      assertEquals(50, respawned.signature.cooldown);
      respawned.onCleanup(CleanupReason.HERO_CHANGE);
      assertEquals(MarkSpec.of(MarkId.MARK_39).durability(), respawned.roster.durability(MarkId.MARK_39), 1.0E-5F);
      assertEquals(0, respawned.roster.cooldown(MarkId.MARK_7));
      assertEquals(0, respawned.veronicaCooldown);
   }
}
