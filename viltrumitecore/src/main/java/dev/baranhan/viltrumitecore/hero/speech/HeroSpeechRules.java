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
   /** An ability line is on screen at least this long before another ability line may replace it. */
   public static final int MIN_SHOWN_BEFORE_REPLACE = 20;
   /** Low-health remark threshold: the hit that takes the speaker to this share of max health or below. */
   public static final float LOW_HEALTH_FRACTION = 0.3F;
   /** A non-player victim is worth a kill line from this max health on (hostile mobs) ... */
   public static final float NOTABLE_HOSTILE_HEALTH = 20.0F;
   /** ... or from this max health on (any big creature). */
   public static final float NOTABLE_ANY_HEALTH = 40.0F;

   public enum Decision {
      PLAY,
      /** Stop the speaker's current line and start this one. */
      REPLACE,
      ALREADY_ACTIVE,
      COOLDOWN,
      OUTRANKED,
      /**
       * Another line is still being read and this one may not cut it: an ambient remark (kill,
       * low health) never interrupts, an ability line waits {@link #MIN_SHOWN_BEFORE_REPLACE} ticks.
       * Skipped, not queued (a late combat quip is worse than none).
       */
      BUSY;

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

   /**
    * {@link #decide(String, HeroLine.Priority, String, HeroLine.Priority, long, long, boolean)} with
    * the active line's age: a higher rank still interrupts at once and an ultimate may hand over to
    * the next ultimate phase, but an {@link HeroLine.Priority#AMBIENT} line never interrupts and an
    * ability line replaces another ability line only after {@link #MIN_SHOWN_BEFORE_REPLACE} ticks
    * (no flicker of two quips in one second). {@code activeAgeTicks} is ignored when nothing plays.
    */
   public static Decision decide(@Nullable String activeLineId, @Nullable HeroLine.Priority activePriority, long activeAgeTicks, String lineId,
      HeroLine.Priority priority, long now, long cooldownUntil, boolean force) {
      Decision legacy = decide(activeLineId, activePriority, lineId, priority, now, cooldownUntil, force);
      if (force || activeLineId == null || legacy != Decision.REPLACE) {
         return legacy;
      }

      if (priority == HeroLine.Priority.AMBIENT) {
         return Decision.BUSY;
      }

      if (priority == HeroLine.Priority.ABILITY && activePriority == HeroLine.Priority.ABILITY && activeAgeTicks < MIN_SHOWN_BEFORE_REPLACE) {
         return Decision.BUSY;
      }

      return Decision.REPLACE;
   }

   /** Whether a kill is worth a remark: another player, a real hostile, or any big creature (never chickens or villagers). */
   public static boolean notableKill(boolean victimIsPlayer, boolean victimHostile, float victimMaxHealth) {
      return victimIsPlayer || victimHostile && victimMaxHealth >= NOTABLE_HOSTILE_HEALTH || victimMaxHealth >= NOTABLE_ANY_HEALTH;
   }

   /** Whether a survived hit took the speaker from above the low-health threshold to at or below it. */
   public static boolean crossedLowHealth(float healthBefore, float damage, float maxHealth) {
      if (!(maxHealth > 0.0F) || !(damage > 0.0F)) {
         return false;
      }

      float after = healthBefore - damage;
      float threshold = maxHealth * LOW_HEALTH_FRACTION;
      return healthBefore > threshold && after > 0.0F && after <= threshold;
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
