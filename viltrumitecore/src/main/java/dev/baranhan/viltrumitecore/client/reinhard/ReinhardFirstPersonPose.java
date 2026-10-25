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
   // Sky Kick: the hand flicks up for the toss, then swings down and back for balance during the kick.
   static final float TOSS_Y = 0.18F;
   static final float TOSS_PITCH = -32.0F;
   static final float KICK_ARM_Y = -0.14F;
   static final float KICK_ARM_Z = 0.14F;
   static final float KICK_ARM_PITCH = 20.0F;
   // Grab & Throw: the hand reaches out and holds at chest height; the throw cocks it back
   // over the hit-stop and drives it forward and down past the crosshair.
   static final float GRAB_X = -0.04F;
   static final float GRAB_Y = 0.10F;
   static final float GRAB_Z = -0.30F;
   static final float GRAB_PITCH = -22.0F;
   static final float COCK_Y = 0.20F;
   static final float COCK_Z = 0.16F;
   static final float COCK_PITCH = -50.0F;
   static final float THROW_Y = -0.08F;
   static final float THROW_Z = -0.42F;
   static final float THROW_PITCH = 30.0F;
   // Dash Behind: the arm sweeps low and back on the vanish, then rises to guard as he lands turned.
   static final float BEHIND_Y = -0.16F;
   static final float BEHIND_Z = 0.10F;
   static final float BEHIND_PITCH = 24.0F;
   static final float BEHIND_ROLL = -10.0F;
   // Stomp: the arm swings up for balance as the leg rises, then jolts down with the slam.
   static final float STOMP_Y = -0.18F;
   static final float STOMP_PITCH = 18.0F;
   // Reflect: the back of the hand raised across the chest, then swatted out to the side.
   static final float GUARD_X = 0.10F;
   static final float GUARD_Y = 0.12F;
   static final float GUARD_Z = -0.10F;
   static final float GUARD_YAW = -28.0F;
   static final float GUARD_ROLL = 18.0F;
   static final float SWAT_X = -0.26F;
   static final float SWAT_YAW = 50.0F;
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
         case SKY_KICK -> kick(ticks);
         case GRAB_THROW -> grab(phase, ticks, length);
         case REFLECT -> backhand(phase, ticks, length);
         case STOMP -> new Offset(0.0F, STOMP_Y, 0.0F, STOMP_PITCH, 0.0F, 0.0F)
            .scaled(strike(ticks, ReinhardRules.STOMP_HIT_TICK, ReinhardRules.STOMP_TICKS));
         case DASH_BEHIND -> new Offset(0.0F, BEHIND_Y, BEHIND_Z, BEHIND_PITCH, 0.0F, BEHIND_ROLL)
            .scaled(strike(ticks, 2, ReinhardRules.BEHIND_ACTION_TICKS));
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

   private static Offset kick(double ticks) {
      int toss = ReinhardRules.KICK_TOSS_TICKS;
      if (ticks < toss) {
         return new Offset(0.0F, TOSS_Y, 0.0F, TOSS_PITCH, 0.0F, 0.0F).scaled(strike(ticks, toss / 2, toss));
      }

      float w = strike(ticks - toss, 1, ReinhardRules.KICK_ACTION_TICKS - toss);
      return new Offset(0.0F, KICK_ARM_Y, KICK_ARM_Z, KICK_ARM_PITCH, 0.0F, 0.0F).scaled(w);
   }

   private static Offset grab(int phase, double ticks, int length) {
      if (phase != ReinhardClips.PHASE_SNAP) {
         // The hold has no fade-out of its own: the throw (or a release) replaces it.
         return new Offset(GRAB_X, GRAB_Y, GRAB_Z, GRAB_PITCH, 0.0F, 0.0F).scaled((float) clamp01(ticks / CATCH_EASE));
      }

      int stop = ReinhardRules.HIT_STOP_TICKS;
      if (ticks < stop) {
         float w = (float) clamp01(ticks / 1.5);
         return new Offset(GRAB_X, COCK_Y, COCK_Z, COCK_PITCH, 0.0F, 0.0F).scaled(w);
      }

      float w = strike(ticks - stop + 0.5, 1, ReinhardRules.THROW_ACTION_TICKS - stop);
      return new Offset(GRAB_X, THROW_Y, THROW_Z, THROW_PITCH, 0.0F, 0.0F).scaled(w);
   }

   private static Offset backhand(int phase, double ticks, int length) {
      float fade = length > 0 ? (float) clamp01((length - ticks) / CATCH_EASE) : 1.0F;
      if (phase != ReinhardClips.PHASE_SNAP) {
         float w = (float) clamp01(ticks / CATCH_EASE) * fade;
         return new Offset(GUARD_X, GUARD_Y, GUARD_Z, 0.0F, GUARD_YAW, GUARD_ROLL).scaled(w);
      }

      // The swat peaks on the second tick, then the hand returns to the guard.
      float swat = strike(ticks, 2, 6);
      return new Offset(GUARD_X + (SWAT_X - GUARD_X) * swat, GUARD_Y, GUARD_Z, 0.0F, GUARD_YAW + (SWAT_YAW - GUARD_YAW) * swat, GUARD_ROLL * (1.0F - swat))
         .scaled(fade);
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
