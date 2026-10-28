package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.ThorCue;
import dev.baranhan.viltrumitecore.hero.thor.ThorMode;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumiteflight.util.FlightState;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.LongPredicate;
import javax.annotation.Nullable;

/**
 * R5 (spec §8), pure: when each Thor loop plays, how loud, and the lifecycle of the per-player instances
 * ({@link ThorSoundLoops} is the client adapter). A loop starts when its condition holds and fades out when it
 * stops holding, the player is removed or out of range, changes hero or the level changes; a loop the sound
 * engine dropped (no heartbeat) is replaced after {@link #RETRY_TICKS} while still wanted.
 */
public final class ThorLoopRules {
   public static final int FADE_IN = 10;
   public static final int FADE_OUT_STEP = 2;
   /** No instance starts beyond this distance from the listener. */
   public static final double START_RANGE = 40.0;
   public static final int STALE_TICKS = 10;
   public static final int RETRY_TICKS = 20;

   /** The three player loops; the flying world hammer has its own instance (ThorSoundLoops.hammerFlight). */
   public enum Kind {
      /** Electric mode is on. */
      ELECTRIC(ThorCue.LOOP_ELECTRIC),
      /** Mjölnir spun in the hand: in flight, or charging a throw. */
      SPIN(ThorCue.LOOP_HAMMER_SPIN),
      /** Wind of fast flight (CRUISE / SONIC). */
      FLIGHT(ThorCue.LOOP_FLIGHT);

      private final ThorCue cue;

      Kind(ThorCue cue) {
         this.cue = cue;
      }

      public ThorCue cue() {
         return this.cue;
      }
   }

   /** What the rules read from one player this tick ({@code thor} false: not Thor, or no snapshot). */
   public record View(boolean thor, boolean removed, ThorMode mode, boolean hammerHeld, @Nullable FlightState flight,
                      @Nullable ThorMove move, @Nullable MovePhase phase, double speed) {
   }

   private ThorLoopRules() {
   }

   static boolean flying(@Nullable FlightState flight) {
      return flight != null && flight != FlightState.NONE;
   }

   /** Does this loop play for this player now? */
   public static boolean wanted(Kind kind, View view) {
      if (!view.thor() || view.removed()) {
         return false;
      }

      return switch (kind) {
         case ELECTRIC -> view.mode() == ThorMode.ELECTRIC;
         case SPIN -> view.hammerHeld() && (flying(view.flight())
            || view.move() == ThorMove.HAMMER_THROW && (view.phase() == MovePhase.INTRO || view.phase() == MovePhase.MAIN));
         case FLIGHT -> view.flight() == FlightState.CRUISE || view.flight() == FlightState.SONIC;
      };
   }

   /** Target volume of a playing loop (before the fade). */
   public static float volume(Kind kind, View view) {
      float base = kind.cue().layers().get(0).volume();
      if (kind == Kind.FLIGHT) {
         return base * (float)clamp(view.speed() / 2.0, 0.3, 1.0);
      } else if (kind == Kind.SPIN && !flying(view.flight())) {
         // A throw charge: the spin is heard closer, softer.
         return base * 0.7F;
      }

      return base;
   }

   /** Target pitch: the wind rises with speed, a sonic flight is the highest. */
   public static float pitch(Kind kind, View view) {
      float base = kind.cue().layers().get(0).pitch();
      if (kind == Kind.FLIGHT) {
         return base * (float)clamp(0.8 + view.speed() * 0.15, 0.8, view.flight() == FlightState.SONIC ? 1.5 : 1.25);
      } else if (kind == Kind.SPIN) {
         return base * (flying(view.flight()) ? 1.0F : 0.9F);
      }

      return base;
   }

   /** Next fade level 0..{@link #FADE_IN}: up one per tick while wanted, faster down otherwise. */
   public static int fade(int level, boolean wanted) {
      return Math.max(0, Math.min(FADE_IN, wanted ? level + 1 : level - FADE_OUT_STEP));
   }

   public static float faded(float volume, int level) {
      return volume * Math.max(0, Math.min(FADE_IN, level)) / FADE_IN;
   }

   public static boolean lost(int now, int heartbeat) {
      return now - heartbeat > STALE_TICKS;
   }

   /** One key per player and loop. */
   public static long key(int player, Kind kind) {
      return (long)player << 4 | kind.ordinal();
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   /** What {@link Loops} needs from a sound engine; the client adapter lives in ThorSoundLoops, tests use a fake. */
   public interface Engine<L> {
      L start(long key, int now);

      void stop(L loop);

      boolean stopped(L loop);

      int heartbeat(L loop);
   }

   /** At most one loop per key; a faded-out loop is forgotten, a dropped one stopped and retried later. */
   public static final class Loops<L> {
      private final Map<Long, L> playing = new HashMap<>();
      private final Map<Long, Integer> retryAt = new HashMap<>();

      public void tick(int now, Iterable<Long> keys, LongPredicate wanted, Engine<L> engine) {
         Iterator<Map.Entry<Long, L>> it = this.playing.entrySet().iterator();
         while (it.hasNext()) {
            Map.Entry<Long, L> entry = it.next();
            L loop = entry.getValue();
            if (engine.stopped(loop)) {
               it.remove();
            } else if (lost(now, engine.heartbeat(loop))) {
               engine.stop(loop);
               it.remove();
               this.retryAt.put(entry.getKey(), now + RETRY_TICKS);
            }
         }

         this.retryAt.values().removeIf(at -> at <= now);
         for (long key : keys) {
            if (!this.playing.containsKey(key) && !this.retryAt.containsKey(key) && wanted.test(key)) {
               this.playing.put(key, engine.start(key, now));
            }
         }
      }

      public void clear(Engine<L> engine) {
         this.playing.values().forEach(engine::stop);
         this.playing.clear();
         this.retryAt.clear();
      }

      public int size() {
         return this.playing.size();
      }

      public boolean playing(long key) {
         return this.playing.containsKey(key);
      }
   }
}
