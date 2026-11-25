package dev.baranhan.viltrumitecore.client.cinematic;

import net.minecraft.world.phys.Vec3;

/**
 * The built-in one-shot shot of the cinematic camera (wave1-22): a slow orbit around a focus point,
 * about three seconds long. Pure math (no Minecraft client classes), so a unit test can check the
 * endpoints and the reduced-motion scaling. T4 replaces this with the {@code cinematics/*.json}
 * scenes; until then every scene id plays this orbit.
 */
public record CinematicPath(Vec3 centre, double radius, double height, int ticks, float sweepTurns) {
   /** ~3 s at 20 ticks per second. */
   public static final int BASE_TICKS = 60;
   public static final double BASE_RADIUS = 5.0;
   public static final double BASE_HEIGHT = 2.6;
   /** Three quarters of a turn from the first to the last frame. */
   public static final float SWEEP = 0.75F;
   /** Reduced camera motion: a smaller and slower orbit. */
   public static final double REDUCED_AMPLITUDE = 0.65;
   public static final float REDUCED_SPEED = 1.5F;

   public static CinematicPath around(Vec3 centre, boolean reduced) {
      Vec3 c = centre == null ? Vec3.ZERO : centre;
      if (reduced) {
         return new CinematicPath(c, BASE_RADIUS * REDUCED_AMPLITUDE, BASE_HEIGHT * REDUCED_AMPLITUDE,
            Math.round(BASE_TICKS * REDUCED_SPEED), SWEEP);
      }
      return new CinematicPath(c, BASE_RADIUS, BASE_HEIGHT, BASE_TICKS, SWEEP);
   }

   /** Scene progress 0..1 at {@code tick}. */
   public float progress(float tick) {
      if (this.ticks <= 0) {
         return 1.0F;
      }
      return (float)clamp01(tick / (double)this.ticks);
   }

   /** Camera position at {@code tick} (world space). */
   public Vec3 position(float tick) {
      float p = this.progress(tick);
      double angle = -Math.PI * 0.5 + p * this.sweepTurns * Math.PI * 2.0;
      double r = this.radius * (1.0 - 0.12 * p);
      double y = this.height * (0.85 + 0.30 * smooth(p));
      return this.centre.add(Math.cos(angle) * r, y, Math.sin(angle) * r);
   }

   /** What the camera looks at (a touch above the focus feet). */
   public Vec3 look() {
      return this.centre.add(0.0, this.height * 0.45, 0.0);
   }

   static double smooth(double u) {
      return u * u * (3.0 - 2.0 * u);
   }

   static double clamp01(double u) {
      return u < 0.0 ? 0.0 : (u > 1.0 ? 1.0 : u);
   }
}
