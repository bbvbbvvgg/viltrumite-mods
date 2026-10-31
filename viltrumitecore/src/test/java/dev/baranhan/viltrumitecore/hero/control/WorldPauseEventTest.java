package dev.baranhan.viltrumitecore.hero.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The event-owned pause (Thanos snap): nobody is exempt and no owner can end it by leaving. */
class WorldPauseEventTest {
   final UUID a = UUID.randomUUID(), b = UUID.randomUUID();
   final WorldPause.Target first = new WorldPause.Target(a, "minecraft:overworld"), second = new WorldPause.Target(b, "minecraft:the_nether");

   @Test
   void everyPlayerIsHeldWhenNoPlayerOwnsThePause() {
      WorldPauseTest.Fake env = new WorldPauseTest.Fake();
      env.players.addAll(List.of(first, second));
      WorldPause.Session s = new WorldPause.Session(UUID.randomUUID(), UUID.randomUUID(), 150, true, 32);
      s.event = true;
      s.sync(env);
      assertTrue(s.isPaused(a));
      assertTrue(s.isPaused(b));
      assertTrue(s.stopsEntity(a, false));
      assertEquals(s.id, env.records.get(first));
      assertEquals(s.id, env.records.get(second));
   }

   @Test
   void anEventPauseHasNoOwnerToLose() {
      assertFalse(WorldPause.ownerGone(true, false, false, false));
      assertTrue(WorldPause.ownerGone(false, false, false, false));
      assertTrue(WorldPause.ownerGone(false, true, false, true));
      assertTrue(WorldPause.ownerGone(false, true, true, false));
      assertFalse(WorldPause.ownerGone(false, true, true, true));
   }

   @Test
   void anEventPauseStillEndsAtItsWatchdog() {
      WorldPauseTest.Fake env = new WorldPauseTest.Fake();
      env.players.add(first);
      WorldPause.Session s = new WorldPause.Session(UUID.randomUUID(), UUID.randomUUID(), 10, true, 32);
      s.event = true;
      for (int i = 0; i < 49; i++) {
         assertFalse(s.tick(env));
      }

      assertTrue(s.tick(env));
      assertTrue(s.ended);
      assertTrue(env.records.isEmpty());
   }
}
