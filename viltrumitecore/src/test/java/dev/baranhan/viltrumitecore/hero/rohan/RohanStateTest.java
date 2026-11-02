package dev.baranhan.viltrumitecore.hero.rohan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Rohan's state lifecycle: what persists, what is clamped, what a death keeps, what timers clean up. */
class RohanStateTest {
   @Test
   void saveLoadRoundTrip() {
      RohanState s = new RohanState();
      s.ink = 42.5F;
      s.inspiration = 30.0F;
      s.cooldowns[0] = 17;
      s.standOut = true;
      s.read.add("minecraft:zombie");
      s.read.add("hero:dio");
      s.start(RohanMove.WRITE);
      CompoundTag nbt = new CompoundTag();
      s.save(nbt);
      RohanState back = new RohanState();
      back.load(nbt);
      assertEquals(42.5F, back.ink);
      assertEquals(30.0F, back.inspiration);
      assertArrayEquals(s.cooldowns, back.cooldowns);
      assertTrue(back.standOut);
      assertEquals(s.read, back.read);
      assertNull(back.move, "moves never persist");
   }

   @Test
   void loadClampsBadValues() {
      CompoundTag nbt = new CompoundTag();
      nbt.putFloat("RohanInk", 1.0E9F);
      nbt.putFloat("RohanInspiration", Float.NaN);
      int[] cds = new int[RohanRules.COOLDOWN_COUNT + 2];
      cds[0] = -40;
      cds[1] = Integer.MAX_VALUE;
      nbt.putIntArray("RohanCooldowns", cds);
      RohanState s = new RohanState();
      s.load(nbt);
      assertEquals(RohanRules.INK_MAX, s.ink);
      assertEquals(0.0F, s.inspiration);
      assertEquals(0, s.cooldowns[0]);
      assertEquals(RohanRules.COOLDOWN_TICKS[1], s.cooldowns[1]);
   }

   @Test
   void respawnKeepsProgressAndDropsTheMoment() {
      RohanState s = new RohanState();
      s.ink = 3.0F;
      s.inspiration = 55.0F;
      s.cooldowns[2] = 99;
      s.read.add("x");
      s.standOut = true;
      s.targetId = 12;
      s.marks.put(5, 1000);
      s.start(RohanMove.DOOR);
      RohanState r = s.respawnCopy();
      assertEquals(RohanRules.INK_MAX, r.ink, "ink refills");
      assertEquals(55.0F, r.inspiration);
      assertEquals(99, r.cooldowns[2]);
      assertTrue(r.read.contains("x"));
      assertFalse(r.standOut);
      assertEquals(-1, r.targetId);
      assertTrue(r.marks.isEmpty());
      assertNull(r.move);
   }

   @Test
   void timersEndMovesMarksAndCooldowns() {
      RohanState s = new RohanState();
      s.cooldowns[0] = 2;
      s.start(RohanMove.NIBS);
      s.tickCount = 10;
      s.marks.put(7, 12);
      assertTrue(s.marked(7));
      for (int i = 0; i < RohanMove.NIBS.length; i++) {
         s.tickTimers();
      }
      assertNull(s.move);
      assertEquals(0, s.cooldowns[0]);
      s.tickCount = 12;
      s.tickTimers();
      assertFalse(s.marked(7));
      assertTrue(s.marks.isEmpty(), "expired marks are removed, not kept forever");
      float before = s.ink = 10.0F;
      s.regen();
      assertTrue(s.ink > before);
      s.ink = RohanRules.INK_MAX;
      s.regen();
      assertEquals(RohanRules.INK_MAX, s.ink);
   }
}
