package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.ThorMode;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumiteflight.util.FlightState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** R5: when Thor's loops play and that every one of them stops. */
class ThorLoopRulesTest {
   private static ThorLoopRules.View view(ThorMode mode, boolean held, FlightState flight, ThorMove move, MovePhase phase) {
      return new ThorLoopRules.View(true, false, mode, held, flight, move, phase, 1.0);
   }

   @Test
   void electricHumFollowsTheMode() {
      assertTrue(ThorLoopRules.wanted(ThorLoopRules.Kind.ELECTRIC, view(ThorMode.ELECTRIC, false, FlightState.NONE, null, null)));
      assertFalse(ThorLoopRules.wanted(ThorLoopRules.Kind.ELECTRIC, view(ThorMode.NORMAL, false, FlightState.NONE, null, null)));
   }

   @Test
   void spinNeedsTheHammerInTheHand() {
      assertTrue(ThorLoopRules.wanted(ThorLoopRules.Kind.SPIN, view(ThorMode.NORMAL, true, FlightState.HOVER, null, null)));
      assertFalse(ThorLoopRules.wanted(ThorLoopRules.Kind.SPIN, view(ThorMode.NORMAL, false, FlightState.CRUISE, null, null)));
      assertFalse(ThorLoopRules.wanted(ThorLoopRules.Kind.SPIN, view(ThorMode.NORMAL, true, FlightState.NONE, null, null)));
      assertTrue(ThorLoopRules.wanted(ThorLoopRules.Kind.SPIN, view(ThorMode.NORMAL, true, FlightState.NONE, ThorMove.HAMMER_THROW, MovePhase.MAIN)));
      assertFalse(ThorLoopRules.wanted(ThorLoopRules.Kind.SPIN, view(ThorMode.NORMAL, true, FlightState.NONE, ThorMove.HAMMER_THROW, MovePhase.FINISH)),
         "the throw has left: no spin in the follow-through");
   }

   @Test
   void flightWindOnlyInFastFlight() {
      assertTrue(ThorLoopRules.wanted(ThorLoopRules.Kind.FLIGHT, view(ThorMode.NORMAL, true, FlightState.CRUISE, null, null)));
      assertTrue(ThorLoopRules.wanted(ThorLoopRules.Kind.FLIGHT, view(ThorMode.NORMAL, true, FlightState.SONIC, null, null)));
      assertFalse(ThorLoopRules.wanted(ThorLoopRules.Kind.FLIGHT, view(ThorMode.NORMAL, true, FlightState.HOVER, null, null)));
      ThorLoopRules.View slow = new ThorLoopRules.View(true, false, ThorMode.NORMAL, true, FlightState.CRUISE, null, null, 0.2);
      ThorLoopRules.View fast = new ThorLoopRules.View(true, false, ThorMode.NORMAL, true, FlightState.SONIC, null, null, 4.0);
      assertTrue(ThorLoopRules.volume(ThorLoopRules.Kind.FLIGHT, fast) > ThorLoopRules.volume(ThorLoopRules.Kind.FLIGHT, slow));
      assertTrue(ThorLoopRules.pitch(ThorLoopRules.Kind.FLIGHT, fast) > ThorLoopRules.pitch(ThorLoopRules.Kind.FLIGHT, slow));
   }

   @Test
   void nothingPlaysForOthersOrRemovedPlayers() {
      ThorLoopRules.View notThor = new ThorLoopRules.View(false, false, null, false, FlightState.SONIC, null, null, 3.0);
      ThorLoopRules.View removed = new ThorLoopRules.View(true, true, ThorMode.ELECTRIC, true, FlightState.SONIC, null, null, 3.0);
      for (ThorLoopRules.Kind kind : ThorLoopRules.Kind.values()) {
         assertFalse(ThorLoopRules.wanted(kind, notThor), kind.name());
         assertFalse(ThorLoopRules.wanted(kind, removed), kind.name());
         assertTrue(kind.cue().loop(), kind.name());
      }
   }

   @Test
   void fadeReachesSilenceAndStops() {
      int level = 0;
      for (int i = 0; i < ThorLoopRules.FADE_IN + 5; i++) {
         level = ThorLoopRules.fade(level, true);
      }

      assertEquals(ThorLoopRules.FADE_IN, level);
      int ticks = 0;
      while (level > 0) {
         level = ThorLoopRules.fade(level, false);
         ticks++;
      }

      assertTrue(ticks <= ThorLoopRules.FADE_IN / ThorLoopRules.FADE_OUT_STEP + 1);
      assertEquals(0.0F, ThorLoopRules.faded(1.0F, 0));
   }

   @Test
   void keysAreDistinctPerPlayerAndKind() {
      assertNotEquals(ThorLoopRules.key(1, ThorLoopRules.Kind.ELECTRIC), ThorLoopRules.key(1, ThorLoopRules.Kind.SPIN));
      assertNotEquals(ThorLoopRules.key(1, ThorLoopRules.Kind.FLIGHT), ThorLoopRules.key(2, ThorLoopRules.Kind.ELECTRIC));
      long key = ThorLoopRules.key(12345, ThorLoopRules.Kind.FLIGHT);
      assertEquals(12345, (int)(key >> 4));
      assertEquals(ThorLoopRules.Kind.FLIGHT.ordinal(), (int)(key & 15));
   }

   /** One loop per key, dropped loops replaced after the retry delay, clear stops all. */
   @Test
   void lifecycle() {
      class Fake {
         boolean stopped;
         int heartbeat;
      }
      List<Fake> started = new ArrayList<>();
      Map<Long, Boolean> wanted = new HashMap<>();
      ThorLoopRules.Engine<Fake> engine = new ThorLoopRules.Engine<>() {
         public Fake start(long key, int now) {
            Fake f = new Fake();
            f.heartbeat = now;
            started.add(f);
            return f;
         }
         public void stop(Fake loop) { loop.stopped = true; }
         public boolean stopped(Fake loop) { return loop.stopped; }
         public int heartbeat(Fake loop) { return loop.heartbeat; }
      };
      ThorLoopRules.Loops<Fake> loops = new ThorLoopRules.Loops<>();
      long key = ThorLoopRules.key(7, ThorLoopRules.Kind.ELECTRIC);
      wanted.put(key, true);
      loops.tick(1, List.of(key), wanted::get, engine);
      loops.tick(2, List.of(key), wanted::get, engine);
      assertEquals(1, started.size());
      // The loop faded out by itself (condition ended): forgotten, and started again when wanted.
      started.get(0).stopped = true;
      wanted.put(key, false);
      loops.tick(3, List.of(key), wanted::get, engine);
      assertFalse(loops.playing(key));
      wanted.put(key, true);
      loops.tick(4, List.of(key), wanted::get, engine);
      assertEquals(2, started.size());
      // The engine dropped it silently: no heartbeat -> stopped, retried after RETRY_TICKS.
      int now = 4 + ThorLoopRules.STALE_TICKS + 1;
      loops.tick(now, List.of(key), wanted::get, engine);
      assertTrue(started.get(1).stopped);
      assertFalse(loops.playing(key));
      loops.tick(now + ThorLoopRules.RETRY_TICKS, List.of(key), wanted::get, engine);
      assertTrue(loops.playing(key));
      loops.clear(engine);
      assertEquals(0, loops.size());
      assertTrue(started.stream().allMatch(f -> f.stopped));
   }
}
