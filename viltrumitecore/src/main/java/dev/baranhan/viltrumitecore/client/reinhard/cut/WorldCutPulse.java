package dev.baranhan.viltrumitecore.client.reinhard.cut;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;

/**
 * Client cadence of the rupture pulse. One pure clock for audio, dust and
 * camera: the hum swell, the derived pulse voice, the smoke wave and the
 * shake all read the same beat, so they cannot drift apart.
 *
 * The first pulse lands 16 ticks after BLAST, then every 50 ticks (2.5 s).
 * A cut that reaches DONE at its usual ~157 ticks therefore gets two or
 * three pulses, never a continuous screen shake.
 *
 * A scene plays a beat only if it admitted that beat's start (see
 * {@link #admits}); a viewer whose scene first appears in the middle of a
 * pulse skips the rest of it instead of getting swell, shake and dust
 * without the voice.
 */
public final class WorldCutPulse {
   public static final int FIRST = ReinhardRules.WORLD_CUT_BLAST + 16;
   public static final int PERIOD = 50;
   public static final int LENGTH = 24;
   /** A start first observed up to this many ticks late (packet or clock jitter) still plays. */
   public static final int CATCH_UP = 3;
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
    * Start tick of the accepted pulse whose envelope covers {@code elapsed},
    * or -1. {@code acceptedUntil} is the elapsed tick of the server DONE (or
    * +inf while working): a pulse that started at or before it may finish, a
    * later beat is rejected so no component starts alone during the fade.
    */
   public static long beat(double elapsed, double acceptedUntil) {
      if (!(elapsed >= FIRST) || !(acceptedUntil >= FIRST)) {
         return -1;
      }
      long last = FIRST + (long)Math.floor((Math.min(elapsed, acceptedUntil) - FIRST) / PERIOD) * PERIOD;
      return elapsed - last < LENGTH ? last : -1;
   }

   /** Fractional ticks since the accepted pulse start (render clock), or -1 outside an envelope. */
   public static double phase(double elapsed, double acceptedUntil) {
      long beat = beat(elapsed, acceptedUntil);
      return beat < 0 ? -1 : elapsed - beat;
   }

   /** Whole ticks since the accepted pulse start, or -1 outside an envelope. */
   public static int age(double elapsed, double acceptedUntil) {
      double phase = phase(elapsed, acceptedUntil);
      return phase < 0 ? -1 : (int)Math.floor(phase);
   }

   /** Working-scene shorthand: every beat up to now is accepted. */
   public static int age(double elapsed) {
      return age(elapsed, Double.POSITIVE_INFINITY);
   }

   /**
    * Whether a scene that last admitted the beat starting at {@code admitted}
    * (-1: none) admits the current beat on this tick: once per beat, on time
    * or at most {@link #CATCH_UP} ticks late. A beat first seen later than
    * that (late join, re-entry) is skipped by every component.
    */
   public static boolean admits(long elapsed, double acceptedUntil, long admitted) {
      long beat = beat(elapsed, acceptedUntil);
      return beat >= 0 && beat != admitted && elapsed - beat <= CATCH_UP;
   }

   /** {@link #phase} of the current beat if the scene admitted it, otherwise -1. */
   public static double admittedPhase(double elapsed, double acceptedUntil, long admitted) {
      return admitted >= 0 && beat(elapsed, acceptedUntil) == admitted ? phase(elapsed, acceptedUntil) : -1;
   }

   /** Smooth attack of one pulse from its phase: 1 at its start, 0 at LENGTH or outside. */
   public static float envelope(double phase) {
      if (!(phase >= 0) || phase >= LENGTH) {
         return 0.0F;
      }
      float left = 1.0F - (float)phase / LENGTH;
      return left * left;
   }

   /** Envelope on the whole-tick clock (hum swell and shake run at tick rate). */
   public static float strength(double elapsed, double acceptedUntil) {
      int age = age(elapsed, acceptedUntil);
      return age < 0 ? 0.0F : envelope(age);
   }

   public static float strength(double elapsed) {
      return strength(elapsed, Double.POSITIVE_INFINITY);
   }

   /** Extra cinematic shake for an envelope value; zero between pulses. */
   public static float shakeFor(float envelope) {
      return SHAKE * envelope;
   }

   /** Extra cinematic shake on top of the scene base; zero between pulses. */
   public static float shake(double elapsed, double acceptedUntil) {
      return shakeFor(strength(elapsed, acceptedUntil));
   }

   public static float shake(double elapsed) {
      return shake(elapsed, Double.POSITIVE_INFINITY);
   }
}
