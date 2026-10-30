package dev.baranhan.viltrumitecore.client.reinhard.erasure;

import static dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules.*;

/**
 * Pure, deterministic camera shake of World Erasure on the scene clock (no game time, no state):
 * a jolt on the rupture, a rumble growing while reality pulls back together, an impulse per shard
 * ring snapping home and the hardest one on the last ring (the final impact), decaying to exactly
 * zero at the end of the scene. Applied only to the rendered camera angles and the glass plane: the
 * player's real yaw/pitch never change, nothing accumulates, the view returns to the locked direction.
 */
public final class ErasureShake {
   private ErasureShake() {}
   /** Rendered-camera offsets (degrees) and glass-plane offset (plane units, 1 = half the screen height). */
   public record Offset(float pitch, float yaw, float roll, float dx, float dy) {
      public static final Offset ZERO = new Offset(0, 0, 0, 0, 0);
   }
   /** Degrees at power 1 (the last snap at shake setting 1). */
   static final float PITCH = 3.4F, YAW = 2.6F, ROLL = 5.5F, PLANE = .035F;
   /** Ticks the final impact takes to settle: ends exactly with the scene. */
   static final float FINAL_DECAY = ERASE_LENGTH - ERASE_REBUILD_END;

   /** Shake power 0..1 at scene time t. */
   public static float power(float t) {
      if (!(t >= ERASE_SHATTER_START) || t >= ERASE_LENGTH) return 0;
      float p = .5F * impulse(t - ERASE_SHATTER_START, 10);
      if (t >= ERASE_DARK_END && t < ERASE_REBUILD_END) {
         float u = (t - ERASE_DARK_END) / (ERASE_REBUILD_END - ERASE_DARK_END);
         p = Math.max(p, .18F + .3F * u * u);
      }
      float[] snaps = ErasureGlass.SNAPS;
      for (int i = 0; i < snaps.length; i++) {
         boolean last = i == snaps.length - 1;
         p = Math.max(p, (last ? 1 : .42F + .08F * i) * impulse(t - snaps[i], last ? FINAL_DECAY : 7));
      }
      return Math.min(1, p);
   }

   /** The shake at t scaled by the player's setting (0..2, non-finite = 1). */
   public static Offset at(float t, float setting) {
      float scale = Float.isFinite(setting) ? Math.max(0, Math.min(2, setting)) : 1;
      float a = power(t) * scale;
      if (a <= 0) return Offset.ZERO;
      // Detuned sines on the scene clock: violent, never periodic-looking, same on every client.
      float pitch = a * PITCH * (.6F * (float)Math.sin(t * 3.1F) + .4F * (float)Math.sin(t * 5.7F + 1.3F));
      float yaw = a * YAW * (.6F * (float)Math.sin(t * 3.9F + .7F) + .4F * (float)Math.sin(t * 6.3F + 2.1F));
      float roll = a * ROLL * (.7F * (float)Math.sin(t * 4.4F + .4F) + .3F * (float)Math.sin(t * 7.9F + 2.9F));
      float dx = a * PLANE * (float)Math.sin(t * 5.1F + 1.9F), dy = a * PLANE * (float)Math.sin(t * 4.7F + .3F);
      return new Offset(pitch, yaw, roll, dx, dy);
   }

   static float impulse(float u, float length) {
      if (u < 0 || u >= length) return 0;
      float k = 1 - u / length;
      return k * k;
   }
}
