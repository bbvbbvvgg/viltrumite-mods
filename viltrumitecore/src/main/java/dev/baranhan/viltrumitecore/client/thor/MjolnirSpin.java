package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumiteflight.util.FlightState;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Player;

/**
 * FX id {@code thor:hammer_spin} (spec §5): Mjölnir in the hand whirls like
 * helicopter blades at 5–8 rev/s while Thor flies, with motion-blur ghost
 * copies. Only the hammer render turns (the MjolnirBody bone about the fist,
 * ThorParts); the arm pose stays with the posers (R1/R3).
 *
 * <p>The rate model is pure (tests); {@link #angle} keeps one accumulator per
 * player keyed by render time, so drawing the same frame twice (third person,
 * first person, shadow pass) never advances the spin twice.
 */
public final class MjolnirSpin {
   /** Revolutions per second per flight state, hammer in hand. */
   public static final float HOVER_RPS = 6.5F;
   public static final float CRUISE_RPS = 7.0F;
   public static final float SONIC_RPS = 8.0F;
   /** Spin-up / spin-down time from rest to full rate and back, ticks (heavy, not instant). */
   public static final float SPIN_UP_TICKS = 14.0F;
   public static final float SPIN_DOWN_TICKS = 18.0F;
   /** Ghost copies behind the hammer and the arc of the trail they cover at full rate (radians). */
   public static final int GHOSTS = 6;
   public static final float MAX_TRAIL = (float)Math.toRadians(150.0);
   /** Seconds of motion one ghost trail shows: ω · TRAIL_SECONDS radians, capped by MAX_TRAIL. */
   public static final float TRAIL_SECONDS = 0.05F;
   /** Grip offset from the MjolnirBody pivot along the handle, geo pixels (mjolnir pivot y 13 − body pivot y 10.75). */
   public static final float GRIP_OFFSET = 2.25F;
   private static final Map<Player, State> STATES = new WeakHashMap<>();

   private MjolnirSpin() {
   }

   /** Target rate in rev/s: only a hammer in the hand of a flying Thor spins. */
   public static float targetRps(@Nullable FlightState flight, boolean inHand) {
      if (!inHand || flight == null) {
         return 0.0F;
      }

      return switch (flight) {
         case HOVER -> HOVER_RPS;
         case CRUISE -> CRUISE_RPS;
         case SONIC -> SONIC_RPS;
         default -> 0.0F;
      };
   }

   /** Rate after {@code dt} ticks of ramping towards {@code target} (linear, spin-up faster than spin-down). */
   public static float approach(float rps, float target, float dt) {
      if (dt <= 0.0F) {
         return rps;
      }

      if (rps < target) {
         return Math.min(target, rps + SONIC_RPS / SPIN_UP_TICKS * dt);
      }

      return Math.max(target, rps - SONIC_RPS / SPIN_DOWN_TICKS * dt);
   }

   /** Radians turned in {@code dt} ticks at {@code rps}. */
   public static float advance(float rps, float dt) {
      return (float)(rps * Math.PI * 2.0 * dt / 20.0);
   }

   /** Trail arc (radians) of the blur at {@code rps}. */
   public static float trail(float rps) {
      return Math.min(MAX_TRAIL, (float)(rps * Math.PI * 2.0 * TRAIL_SECONDS));
   }

   /** Angle of ghost {@code index} (1..GHOSTS) behind the hammer at {@code angle}. */
   public static float ghostAngle(float angle, float rps, int index) {
      return angle - trail(rps) * index / GHOSTS;
   }

   /** Ghost opacity: fading copies, only once the spin is fast enough to blur (≥ 2 rev/s). */
   public static float ghostAlpha(float rps, int index) {
      float blur = Math.max(0.0F, Math.min(1.0F, (rps - 2.0F) / 3.0F));
      return 0.42F * blur * (1.0F - (index - 1.0F) / GHOSTS);
   }

   /**
    * MjolnirBody turns about its local X axis (the arm axis once the grip
    * bone is applied) through the grip: rotation plus the pivot shift
    * {@code d = g − R·g}, g = (0, GRIP_OFFSET, 0). Returns {rotX, posX, posY, posZ}.
    */
   public static float[] bodyPose(float angle) {
      float cos = (float)Math.cos(angle);
      float sin = (float)Math.sin(angle);
      return new float[]{angle, 0.0F, GRIP_OFFSET * (1.0F - cos), -GRIP_OFFSET * sin};
   }

   /** Spin state of one player at render time {@code time} (ticks + partial). Render thread. */
   public static State update(Player player, double time, float target) {
      State state = STATES.computeIfAbsent(player, p -> new State(time));
      state.step(time, target);
      return state;
   }

   @Nullable
   public static State get(Player player) {
      return STATES.get(player);
   }

   /** Mutable accumulator: same time = same angle (idempotent per frame). */
   public static final class State {
      private double time;
      public float rps;
      public float angle;

      public State(double time) {
         this.time = time;
      }

      public void step(double now, float target) {
         float dt = (float)(now - this.time);
         if (dt < 0.0F || dt > 40.0F) {
            // World reload / time jump: no giant turn, keep the rate.
            this.time = now;
            return;
         }

         if (dt == 0.0F) {
            return;
         }

         float before = this.rps;
         this.rps = approach(this.rps, target, dt);
         this.angle = (float)((this.angle + advance((before + this.rps) * 0.5F, dt)) % (Math.PI * 2.0));
         this.time = now;
      }

      public boolean spinning() {
         return this.rps > 0.05F;
      }
   }
}
