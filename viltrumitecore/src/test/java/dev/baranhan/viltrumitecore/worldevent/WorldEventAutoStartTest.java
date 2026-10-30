package dev.baranhan.viltrumitecore.worldevent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

/** Viltrumite NPC events start only by command: no scheduler, no resume from a save. */
class WorldEventAutoStartTest {
   @Test
   void commandEventsSurvive() {
      assertTrue(WorldEventManager.keepsEvent(true));
   }

   @Test
   void legacyAutomaticEventsAreDropped() {
      assertFalse(WorldEventManager.keepsEvent(false));
   }

   @Test
   void savedEventsDoNotResumeAtServerStart() {
      CompoundTag event = new CompoundTag();
      event.putString("Id", "trio");
      event.putBoolean("Manual", true);
      ListTag events = new ListTag();
      events.add(event);
      CompoundTag tag = new CompoundTag();
      tag.put("Events", events);
      tag.putLong("LastEventTime", 42L);
      WorldEventManager manager = WorldEventManager.load(tag);
      assertTrue(manager.activeEvents().isEmpty());
      assertEquals(42L, manager.lastEventTime());
   }
}
