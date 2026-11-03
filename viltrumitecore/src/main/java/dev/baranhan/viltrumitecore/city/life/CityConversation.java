package dev.baranhan.viltrumitecore.city.life;

import java.util.function.IntPredicate;

/**
 * One voiced chat between two citizens (issue #136): which dialogue of {@link CityLifeCatalog}, who speaks which
 * turn with which voice, and when the next line starts (the length of the take plus a short, human pause). The
 * citizen goal ({@code CitizenGoals.Talk}) ticks it; {@link CityLife} plays the lines at the speaker. The choice of
 * the dialogue and the timing are pure and unit tested.
 */
public final class CityConversation {
   /** Ticks of silence between two lines (min, extra random). */
   public static final int GAP_MIN = 5;
   public static final int GAP_RANDOM = 8;
   /** Ticks the two keep standing together after the last line. */
   public static final int AFTER_LAST = 30;

   public final int dialogue;
   /** Voice index (CityLifeCatalog.VOICES) of the one who starts (role 0) and of the one who answers (role 1). */
   public final int[] voices;
   public final float[] pitches;
   private final int[] gaps;
   private int turn;
   private long nextAt;
   private final long endsAt;

   CityConversation(int dialogue, int voiceA, int voiceB, float pitchA, float pitchB, long now, int[] gaps) {
      this.dialogue = dialogue;
      this.voices = new int[]{voiceA, voiceB};
      this.pitches = new float[]{pitchA, pitchB};
      this.gaps = gaps;
      this.nextAt = now + 10;
      this.endsAt = now + 10 + length(dialogue, this.voices, this.pitches, gaps) + AFTER_LAST;
   }

   /** Pure: ticks from the first line to the end of the last one (gaps[i] after turn i). */
   static int length(int dialogue, int[] voices, float[] pitches, int[] gaps) {
      int[] turns = CityLifeCatalog.TURNS[dialogue];
      int t = 0;
      for (int i = 0; i < turns.length; i++) {
         int role = turns[i] >>> 16;
         t += take(voices[role], turns[i] & 0xFFFF, pitches[role]);
         if (i < turns.length - 1) {
            t += gaps[i % gaps.length];
         }
      }

      return t;
   }

   /** Pure: ticks a take lasts at a playback pitch (the engine speeds the file up with the pitch). */
   static int take(int voice, int line, float pitch) {
      return (int)Math.ceil(CityLifeCatalog.ticks(voice, line) / Math.max(0.5F, pitch));
   }

   public long endsAt() {
      return this.endsAt;
   }

   public boolean done() {
      return this.turn >= CityLifeCatalog.TURNS[this.dialogue].length;
   }

   /**
    * The line due at {@code now}: role << 16 | line, or -1 when nothing starts this tick. Advances to the next
    * turn (the pause after a line is its entry of the gaps given at the start).
    */
   int next(long now) {
      int[] turns = CityLifeCatalog.TURNS[this.dialogue];
      if (this.turn >= turns.length || now < this.nextAt) {
         return -1;
      }

      int t = turns[this.turn++];
      int role = t >>> 16;
      this.nextAt = now + take(this.voices[role], t & 0xFFFF, this.pitches[role]) + this.gaps[(this.turn - 1) % this.gaps.length];
      return t;
   }

   /** Pure: the time-of-day class of a day time (0 = 6:00): morning 5-9, day 9-17, evening 17-20, night. */
   public static int timeClass(long dayTime) {
      int t = (int)Math.floorMod(dayTime, 24000L);
      if (t >= 23000 || t < 3000) {
         return CityLifeCatalog.MORNING;
      }

      if (t < 11000) {
         return CityLifeCatalog.DAY;
      }

      return t < 14000 ? CityLifeCatalog.EVENING : -1;
   }

   /** Pure: whether a dialogue fits a mood, a time class (-1 night) and the weather. */
   static boolean fits(int dialogue, int mood, int time, boolean rain) {
      if (CityLifeCatalog.MOOD[dialogue] != mood) {
         return false;
      }

      return switch (CityLifeCatalog.WHEN[dialogue]) {
         case CityLifeCatalog.ANY -> true;
         case CityLifeCatalog.RAIN -> rain;
         case CityLifeCatalog.CLEAR -> !rain && time == CityLifeCatalog.DAY;
         // at night people still say good night
         case CityLifeCatalog.EVENING -> time == CityLifeCatalog.EVENING || time == -1;
         default -> CityLifeCatalog.WHEN[dialogue] == time;
      };
   }

   /**
    * Pure: a dialogue for this mood, time and weather, weighted (rain talk weighs more while it rains), never one
    * of the {@code recent} ones; -1 when none is left. {@code roll} is uniform 0..1.
    */
   public static int pick(int mood, int time, boolean rain, IntPredicate recent, float roll) {
      int total = 0;
      for (int d = 0; d < CityLifeCatalog.DIALOGUES.length; d++) {
         if (fits(d, mood, time, rain) && !recent.test(d)) {
            total += CityLifeCatalog.WEIGHT[d];
         }
      }

      if (total == 0) {
         return -1;
      }

      int at = Math.min(total - 1, (int)(roll * total));
      for (int d = 0; d < CityLifeCatalog.DIALOGUES.length; d++) {
         if (fits(d, mood, time, rain) && !recent.test(d)) {
            at -= CityLifeCatalog.WEIGHT[d];
            if (at < 0) {
               return d;
            }
         }
      }

      return -1;
   }
}
