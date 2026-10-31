package dev.baranhan.viltrumitecore.client.chitauri;

/**
 * Pure geometry of the portal "window" (unit tested): the plane basis and the projection of a point behind the
 * plane onto it along the eye ray, which is how the far space inside the portal gets real parallax and stays inside
 * the rim from every angle. Vectors are double[3].
 */
public final class PortalGeometry {
   private PortalGeometry() {
   }

   /** Two unit axes spanning the plane of normal {@code n} (right-handed: a × b = n). */
   public static double[][] basis(double[] n) {
      double[] a = cross(n, new double[]{0, 0, 1});
      if (dot(a, a) < 1.0E-6) {
         a = cross(n, new double[]{1, 0, 0});
      }
      a = normalize(a);
      double[] b = normalize(cross(n, a));
      return new double[][]{a, b};
   }

   /**
    * Where the eye sees {@code s} on the plane (point {@code c}, normal {@code n}): the intersection of the ray eye→s
    * with the plane, or null when the ray runs parallel or the point is on the eye's side of the plane.
    */
   public static double[] project(double[] eye, double[] s, double[] c, double[] n) {
      double[] d = sub(s, eye);
      double denom = dot(d, n);
      if (Math.abs(denom) < 1.0E-9) {
         return null;
      }
      double t = dot(sub(c, eye), n) / denom;
      if (t <= 0.0 || t >= 1.0) {
         return null;
      }
      return new double[]{eye[0] + d[0] * t, eye[1] + d[1] * t, eye[2] + d[2] * t};
   }

   /** In-plane coordinates of {@code p} relative to the centre along the axes. */
   public static double[] planar(double[] p, double[] c, double[] a, double[] b) {
      double[] d = sub(p, c);
      return new double[]{dot(d, a), dot(d, b)};
   }

   /** Clamps in-plane coordinates into a disc of radius {@code r}; returns the scale applied (1 = inside). */
   public static double clampDisc(double[] uv, double r) {
      double len = Math.sqrt(uv[0] * uv[0] + uv[1] * uv[1]);
      if (len <= r || len < 1.0E-9) {
         return 1.0;
      }
      double k = r / len;
      uv[0] *= k;
      uv[1] *= k;
      return k;
   }

   /**
    * True when {@code p} is still inside the portal: behind the plane (more than {@code margin}) and within the
    * tube of radius {@code r} behind the disc, no deeper than {@code depth}.
    */
   public static boolean behind(double[] p, double[] c, double[] n, double r, double margin, double depth) {
      double[] d = sub(p, c);
      double along = dot(d, n);
      if (along >= -margin || along < -depth) {
         return false;
      }
      double lx = d[0] - n[0] * along, ly = d[1] - n[1] * along, lz = d[2] - n[2] * along;
      return lx * lx + ly * ly + lz * lz <= r * r;
   }

   static double dot(double[] a, double[] b) {
      return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
   }

   static double[] sub(double[] a, double[] b) {
      return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
   }

   static double[] cross(double[] a, double[] b) {
      return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
   }

   static double[] normalize(double[] a) {
      double l = Math.sqrt(dot(a, a));
      return l < 1.0E-12 ? new double[]{0, 0, 0} : new double[]{a[0] / l, a[1] / l, a[2] / l};
   }
}
