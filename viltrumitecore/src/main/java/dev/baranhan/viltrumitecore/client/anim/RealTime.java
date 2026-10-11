package dev.baranhan.viltrumitecore.client.anim;

/**
 * Real-time clock for frame-rate independent easing, in float seconds since
 * the client started. {@code System.nanoTime() / 1e9F} counts from an
 * arbitrary origin (often OS uptime): after days the float step grows to
 * tens of milliseconds and eased weights move in visible steps.
 */
public final class RealTime {
   private static final long START = System.nanoTime();

   private RealTime() {
   }

   public static float seconds() {
      return (System.nanoTime() - START) / 1.0E9F;
   }
}
