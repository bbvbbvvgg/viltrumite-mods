package dev.baranhan.viltrumitecore.client.anim.animation;

/** Nonuniform, bounded C1 Hermite interpolation. Opt-in; legacy easings are untouched. */
public final class MonotoneCubic {
   private MonotoneCubic() {}
   public static double tangent(double leftSpan, double leftSlope, double rightSpan, double rightSlope) {
      if (leftSpan<=0 || rightSpan<=0 || leftSlope*rightSlope<=0) return 0;
      double w1=2*rightSpan+leftSpan, w2=rightSpan+2*leftSpan;
      return (w1+w2)/(w1/leftSlope+w2/rightSlope);
   }
   public static float sample(double a, double b, double slopeA, double slopeB, double span, double t) {
      double t2=t*t, t3=t2*t;
      return (float)((2*t3-3*t2+1)*a+(t3-2*t2+t)*span*slopeA
         +(-2*t3+3*t2)*b+(t3-t2)*span*slopeB);
   }
}
