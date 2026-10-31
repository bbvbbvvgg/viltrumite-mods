package dev.baranhan.viltrumitecore.hero.ironman.legion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkDamage;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkLocation;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkRoster;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkSpec;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class LegionRosterTest {
   @Test
   void legionUsesOwnershipTransitions() {
      MarkRoster roster = new MarkRoster();
      assertTrue(roster.move(MarkId.MARK_7, MarkLocation.STORED, MarkLocation.LEGION));
      // A mark is in one place only: it cannot also be worn, emptied or delivered.
      assertFalse(roster.move(MarkId.MARK_7, MarkLocation.STORED, MarkLocation.WORN));
      assertFalse(roster.move(MarkId.MARK_7, MarkLocation.EMPTY, MarkLocation.IN_DELIVERY));
      assertFalse(roster.choosable(MarkId.MARK_7));
      assertFalse(roster.legionCallable(MarkId.MARK_7));
      assertEquals(MarkLocation.LEGION, roster.location(MarkId.MARK_7));
      assertEquals(List.of(MarkId.MARK_7), roster.legion());
   }

   @Test
   void callableOnlyFromStorageWithoutCooldown() {
      MarkRoster roster = new MarkRoster();
      roster.move(MarkId.MARK_42, MarkLocation.STORED, MarkLocation.WORN);
      roster.move(MarkId.MARK_15, MarkLocation.STORED, MarkLocation.EMPTY);
      roster.move(MarkId.MARK_39, MarkLocation.STORED, MarkLocation.IN_DELIVERY);
      roster.breakMark(MarkId.MARK_17);
      roster.setDurability(MarkId.IRON_HEART_MK3, 0.0F);
      assertTrue(roster.legionCallable(MarkId.MARK_7));
      assertTrue(roster.legionCallable(MarkId.WAR_MACHINE_MK2));
      for (MarkId id : new MarkId[]{MarkId.MARK_42, MarkId.MARK_15, MarkId.MARK_39, MarkId.MARK_17, MarkId.IRON_HEART_MK3}) {
         assertFalse(roster.legionCallable(id), id.name());
      }
   }

   @Test
   void recallWorldReturnsLegion() {
      MarkRoster roster = new MarkRoster();
      roster.move(MarkId.MARK_7, MarkLocation.STORED, MarkLocation.LEGION);
      roster.move(MarkId.MARK_42, MarkLocation.STORED, MarkLocation.WORN);
      roster.recallWorld();
      assertEquals(MarkLocation.STORED, roster.location(MarkId.MARK_7));
      assertEquals(MarkLocation.WORN, roster.location(MarkId.MARK_42));
      assertTrue(MarkLocation.LEGION.inWorld());
      assertFalse(MarkLocation.WORN.inWorld());
   }

   @Test
   void recallLegionKeepsTheRest() {
      MarkRoster roster = new MarkRoster();
      roster.move(MarkId.MARK_7, MarkLocation.STORED, MarkLocation.LEGION);
      roster.move(MarkId.MARK_15, MarkLocation.STORED, MarkLocation.EMPTY);
      roster.recallLegion();
      assertEquals(MarkLocation.STORED, roster.location(MarkId.MARK_7));
      assertEquals(MarkLocation.EMPTY, roster.location(MarkId.MARK_15));
   }

   @Test
   void durabilityStoredOnRecall() {
      MarkRoster roster = new MarkRoster();
      roster.move(MarkId.MARK_7, MarkLocation.STORED, MarkLocation.LEGION);
      MarkDamage.Result hit = roster.damageLegion(MarkId.MARK_7, 100.0F);
      float expected = MarkSpec.of(MarkId.MARK_7).durability() - 100.0F * MarkSpec.of(MarkId.MARK_7).plating();
      assertEquals(expected, hit.durability(), 1.0E-4);
      roster.recallLegion();
      // No free repair through a re-call: the same durability comes back.
      assertEquals(expected, roster.durability(MarkId.MARK_7), 1.0E-4);
      assertTrue(roster.move(MarkId.MARK_7, MarkLocation.STORED, MarkLocation.LEGION));
      assertEquals(expected, roster.durability(MarkId.MARK_7), 1.0E-4);
   }

   @Test
   void damageOnlyWhileFlying() {
      MarkRoster roster = new MarkRoster();
      MarkDamage.Result none = roster.damageLegion(MarkId.MARK_7, 50.0F);
      assertFalse(none.absorbed());
      assertEquals(MarkSpec.of(MarkId.MARK_7).durability(), roster.durability(MarkId.MARK_7), 1.0E-4);
   }

   @Test
   void breakStartsMarkCooldown() {
      MarkRoster roster = new MarkRoster();
      roster.move(MarkId.MARK_42, MarkLocation.STORED, MarkLocation.LEGION);
      MarkDamage.Result hit = roster.damageLegion(MarkId.MARK_42, 10_000.0F);
      assertTrue(hit.broke());
      roster.breakMark(MarkId.MARK_42);
      assertEquals(MarkLocation.STORED, roster.location(MarkId.MARK_42));
      assertEquals(MarkRoster.BREAK_COOLDOWN, roster.cooldown(MarkId.MARK_42));
      assertFalse(roster.legionCallable(MarkId.MARK_42));
   }

   @Test
   void relogBringsLegionHomeWithDurability() {
      MarkRoster roster = new MarkRoster();
      roster.move(MarkId.MARK_39, MarkLocation.STORED, MarkLocation.LEGION);
      roster.damageLegion(MarkId.MARK_39, 40.0F);
      float left = roster.durability(MarkId.MARK_39);
      CompoundTag tag = new CompoundTag();
      roster.save(tag);
      MarkRoster loaded = new MarkRoster();
      loaded.load(tag);
      assertEquals(MarkLocation.STORED, loaded.location(MarkId.MARK_39));
      assertEquals(left, loaded.durability(MarkId.MARK_39), 1.0E-4);
      assertTrue(loaded.legion().isEmpty());
   }
}
