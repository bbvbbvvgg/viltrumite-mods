package dev.baranhan.viltrumitecore.client.dio;

import dev.baranhan.viltrumitecore.hero.dio.DioRules;

/**
 * Pure placement rules of The World around DIO (unit tested): where the stand
 * wants to be in DIO's frame (right, up, forward in blocks), how fast it
 * follows, and its opacity in the summon/dismiss phases.
 */
public final class StandMotion {
   public enum Mode {
      /** Behind DIO's right shoulder. */
      IDLE(0.55, 0.15, -0.65, 9.0F),
      /** In front of DIO, striking. */
      ATTACK(0.0, 0.0, 0.85, 28.0F),
      /** First person strikes: lower and closer so the arms read from the camera. */
      ATTACK_FIRST_PERSON(0.0, -0.35, 0.55, 30.0F),
      /** Guard: right in front, arms crossed. */
      GUARD(0.0, 0.0, 0.7, 22.0F),
      /** ZA WARUDO: looming over DIO's back, arms spread. */
      TIME_STOP(0.0, 0.3, -0.6, 14.0F),
      /** Road Roller ride: on the roof behind DIO (who stands on the nose), striking down. */
      ROLLER(0.0, 0.35, -0.9, 30.0F),
      /** Road Roller leap: close behind, stiff spring so it keeps up. */
      LEAP(0.35, 0.2, -0.45, 24.0F);

      public final double right, up, forward;
      /** Spring rate, 1/s (exponential approach). */
      public final float rate;

      Mode(double right, double up, double forward, float rate) {
         this.right = right;
         this.up = up;
         this.forward = forward;
         this.rate = rate;
      }

      public boolean facesView() {
         return this != IDLE && this != LEAP;
      }
   }

   private StandMotion() {
   }

   /** World offset of a mode for a yaw in degrees: {dx, dy, dz}. Minecraft yaw: 0 = +Z, 90 = -X. */
   public static double[] offset(Mode mode, float yawDeg) {
      double yaw = Math.toRadians(yawDeg);
      double fx = -Math.sin(yaw), fz = Math.cos(yaw);
      double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
      return new double[]{fx * mode.forward + rx * mode.right, mode.up, fz * mode.forward + rz * mode.right};
   }

   /** Fraction of the remaining distance covered in dt seconds at a rate (frame-rate independent). */
   public static float follow(float rate, float dtSeconds) {
      return 1.0F - (float)Math.exp(-rate * Math.max(0.0F, dtSeconds));
   }

   /** Opacity during the summon (rises with a three-frame ghost stutter) and the dismiss (falls). */
   public static float phaseAlpha(int phase, double ticks) {
      if (phase == DioRules.STAND_SUMMONING) {
         double u = Math.max(0.0, Math.min(1.0, ticks / DioRules.SUMMON_TICKS));
         return (float)(u * u * (3.0 - 2.0 * u));
      }

      if (phase == DioRules.STAND_DISMISSING) {
         double u = Math.max(0.0, Math.min(1.0, ticks / DioRules.DISMISS_TICKS));
         return (float)(1.0 - u * u);
      }

      return phase == DioRules.STAND_OUT ? 1.0F : 0.0F;
   }

   /** Fade when the camera is inside or right next to the stand (third person and other viewers). */
   public static float cameraFade(double distance) {
      return (float)Math.max(0.12, Math.min(1.0, (distance - 0.55) / 1.0));
   }
}
