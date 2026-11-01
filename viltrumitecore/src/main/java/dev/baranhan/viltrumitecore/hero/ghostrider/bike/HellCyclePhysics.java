package dev.baranhan.viltrumitecore.hero.ghostrider.bike;

import static dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleRules.*;

/**
 * The Hell Cycle's riding model: one tick of throttle, brake, steering, lean, drift, slope and jump dynamics on a
 * plain state object. No world access: the entity measures the ground (on ground, terrain pitch), runs
 * {@link #step}, resolves the proposed motion against blocks and reports walls back through {@link #hitWall}.
 *
 * <p>Conventions: Minecraft yaw in degrees (0 faces +Z, turning right increases it); heading
 * {@code h = (-sin yaw, cos yaw)}, left {@code l = (cos yaw, sin yaw)}; steer and lean are positive to the left.
 * Velocity is world space (blocks per tick). The tyres pull the direction of travel onto the heading by a grip
 * fraction of the slip angle per tick and keep the speed (drift scrubs a little), so turns keep momentum and a
 * drift lets the heading swing past the direction of travel.
 */
public final class HellCyclePhysics {
   /** One-tick events (bits of {@link State#events}) for sound and FX. */
   public static final int EV_JUMP = 1;
   public static final int EV_LAND = 1 << 1;
   public static final int EV_CRASH = 1 << 2;
   public static final int EV_DRIFT_START = 1 << 3;
   public static final int EV_DRIFT_BOOST = 1 << 4;
   public static final int EV_BACKFIRE = 1 << 5;
   public static final int EV_SHIFT = 1 << 6;
   public static final int EV_STOPPIE = 1 << 7;

   private HellCyclePhysics() {
   }

   /** Rider input for one tick. Throttle and steer are -1..1 (W = +1, A = +1). */
   public record Input(double throttle, double steer, boolean jump, boolean drift, boolean boost) {
      public static final Input NONE = new Input(0.0, 0.0, false, false, false);
      /** No rider: brake to a stop (a parked bike never rolls into a crowd). */
      public static final Input PARK = new Input(-1.0, 0.0, false, false, false);
   }

   public static final class State {
      public double yaw;
      public double velX;
      public double velY;
      public double velZ;
      /** Smoothed handlebar, -1..1 (positive left). */
      public double steer;
      /** Smoothed lean, degrees (positive left). */
      public double lean;
      /** Terrain pitch under the wheels, degrees (positive nose up); written by the entity. */
      public double pitch;
      public boolean onGround = true;
      public boolean drifting;
      public int driftTicks;
      public boolean skidding;
      public boolean braking;
      /** Forward speed when the current brake started (stoppie threshold). */
      public double brakeFrom;
      /** Visual rear-wheel lift (stoppie) and front-wheel lift (wheelie), 0-1. */
      public double stoppie;
      public double wheelie;
      public int wheelieTicks;
      public boolean jumpHeld;
      public boolean boostHeld;
      public int airTicks;
      public int crashTicks;
      public double rpm = IDLE_RPM;
      public int gear = 1;
      public double lastThrottle;
      public int backfireCooldown;
      /** Yaw change applied by the last step (deg). */
      public double yawDelta;
      public int events;

      public double speed() {
         return Math.sqrt(this.velX * this.velX + this.velZ * this.velZ);
      }

      /** Speed along the heading (negative when reversing). */
      public double forward() {
         double r = Math.toRadians(this.yaw);
         return -Math.sin(r) * this.velX + Math.cos(r) * this.velZ;
      }

      /** Signed angle (deg) between the heading (or the tail when reversing) and the direction of travel. */
      public double slip() {
         double r = Math.toRadians(this.yaw);
         double hx = -Math.sin(r);
         double hz = Math.cos(r);
         double f = hx * this.velX + hz * this.velZ;
         double s = Math.cos(r) * this.velX + Math.sin(r) * this.velZ;
         if (Math.abs(f) < 1.0E-6 && Math.abs(s) < 1.0E-6) {
            return 0.0;
         }
         return Math.toDegrees(Math.atan2(s, Math.abs(f)));
      }

      public void setVelocity(double vx, double vy, double vz) {
         this.velX = vx;
         this.velY = vy;
         this.velZ = vz;
      }
   }

