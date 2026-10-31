package dev.baranhan.viltrumitecore.client.thanos;

import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSceneTimeline;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStone;
import java.util.ArrayList;
import java.util.List;

/**
 * Sound cues of the snap and universe scene (pure, unit tested): each cue fires exactly once when
 * the scene clock passes its tick, whatever the clock's step (a late join or a resync skips the
 * cues already behind it instead of replaying them).
 */
public final class ThanosSceneCues {
   public enum Kind {
      WORLD_STOP,
      GAUNTLET_RAISE,
      STONE_LIGHT,
      FINGERS,
      SNAP,
      DISSOLVE,
      VOID_START,
      OD_LAGNA_ARRIVE,
      LINE,
      RESTORE,
      VOID_END
   }

   /** One cue: its kind, its tick and an index (the stone or the line), else -1. */
   public record Cue(Kind kind, int tick, int index) {
   }

   public static final int GAUNTLET_RAISE = 6;
   public static final int FINGERS = ThanosSceneTimeline.FACE_END + 2;
   public static final int VOID_START = ThanosSceneTimeline.DISSOLVE_END - 30;
   public static final int VOID_END = ThanosSceneTimeline.RESTORE_END - 30;
   public static final List<Cue> ALL = build();

   private ThanosSceneCues() {
   }

   private static List<Cue> build() {
      List<Cue> out = new ArrayList<>();
      out.add(new Cue(Kind.WORLD_STOP, 0, -1));
      out.add(new Cue(Kind.GAUNTLET_RAISE, GAUNTLET_RAISE, -1));
      for (int i = 0; i < ThanosStone.values().length; i++) {
         out.add(new Cue(Kind.STONE_LIGHT, ThanosSceneTimeline.STONES_START + i * ThanosSceneTimeline.STONE_STEP, i));
      }

      out.add(new Cue(Kind.FINGERS, FINGERS, -1));
      out.add(new Cue(Kind.SNAP, ThanosSceneTimeline.SNAP, -1));
      out.add(new Cue(Kind.DISSOLVE, ThanosSceneTimeline.DISSOLVE_START, -1));
      out.add(new Cue(Kind.VOID_START, VOID_START, -1));
      out.add(new Cue(Kind.OD_LAGNA_ARRIVE, ThanosSceneTimeline.VOID_END, -1));
      for (int i = 0; i < ThanosSceneTimeline.LINES; i++) {
         out.add(new Cue(Kind.LINE, ThanosSceneTimeline.SPEECH_START + i * ThanosSceneTimeline.LINE_TICKS, i));
      }

      out.add(new Cue(Kind.RESTORE, ThanosSceneTimeline.THANOS_GONE, -1));
      out.add(new Cue(Kind.VOID_END, VOID_END, -1));
      out.sort((a, b) -> Integer.compare(a.tick, b.tick));
      return List.copyOf(out);
   }

   /**
    * Cues in (from, to]; the scene start (from below 0) includes tick 0. A clock that jumped by
    * more than {@code skipAfter} ticks only plays the cues within the last {@code skipAfter}.
    */
   public static List<Cue> between(float from, float to, float skipAfter) {
      if (!(to > from)) {
         return List.of();
      }

      float start = to - from > skipAfter ? to - skipAfter : from;
      List<Cue> out = new ArrayList<>(2);
      for (Cue c : ALL) {
         if (c.tick > start && c.tick <= to) {
            out.add(c);
         }
      }

      return out;
   }

   /** Is the void loop wanted at scene time t (it fades over its last 30 ticks)? */
   public static boolean voidWanted(float t) {
      return t >= VOID_START && t < VOID_END;
   }
}
