package dev.baranhan.viltrumitecore.t1000;

/**
 * Pure placement of a wound on the rest pose of the humanoid model (pixels; y down from the neck, -z the face,
 * -x the right side; covered by T1000RulesTest). The client turns the point into the part's own space.
 */
public final class T1000WoundMath {
   /** Rest pivots of the parts {x, y, z}: head, body, right arm, left arm, right leg, left leg. */
   public static final float[][] PIVOTS = {{0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9F, 12, 0}, {1.9F, 12, 0}};
   /** Boxes of the parts in their own space {minX, minY, minZ, maxX, maxY, maxZ} (wide arms). */
   public static final float[][] BOXES = {
      {-4, -8, -4, 4, 0, 4}, {-4, 0, -2, 4, 12, 2}, {-3, -2, -2, 1, 10, 2}, {-1, -2, -2, 3, 10, 2}, {-2, 0, -2, 2, 12, 2}, {-2, 0, -2, 2, 12, 2}
   };

   private T1000WoundMath() {
   }

   /** The part a rest-model point (x, y) belongs to. */
   public static int partAt(float x, float y) {
      if (y < 0.0F) {
         return T1000Entity.PART_HEAD;
      }
      if (y < 12.0F) {
         if (x < -4.0F) {
            return T1000Entity.PART_RIGHT_ARM;
         }
         if (x > 4.0F) {
            return T1000Entity.PART_LEFT_ARM;
         }
         return T1000Entity.PART_BODY;
      }
      return x < 0.0F ? T1000Entity.PART_RIGHT_LEG : T1000Entity.PART_LEFT_LEG;
   }

   /**
    * A wound at the surface point of the part hit from rest-model direction (x, y, z): the horizontal direction
    * from the part's axis is pushed out to its box. {@code jitter} 0..1 spreads hits a little.
    */
   public static T1000Entity.Wound onSurface(double x, float y, double z, float size, int start, int kind, float jitter) {
      float yy = Math.max(-7.6F, Math.min(23.6F, y + (jitter - 0.5F) * 1.5F));
      int part = partAt((float)Math.max(-7.9, Math.min(7.9, x)), yy);
      float[] pv = PIVOTS[part];
      float[] b = BOXES[part];
      float cx = (b[0] + b[3]) * 0.5F + pv[0];
      float cz = (b[2] + b[5]) * 0.5F + pv[2];
      float hx = (b[3] - b[0]) * 0.5F;
      float hz = (b[5] - b[2]) * 0.5F;
      double dx = x - cx;
      double dz = z - cz;
      double m = Math.max(Math.abs(dx) / hx, Math.abs(dz) / hz);
      if (m < 1.0E-4) {
         dz = -hz;
         dx = 0.0;
         m = 1.0;
      }
      // the point on the face it came through
      float sx = (float)(cx + Math.max(-hx, Math.min(hx, dx / m * hx)));
      float sz = (float)(cz + Math.max(-hz, Math.min(hz, dz / m * hz)));
      float minY = b[1] + pv[1] + 0.6F;
      float maxY = b[4] + pv[1] - 0.6F;
      return new T1000Entity.Wound(part, sx, Math.max(minY, Math.min(maxY, yy)), sz, size, start, kind);
   }
}