   public static void step(State s, Input in) {
      s.events = 0;
      boolean ground = s.onGround;
      s.airTicks = ground ? 0 : s.airTicks + 1;
      if (s.crashTicks > 0) {
         s.crashTicks--;
      }
      if (s.backfireCooldown > 0) {
         s.backfireCooldown--;
      }
      boolean locked = s.crashTicks > 0;
      double throttle = locked ? 0.0 : clamp(in.throttle(), -1.0, 1.0);
      double steerIn = locked ? 0.0 : clamp(in.steer(), -1.0, 1.0);
      boolean boost = in.boost();
      double vmax = boost ? BOOST_SPEED : MAX_SPEED;

      double r = Math.toRadians(s.yaw);
      double hx = -Math.sin(r);
      double hz = Math.cos(r);
      double lx = Math.cos(r);
      double lz = Math.sin(r);
      double fwd = hx * s.velX + hz * s.velZ;
      double lat = lx * s.velX + lz * s.velZ;

      // --- drift state (enter at speed with the bars turned, leave on release or when too slow) ---
      if (!s.drifting && in.drift() && ground && !locked && fwd >= DRIFT_MIN_SPEED && Math.abs(steerIn) >= DRIFT_STEER) {
         s.drifting = true;
         s.driftTicks = 0;
         s.events |= EV_DRIFT_START;
      } else if (s.drifting && (!in.drift() || fwd < DRIFT_KEEP_SPEED || locked || s.airTicks > 6)) {
         if (!in.drift() && !locked && s.driftTicks >= DRIFT_BOOST_TICKS && fwd >= DRIFT_KEEP_SPEED) {
            fwd += DRIFT_EXIT_BOOST;
            s.events |= EV_DRIFT_BOOST;
         }
         s.drifting = false;
         s.driftTicks = 0;
      }
      if (s.drifting) {
         s.driftTicks++;
      }
      s.skidding = !s.drifting && in.drift() && ground && fwd > 0.05;

      // --- throttle, brake, coast, slope ---
      boolean wasBraking = s.braking;
      s.braking = false;
      if (ground) {
         if (throttle > 0.05) {
            if (fwd < -0.01) {
               fwd = Math.min(0.0, fwd + BRAKE * throttle);
               s.braking = true;
            } else if (fwd < vmax) {
               double a = boost ? BOOST_ACCEL : accel(fwd, vmax);
               fwd = Math.min(vmax, fwd + a * throttle);
            }
         } else if (throttle < -0.05) {
            if (fwd > 0.01) {
               if (!wasBraking) {
                  s.brakeFrom = fwd;
               }
               fwd = Math.max(0.0, fwd + BRAKE * throttle);
               s.braking = true;
            } else {
               fwd = Math.max(-REVERSE_SPEED, fwd + REVERSE_ACCEL * throttle);
            }
         }
         if (fwd > vmax) {
            fwd = Math.max(vmax, fwd - OVERSPEED_DECAY);
         }
         if (Math.abs(throttle) <= 0.05) {
            fwd = towardZero(fwd, COAST + COAST_RATE * Math.abs(fwd));
         }
         if (s.skidding) {
            fwd = towardZero(fwd, HANDBRAKE);
         }
         if (Math.abs(fwd) > 0.02 || Math.abs(throttle) > 0.05) {
            fwd -= SLOPE_GRAVITY * Math.sin(Math.toRadians(s.pitch));
         }
         if (Math.abs(throttle) <= 0.05 && Math.abs(fwd) < 0.004) {
            fwd = 0.0;
         }
      }

      // --- handlebar and heading ---
      boolean outward = Math.abs(steerIn) > Math.abs(s.steer) && (s.steer == 0.0 || Math.signum(steerIn) == Math.signum(s.steer));
      s.steer = approach(s.steer, steerIn, outward ? STEER_RATE : STEER_RETURN);
      double speedNow = Math.abs(fwd);
      double rate = yawRate(speedNow);
      if (s.drifting) {
         rate *= DRIFT_TURN;
      }
      if (!ground) {
         rate *= AIR_STEER;
      }
      double dir = fwd < -0.01 ? -1.0 : 1.0;
      double dYaw = -rate * s.steer * dir;
      s.yaw = wrap(s.yaw + dYaw);
      s.yawDelta = dYaw;

      // --- tyres: the direction of travel converges on the new heading ---
      double vx = hx * fwd + lx * lat;
      double vz = hz * fwd + lz * lat;
      double mag = Math.sqrt(vx * vx + vz * vz);
      double r2 = Math.toRadians(s.yaw);
      double hx2 = -Math.sin(r2);
      double hz2 = Math.cos(r2);
      double lx2 = Math.cos(r2);
      double lz2 = Math.sin(r2);
      if (mag > 1.0E-4) {
         double f2 = hx2 * vx + hz2 * vz;
         double s2 = lx2 * vx + lz2 * vz;
         double back = f2 < 0.0 ? -1.0 : 1.0;
         double slip = Math.atan2(s2, Math.abs(f2));
         double grip = !ground ? AIR_GRIP : s.drifting ? DRIFT_GRIP : s.skidding ? HANDBRAKE_GRIP : GRIP;
         slip *= 1.0 - grip;
         double cap = Math.toRadians(SLIP_MAX);
         slip = clamp(slip, -cap, cap);
         if (s.drifting) {
            mag *= 1.0 - DRIFT_SCRUB;
         }
         vx = mag * (hx2 * Math.cos(slip) * back + lx2 * Math.sin(slip));
         vz = mag * (hz2 * Math.cos(slip) * back + lz2 * Math.sin(slip));
      } else {
         vx = 0.0;
         vz = 0.0;
      }
      s.velX = vx;
      s.velZ = vz;

      // --- lean into the turn (balance of lateral acceleration and gravity) ---
      double leanTarget;
      if (ground) {
         leanTarget = Math.signum(-dYaw * dir) * leanFor(s.speed(), dYaw, s.drifting);
         s.lean += (leanTarget - s.lean) * LEAN_SMOOTH;
      } else {
         leanTarget = s.steer * AIR_LEAN;
         s.lean += (leanTarget - s.lean) * 0.1;
      }

      // --- jump and gravity ---
      if (ground && in.jump() && !s.jumpHeld && !locked) {
         s.velY = boost ? BOOST_JUMP : JUMP;
         s.events |= EV_JUMP;
      }
      s.jumpHeld = in.jump();
      s.velY = (s.velY - GRAVITY) * AIR_DRAG;

      // --- stunts: stoppie on a hard stop, wheelie on a dash or a full-throttle launch ---
      if (s.braking && fwd > 0.0 && s.brakeFrom >= STOPPIE_SPEED && !wasBraking) {
         s.events |= EV_STOPPIE;
      }
      double stoppieTarget = s.braking && fwd > 0.12 && s.brakeFrom >= STOPPIE_SPEED && ground ? 1.0 : 0.0;
      s.stoppie = approach(s.stoppie, stoppieTarget, stoppieTarget > s.stoppie ? 0.25 : 0.12);
      if (boost && !s.boostHeld) {
         s.wheelieTicks = WHEELIE_BOOST_TICKS;
      }
      s.boostHeld = boost;
      if (s.wheelieTicks > 0) {
         s.wheelieTicks--;
      }
      double wheelieTarget = s.wheelieTicks > 0 ? 1.0 : ground && throttle > 0.9 && fwd >= 0.0 && fwd < 0.12 ? 0.35 : 0.0;
      if (s.stoppie > 0.05) {
         wheelieTarget = 0.0;
      }
      s.wheelie = approach(s.wheelie, wheelieTarget, wheelieTarget > s.wheelie ? 0.2 : 0.1);

      // --- engine: gears, rpm, backfire ---
      int gear = gear(fwd);
      double rpmTarget;
      if (!ground) {
         rpmTarget = IDLE_RPM + 0.8 * Math.max(0.0, throttle);
      } else if (Math.abs(fwd) < 0.03) {
         rpmTarget = throttle > 0.05 || s.drifting ? 0.55 : IDLE_RPM;
      } else {
         rpmTarget = gearRpm(fwd, gear) + (throttle > 0.05 ? 0.06 : 0.0);
      }
      if (ground && gear > s.gear) {
         s.events |= EV_SHIFT;
         s.rpm *= 0.72;
      }
      s.gear = gear;
      s.rpm += (rpmTarget - s.rpm) * 0.3;
      if (s.lastThrottle > 0.5 && throttle <= 0.05 && s.rpm > 0.6 && s.backfireCooldown == 0) {
         s.events |= EV_BACKFIRE;
         s.backfireCooldown = 10;
      }
      s.lastThrottle = throttle;
   }

