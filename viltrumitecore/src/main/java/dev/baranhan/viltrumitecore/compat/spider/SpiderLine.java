package dev.baranhan.viltrumitecore.compat.spider;

import net.minecraft.world.phys.Vec3;

/** A swinging Spider's web line: from the hand to the anchor, with its rest length (blocks). Our own type. */
public record SpiderLine(Vec3 hand, Vec3 anchor, float rope) {
   /** Pure: squared distance from a point to the line segment. */
   public double distanceSqr(Vec3 p) {
      Vec3 d = this.anchor.subtract(this.hand);
      double len = d.lengthSqr();
      double t = len < 1.0E-9 ? 0.0 : Math.max(0.0, Math.min(1.0, p.subtract(this.hand).dot(d) / len));
      return this.hand.add(d.scale(t)).distanceToSqr(p);
   }

   /** Pure: the point at fraction t (0 hand .. 1 anchor). */
   public Vec3 at(double t) {
      return this.hand.add(this.anchor.subtract(this.hand).scale(Math.max(0.0, Math.min(1.0, t))));
   }

   /** Pure: fraction of the closest point on the line to p. */
   public double project(Vec3 p) {
      Vec3 d = this.anchor.subtract(this.hand);
      double len = d.lengthSqr();
      return len < 1.0E-9 ? 0.0 : Math.max(0.0, Math.min(1.0, p.subtract(this.hand).dot(d) / len));
   }

   /**
    * Pure: smallest squared distance between this line and the segment a..b (a claw arc chord or a tendril). Sampled
    * (17 points on each) — exact enough for a 0.6-block capsule.
    */
   public double distanceSqr(Vec3 a, Vec3 b) {
      double best = Double.MAX_VALUE;
      for (int i = 0; i <= 16; i++) {
         Vec3 p = a.add(b.subtract(a).scale(i / 16.0));
         best = Math.min(best, this.distanceSqr(p));
      }
      return best;
   }
}
