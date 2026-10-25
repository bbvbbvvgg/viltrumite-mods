package dev.baranhan.viltrumitecore.client.anim.pose;

import dev.baranhan.viltrumitecore.client.anim.animation.MonotoneCubic;

/**
 * Keyframe math shared by every hero pose layer (first used by Regulus,
 * animation-system §3.5). Rows are {@code {tick, pitchDeg, yawDeg, rollDeg,
 * x, y, z}}. A row built with {@link #under(float)} means "the pose
 * underneath" (vanilla locomotion plus earlier layers), so every timeline
 * enters from and returns to whatever the body was already doing.
 */
public final class PoseKeys {
   /** Strike keys overshoot their settle pose, same as the Viltrumite punch S2 key. */
   public static final float OVERSHOOT = 1.15F;

   private PoseKeys() {
   }

   public static float[] key(float tick, float pitch, float yaw, float roll, float x, float y, float z) {
      return new float[]{tick, pitch, yaw, roll, x, y, z};
   }

   public static float[] key(float tick, float pitch, float yaw, float roll) {
      return key(tick, pitch, yaw, roll, 0.0F, 0.0F, 0.0F);
   }

   /** Strike key: the settle values pushed {@link #OVERSHOOT} past the target. */
   public static float[] strike(float tick, float pitch, float yaw, float roll, float x, float y, float z) {
      return key(tick, pitch * OVERSHOOT, yaw * OVERSHOOT, roll * OVERSHOOT, x * OVERSHOOT, y * OVERSHOOT, z * OVERSHOOT);
   }

   /** "The pose underneath" row: enter from / return to locomotion. */
   public static float[] under(float tick) {
      return new float[]{tick, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN};
   }

   public static boolean isUnder(float[] row) {
      return Float.isNaN(row[1]);
   }

   /**
    * Fractional key index for {@code elapsed}: the integer part is the segment
    * start row, the fraction is the smoothstep-eased progress inside it.
    * Clamps to the first/last row outside the timeline.
    */
   public static float locate(float[][] keys, float elapsed) {
      int last = keys.length - 1;
      if (elapsed <= keys[0][0]) {
         return 0.0F;
      }
      for (int i = 0; i < last; i++) {
         float from = keys[i][0];
         float to = keys[i + 1][0];
         if (elapsed < to) {
            float t = clamp01((elapsed - from) / Math.max(1.0E-4F, to - from));
            return (float)i + t * t * (3.0F - 2.0F * t);
         }
      }
      return (float)last;
   }

   /**
    * Samples channel {@code channel} (0..5) of a timeline. {@code base} is the
    * value underneath; additive rows add to it, absolute rows replace it.
    */
   public static float sample(float[][] keys, float elapsed, int channel, float base, boolean additive) {
      float at = locate(keys, elapsed);
      int i = (int)at;
      float t = at - (float)i;
      float a = resolve(keys[i], channel, base, additive);
      if (t <= 0.0F || i + 1 >= keys.length) {
         return a;
      }
      float b = resolve(keys[i + 1], channel, base, additive);
      return a + (b - a) * t;
   }

   /** Opt-in bounded cubic: preserves velocity through non-extremum keys. */
   public static float sampleContinuous(float[][] keys, float elapsed, int channel, float base, boolean additive) {
      int last=keys.length-1;
      if(elapsed<=keys[0][0]) return resolve(keys[0],channel,base,additive);
      if(elapsed>=keys[last][0]) return resolve(keys[last],channel,base,additive);
      int i=0;
      while(i+1<last && elapsed>=keys[i+1][0]) i++;
      double span=keys[i+1][0]-keys[i][0];
      return MonotoneCubic.sample(
         resolve(keys[i],channel,base,additive),resolve(keys[i+1],channel,base,additive),
         tangent(keys,i,channel,base,additive),tangent(keys,i+1,channel,base,additive),span,
         (elapsed-keys[i][0])/span);
   }

   private static double tangent(float[][] keys,int i,int channel,float base,boolean additive) {
      if(i<=0 || i>=keys.length-1) return 0;
      double left=keys[i][0]-keys[i-1][0], right=keys[i+1][0]-keys[i][0];
      double a=resolve(keys[i-1],channel,base,additive),b=resolve(keys[i],channel,base,additive),c=resolve(keys[i+1],channel,base,additive);
      return MonotoneCubic.tangent(left,(b-a)/left,right,(c-b)/right);
   }

   public static float resolve(float[] row, int channel, float base, boolean additive) {
      if (isUnder(row)) {
         return base;
      }
      float value = row[channel + 1];
      return additive ? base + value : value;
   }

   public static float clamp01(float value) {
      return value < 0.0F ? 0.0F : Math.min(1.0F, value);
   }
}
