package dev.baranhan.viltrumitecore.client.anim.animation;

import java.util.function.DoubleUnaryOperator;

public final class KeyframeStack {
   private static final KeyframeStack EMPTY_ZERO = new KeyframeStack(
      new double[0], new float[0], new float[0], new DoubleUnaryOperator[0], new boolean[0], 0.0F
   );
   private static final KeyframeStack EMPTY_ONE = new KeyframeStack(new double[0], new float[0], new float[0], new DoubleUnaryOperator[0], new boolean[0], 1.0F);
   private final double[] times;
   private final float[] pre;
   private final float[] post;
   private final DoubleUnaryOperator[] easings;
   private final boolean[] smooth;
   private final float defaultValue;
   private final boolean[] monotone;

   public KeyframeStack(double[] times, float[] pre, float[] post, DoubleUnaryOperator[] easings, boolean[] smooth, float defaultValue) {
      this(times,pre,post,easings,smooth,new boolean[times.length],defaultValue);
   }

   public KeyframeStack(double[] times, float[] pre, float[] post, DoubleUnaryOperator[] easings,
      boolean[] smooth, boolean[] monotone, float defaultValue) {
      this.monotone = monotone;
      this.times = times;
      this.pre = pre;
      this.post = post;
      this.easings = easings;
      this.smooth = smooth;
      this.defaultValue = defaultValue;
   }

   public static KeyframeStack empty(float defaultValue) {
      if (defaultValue == 0.0F) {
         return EMPTY_ZERO;
      } else {
         return defaultValue == 1.0F
            ? EMPTY_ONE
            : new KeyframeStack(new double[0], new float[0], new float[0], new DoubleUnaryOperator[0], new boolean[0], defaultValue);
      }
   }

   public boolean isEmpty() {
      return this.times.length == 0;
   }

   public float sample(double time) {
      int n = this.times.length;
      if (n == 0) {
         return this.defaultValue;
      } else if (n == 1) {
         return this.post[0];
      } else if (time <= this.times[0]) {
         return this.pre[0];
      } else if (time >= this.times[n - 1]) {
         return this.post[n - 1];
      } else {
         int i = 0;
         int lo = 0;
         int hi = n - 1;

         while (lo <= hi) {
            int mid = lo + hi >>> 1;
            if (this.times[mid] <= time) {
               i = mid;
               lo = mid + 1;
            } else {
               hi = mid - 1;
            }
         }

         if (i >= n - 1) {
            return this.post[n - 1];
         } else {
            double t0 = this.times[i];
            double t1 = this.times[i + 1];
            double span = t1 - t0;
            double f = span <= 0.0 ? 0.0 : (time - t0) / span;
            float v0 = this.post[i];
            float v1 = this.pre[i + 1];
            if (this.monotone[i]) {
               return MonotoneCubic.sample(v0,v1,tangent(i),tangent(i+1),span,f);
            } else if (this.smooth[i]) {
               float vPrev = i > 0 ? this.post[i - 1] : v0;
               float vNext = i + 2 < n ? this.pre[i + 2] : v1;
               return catmullRom(vPrev, v0, v1, vNext, f);
            } else {
               DoubleUnaryOperator easing = this.easings[i];
               double eased = easing == null ? f : easing.applyAsDouble(f);
               return (float)((double)v0 + (double)(v1 - v0) * eased);
            }
         }
      }
   }

   private double tangent(int i) {
      if (i<=0 || i>=times.length-1 || pre[i]!=post[i] || !monotone[i-1] || !monotone[i]) return 0;
      double left=times[i]-times[i-1], right=times[i+1]-times[i];
      return MonotoneCubic.tangent(left,(pre[i]-post[i-1])/left,right,(pre[i+1]-post[i])/right);
   }

   private static float catmullRom(float p0, float p1, float p2, float p3, double t) {
      double t2 = t * t;
      double t3 = t2 * t;
      return (float)(
         0.5
            * (
               (double)(2.0F * p1)
                  + (double)(-p0 + p2) * t
                  + (double)(2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2
                  + (double)(-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3
            )
      );
   }
}
