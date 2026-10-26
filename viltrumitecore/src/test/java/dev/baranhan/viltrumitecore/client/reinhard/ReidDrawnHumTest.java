package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.client.reinhard.reid.ReidDrawnHum;
import dev.baranhan.viltrumitecore.client.render.vfx.SoundDuck;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReidDrawnHumTest {
   @Test
   void fadesInOverASecondAndOutFaster() {
      int level = 1, ticks = 0;
      while (level < ReidDrawnHum.FADE_IN) {
         level = ReidDrawnHum.fade(level, true);
         ticks++;
      }
      assertEquals(ReidDrawnHum.FADE_IN - 1, ticks);
      assertEquals(ReidDrawnHum.FADE_IN, ReidDrawnHum.fade(level, true), "holds at full");
      int out = 0;
      while (level > 0) {
         level = ReidDrawnHum.fade(level, false);
         out++;
      }
      assertTrue(out < ticks / 2, "sheathing silences it quickly");
      assertEquals(0, ReidDrawnHum.fade(0, false));
   }

   @Test
   void quietAndBounded() {
      assertEquals(0.0F, ReidDrawnHum.volume(0));
      assertEquals(ReidDrawnHum.VOLUME, ReidDrawnHum.volume(ReidDrawnHum.FADE_IN), 1.0E-6F);
      assertEquals(ReidDrawnHum.VOLUME, ReidDrawnHum.volume(99), 1.0E-6F);
      assertTrue(ReidDrawnHum.VOLUME <= 0.3F, "an ambient bed under the strikes");
      float last = -1;
      for (int i = 0; i <= ReidDrawnHum.FADE_IN; i++) {
         assertTrue(ReidDrawnHum.volume(i) > last);
         last = ReidDrawnHum.volume(i);
      }
   }

   /** A fake sound engine: it ticks the loops it holds and can drop them without telling them, like a duck to 0. */
   static final class FakeEngine implements ReidDrawnHum.Engine<FakeEngine.Loop> {
      static final class Loop {
         final int player; int heartbeat; boolean stopped;
         Loop(int player, int now) { this.player = player; this.heartbeat = now; }
      }
      final List<Loop> started = new ArrayList<>();
      final Set<Loop> held = new HashSet<>();
      public Loop start(int player, int now) { Loop l = new Loop(player, now); started.add(l); held.add(l); return l; }
      public void stop(Loop loop) { loop.stopped = true; held.remove(loop); }
      public boolean stopped(Loop loop) { return loop.stopped; }
      public int heartbeat(Loop loop) { return loop.heartbeat; }
      /** One engine tick: every held loop ticks. */
      void tick(int now) { for (Loop l : held) l.heartbeat = now; }
      /** The engine drops every channel and forgets the instances (they are not stopped). */
      void dropAll() { held.clear(); }
      long audible(int player) { return held.stream().filter(l -> l.player == player && !l.stopped).count(); }
   }

   @Test
   void restoresTheHumAfterTheEngineDropsItAndNeverDuplicates() {
      ReidDrawnHum.Loops<FakeEngine.Loop> loops = new ReidDrawnHum.Loops<>();
      FakeEngine engine = new FakeEngine();
      List<Integer> players = List.of(7, 9);
      boolean[] drawn = {true};
      int now = 0;
      for (; now < 40; now++) {
         engine.tick(now);
         loops.tick(now, players, id -> id == 7 && drawn[0], engine);
         assertEquals(1, engine.audible(7), "one hum while drawn");
         assertEquals(0, engine.audible(9), "no hum for a sheathed player");
      }
      // World Erasure: the duck to silence stops the channel; the instance is not told.
      engine.dropAll();
      int restoredAt = -1;
      for (; now < 120; now++) {
         engine.tick(now);
         loops.tick(now, players, id -> id == 7 && drawn[0], engine);
         assertTrue(engine.audible(7) <= 1, "never two hums at once");
         assertTrue(loops.size() <= 1);
         if (restoredAt < 0 && engine.audible(7) == 1) restoredAt = now;
      }
      assertTrue(restoredAt > 40 && restoredAt <= 40 + ReidDrawnHum.STALE_TICKS + ReidDrawnHum.RETRY_TICKS + 2,
         "restored automatically while Reid stays drawn: " + restoredAt);
      assertEquals(2, engine.started.size(), "exactly one replacement");
      assertTrue(engine.started.get(0).stopped, "the dropped instance is stopped before its replacement");
      // Sheathing: our own fade-out stops the loop; drawing again starts one new loop at once.
      drawn[0] = false;
      engine.started.get(1).stopped = true;
      engine.held.remove(engine.started.get(1));
      loops.tick(now++, players, id -> id == 7 && drawn[0], engine);
      assertEquals(0, loops.size());
      drawn[0] = true;
      loops.tick(now++, players, id -> id == 7 && drawn[0], engine);
      assertEquals(1, engine.audible(7), "a new draw starts the hum without a retry delay");
      assertEquals(3, engine.started.size());
   }

   @Test
   void healthyLoopsAreNeverReplaced() {
      ReidDrawnHum.Loops<FakeEngine.Loop> loops = new ReidDrawnHum.Loops<>();
      FakeEngine engine = new FakeEngine();
      for (int now = 0; now < 2000; now++) {
         engine.tick(now);
         loops.tick(now, List.of(3), id -> true, engine);
      }
      assertEquals(1, engine.started.size(), "no churn: one loop for the whole session");
      assertFalse(ReidDrawnHum.lost(100, 100 - ReidDrawnHum.STALE_TICKS));
      assertTrue(ReidDrawnHum.lost(100, 100 - ReidDrawnHum.STALE_TICKS - 1));
   }

   @Test
   void aDuckNeverSilencesAChannelToZero() {
      assertEquals(0.6F, SoundDuck.duck(0.6F, 1.0F), 1.0E-6F);
      assertEquals(0.3F, SoundDuck.duck(0.6F, 0.5F), 1.0E-6F);
      float silenced = SoundDuck.duck(0.6F, 0.0F);
      assertTrue(silenced > 0.0F, "the engine would stop a channel at exactly 0");
      assertTrue(silenced < 1.0E-3F, "still inaudible (-80 dB)");
      assertEquals(0.0F, SoundDuck.duck(0.0F, 0.0F), "a silent sound stays silent");
      assertEquals(0.6F, SoundDuck.duck(0.6F, 2.0F), 1.0E-6F, "never louder");
   }
}
