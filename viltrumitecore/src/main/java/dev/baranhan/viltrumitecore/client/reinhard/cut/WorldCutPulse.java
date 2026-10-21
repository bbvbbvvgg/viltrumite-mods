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

   /** Exact client tick of a pulse start; false before BLAST+30 and off-beat. */
   public static boolean due(long elapsed) {
      return elapsed >= FIRST && (elapsed - FIRST) % PERIOD == 0;
   }

   /** Ticks since the last pulse start, or -1 before the first pulse. Partial ticks allowed. */
   public static int age(double elapsed) {
      if (!(elapsed >= FIRST)) {
         return -1;
      }
      return (int)Math.floor((elapsed - FIRST) % PERIOD);
   }

   /** Smooth attack of one pulse: 1 at its start, 0 at LENGTH. */
   public static float strength(double elapsed) {
      int age = age(elapsed);
      if (age < 0 || age >= LENGTH) {
         return 0.0F;
      }
      float left = 1.0F - age / (float)LENGTH;
      return left * left;
   }

   /** Extra cinematic shake on top of the scene base; zero between pulses. */
   public static float shake(double elapsed) {
      return SHAKE * strength(elapsed);
   }
}
