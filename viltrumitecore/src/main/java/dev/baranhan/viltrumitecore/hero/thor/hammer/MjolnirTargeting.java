package dev.baranhan.viltrumitecore.hero.thor.hammer;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/**
 * Pure target choice of a thrown hammer (№33, §4): an enemy near the aim
 * direction at launch, then after each pierce the nearest one not yet hit by
 * the same throw. Nothing beyond 128 blocks of the throw origin; no manual
 * steering. The world wrapper (MjolnirEntity) collects visible enemies.
 */
public final class MjolnirTargeting {
   /** A visible enemy: entity id and the point aimed at. */
   public record Candidate(int id, Vec3 point) {
   }

   private MjolnirTargeting() {
   }

   public static boolean inRange(Vec3 origin, Vec3 point) {
      return origin.distanceToSqr(point) <= MjolnirTuning.THROW_RANGE * MjolnirTuning.THROW_RANGE;
   }

   /** Launch target: inside the aim cone and the range; the one closest to the aim line wins, then the nearest. */
   @Nullable
   public static Candidate aim(Vec3 origin, Vec3 look, List<Candidate> candidates) {
      if (look.lengthSqr() < 1.0E-8) {
         return null;
      }

      Vec3 aim = look.normalize();
      Candidate best = null;
      double bestCos = -2.0;
      double bestDistance = Double.MAX_VALUE;
      for (Candidate candidate : candidates) {
         Vec3 to = candidate.point().subtract(origin);
         double distance = to.length();
         if (distance < 1.0E-6 || !inRange(origin, candidate.point())) {
            continue;
         }

         double cos = to.scale(1.0 / distance).dot(aim);
         if (cos < MjolnirTuning.AIM_CONE_COS) {
            continue;
         }

         if (cos > bestCos + 1.0E-4 || Math.abs(cos - bestCos) <= 1.0E-4 && distance < bestDistance) {
            best = candidate;
            bestCos = cos;
            bestDistance = distance;
         }
      }

      return best;
   }

   /** After a pierce: the nearest enemy to the hammer not hit by this throw, near it and inside the range. */
   @Nullable
   public static Candidate next(Vec3 hammer, Vec3 origin, Collection<Integer> hit, List<Candidate> candidates) {
      Candidate best = null;
      double bestDistance = MjolnirTuning.NEXT_TARGET_RADIUS * MjolnirTuning.NEXT_TARGET_RADIUS;
      for (Candidate candidate : candidates) {
         if (hit.contains(candidate.id()) || !inRange(origin, candidate.point())) {
            continue;
         }

         double distance = hammer.distanceToSqr(candidate.point());
         if (distance <= bestDistance) {
            best = candidate;
            bestDistance = distance;
         }
      }

      return best;
   }
}
