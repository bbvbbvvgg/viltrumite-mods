package dev.baranhan.viltrumitecore.client.hero.speech;

/**
 * Pure timeline of one subtitle, in ticks from its start: enter (fade + 6 px
 * rise), pages held long enough to read them (by text length), a soft text
 * crossfade between pages, and the exit (fade + 4 px sink). A forced exit
 * (interrupt, replacement) is shorter and starts from the current opacity.
 */
public final class SubtitleTiming {
   public static final int ENTER = 10;
   public static final int EXIT = 10;
   public static final int FORCED_EXIT = 6;
   public static final int PAGE_OUT = 5;
   public static final int PAGE_IN = 7;
   public static final float SLIDE = 6.0F;
   public static final float EXIT_SLIDE = 4.0F;
   public static final float DRIFT = 2.0F;
   public static final int MIN_PAGE = 40;
   public static final int MAX_PAGE = 240;
   /** Shortest page when an explicit line duration is spread over its pages. */
   public static final int MIN_SPREAD_PAGE = 30;
   public static final float BASE_TICKS = 18.0F;
   public static final float TICKS_PER_CHAR = 1.15F;

   public record Frame(float alpha, float slide, int page, float textAlpha, float textDrift, boolean finished) {
   }

   private SubtitleTiming() {
   }

   /** Reading time of one page: about 17 characters a second plus a short settle, 2..12 s, times the player's scale. */
   public static int autoPageTicks(int chars, double scale) {
      double ticks = (BASE_TICKS + TICKS_PER_CHAR * Math.max(0, chars)) * clampScale(scale);
      return (int)Math.max(MIN_PAGE, Math.min(MAX_PAGE, Math.round(ticks)));
   }

   /** Page lengths: automatic, or an explicit whole-line duration spread by text length. */
   public static int[] pageTicks(int[] chars, int explicitTotal, double scale) {
      int[] ticks = new int[chars.length];
      if (explicitTotal > 0) {
         long sum = 0;
         for (int c : chars) {
            sum += Math.max(1, c);
         }

         for (int i = 0; i < chars.length; i++) {
            ticks[i] = (int)Math.max(MIN_SPREAD_PAGE, Math.round((double)explicitTotal * Math.max(1, chars[i]) / sum));
         }
      } else {
         for (int i = 0; i < chars.length; i++) {
            ticks[i] = autoPageTicks(chars[i], scale);
         }
      }

      return ticks;
   }

   public static int spoken(int[] pageTicks) {
      int total = 0;
      for (int t : pageTicks) {
         total += t;
      }

      return total;
   }

   /** Whole length including the normal exit. */
   public static int length(int[] pageTicks) {
      return spoken(pageTicks) + EXIT;
   }

   /** State at {@code t} ticks; {@code forcedAt} is the forced-exit time (same clock) or NaN. */
   public static Frame frame(int[] pageTicks, double t, double forcedAt) {
      int spoken = spoken(pageTicks);
      double exitStart = spoken;
      double exitLength = EXIT;
      if (!Double.isNaN(forcedAt) && forcedAt < spoken) {
         // Interrupted while speaking; once the normal exit runs, it simply finishes.
         exitStart = Math.max(0.0, forcedAt);
         exitLength = FORCED_EXIT;
      }

      double enter = easeOut(clamp(t / ENTER));
      double out = clamp((t - exitStart) / exitLength);
      double exitEase = easeIn(out);
      float alpha = (float)(enter * (1.0 - exitEase));
      float slide = (float)(SLIDE * (1.0 - enter) + EXIT_SLIDE * exitEase);
      boolean finished = t >= exitStart + exitLength;
      int page = 0;
      double pageStart = 0.0;
      for (int i = 0; i < pageTicks.length - 1 && t >= pageStart + pageTicks[i]; i++) {
         pageStart += pageTicks[i];
         page = i + 1;
      }

      double textIn = page == 0 ? clamp((t - 2.0) / (ENTER - 2.0)) : clamp((t - pageStart) / PAGE_IN);
      float textAlpha = (float)easeOut(textIn);
      float drift = page == 0 ? 0.0F : (float)(DRIFT * (1.0 - easeOut(textIn)));
      if (page < pageTicks.length - 1) {
         double pageEnd = pageStart + pageTicks[page];
         double fadeOut = clamp((pageEnd - t) / PAGE_OUT);
         if (fadeOut < 1.0) {
            textAlpha *= (float)fadeOut;
            drift -= (float)(DRIFT * 0.5 * (1.0 - fadeOut));
         }
      }

      return new Frame(alpha, slide, page, textAlpha, drift, finished);
   }

   static double clamp(double v) {
      return v < 0.0 ? 0.0 : v > 1.0 ? 1.0 : v;
   }

   static double easeOut(double x) {
      double inv = 1.0 - x;
      return 1.0 - inv * inv * inv;
   }

   static double easeIn(double x) {
      return x * x * x;
   }

   private static double clampScale(double scale) {
      return Double.isNaN(scale) ? 1.0 : Math.max(0.5, Math.min(2.5, scale));
   }
}
