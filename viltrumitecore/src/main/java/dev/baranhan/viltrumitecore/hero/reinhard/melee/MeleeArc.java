package dev.baranhan.viltrumitecore.hero.reinhard.melee;

/**
 * Shared geometry of one ordinary melee swing (fists and Reid). A swing is a path of
 * view-relative angles (yaw: + to the right of the crosshair, pitch: + up, degrees),
 * swept between {@code contactStart} and {@code contactEnd} ticks of the action timeline.
 * The server hit test ({@link MeleeHits}) and the client slash trail read the same path,
 * so the visible arc is the hit area. No Minecraft calls: unit tested.
 */
public final class MeleeArc {
   private MeleeArc() {
   }

   /**
    * @param reach      eye-to-box distance limit, blocks
    * @param width      angular half-thickness of the blade path, degrees
    * @param maxTargets distinct targets one swing may hit
    */
   public record Swing(float yawFrom, float pitchFrom, float yawTo, float pitchTo, double reach, float width,
      int contactStart, int contactEnd, int maxTargets) {
      /** Sweep progress 0..1 covered at the END of action tick {@code tick} (fractional on the client). */
      public double progress(double tick) {
         double span = Math.max(1, this.contactEnd - this.contactStart + 1);
         return clamp((tick - this.contactStart + 1) / span);
      }

      /** View-relative {yaw, pitch} of the blade at sweep progress {@code p} (eased: fastest mid-arc). */
      public double[] at(double p) {
         double t = ease(clamp(p));
         return new double[]{this.yawFrom + (this.yawTo - this.yawFrom) * t, this.pitchFrom + (this.pitchTo - this.pitchFrom) * t};
      }

      /** +1 when the blade travels to the right, -1 to the left, 0 for a straight thrust/chop. */
      public int side() {
         float d = this.yawTo - this.yawFrom;
         return Math.abs(d) < 1.0F ? 0 : d > 0 ? 1 : -1;
      }

      /** The same swing mirrored left/right. */
      public Swing mirrored() {
         return new Swing(-this.yawFrom, this.pitchFrom, -this.yawTo, this.pitchTo, this.reach, this.width, this.contactStart,
            this.contactEnd, this.maxTargets);
      }
   }

   static double clamp(double v) {
      return v < 0 ? 0 : v > 1 ? 1 : v;
   }

   /** Smoothstep: the blade accelerates out of the wind-up and brakes into the follow-through. */
   static double ease(double t) {
      return t * t * (3 - 2 * t);
   }

   /**
    * Camera basis for a Minecraft view (degrees): forward {@code f}, screen-right {@code r}, up {@code u}.
    * Right comes from the yaw alone, so straight up/down views stay defined.
    */
   public static double[][] basis(float yawDeg, float pitchDeg) {
      double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
      double[] f = {-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
      double[] r = {-Math.cos(yaw), 0, -Math.sin(yaw)};
      double[] u = {r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]};
      return new double[][]{f, r, u};
   }

   /** World direction of view-relative angles (degrees) in a {@link #basis}. */
   public static double[] direction(double[][] basis, double yawDeg, double pitchDeg) {
      double a = Math.toRadians(yawDeg), b = Math.toRadians(pitchDeg);
      double cf = Math.cos(b) * Math.cos(a), cr = Math.cos(b) * Math.sin(a), cu = Math.sin(b);
      double[] f = basis[0], r = basis[1], u = basis[2];
      return new double[]{cf * f[0] + cr * r[0] + cu * u[0], cf * f[1] + cr * r[1] + cu * u[1], cf * f[2] + cr * r[2] + cu * u[2]};
   }

   /** View-relative {yaw, pitch, forward} (degrees, degrees, blocks) of a world offset from the eye. */
   public static double[] angles(double[][] basis, double dx, double dy, double dz) {
      double[] f = basis[0], r = basis[1], u = basis[2];
      double zf = dx * f[0] + dy * f[1] + dz * f[2];
      double xr = dx * r[0] + dy * r[1] + dz * r[2];
      double yu = dx * u[0] + dy * u[1] + dz * u[2];
      return new double[]{Math.toDegrees(Math.atan2(xr, zf)), Math.toDegrees(Math.atan2(yu, Math.sqrt(xr * xr + zf * zf))), zf};
   }

   /** Angular distance (degrees) from point (y, p) to the angle-space segment a→b. */
   public static double segmentDistance(double y, double p, double[] a, double[] b) {
      double dy = b[0] - a[0], dp = b[1] - a[1];
      double len = dy * dy + dp * dp;
      double t = len < 1.0E-9 ? 0 : clamp(((y - a[0]) * dy + (p - a[1]) * dp) / len);
      double ey = a[0] + dy * t - y, ep = a[1] + dp * t - p;
      return Math.sqrt(ey * ey + ep * ep);
   }

   /** Angular radius (degrees) of a body of bounding radius {@code radius} at {@code distance}. */
   public static double angularRadius(double radius, double distance) {
      return Math.toDegrees(Math.atan2(radius, Math.max(0.05, distance)));
   }

   /**
    * Pure hit rule for one tick: does the blade path swept between progress {@code p0} and
    * {@code p1} touch a body seen at view angles (yaw, pitch) with angular radius {@code bodyDeg}?
    * The path is checked as short angle-space segments so a wide sweep keeps its curve.
    */
   public static boolean touches(Swing swing, double p0, double p1, double yaw, double pitch, double bodyDeg) {
      double limit = bodyDeg + swing.width();
      int steps = Math.max(1, (int) Math.ceil(Math.abs(p1 - p0) * 8));
      double[] prev = swing.at(p0);
      for (int i = 1; i <= steps; i++) {
         double[] next = swing.at(p0 + (p1 - p0) * i / steps);
         if (segmentDistance(yaw, pitch, prev, next) <= limit) {
            return true;
         }

         prev = next;
      }

      return false;
   }
}
