package dev.baranhan.viltrumitecore.combat.feel;

/**
 * Trauma-based camera shake mixer (wave1-22). Pure math without Minecraft classes: the client
 * feeds it from packets and samples it every frame.
 *
 * <ul>
 *   <li>The visible shake is trauma squared, so small hits stay small and big ones punch.</li>
 *   <li>Repeated cues of one profile merge into one layer, so spam cannot stack into a blur.</li>
 *   <li>Every axis is soft-limited (tanh), so even all profiles at once stay readable.</li>
 * </ul>
 */
public final class ShakeMixer {
   public static final int MAX_LAYERS = 8;
   /** Soft limit of pitch, yaw and roll offsets, degrees. */
   public static final float LIMIT_DEG = 9.0F;
   /** Hard limit of the FOV kick, degrees. */
   public static final float LIMIT_FOV = 8.0F;
   private static final float MERGE = 0.35F;
   private static final float DROP = 0.001F;
   private static final double GOLDEN_ANGLE = 2.3999631;
   private static final double TWO_PI = Math.PI * 2.0;

   private final ShakeProfile[] profiles = new ShakeProfile[MAX_LAYERS];
   private final float[] trauma = new float[MAX_LAYERS];
   private final float[] phase = new float[MAX_LAYERS];
   private int count;
   private int seed;

   /** Adds trauma (0..1) of a profile. Null, NaN, infinite and non-positive amounts are ignored. */
   public void add(ShakeProfile profile, float amount) {
      if (profile == null || Float.isInfinite(amount) || !(amount > 0.0F)) {
         return;
      }
      float a = Math.min(1.0F, amount);
      for (int i = 0; i < this.count; i++) {
         if (this.profiles[i] == profile) {
            float old = this.trauma[i];
            this.trauma[i] = Math.min(1.0F, Math.max(old, a) + MERGE * Math.min(old, a));
            return;
         }
      }
      int slot;
      if (this.count < MAX_LAYERS) {
         slot = this.count++;
      } else {
         slot = 0;
         for (int i = 1; i < this.count; i++) {
            if (this.trauma[i] < this.trauma[slot]) {
               slot = i;
            }
         }
         if (this.trauma[slot] >= a) {
            return;
         }
      }
      this.profiles[slot] = profile;
      this.trauma[slot] = a;
      this.phase[slot] = (float)((this.seed++ * GOLDEN_ANGLE) % TWO_PI);
   }

   /** One game tick: every layer loses its profile decay; spent layers are dropped. */
   public void tick() {
      int w = 0;
      for (int i = 0; i < this.count; i++) {
         float t = this.trauma[i] - this.profiles[i].decay;
         if (t > DROP) {
            this.profiles[w] = this.profiles[i];
            this.trauma[w] = t;
            this.phase[w] = this.phase[i];
            w++;
         }
      }
      for (int i = w; i < this.count; i++) {
         this.profiles[i] = null;
      }
      this.count = w;
   }

   public void clear() {
      for (int i = 0; i < this.count; i++) {
         this.profiles[i] = null;
      }
      this.count = 0;
   }

   public int layers() {
      return this.count;
   }

   public float trauma(int layer) {
      return this.trauma[layer];
   }

   /** Writes {pitch, yaw, roll, fovKick} in degrees for {@code time} (ticks + partial) into out. */
   public void sample(float time, float[] out) {
      float pitch = 0.0F;
      float yaw = 0.0F;
      float roll = 0.0F;
      float fov = 0.0F;
      for (int i = 0; i < this.count; i++) {
         ShakeProfile p = this.profiles[i];
         float s = this.trauma[i] * this.trauma[i];
         float amp = s * p.amplitude;
         float x = time * p.frequency;
         float ph = this.phase[i];
         pitch += amp * p.pitch * noise(x, ph);
         yaw += amp * p.yaw * noise(x * 1.13F, ph + 2.1F);
         roll += amp * p.roll * noise(x * 0.87F, ph + 4.2F);
         fov += s * p.fovKick;
      }
      out[0] = soft(pitch, LIMIT_DEG);
      out[1] = soft(yaw, LIMIT_DEG);
      out[2] = soft(roll, LIMIT_DEG);
      out[3] = Math.min(LIMIT_FOV, fov);
   }

   /** Three detuned sines: smooth, cheap, never visibly periodic. Range -1..1. */
   static float noise(float x, float p) {
      return (float)(Math.sin(x * 2.9F + p) * 0.5 + Math.sin(x * 5.3F + p * 1.7F + 1.3F) * 0.3 + Math.sin(x * 8.9F + p * 0.6F + 2.7F) * 0.2);
   }

   static float soft(float v, float limit) {
      return (float)(limit * Math.tanh(v / limit));
   }
}
