package dev.baranhan.viltrumitecore.client.sinclair;

/**
 * Pure damped spring for cloth and ears (secondary motion). Integrated in fixed sub-steps of at most
 * 1/120 s so it behaves the same at any frame rate; a frame gap longer than {@link #MAX_DT} (pause,
 * unrendered entity) settles instead of exploding.
 */
public final class Spring {
   static final float STEP = 1.0F / 120.0F;
   static final float MAX_DT = 0.25F;
   public float value;
   public float velocity;
   private final float stiffness;
   private final float damping;
   private final float limit;

   /**
    * @param stiffness spring constant (1/s²)
    * @param damping   velocity damping (1/s)
    * @param limit     |value| bound (degrees)
    */
   public Spring(float stiffness, float damping, float limit) {
      this.stiffness = stiffness;
      this.damping = damping;
      this.limit = limit;
   }

   /** Advances towards {@code target} by {@code dt} seconds and returns the new value. */
   public float step(float target, float dt) {
      if (!Float.isFinite(dt) || dt <= 0.0F) {
         return this.value;
      }

      if (dt > MAX_DT) {
         this.value = clamp(target);
         this.velocity = 0.0F;
         return this.value;
      }

      float left = dt;
      while (left > 0.0F) {
         float h = Math.min(STEP, left);
         float accel = this.stiffness * (target - this.value) - this.damping * this.velocity;
         this.velocity += accel * h;
         this.value += this.velocity * h;
         left -= h;
      }

      if (Math.abs(this.value) > this.limit) {
         this.value = clamp(this.value);
         this.velocity *= -0.3F;
      }

      return this.value;
   }

   /** Adds an instantaneous velocity kick (degrees per second), e.g. a sharp gesture. */
   public void kick(float amount) {
      this.velocity += amount;
   }

   private float clamp(float v) {
      return Math.max(-this.limit, Math.min(this.limit, v));
   }
}
