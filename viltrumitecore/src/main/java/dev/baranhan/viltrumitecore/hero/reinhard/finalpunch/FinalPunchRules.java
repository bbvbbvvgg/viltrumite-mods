package dev.baranhan.viltrumitecore.hero.reinhard.finalpunch;

/**
 * Reinhard – THE FINAL PUNCH (secret, Shift + N): the one timeline every side reads
 * (server beats, clip {@code final_punch}, camera, VFX, audio). Scene ticks, 20 t/s,
 * {@link #LENGTH} = 24 s. Pure; see docs/design/2026-10-31-reinhard-final-punch.md.
 */
public final class FinalPunchRules {
   /** Whole scene (and the {@code final_punch} clip): 480 ticks = 24 s. */
   public static final int LENGTH = 480;

   // Phase I – the sentence (face close-up, orbit, wind). Head lowered until LOOK_UP.
   public static final int CAMERA_IN = 14;
   public static final int LOOK_UP = 46;
   // Phase II – the impossible windup (whole body; one leg from LEG_LIFT, full at LEG_UP).
   public static final int WINDUP_START = 40;
   public static final int LEG_LIFT = 150;
   public static final int LEG_UP = 205;
   public static final int WINDUP_MAX = 330;
   // Phase III – the fist ignites: sparks, fire, rings and arcs, then the small sun.
   public static final int SPARKS = 60;
   public static final int FIRE = 140;
   public static final int RINGS = 205;
   public static final int ARCS = 245;
   public static final int SUN = 320;
   // Phase IV – reality loses its mind (streaks, debris, giant rings, storm impulses).
   public static final int WARP = 260;
   // Phase V – absolute silence: everything stops, the camera closes on the fist; the slow strike.
   public static final int SILENCE = 398;
   public static final int STRIKE = 412;
   /** Phase VI – the fist meets the jaw. */
   public static final int CONTACT = 444;
   /** Phase VII – hit-stop, then the launch and the calm aftermath. */
   public static final int HIT_STOP_TICKS = 6;
   public static final int LAUNCH = CONTACT + HIT_STOP_TICKS;
   public static final int LOWER_FIST = 462;
   public static final int CAMERA_OUT = 466;

   /**
    * Speech (subtitles only, no synthesised voice): twelve pages, each started at its tick and
    * shown for its ticks; the pauses grow towards the last three words, the last ends on the silence.
    */
   public static final int[] LINE_AT = {10, 58, 112, 146, 202, 242, 284, 328, 348, 368, 378, 388};
   public static final int[] LINE_TICKS = {44, 50, 32, 52, 36, 38, 40, 18, 18, 10, 10, 10};
   public static final int LINES = LINE_AT.length;

   /** Target search: radius and the half-angle of the look cone (degrees). */
   public static final double TARGET_RADIUS = 16.0;
   public static final double TARGET_CONE_DEGREES = 28.0;
   /** Phase IV storm: radius and entity cap of one impulse pulse, the pulse interval. */
   public static final double STORM_RADIUS = 10.0;
   public static final int STORM_MAX_ENTITIES = 24;
   public static final int STORM_INTERVAL = 5;
   /** The held target may drift this far from its anchor before the scene counts it lost. */
   public static final double LOST_DISTANCE = 6.0;

   /** Jaw stays knocked off this long on the clients (visual only). */
   public static final int JAW_TICKS = 20 * 30;

   // Config clamps (ViltrumiteCoreConfig values are clamped to these).
   public static final float MAX_DAMAGE = 1000.0F;
   public static final double MAX_LAUNCH_SPEED = 8.0;
   public static final int MAX_SUSTAIN_TICKS = 40;
   public static final int MAX_COOLDOWN_SECONDS = 3600;

   /** What happens on one server tick of the scene (pure, unit tested). */
   public enum Beat {
      HOLD,
      CONTACT,
      HIT_STOP,
      LAUNCH,
      AFTER,
      END;
   }

   private FinalPunchRules() {
   }

   public static Beat beat(int elapsed) {
      if (elapsed < CONTACT) return Beat.HOLD;
      if (elapsed == CONTACT) return Beat.CONTACT;
      if (elapsed < LAUNCH) return Beat.HIT_STOP;
      if (elapsed == LAUNCH) return Beat.LAUNCH;
      return elapsed < LENGTH ? Beat.AFTER : Beat.END;
   }

   /** Index of the speech page that starts on this tick, −1 = none. */
   public static int lineAt(int elapsed) {
      for (int i = 0; i < LINES; i++) {
         if (LINE_AT[i] == elapsed) return i;
      }
      return -1;
   }

   /** Phase IV storm pulse on this tick. */
   public static boolean stormPulse(int elapsed) {
      return elapsed >= WARP && elapsed < SILENCE && (elapsed - WARP) % STORM_INTERVAL == 0;
   }

   /** 0..1 build of the storm (Phase IV), 0 outside it. */
   public static double storm(double ticks) {
      if (ticks < WARP || ticks >= SILENCE) return 0.0;
      return clamp01((ticks - WARP) / (double) (SILENCE - 20 - WARP));
   }

   /** 0..1 charge of the fist (sparks → small sun), held through the silence and the strike. */
   public static double charge(double ticks) {
      if (ticks < SPARKS) return 0.0;
      if (ticks >= CONTACT) return 0.0;
      return clamp01((ticks - SPARKS) / (double) (SUN - SPARKS));
   }

   /** 0..1 progress of the strike (STRIKE → CONTACT). */
   public static double strike(double ticks) {
      return clamp01((ticks - STRIKE) / (double) (CONTACT - STRIKE));
   }

   /** The music, wind and hum stop dead at the silence. */
   public static boolean silent(double ticks) {
      return ticks >= SILENCE;
   }

   /** Ticks of one speech page on screen (until the next page starts). */
   public static int lineTicks(int index) {
      return LINE_TICKS[Math.max(0, Math.min(LINES - 1, index))];
   }

   public static float damage(float configured) {
      return Float.isFinite(configured) ? Math.max(0.0F, Math.min(MAX_DAMAGE, configured)) : 0.0F;
   }

   public static double launchSpeed(double configured) {
      return Double.isFinite(configured) ? Math.max(0.0, Math.min(MAX_LAUNCH_SPEED, configured)) : 0.0;
   }

   public static int sustainTicks(int configured) {
      return Math.max(1, Math.min(MAX_SUSTAIN_TICKS, configured));
   }

   public static int cooldownTicks(int seconds) {
      return Math.max(0, Math.min(MAX_COOLDOWN_SECONDS, seconds)) * 20;
   }

   static double clamp01(double v) {
      return v <= 0 ? 0 : v >= 1 ? 1 : v;
   }
}
