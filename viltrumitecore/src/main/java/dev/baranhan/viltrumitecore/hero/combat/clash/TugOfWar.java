package dev.baranhan.viltrumitecore.hero.combat.clash;

/**
 * Pure beam-struggle state: the collision point on the line between two muzzles (0 = at A, 1 = at B).
 * The stronger side pushes the point towards the weaker one; reaching the weaker muzzle ends the struggle.
 */
public final class TugOfWar {
   public enum Result {
      RUNNING,
      A_WINS,
      B_WINS,
      DRAW
   }

   private double point;
   private int age;

   public TugOfWar(double start) {
      this.point = BeamMath.clamp(start, ClashRules.WIN_MARGIN, 1.0 - ClashRules.WIN_MARGIN);
   }

   /** Advances one tick with the current pressure of both sides. */
   public Result tick(double pressureA, double pressureB) {
      this.age++;
      if (this.age > ClashRules.GRACE_TICKS) {
         double total = Math.max(0.0, pressureA) + Math.max(0.0, pressureB);
         if (total > 1.0E-6) {
            this.point += ClashRules.POINT_SPEED * (Math.max(0.0, pressureA) - Math.max(0.0, pressureB)) / total;
         }
      }

      if (this.point >= 1.0 - ClashRules.WIN_MARGIN) {
         this.point = 1.0 - ClashRules.WIN_MARGIN;
         return Result.A_WINS;
      } else if (this.point <= ClashRules.WIN_MARGIN) {
         this.point = ClashRules.WIN_MARGIN;
         return Result.B_WINS;
      } else {
         return this.age >= ClashRules.MAX_TICKS ? Result.DRAW : Result.RUNNING;
      }
   }

   public double point() {
      return this.point;
   }

   public int age() {
      return this.age;
   }

   public boolean inGrace() {
      return this.age <= ClashRules.GRACE_TICKS;
   }

   /** -1 (A about to lose) .. 1 (B about to lose), for meters. */
   public double balance() {
      double half = 0.5 - ClashRules.WIN_MARGIN;
      return BeamMath.clamp((this.point - 0.5) / half, -1.0, 1.0);
   }
}