   /** The wheels touched down this tick: lean and lifts settle, the suspension cue fires. */
   public static void land(State s) {
      if (s.airTicks > 3) {
         s.events |= EV_LAND;
      }
      if (s.velY < 0.0) {
         s.velY = 0.0;
      }
   }

   /**
   * A wall stopped part of the motion. {@code blockedForward} is the forward speed the wall took (b/t, along the
   * heading); {@code vx, vz} the motion that remained. A head-on blow at {@link HellCycleRules#CRASH_SPEED} or more
   * throws the bike back and locks the controls briefly; a glancing one just keeps what the wall left.
   * Returns true on a crash.
   */
   public static boolean hitWall(State s, double blockedForward, double vx, double vz) {
      if (blockedForward >= CRASH_SPEED) {
         double r = Math.toRadians(s.yaw);
         s.velX = Math.sin(r) * CRASH_BOUNCE;
         s.velZ = -Math.cos(r) * CRASH_BOUNCE;
         s.crashTicks = CRASH_LOCK_TICKS;
         s.drifting = false;
         s.driftTicks = 0;
         s.stoppie = 0.0;
         s.wheelie = 0.0;
         s.rpm = IDLE_RPM;
         s.events |= EV_CRASH;
         return true;
      }
      s.velX = vx;
      s.velZ = vz;
      return false;
   }

   static double approach(double value, double target, double step) {
      return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
   }

   static double towardZero(double value, double step) {
      return value > 0.0 ? Math.max(0.0, value - step) : Math.min(0.0, value + step);
   }

   static double clamp(double v, double lo, double hi) {
      return v < lo ? lo : v > hi ? hi : v;
   }

   static double wrap(double deg) {
      deg %= 360.0;
      if (deg >= 180.0) {
         deg -= 360.0;
      }
      if (deg < -180.0) {
         deg += 360.0;
      }
      return deg;
   }
}
