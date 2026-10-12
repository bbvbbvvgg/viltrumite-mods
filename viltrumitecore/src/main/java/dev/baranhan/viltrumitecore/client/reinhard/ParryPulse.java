package dev.baranhan.viltrumitecore.client.reinhard;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Real-time weight of the parry pose (skill §6 weight manager): eases toward 1
 * while the synced PARRY_POSE flag is set ({@link #K_IN}, a snap) and back to 0
 * after it ({@link #K_OUT}); the clip time restarts on every rise of the flag.
 * Third person and first person keep separate instances (each is called from its
 * own render path). Render thread only.
 */
public final class ParryPulse {
   public static final float K_IN = 40.0F;
   public static final float K_OUT = 10.0F;
   public static final double MAX_DT = 0.1;
   /** Clip time cap: the clip holds its last frame. */
   public static final double MAX_SECONDS = 2.0;
   /** Third-person model pose. */
   public static final ParryPulse THIRD_PERSON = new ParryPulse();
   /** First-person arm flick. */
   public static final ParryPulse FIRST_PERSON = new ParryPulse();
   private final Map<Object, State> states = new WeakHashMap<>();

   /** Advances one player's pulse to {@code nanoTime} and returns weight and clip seconds. */
   public Sample sample(Object player, boolean flag, long nanoTime) {
      State state = this.states.get(player);
      if (state == null) {
         state = new State(nanoTime);
         this.states.put(player, state);
      }

      double dt = Math.max(0.0, Math.min(MAX_DT, (nanoTime - state.lastNanos) / 1.0E9));
      state.lastNanos = nanoTime;
      if (flag && !state.flag) {
         state.seconds = 0.0;
      } else {
         state.seconds = Math.min(MAX_SECONDS, state.seconds + dt);
      }

      state.flag = flag;
      if (dt > 0.0) {
         float a = 1.0F - (float) Math.exp(-(flag ? K_IN : K_OUT) * dt);
         state.weight += ((flag ? 1.0F : 0.0F) - state.weight) * a;
      }

      if (!flag && state.weight < 0.001F) {
         state.weight = 0.0F;
      }

      return new Sample(state.weight, state.seconds);
   }

   public void reset(Object player) {
      this.states.remove(player);
   }

   public record Sample(float weight, double seconds) {
   }

   private static final class State {
      long lastNanos;
      boolean flag;
      float weight;
      double seconds = MAX_SECONDS;

      State(long nanos) {
         this.lastNanos = nanos;
      }
   }
}
