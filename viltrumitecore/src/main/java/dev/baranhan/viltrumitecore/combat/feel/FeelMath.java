package dev.baranhan.viltrumitecore.combat.feel;

/** Pure timing and falloff math of the cinematic combat feel (wave1-22); unit-tested. */
public final class FeelMath {
   /** The inverted frame: about 3 frames at 60 FPS, under 2 frames at 30 FPS. */
   public static final long IMPACT_INVERT_MS = 50L;
   public static final long IMPACT_LINES_MS = 115L;
   public static final long IMPACT_TOTAL_MS = 175L;
   /** Photosensitivity guard: at most 2.5 impact frames per second. */
   public static final long IMPACT_COOLDOWN_MS = 400L;
   /** Camera motion multiplier with reduced camera motion. */
   public static final float REDUCED_MOTION = 0.35F;

   public static final int PHASE_NONE = 0;
   public static final int PHASE_INVERT = 1;
   public static final int PHASE_LINES = 2;
   public static final int PHASE_FADE = 3;

   private FeelMath() {
   }

   /** Linear distance falloff in 0..1; zero or negative range means "nobody". */
   public static float falloff(double distance, double range) {
      if (!(range > 0.0) || !(distance >= 0.0)) {
         return 0.0F;
      }
      return (float)Math.max(0.0, 1.0 - distance / range);
   }

   /** Ring radius (screen heights) at progress 0..1: fast start, soft landing (ease-out cubic). */
   public static float waveRadius(float progress, float max) {
      if (!(progress > 0.0F)) {
         return 0.0F;
      }
      if (progress >= 1.0F) {
         return max;
      }
      float inv = 1.0F - progress;
      return max * (1.0F - inv * inv * inv);
   }

   /** Ring strength at progress 0..1: rises over the first 8 %, then fades as (1 - p)^1.5. */
   public static float waveStrength(float progress) {
      if (!(progress > 0.0F) || !(progress < 1.0F)) {
         return 0.0F;
      }
      if (progress < 0.08F) {
         return progress / 0.08F * (float)Math.pow(0.92, 1.5);
      }
      return (float)Math.pow(1.0F - progress, 1.5);
   }

   /** Impact frame phase {@code elapsed} ms after the hit; without inversion lines start at once. */
   public static int impactPhase(long elapsed, boolean invert) {
      if (elapsed < 0L || elapsed >= IMPACT_TOTAL_MS) {
         return PHASE_NONE;
      }
      if (invert && elapsed < IMPACT_INVERT_MS) {
         return PHASE_INVERT;
      }
      return elapsed < IMPACT_LINES_MS ? PHASE_LINES : PHASE_FADE;
   }

   /** Opacity of the speed lines: full until the fade, then linear to zero. */
   public static float impactAlpha(long elapsed) {
      if (elapsed < 0L || elapsed >= IMPACT_TOTAL_MS) {
         return 0.0F;
      }
      if (elapsed < IMPACT_LINES_MS) {
         return 1.0F;
      }
      return 1.0F - (float)(elapsed - IMPACT_LINES_MS) / (float)(IMPACT_TOTAL_MS - IMPACT_LINES_MS);
   }

   /** Cooldown check; {@code last < 0} means no impact frame was shown yet. */
   public static boolean impactAllowed(long now, long last) {
      return last < 0L || now - last >= IMPACT_COOLDOWN_MS;
   }

   /** NaN and negatives become 0, values above max become max. */
   public static float sane(float value, float max) {
      if (!(value > 0.0F)) {
         return 0.0F;
      }
      return Math.min(max, value);
   }

   public static float clamp01(float value) {
      return sane(value, 1.0F);
   }
}
