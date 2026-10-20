package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.anim.animation.Animation;

/** Sampling policy for Reinhard's stateless geo clips; not a second animation engine. */
public final class ReinhardPlayback {
   private ReinhardPlayback() {}

   /** Identical bounded server time for first person, third person and cinematics. */
   public static double actionTicks(int elapsed, int length, float partial, double held) {
      double ticks = elapsed + (Float.isFinite(partial) ? partial : 0) - (Double.isFinite(held) ? Math.max(0, held) : 0);
      return Math.max(0, length > 0 ? Math.min(length, ticks) : ticks);
   }

   public static double time(Animation clip, double seconds) {
      if (!Double.isFinite(seconds) || seconds <= 0.0 || clip.lengthSeconds() <= 0.0) return 0.0;
      return clip.loopType() == Animation.LoopType.LOOP
         ? seconds % clip.lengthSeconds() : Math.min(seconds, clip.lengthSeconds());
   }

   /** Two-tick eased entry and recovery over the current underlying locomotion. */
   public static float weight(double seconds, double length) {
      if (!Double.isFinite(seconds) || length <= 0 || seconds <= 0 || seconds >= length) return 0;
      double in = Math.min(1, seconds / .1);
      double out = Math.min(1, (length - seconds) / .1);
      return (float)(smooth(in) * smooth(out));
   }

   /** A snap/throw continues the preceding guard/hold: never fade back to idle on its first frame. */
   public static float exit(double seconds, double length) {
      if (!Double.isFinite(seconds) || length <= 0 || seconds >= length) return 0;
      return (float)smooth(Math.max(0, Math.min(1, (length-seconds)/.1)));
   }

   private static double smooth(double t) { return t * t * (3 - 2 * t); }
}
