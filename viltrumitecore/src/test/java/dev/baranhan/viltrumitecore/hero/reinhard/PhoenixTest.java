package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.PersistentCooldowns;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.Phoenix;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Divine Protection of the Phoenix (spec §4.2). */
class PhoenixTest {
   @Test
   void revivesWhenReady() {
      assertTrue(Phoenix.saves(true, 5.0F, 100.0F, 0));
      assertTrue(Phoenix.saves(true, 5.0F, 5.0F, 0), "exactly lethal");
      assertFalse(Phoenix.saves(true, 5.0F, 4.0F, 0), "not lethal: the hit just lands");
      assertFalse(Phoenix.saves(false, 5.0F, 100.0F, 0), "ordinary damage is floored, never revived");
   }

   @Test
   void secondDeathInsideCooldownKills() {
      CompoundTag store = new CompoundTag();
      assertTrue(Phoenix.saves(true, 3.0F, 50.0F, PersistentCooldowns.get(store, ReinhardRules.PHOENIX_KEY)));
      PersistentCooldowns.set(store, ReinhardRules.PHOENIX_KEY, ReinhardRules.PHOENIX_COOLDOWN);
      for (int i = 0; i < 6000; i++) {
         PersistentCooldowns.tickTag(store);
      }
      assertFalse(Phoenix.saves(true, 20.0F, 50.0F, PersistentCooldowns.get(store, ReinhardRules.PHOENIX_KEY)), "5 min later: real death");
      for (int i = 0; i < 6000; i++) {
         PersistentCooldowns.tickTag(store);
      }
      assertTrue(Phoenix.saves(true, 20.0F, 50.0F, PersistentCooldowns.get(store, ReinhardRules.PHOENIX_KEY)), "10 min online: ready again");
   }

   @Test
   void cooldownIs12000() {
      assertEquals(12000, ReinhardRules.PHOENIX_COOLDOWN);
      assertEquals("reinhard:phoenix", ReinhardRules.PHOENIX_KEY);
   }

   @Test
   void offlineDoesNotTick() {
      CompoundTag store = new CompoundTag();
      PersistentCooldowns.set(store, ReinhardRules.PHOENIX_KEY, ReinhardRules.PHOENIX_COOLDOWN);
      // Offline = no tick calls; a save/load of the player data is a plain copy.
      CompoundTag reloaded = store.copy();
      assertEquals(ReinhardRules.PHOENIX_COOLDOWN, PersistentCooldowns.get(reloaded, ReinhardRules.PHOENIX_KEY));
   }

   @Test
   void survivesHeroChange() {
      CompoundTag store = new CompoundTag();
      PersistentCooldowns.set(store, ReinhardRules.PHOENIX_KEY, 500);
      ReinhardState fresh = new ReinhardState();
      CompoundTag heroNbt = new CompoundTag();
      fresh.save(heroNbt);
      assertFalse(heroNbt.toString().contains("phoenix"), "the timer is not hero state");
      assertEquals(500, PersistentCooldowns.get(store, ReinhardRules.PHOENIX_KEY));
   }

   @Test
   void pickPointAwayFromAttacker() {
      // Attacker on +x: the point lies on the -x side, 6-10 blocks out.
      int[] point = Phoenix.pickPoint(0, 0, new double[]{-1.0, 0.0}, (x, z) -> true);
      double dist = Math.sqrt(point[0] * point[0] + point[1] * point[1]);
      assertTrue(point[0] < 0, "x=" + point[0]);
      assertTrue(Math.abs(point[1]) <= 1, "z=" + point[1]);
      assertTrue(dist >= ReinhardRules.PHOENIX_MIN_DIST - 0.5 && dist <= ReinhardRules.PHOENIX_MAX_DIST + 0.5, "dist " + dist);
   }

   @Test
   void pickPointSkipsBlockedSpots() {
      int[] point = Phoenix.pickPoint(10, 10, new double[]{-1.0, 0.0}, (x, z) -> x > 10);
      assertTrue(point[0] > 10, "only the +x half fits");
   }

   @Test
   void pickPointFallsBackToOrigin() {
      assertArrayEquals(new int[]{3, -4}, Phoenix.pickPoint(3, -4, null, (x, z) -> false));
   }
}
