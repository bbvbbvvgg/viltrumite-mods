package dev.baranhan.viltrumitecore.scp.scp096;

/**
 * Pure geometry of "seeing the face" (no Minecraft types; covered by Scp096SightTest). Vectors are given as
 * components. The world glue (eyes, look vectors, raycasts, effects) lives in {@link Scp096Entity}.
 */
public final class Scp096Sight {
   private Scp096Sight() {
   }

   /**
    * True when the face point lies inside the observer's view frustum (half angles in radians) and in range, and
    * the observer stands in front of the face (angle between the face normal and the direction from the face to
    * the eyes at most {@code maxFront}). The look and face normal vectors need not be normalized.
    */
   public static boolean faceInView(double ex, double ey, double ez, double lx, double ly, double lz,
                                    double fx, double fy, double fz, double nx, double ny, double nz,
                                    double halfH, double halfV, double range, double maxFront) {
      double dx = fx - ex;
      double dy = fy - ey;
      double dz = fz - ez;
      double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (!(dist > 1.0E-4) || dist > range) {
         return false;
      }
      double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
      if (!(ll > 1.0E-6)) {
         return false;
      }
      lx /= ll;
      ly /= ll;
      lz /= ll;
      // camera basis: forward = look, right = look x up(0,1,0), up' = right x forward
      double rx = -lz;
      double ry = 0.0;
      double rz = lx;
      double rl = Math.sqrt(rx * rx + rz * rz);
      if (rl < 1.0E-6) {
         // looking straight up or down: any horizontal right vector
         rx = 1.0;
         rz = 0.0;
         rl = 1.0;
      }
      rx /= rl;
      rz /= rl;
      double ux = ry * lz - rz * ly;
      double uy = rz * lx - rx * lz;
      double uz = rx * ly - ry * lx;
      double cz = dx * lx + dy * ly + dz * lz;
      if (cz <= 0.05) {
         return false;
      }
      double cx = dx * rx + dy * ry + dz * rz;
      double cy = dx * ux + dy * uy + dz * uz;
      if (Math.abs(cx) > cz * Math.tan(halfH) || Math.abs(cy) > cz * Math.tan(halfV)) {
         return false;
      }
      return inFront(ex, ey, ez, fx, fy, fz, nx, ny, nz, maxFront);
   }

   /** The eyes are on the face's side: angle between the face normal and face -> eyes at most maxFront. */
   public static boolean inFront(double ex, double ey, double ez, double fx, double fy, double fz, double nx, double ny, double nz, double maxFront) {
      double tx = ex - fx;
      double ty = ey - fy;
      double tz = ez - fz;
      double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
      double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
      if (tl < 1.0E-6 || nl < 1.0E-6) {
         return false;
      }
      double cos = (tx * nx + ty * ny + tz * nz) / (tl * nl);
      return cos >= Math.cos(maxFront);
   }

   /** Unit face normal from a head yaw and pitch in Minecraft degrees (yaw 0 = +Z, pitch > 0 looks down). */
   public static double[] normal(float yawDeg, float pitchDeg) {
      double yaw = Math.toRadians(yawDeg);
      double pitch = Math.toRadians(pitchDeg);
      double c = Math.cos(pitch);
      return new double[]{-Math.sin(yaw) * c, -Math.sin(pitch), Math.cos(yaw) * c};
   }

   /** Horizontal angle (radians, 0..pi) between a look direction and the direction to a point. */
   public static double horizontalAngle(double lx, double lz, double dx, double dz) {
      double a = Math.sqrt(lx * lx + lz * lz);
      double b = Math.sqrt(dx * dx + dz * dz);
      if (a < 1.0E-6 || b < 1.0E-6) {
         return 0.0;
      }
      double cos = (lx * dx + lz * dz) / (a * b);
      return Math.acos(Math.max(-1.0, Math.min(1.0, cos)));
   }
}
