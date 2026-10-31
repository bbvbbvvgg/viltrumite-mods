package dev.baranhan.viltrumitecore.hero.captain;

import net.minecraft.world.phys.Vec3;

/** Pure flight geometry of the thrown shield (soft homing). Unit-tested in CaptainRulesTest. */
public final class ShieldFlight {
   private ShieldFlight() {
   }

   /** Angle in degrees between two directions of any length (0 when one of them is zero). */
   public static double degrees(Vec3 a, Vec3 b) {
      double la = a.length();
      double lb = b.length();
      if (la < 1.0E-9 || lb < 1.0E-9) {
         return 0.0;
      }
      return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, a.dot(b) / (la * lb)))));
   }

   /** Unit direction {@code dir} turned toward {@code want} by at most {@code maxDegrees} (rotation in their plane). */
   public static Vec3 steer(Vec3 dir, Vec3 want, double maxDegrees) {
      if (want.lengthSqr() < 1.0E-12) {
         return dir.normalize();
      }
      if (dir.lengthSqr() < 1.0E-12) {
         return want.normalize();
      }
      Vec3 a = dir.normalize();
      Vec3 b = want.normalize();
      if (degrees(a, b) <= maxDegrees) {
         return b;
      }
      Vec3 perp = b.subtract(a.scale(a.dot(b)));
      if (perp.lengthSqr() < 1.0E-12) {
         // exactly behind: turn over any side
         perp = Math.abs(a.y) < 0.9 ? a.cross(new Vec3(0, 1, 0)) : a.cross(new Vec3(1, 0, 0));
      }
      double max = Math.toRadians(maxDegrees);
      return a.scale(Math.cos(max)).add(perp.normalize().scale(Math.sin(max))).normalize();
   }
}
