package dev.baranhan.viltrumitecore.t1000;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

/** World memory: instances survive a restart as dormant (never duplicated), destruction starts the respawn clock. */
class T1000SavedDataTest {
   private static CompoundTag world(UUID... ids) {
      CompoundTag tag = new CompoundTag();
      tag.putBoolean("Natural", false);
      tag.putLong("NextCheck", 1234L);
      tag.putLong("NextWake", 8000L);
      ListTag list = new ListTag();
      for (UUID id : ids) {
         T1000SavedData.Instance i = new T1000SavedData.Instance(id);
         i.x = 10.5;
         i.y = 64.0;
         i.z = -3.25;
         i.disguise = 777;
         i.integrity = 42.0F;
         i.core = 33.0F;
         i.natural = true;
         i.dormant = false;
         i.state = "STALK";
         list.add(i.save());
      }
      // the same id twice (a corrupted/duplicated list) must stay one instance
      if (ids.length > 0) {
         list.add(list.getCompound(0).copy());
      }
      tag.put("Instances", list);
      return tag;
   }

   @Test
   void restartMakesEveryInstanceDormantAndKeepsItsState() {
      UUID a = UUID.randomUUID();
      UUID b = UUID.randomUUID();
      T1000SavedData d = T1000SavedData.load(world(a, b));
      assertEquals(2, d.instances().size());
      assertEquals(0, d.activeCount());
      assertEquals(2, d.dormantCount());
      T1000SavedData.Instance i = d.instance(a);
      assertTrue(i.dormant);
      assertEquals(10.5, i.x);
      assertEquals(777, i.disguise);
      assertEquals(42.0F, i.integrity);
      assertEquals(33.0F, i.core);
      assertTrue(i.natural);
      assertFalse(d.naturalEnabled());
      assertEquals(1234L, d.nextCheck());
   }

   @Test
   void saveLoadRoundTrip() {
      UUID a = UUID.randomUUID();
      T1000SavedData d = T1000SavedData.load(world(a));
      CompoundTag again = d.save(new CompoundTag());
      T1000SavedData e = T1000SavedData.load(again);
      assertEquals(1, e.instances().size());
      assertEquals(d.instance(a).dormantSince, e.instance(a).dormantSince);
      assertEquals("STALK", e.instance(a).state);
   }

   @Test
   void destructionForgetsAndStartsTheRespawnClock() {
      UUID a = UUID.randomUUID();
      T1000SavedData d = T1000SavedData.load(world(a));
      assertEquals(Long.MIN_VALUE, d.lastDestroyed());
      d.destroyed(a, 5000L);
      assertNull(d.instance(a));
      assertEquals(5000L, d.lastDestroyed());
      assertEquals(1, d.destroyedTotal());
      // destroying an unknown one changes nothing
      d.destroyed(UUID.randomUUID(), 9000L);
      assertEquals(5000L, d.lastDestroyed());
      assertEquals(1, d.destroyedTotal());
      T1000SavedData f = T1000SavedData.load(d.save(new CompoundTag()));
      assertEquals(5000L, f.lastDestroyed());
      assertEquals(1, f.destroyedTotal());
   }

   @Test
   void operatorRemovalForgetsWithoutTheRespawnDelay() {
      UUID a = UUID.randomUUID();
      UUID b = UUID.randomUUID();
      T1000SavedData d = T1000SavedData.load(world(a, b));
      assertTrue(d.forget(a));
      assertFalse(d.forget(a));
      assertEquals(Long.MIN_VALUE, d.lastDestroyed());
      assertEquals(1, d.forgetAll());
      assertEquals(0, d.instances().size());
   }

   @Test
   void emptyWorldDefaults() {
      T1000SavedData d = T1000SavedData.load(new CompoundTag());
      assertTrue(d.naturalEnabled());
      assertEquals(Long.MIN_VALUE, d.lastNaturalSpawn());
      assertEquals(0, d.instances().size());
   }
}
