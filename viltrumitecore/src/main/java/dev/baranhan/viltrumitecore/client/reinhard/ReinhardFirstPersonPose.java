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
   // Step Dash: the arm drops and swings back with the lean.
   static final float DASH_Y = -0.10F;
   static final float DASH_Z = 0.12F;
   static final float DASH_PITCH = 14.0F;
   // Catch: the palm up in front of the face, then a short closing jerk on the catch.
   static final float CATCH_X = -0.06F;
   static final float CATCH_Y = 0.16F;
   static final float CATCH_Z = -0.12F;
   static final float CATCH_PITCH = -38.0F;
   static final float CATCH_ROLL = -8.0F;
   static final float SNAP_Z = -0.12F;
   static final float SNAP_PITCH = 10.0F;
   static final float SNAP_ROLL = 12.0F;
   // Fist Combo: 1 = straight, 2 = hook from the outside, 3 = raised then hammered down.
   static final float HOOK_X = -0.22F;
   static final float HOOK_YAW = 30.0F;
   static final float HAMMER_RAISE_Y = 0.22F;
   static final float HAMMER_Y = -0.12F;
   static final float HAMMER_Z = -0.30F;
   static final float HAMMER_PITCH = 25.0F;
   /** Ticks to raise / lower the hand. */
   static final double CATCH_EASE = 2.0;

   private ReinhardFirstPersonPose() {
   }

   public record Offset(float x, float y, float z, float pitch, float yaw, float roll) {
      public static final Offset NONE = new Offset(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

      Offset scaled(float w) {
         return new Offset(this.x * w, this.y * w, this.z * w, this.pitch * w, this.yaw * w, this.roll * w);
      }
   }

   public static Offset of(@Nullable ReinhardAbility ability, int phase, double ticks) {
      return of(ability, phase, ticks, -1);
   }

   /** {@code length} = the synced action length (≤ 0 unknown: no fade-out at the end). */
   public static Offset of(@Nullable ReinhardAbility ability, int phase, double ticks, int length) {
      if (ability == null) {
         return Offset.NONE;
      }

      return switch (ability) {
         case LIGHT_PUNCH -> new Offset(JAB_X, JAB_Y, JAB_Z, JAB_PITCH, 0.0F, 0.0F)
            .scaled(strike(ticks, ReinhardRules.LIGHT_PUNCH_HIT_TICK, ReinhardRules.LIGHT_PUNCH_TICKS));
         case STEP_DASH -> new Offset(0.0F, DASH_Y, DASH_Z, DASH_PITCH, 0.0F, 0.0F)
            .scaled(strike(ticks, ReinhardRules.STEP_TICKS / 2, ReinhardRules.STEP_ACTION_TICKS));
         case CATCH -> catchHand(phase, ticks, length);
         case FIST_COMBO -> combo(phase, ticks);
         default -> Offset.NONE;
      };
   }

   private static Offset combo(int phase, double ticks) {
      int step = Math.max(0, Math.min(phase, ReinhardRules.COMBO_HIT_TICKS.length - 1));
      float w = strike(ticks, ReinhardRules.COMBO_HIT_TICKS[step], ReinhardRules.COMBO_STEP_TICKS[step]);
      return switch (step) {
         case 0 -> new Offset(JAB_X, JAB_Y, JAB_Z, JAB_PITCH, 0.0F, 0.0F).scaled(w);
         case 1 -> new Offset(HOOK_X, JAB_Y, JAB_Z * 0.8F, JAB_PITCH, HOOK_YAW, 0.0F).scaled(w);
         default -> {
            // Up first (wind-up over the first half of the run to the contact), then down and forward.
            int contact = ReinhardRules.COMBO_HIT_TICKS[step];
            float raise = ticks < contact ? (float) Math.sin(Math.PI * ticks / contact) : 0.0F;
            yield new Offset(0.0F, HAMMER_RAISE_Y * raise + HAMMER_Y * w, HAMMER_Z * w, -40.0F * raise + HAMMER_PITCH * w, 0.0F, 0.0F);
         }
      };
   }

   private static Offset catchHand(int phase, double ticks, int length) {
      float fade = length > 0 ? (float) clamp01((length - ticks) / CATCH_EASE) : 1.0F;
      if (phase != ReinhardClips.PHASE_SNAP) {
         float w = (float) clamp01(ticks / CATCH_EASE) * fade;
         return new Offset(CATCH_X, CATCH_Y, CATCH_Z, CATCH_PITCH, 0.0F, CATCH_ROLL).scaled(w);
      }

      // Held up the whole snap; the jerk peaks on the first tick and settles by the fourth.
      float jerk = strike(ticks, 1, 4);
      return new Offset(CATCH_X, CATCH_Y, CATCH_Z + SNAP_Z * jerk, CATCH_PITCH + SNAP_PITCH * jerk, 0.0F, CATCH_ROLL + SNAP_ROLL * jerk).scaled(fade);
   }

   private static double clamp01(double v) {
      return v < 0.0 ? 0.0 : Math.min(1.0, v);
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
