package dev.baranhan.viltrumitecore.combat.feel;

/**
 * Camera shake profiles of the cinematic combat (wave1-22). Amplitude is in degrees at full
 * trauma, frequency scales the noise speed, decay is trauma lost per tick, pitch/yaw/roll weight
 * the axes and fovKick widens the view (degrees) at full trauma.
 *
 * <p>The ordinal is the wire id of FeelS2CPacket: append new profiles at the end only.
 */
public enum ShakeProfile {
   LIGHT_HIT(1.1F, 1.15F, 0.16F, 1.0F, 0.7F, 0.35F, 0.0F),
   HEAVY_HIT(2.4F, 0.95F, 0.085F, 1.0F, 0.8F, 0.55F, 1.2F),
   SLAM(3.2F, 0.7F, 0.055F, 1.25F, 0.45F, 0.5F, 2.5F),
   EXPLOSION(3.8F, 0.85F, 0.04F, 1.0F, 1.0F, 0.75F, 3.0F),
   RUMBLE(0.9F, 1.6F, 0.018F, 0.6F, 0.6F, 0.2F, 0.0F),
   ULTIMATE(4.6F, 0.65F, 0.032F, 1.0F, 0.9F, 1.0F, 5.0F),
   FINISHER(3.0F, 0.55F, 0.06F, 1.1F, 0.6F, 0.9F, 4.0F);

   private static final ShakeProfile[] VALUES = values();

   public final float amplitude;
   public final float frequency;
   public final float decay;
   public final float pitch;
   public final float yaw;
   public final float roll;
   public final float fovKick;

   ShakeProfile(float amplitude, float frequency, float decay, float pitch, float yaw, float roll, float fovKick) {
      this.amplitude = amplitude;
      this.frequency = frequency;
      this.decay = decay;
      this.pitch = pitch;
      this.yaw = yaw;
      this.roll = roll;
      this.fovKick = fovKick;
   }

   /** Wire id to profile; unknown ids fall back to {@link #LIGHT_HIT}. */
   public static ShakeProfile byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : LIGHT_HIT;
   }
}
