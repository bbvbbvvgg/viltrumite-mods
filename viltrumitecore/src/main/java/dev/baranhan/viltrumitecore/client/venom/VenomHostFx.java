package dev.baranhan.viltrumitecore.client.venom;

/**
 * Pure timing of what a ridden host sees (VenomHostFxTest): the view is never black. A narrow slit of sight
 * (the symbiote's mask over the eyes) breathes open and shut; now and then it tears wide for a glimpse, more often
 * the harder the host fights; scary flashes (teeth, the white eyes) strike at irregular moments; the veins crawl in
 * from the edges while the symbiote takes hold and draw back as it loses its grip.
 */
public final class VenomHostFx {
   /** The slit never closes below this (fraction of the screen height): the host always sees something. */
   public static final float MIN_OPEN = 0.24F;
   /** The view's floor brightness under the darkest vignette. */
   public static final float MIN_LIGHT = 0.2F;

   private VenomHostFx() {
   }

   /**
    * Pure: the slit's openness 0-1 at {@code seconds} into the control phase. {@code resist} 0-1 (the bar): fighting
    * back widens the breathing and makes glimpses longer. {@code seed} varies the glimpse pattern per session.
    */
   public static float slit(double seconds, float resist, int seed) {
      double breath = 0.5 + 0.5 * Math.sin(seconds * 1.7 + seed);
      float open = MIN_OPEN + (float)breath * (0.12F + 0.25F * resist);
      // a glimpse: every 3-6 s the slit tears open for 0.5-1.3 s
      double period = 4.5 - 1.5 * resist + (seed & 3) * 0.4;
      double phase = (seconds + (seed % 7) * 0.61) % period;
      double length = 0.5 + 0.8 * resist;
      if (phase < length) {
         double g = Math.sin(Math.PI * phase / length);
         open = Math.max(open, (float)(MIN_OPEN + (0.92 - MIN_OPEN) * g));
      }
      return Math.max(MIN_OPEN, Math.min(1.0F, open));
   }

   /** Pure: a scary flash 0-1 (a short spike) at irregular times; never two in a row closer than ~2 s. */
   public static float flash(double seconds, int seed) {
      double period = 5.3 + (seed % 5) * 0.37;
      double phase = (seconds + seed * 0.13) % period;
      double second = (seconds * 0.77 + seed * 0.29) % (period * 1.9);
      float a = phase < 0.22 ? (float)(1.0 - phase / 0.22) : 0.0F;
      float b = second < 0.16 ? (float)(1.0 - second / 0.16) * 0.7F : 0.0F;
      return Math.max(a, b);
   }

   /**
    * Pure: the vein cover 0-1. Engulf: they rush in with the coat ({@code engulf} 0-1); control: full, pulsing with the
    * heartbeat; the end: they draw back over {@code release} 0-1.
    */
   public static float veins(float engulf, boolean control, float release, double seconds) {
      float base = control ? 0.82F + 0.18F * (float)Math.max(0, Math.sin(seconds * 2.4 * Math.PI)) : engulf * 0.9F;
      return Math.max(0, Math.min(1, base * (1.0F - release)));
   }

   /** Pure: how many ticks apart the heartbeat plays (it races as the host fights). */
   public static int heartbeatGap(float resist) {
      return Math.max(9, Math.round(20 - 10 * resist));
   }
}
