package dev.baranhan.viltrumitecore.perf;

/**
 * Distance level of detail: 1 up to {@code near}, then linear down to the priority's
 * {@link PerfPriority#lodFloor} at {@code far} and beyond. CRITICAL is always 1. Pure.
 */
public final class DistanceLod {
   private DistanceLod() {
   }

   public static double factor(PerfPriority priority, double distance, double near, double far) {
      if (priority.critical() || distance <= near) {
         return 1.0;
      }

      if (far <= near || distance >= far) {
         return priority.lodFloor;
      }

      double t = (distance - near) / (far - near);
      return 1.0 - t * (1.0 - priority.lodFloor);
   }

   public static double factorSq(PerfPriority priority, double distanceSq, double near, double far) {
      if (priority.critical() || distanceSq <= near * near) {
         return 1.0;
      }

      return factor(priority, Math.sqrt(distanceSq), near, far);
   }
}
