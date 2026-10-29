package dev.baranhan.viltrumitecore.hero.speech;

import java.util.function.IntPredicate;
import javax.annotation.Nullable;

/** Pure server rules of {@link HeroSpeech}: start/skip decisions, variant rotation and line lifetime. */
public final class HeroSpeechRules {
   /** Server-side lifetime estimate of one fragment with an automatic duration (the client times the real pages). */
   public static final int ESTIMATE_TICKS_PER_FRAGMENT = 160;
   /** Enter/exit fades on top of the spoken time. */
   public static final int FADE_GRACE_TICKS = 20;
   public static final String SPEAKER_KEY_PREFIX = "subtitle.viltrumitecore.speaker.";

   public enum Decision {
      PLAY,
      /** Stop the speaker's current line and start this one. */
      REPLACE,
      ALREADY_ACTIVE,
      COOLDOWN,
      OUTRANKED;

      public boolean starts() {
         return this == PLAY || this == REPLACE;
      }
   }

   private HeroSpeechRules() {
   }

   /**
    * One speaker never shows two panels: the same line is never restarted while
    * it plays (no per-tick repeats), a different line replaces it only with an
    * equal or higher priority, and a finished line waits out its cooldown.
    * {@code force} (operator preview) skips the cooldown and restarts.
    */
   public static Decision decide(@Nullable String activeLineId, @Nullable HeroLine.Priority activePriority, String lineId, HeroLine.Priority priority,
      long now, long cooldownUntil, boolean force) {
      if (activeLineId != null) {
         if (activeLineId.equals(lineId)) {
            return force ? Decision.REPLACE : Decision.ALREADY_ACTIVE;
         }

         if (!force && now < cooldownUntil) {
            return Decision.COOLDOWN;
         }

         return force || activePriority == null || priority.compareTo(activePriority) >= 0 ? Decision.REPLACE : Decision.OUTRANKED;
      }

      return !force && now < cooldownUntil ? Decision.COOLDOWN : Decision.PLAY;
   }

   /** Ticks the server treats the line as speaking: the explicit duration, else a per-fragment estimate, plus the fades. */
   public static int lifetimeTicks(int durationTicks, int fragments) {
      int spoken = durationTicks > 0 ? durationTicks : Math.max(1, fragments) * ESTIMATE_TICKS_PER_FRAGMENT;
      return spoken + FADE_GRACE_TICKS;
   }

   /** Next variant after {@code last} (-1 = none yet) that is ready, round robin; -1 when none is. */
   public static int nextVariant(int last, int size, IntPredicate ready) {
      for (int step = 1; step <= size; step++) {
         int index = Math.floorMod(last + step, size);
         if (ready.test(index)) {
            return index;
         }
      }

      return -1;
   }

   public static String defaultSpeakerKey(String heroKey) {
      return SPEAKER_KEY_PREFIX + heroKey;
   }
}
