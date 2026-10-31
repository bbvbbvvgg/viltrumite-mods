package dev.baranhan.viltrumitecore.entity.chitauri;

/**
 * Pure scoring of a firing position for a Chitauri soldier: half cover (body hidden, eyes see the
 * target) beats open ground, the preferred range and the squad's flank angle are kept, long walks
 * are avoided. Higher is better.
 */
public final class CoverScore {
   public static final double PREFERRED_MIN = 9.0;
   public static final double PREFERRED_MAX = 20.0;

   private CoverScore() {
   }

   /**
    * @param bodyCovered ray from the spot's chest height to the target is blocked
    * @param eyeClear    ray from the spot's eye height to the target is clear
    * @param range       spot → target distance
    * @param walk        soldier → spot distance
    * @param angleOff    radians between the spot's bearing from the target and the wanted flank bearing
    */
   public static double score(boolean bodyCovered, boolean eyeClear, double range, double walk, double angleOff) {
      double s = 0.0;
      if (eyeClear) {
         s += 3.0;
         if (bodyCovered) {
            s += 4.0;
         }
      } else {
         s -= bodyCovered ? 1.0 : 3.0;
      }
      if (range < PREFERRED_MIN) {
         s -= (PREFERRED_MIN - range) * 0.6;
      } else if (range > PREFERRED_MAX) {
         s -= (range - PREFERRED_MAX) * 0.35;
      }
      s -= walk * 0.12;
      s -= Math.abs(angleOff) * 1.6;
      return s;
   }

   /** Flank bearing offset (radians) for the n-th member of a squad: 0, ±0.9, ±1.6, ... */
   public static double flankOffset(int member) {
      if (member <= 0) {
         return 0.0;
      }
      int k = (member + 1) / 2;
      double a = Math.min(2.4, 0.9 + (k - 1) * 0.7);
      return member % 2 == 1 ? a : -a;
   }
}
