package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import javax.annotation.Nullable;

/**
 * First-person pose of Reinhard's main arm during an action (stage 3): an offset
 * on the {@code RenderArmEvent} pose stack (blocks, x mirrored for a left main
 * arm, −z = away from the camera) plus extra arm rotation in degrees. Pure: the
 * synced ability, phase and elapsed ticks (with partial tick) in, the offset out.
 */
public final class ReinhardFirstPersonPose {
   // Light Punch: a straight jab toward the crosshair.
   static final float JAB_X = -0.10F;
   static final float JAB_Y = 0.06F;
   static final float JAB_Z = -0.38F;
   static final float JAB_PITCH = -10.0F;

   private ReinhardFirstPersonPose() {
   }

   public record Offset(float x, float y, float z, float pitch, float yaw, float roll) {
      public static final Offset NONE = new Offset(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

      Offset scaled(float w) {
         return new Offset(this.x * w, this.y * w, this.z * w, this.pitch * w, this.yaw * w, this.roll * w);
      }
   }

   public static Offset of(@Nullable ReinhardAbility ability, int phase, double ticks) {
      if (ability == null) {
         return Offset.NONE;
      }

      return switch (ability) {
         case LIGHT_PUNCH -> new Offset(JAB_X, JAB_Y, JAB_Z, JAB_PITCH, 0.0F, 0.0F)
            .scaled(strike(ticks, ReinhardRules.LIGHT_PUNCH_HIT_TICK, ReinhardRules.LIGHT_PUNCH_TICKS));
         default -> Offset.NONE;
      };
   }

   /** 0 → 1 (eased) by the contact tick, back to 0 by the end of the action. */
   static float strike(double ticks, int contact, int length) {
      if (ticks <= 0.0 || ticks >= length) {
         return 0.0F;
      }

      if (ticks <= contact) {
         double t = ticks / contact;
         return (float) (t * t * (3.0 - 2.0 * t));
      }

      return (float) (1.0 - (ticks - contact) / (length - contact));
   }
}
