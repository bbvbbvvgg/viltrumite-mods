package dev.baranhan.viltrumitecore.hero.thor.scene;

/**
 * Pure math of the scene pose cross-fade (client ScenePoseFade): rows of
 * {@link #STRIDE} values per model part (x/y/z rotation in radians, then
 * x/y/z pivot), angles blended the short way round.
 */
public final class PoseBlend {
   public static final int STRIDE = 6;

   private PoseBlend() {
   }

   /** Smoothstep weight of the new pose after {@code elapsed} of {@code length} ticks (1 = done). */
   public static float weight(float elapsed, float length) {
      if (length <= 0.0F || elapsed >= length) {
         return 1.0F;
      }

      float s = Math.max(0.0F, elapsed / length);
      return s * s * (3.0F - 2.0F * s);
   }

   public static float[] blend(float[] from, float[] to, float w) {
      float[] out = new float[to.length];
      for (int i = 0; i < to.length; i++) {
         float a = i < from.length ? from[i] : to[i];
         if (i % STRIDE < 3) {
            float d = (float)Math.IEEEremainder(to[i] - a, Math.PI * 2.0);
            out[i] = a + d * w;
         } else {
            out[i] = a + (to[i] - a) * w;
         }
      }
      return out;
   }
}
