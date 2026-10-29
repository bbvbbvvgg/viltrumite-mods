package dev.baranhan.viltrumitecore.client.dio;

/**
 * Pure screen timing of a stopped-time field for one viewer (unit tested):
 * {grey, invert, ripple} for the dash_impact post pass. Ages are field ticks;
 * negative ages are the cast before the capture (the wave leaves DIO 6 ticks
 * before it, radius 0 → full in 10 ticks, spec §5.5).
 */
public final class TimeStopScreen {
   public static final double WAVE_LEAD = 6.0;
   public static final double WAVE_TICKS = 10.0;
   /** Ticks the screen ring needs to sweep out once the front passed the viewer. */
   public static final double RING_TICKS = 8.0;
   public static final double RESUME_TICKS = 20.0;
   public static final double RETURN_TICKS = 6.0;
   public static final float[] NONE = {0.0F, 0.0F, 0.0F};

   private TimeStopScreen() {
   }

   /** Visual capture front radius at a field age. */
   public static double front(double age, double radius) {
      return radius * Math.max(0.0, Math.min(1.0, (age + WAVE_LEAD) / WAVE_TICKS));
   }

   /** Reverse (resume) front radius with {@code left} stopped ticks to go; full radius before the resume window. */
   public static double backFront(double left, double radius) {
      return radius * Math.max(0.0, Math.min(1.0, left / RESUME_TICKS));
   }

   /**
    * @param age      field age in ticks (negative during the cast)
    * @param left     stopped ticks left; ignored while {@code endedFor < 0} for scripted fields
    * @param scripted the Road Roller field (no resume window: it ends with a packet)
    * @param endedFor ticks since the field ended, or -1 while it runs
    * @param distance viewer distance to the field center
    */
   public static float[] screen(double age, double left, boolean scripted, double endedFor, double distance, double radius) {
      if (distance > radius || radius <= 0.0) {
         return NONE;
      }

      double reach = WAVE_TICKS * distance / radius - WAVE_LEAD;
      double since = age - reach;
      if (since < 0.0) {
         return NONE;
      }

      float grey = (float)Math.min(1.0, since / 6.0);
      float invert = 0.0F, ripple = 0.0F;
      if (since < RING_TICKS) {
         ripple = (float)(since / RING_TICKS * 1.4);
         invert = 1.0F;
      }

      double back;
      if (endedFor >= 0.0) {
         // Ended (scripted end, hurry or a cut): colours run back from the edge in RETURN_TICKS.
         back = scripted ? endedFor : RESUME_TICKS + endedFor;
         if (scripted) {
            return returning(grey, back / RETURN_TICKS);
         }
      } else if (scripted) {
         return new float[]{grey, invert, ripple};
      } else {
         back = RESUME_TICKS - left;
      }

      if (back <= 0.0) {
         return new float[]{grey, invert, ripple};
      }

      // Reverse front: reaches this viewer when left == RESUME_TICKS * distance / radius.
      double passBack = back - (RESUME_TICKS - RESUME_TICKS * distance / radius);
      if (passBack < 0.0) {
         return new float[]{grey, invert, ripple};
      }

      return returning(grey, passBack / RETURN_TICKS);
   }

   /** Colour return once the reverse front passed: a contracting inverted ring, then the grey lifts. */
   private static float[] returning(float grey, double u) {
      if (u >= 1.0) {
         return NONE;
      }

      float ring = (float)(1.4 * (1.0 - u));
      return new float[]{(float)(grey * (1.0 - u)), (float)(0.75 * (1.0 - u)), Math.max(0.001F, ring)};
   }
}
