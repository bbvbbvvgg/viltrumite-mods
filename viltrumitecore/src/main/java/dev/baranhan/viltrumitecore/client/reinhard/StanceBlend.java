package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-player stance weights for Reinhard's three modes (skill §6 weight manager):
 * ease 1 − exp(−k·dt) toward the target mode, wall-clock dt clamped to 0.1 s.
 * Exponential easing depends only on elapsed time, so extra calls in one frame
 * (shadow pass, inventory preview) do not speed it up. Render thread only.
 */
public final class StanceBlend {
   public static final float K = 12.0F;
   public static final double MAX_DT = 0.1;
   /** Above this target weight the renderer poses the target stance alone. */
   public static final float SETTLED = 0.999F;
   private static final ReinhardMode[] MODES = ReinhardMode.values();
   private static final Map<Object, State> STATES = new WeakHashMap<>();

   private StanceBlend() {
   }

   /** Advances and returns the weights by mode ordinal. A new (or reset) player starts on the target. */
   public static float[] weights(Object player, ReinhardMode target, long nanoTime) {
      State state = STATES.get(player);
      if (state == null) {
         state = new State(nanoTime);
         state.weights[target.ordinal()] = 1.0F;
         STATES.put(player, state);
         return state.weights;
      }

      double dt = Math.max(0.0, Math.min(MAX_DT, (nanoTime - state.lastNanos) / 1.0E9));
      state.lastNanos = nanoTime;
      if (dt > 0.0) {
         float a = 1.0F - (float) Math.exp(-K * dt);
         for (int i = 0; i < state.weights.length; i++) {
            float goal = i == target.ordinal() ? 1.0F : 0.0F;
            state.weights[i] += (goal - state.weights[i]) * a;
         }
      }

      return state.weights;
   }

   /** Forget a player (no longer Reinhard): the next call starts on its target stance. */
   public static void reset(Object player) {
      STATES.remove(player);
   }

   /** The two stances to blend: the strongest other mode → target, weight = target share of the pair. */
   public static Mix mix(float[] weights, ReinhardMode target) {
      float to = weights[target.ordinal()];
      if (to >= SETTLED) {
         return new Mix(target, target, 1.0F, weights.clone());
      }

      ReinhardMode from = target;
      float best = -1.0F;
      for (ReinhardMode mode : MODES) {
         if (mode != target && weights[mode.ordinal()] > best) {
            best = weights[mode.ordinal()];
            from = mode;
         }
      }

      float sum = to + best;
      return new Mix(from, target, sum <= 0.0F ? 1.0F : to / sum, weights.clone());
   }

   public record Mix(ReinhardMode from, ReinhardMode to, float weight, float[] weights) {
      public Mix(ReinhardMode from, ReinhardMode to, float weight) {this(from,to,weight,null);}
   }

   private static final class State {
      final float[] weights = new float[MODES.length];
      long lastNanos;

      State(long nanos) {
         this.lastNanos = nanos;
      }
   }
}
