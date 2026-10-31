package dev.baranhan.viltrumitecore.client.captain;

import dev.baranhan.viltrumitecore.hero.captain.CaptainRules;

/**
 * Tactical Roll body spin (pure math, model pixels). The roll clip only tucks the limbs into a ball; the 360 is
 * applied by CaptainRenderer about the tuck centroid so the body turns like a wheel instead of around the feet
 * (which sank the head into the floor and flipped the model when the clip faded out at 360 degrees).
 * The centroid and the floor-contact table were solved by tools/assets/captain_preview.py on the roll tuck pose.
 */
public final class CaptainRoll {
   /** Tuck centroid in model px (y up, -z forward). */
   public static final float PIVOT_Y = 9.24F;
   public static final float PIVOT_Z = -9.93F;
   /** Centroid height that keeps the lowest vertex on the floor, every 15 degrees of forward pitch (0..360). */
   private static final float[] CONTACT = {8.5F, 8.27F, 9.17F, 9.59F, 9.76F, 11.04F, 11.57F, 11.31F, 10.28F, 8.55F, 7.02F, 6.58F, 8.5F,
      9.91F, 10.65F, 11.79F, 12.31F, 12.29F, 11.57F, 11.06F, 10.82F, 9.85F, 8.21F, 8.43F, 8.5F};
   /** The spin runs from tick 1 (tucked) to tick 8, then the clip unfolds into the crouch. */
   static final double SPIN_START = 1.0;
   static final double SPIN_END = CaptainRules.ROLL_TICKS - 2.0;

   private CaptainRoll() {
   }

   /** Forward pitch in degrees (0..360) at the given server-clock tick of the roll. */
   public static float angle(double ticks) {
      double x = (ticks - SPIN_START) / (SPIN_END - SPIN_START);
      x = Math.max(0.0, Math.min(1.0, x));
      return (float)(360.0 * x * x * (3.0 - 2.0 * x));
   }

   /** Vertical offset (px) of the centroid relative to rest at the given pitch: 0 at 0 and 360 degrees. */
   public static float lift(float angleDeg) {
      float a = Math.max(0.0F, Math.min(360.0F, angleDeg)) / 15.0F;
      int i = Math.min(CONTACT.length - 2, (int)a);
      float f = a - i;
      return CONTACT[i] + (CONTACT[i + 1] - CONTACT[i]) * f - CONTACT[0];
   }

   /** Minecraft yaw (degrees) of a horizontal motion, or NaN when the motion is too small to tell. */
   public static float travelYaw(double dx, double dz) {
      if (dx * dx + dz * dz < 1.0E-4) {
         return Float.NaN;
      }
      return (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
   }
}
