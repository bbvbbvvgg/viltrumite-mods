package dev.baranhan.viltrumitecore.hero.combat.clash;

import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/** Pure geometry for beam struggles: where two beams meet and where the collision point sits. */
public final class BeamMath {
   private BeamMath() {
   }

   /**
    * Two beams meet.
    *
    * @param alongA distance from muzzle A to the meeting point, in blocks
    * @param alongB distance from muzzle B to the meeting point, in blocks
    * @param gap    shortest distance between the two beams there
    */
   public record Meeting(double alongA, double alongB, double gap, Vec3 point) {
   }

   /**
    * Where beam A ({@code originA + s * dirA}, {@code 0 <= s <= rangeA}) meets beam B, or {@code null} when
    * they are not head-on, do not reach each other, or pass too far apart.
    *
    * <p>The gap between a point walking along A and segment B is convex, so the stretch of A that touches B is one
    * interval; the beams meet in its middle. Closest approach alone is not enough: two almost anti-parallel beams
    * are closest at a muzzle even though they overlap for many blocks.
    */
   @Nullable
   public static Meeting meet(Vec3 originA, Vec3 dirA, double rangeA, Vec3 originB, Vec3 dirB, double rangeB) {
      if (dirA.lengthSqr() < 1.0E-8 || dirB.lengthSqr() < 1.0E-8 || rangeA <= 0.0 || rangeB <= 0.0) {
         return null;
      }

      Vec3 a = dirA.normalize();
      Vec3 b = dirB.normalize();
      if (a.dot(b) > ClashRules.HEAD_ON_DOT) {
         return null;
      }

      double step = Math.max(0.25, rangeA / 512.0);
      double first = Double.NaN;
      double last = Double.NaN;
      for (double s = 0.0; s <= rangeA + 1.0E-9; s += step) {
         if (touches(originA, a, s, originB, b, rangeB)) {
            if (Double.isNaN(first)) {
               first = s;
            }

            last = s;
         } else if (!Double.isNaN(first)) {
            break;
         }
      }

      if (Double.isNaN(first)) {
         return null;
      }

      double s = (first + last) * 0.5;
      Vec3 onA = originA.add(a.scale(s));
      double t = clamp(onA.subtract(originB).dot(b), 0.0, rangeB);
      Vec3 onB = originB.add(b.scale(t));
      return new Meeting(s, t, onA.distanceTo(onB), onA.add(onB).scale(0.5));
   }

   private static boolean touches(Vec3 originA, Vec3 a, double s, Vec3 originB, Vec3 b, double rangeB) {
      if (s < ClashRules.MIN_BEAM_LENGTH) {
         return false;
      }

      Vec3 onA = originA.add(a.scale(s));
      double t = clamp(onA.subtract(originB).dot(b), 0.0, rangeB);
      return t >= ClashRules.MIN_BEAM_LENGTH && onA.distanceToSqr(originB.add(b.scale(t))) <= ClashRules.CONTACT_RADIUS * ClashRules.CONTACT_RADIUS;
   }

   /** Fraction of the way from muzzle A to muzzle B where {@code point} sits, kept inside the playable band. */
   public static double startFraction(Vec3 originA, Vec3 originB, Vec3 point) {
      Vec3 axis = originB.subtract(originA);
      double lengthSqr = axis.lengthSqr();
      if (lengthSqr < 1.0E-8) {
         return 0.5;
      }

      double f = point.subtract(originA).dot(axis) / lengthSqr;
      double band = ClashRules.WIN_MARGIN + 0.08;
      return clamp(f, band, 1.0 - band);
   }

   /** The collision point in the world for fraction {@code f} along A to B. */
   public static Vec3 at(Vec3 originA, Vec3 originB, double f) {
      return originA.add(originB.subtract(originA).scale(f));
   }

   static double clamp(double value, double min, double max) {
      return value < min ? min : Math.min(value, max);
   }
}
