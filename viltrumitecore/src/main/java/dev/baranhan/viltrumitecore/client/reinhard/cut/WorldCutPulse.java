package dev.baranhan.viltrumitecore.client.reinhard.cut;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;

/**
 * Client cadence of the rupture pulse. One pure clock for audio, dust and
 * camera: the hum swell, the derived pulse voice, the smoke wave and the
 * shake all read the same tick, so they cannot drift apart.
 *
 * The first pulse lands 16 ticks after BLAST, then every 50 ticks (2.5 s).
 * A cut that reaches DONE at its usual ~157 ticks therefore gets two or
 * three pulses, never a continuous screen shake.
 */
public final class WorldCutPulse {
   public static final int FIRST = ReinhardRules.WORLD_CUT_BLAST + 16;
   public static final int PERIOD = 50;
   public static final int LENGTH = 24;
   private static final float SHAKE = 2.4F;

   private WorldCutPulse() {
   }

   /** Exact client tick of a pulse start; no beat at or after the accepted end. */
   public static boolean due(long elapsed, double acceptedUntil) {
      return elapsed >= FIRST && elapsed <= acceptedUntil && (elapsed - FIRST) % PERIOD == 0;
   }

   /** Working-scene shorthand: every beat up to now is accepted. */
   public static boolean due(long elapsed) {
      return due(elapsed, Double.POSITIVE_INFINITY);
   }

   /**
    * Ticks since the last accepted pulse start, or -1 before the first pulse.
    * {@code acceptedUntil} is the elapsed tick of the server DONE (or +inf
    * while working): a pulse that started at or before it may finish, a later
    * beat is rejected so no component starts alone during the fade.
    */
   public static int age(double elapsed, double acceptedUntil) {
      if (!(elapsed >= FIRST) || !(acceptedUntil >= FIRST)) {
         return -1;
      }
      double lastBeat = FIRST + Math.floor((Math.min(elapsed, acceptedUntil) - FIRST) / PERIOD) * PERIOD;
      int age = (int)Math.floor(elapsed - lastBeat);
      return age >= 0 && age < LENGTH ? age : -1;
   }

   /** Working-scene shorthand: every beat up to now is accepted. */
   public static int age(double elapsed) {
      return age(elapsed, Double.POSITIVE_INFINITY);
   }

   /** Smooth attack of one pulse: 1 at its start, 0 at LENGTH. */
   public static float strength(double elapsed, double acceptedUntil) {
      int age = age(elapsed, acceptedUntil);
      if (age < 0) {
         return 0.0F;
      }
      float left = 1.0F - age / (float)LENGTH;
      return left * left;
   }

   public static float strength(double elapsed) {
      return strength(elapsed, Double.POSITIVE_INFINITY);
   }

   /** Extra cinematic shake on top of the scene base; zero between pulses. */
   public static float shake(double elapsed, double acceptedUntil) {
      return SHAKE * strength(elapsed, acceptedUntil);
   }

   public static float shake(double elapsed) {
      return shake(elapsed, Double.POSITIVE_INFINITY);
   }
}
