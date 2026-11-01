package dev.baranhan.viltrumitecore.hero.ghostrider.bike;

/**
 * Tuning of the Hell Cycle (docs/design/2026-11-02-ghost-rider-design.md §5.2, §5.3, §8). Units: blocks, ticks,
 * degrees. Pure numbers and functions only: the riding model ({@link HellCyclePhysics}) and the tests read them.
 */
public final class HellCycleRules {
   // --- longitudinal ---
   /** Top speed on the throttle: 0.85 b/t = 17 b/s = 61 km/h. */
   public static final double MAX_SPEED = 0.85;
   /** Hell Dash top speed: 1.30 b/t = 94 km/h. */
   public static final double BOOST_SPEED = 1.30;
   /** Walking-pace reverse for backing out of a corner (0.1 b/t = 7 km/h; QA: 0.18 felt like a second gear). */
   public static final double REVERSE_SPEED = 0.10;
   /** Throttle acceleration at a standstill; it falls off as (1 - v / vmax)^ACCEL_CURVE (drag included). */
   public static final double ACCEL = 0.032;
   public static final double ACCEL_CURVE = 0.65;
   public static final double BOOST_ACCEL = 0.06;
   public static final double REVERSE_ACCEL = 0.008;
   /** Both brakes, per tick: about 1.4 g, 61 km/h to a standstill in 1.25 s over ~10.6 blocks (QA: 0.055 stopped dead). */
   public static final double BRAKE = 0.034;
   /** Rolling resistance and engine braking with the throttle closed: COAST + COAST_RATE * v per tick. */
   public static final double COAST = 0.004;
   public static final double COAST_RATE = 0.006;
   /** How fast a boost's surplus speed bleeds off once the dash ends. */
   public static final double OVERSPEED_DECAY = 0.02;
   /** Gravity along the frame on a slope: a -= SLOPE_GRAVITY * sin(pitch). */
   public static final double SLOPE_GRAVITY = 0.04;

   // --- steering ---
   public static final double MIN_RADIUS = 2.4;
   /** Lateral acceleration the tyres hold: R = max(MIN_RADIUS, v^2 / LATERAL_ACCEL). */
   public static final double LATERAL_ACCEL = 0.062;
   /** Feet-on-the-ground pivot at walking pace (deg/t), so a parked bike can be turned round. */
   public static final double PIVOT_RATE = 2.0;
   public static final double PIVOT_SPEED = 0.12;
   public static final double STEER_RATE = 0.16;
   public static final double STEER_RETURN = 0.22;
   public static final double AIR_STEER = 0.3;
   /** Fraction of the slip angle the tyres remove per tick (velocity direction converges on the heading). */
   public static final double GRIP = 0.55;
   public static final double AIR_GRIP = 0.02;

   // --- lean ---
   public static final double LEAN_MAX = 38.0;
   public static final double LEAN_MAX_DRIFT = 44.0;
   public static final double LEAN_SMOOTH = 0.25;
   public static final double AIR_LEAN = 12.0;

   // --- drift ---
   public static final double DRIFT_MIN_SPEED = 0.35;
   public static final double DRIFT_KEEP_SPEED = 0.22;
   public static final double DRIFT_STEER = 0.3;
   /** Yaw rate multiplier while drifting (QA: 1.55 spun the bike 250 degrees in under two seconds). */
   public static final double DRIFT_TURN = 1.3;
   public static final double DRIFT_GRIP = 0.12;
   public static final double DRIFT_SCRUB = 0.008;
   public static final double SLIP_MAX = 55.0;
   public static final int DRIFT_BOOST_TICKS = 18;
   public static final double DRIFT_EXIT_BOOST = 0.12;
   /** Drift key without steering: a locked rear wheel. */
   public static final double HANDBRAKE = 0.03;
   public static final double HANDBRAKE_GRIP = 0.3;

   // --- vertical ---
   public static final double GRAVITY = 0.08;
   public static final double AIR_DRAG = 0.98;
   public static final double JUMP = 0.48;
   public static final double BOOST_JUMP = 0.62;
   public static final float STEP_HEIGHT = 1.0F;

   // --- collisions and stunts ---
   /** Forward speed lost into a wall at or above this is a head-on crash. */
   public static final double CRASH_SPEED = 0.5;
   public static final double CRASH_BOUNCE = 0.12;
   public static final int CRASH_LOCK_TICKS = 8;
   /** Braking from this speed or more lifts the rear wheel (stoppie). */
   public static final double STOPPIE_SPEED = 0.55;
   public static final double STOPPIE_MAX_DEG = 18.0;
   public static final double WHEELIE_MAX_DEG = 20.0;
   public static final int WHEELIE_BOOST_TICKS = 12;

   // --- engine (sound and exhaust) ---
   public static final double IDLE_RPM = 0.12;
   /** Upper speed of gears 1-5 (the fifth only on a dash). */
   public static final double[] GEAR_TOP = {0.20, 0.40, 0.60, 0.86, BOOST_SPEED + 0.05};

   // --- body ---
   public static final float WIDTH = 1.1F;
   public static final float HEIGHT = 1.6F;
   /** Axle to axle (the model: axles at z -+14.4 px). */
   public static final double WHEELBASE = 1.8;
   /** Wheel radius (6 px): one block of travel turns a wheel 152.8 degrees. */
   public static final double WHEEL_RADIUS = 0.375;
   /** Centre of the nose / tail probe boxes ahead of / behind the centre, and their half size. */
   public static final double NOSE = 1.0;
   public static final double PROBE_HALF = 0.25;
   /** Rider's feet above the bike's base (seat top at ~0.85). */
   public static final double SEAT_Y = 0.2;
   /** The rider's hips sit this far behind the centre of the wheelbase (model: z +6.4 px). */
   public static final double SEAT_BACK = 0.40;

   // --- lifecycle ---
   public static final int MATERIALIZE_TICKS = 20;
   public static final int DEMATERIALIZE_TICKS = 14;
   public static final int MOUNT_TICKS = 8;
   public static final int UNATTENDED_TICKS = 600;
   public static final int SUMMON_COOLDOWN = 40;
   public static final int BOOST_TICKS = 40;
   public static final int BOOST_COOLDOWN = 160;

   private HellCycleRules() {
   }

   /** km/h on the speedometer (1 b/t = 20 m/s = 72 km/h). */
   public static double kmh(double blocksPerTick) {
      return Math.abs(blocksPerTick) * 72.0;
   }

   /** Throttle acceleration at forward speed {@code v} towards {@code vmax}. */
   public static double accel(double v, double vmax) {
      if (v >= vmax) {
         return 0.0;
      }
      double f = 1.0 - Math.max(0.0, v) / vmax;
      return ACCEL * Math.pow(f, ACCEL_CURVE);
   }

   public static double turnRadius(double speed) {
      return Math.max(MIN_RADIUS, speed * speed / LATERAL_ACCEL);
   }

   /** Full-lock yaw rate (deg/t) at a ground speed, before drift and air factors. */
   public static double yawRate(double speed) {
      speed = Math.abs(speed);
      double rate = Math.toDegrees(speed / turnRadius(speed));
      if (speed < PIVOT_SPEED) {
         rate = Math.max(rate, PIVOT_RATE * (1.0 - speed / PIVOT_SPEED));
      }
      return rate;
   }

   /** Lean (deg) that balances the lateral acceleration v * omega against gravity, capped. */
   public static double leanFor(double speed, double yawRateDeg, boolean drift) {
      double lateral = Math.abs(speed) * Math.toRadians(Math.abs(yawRateDeg));
      double lean = Math.toDegrees(Math.atan2(lateral, GRAVITY));
      return Math.min(lean, drift ? LEAN_MAX_DRIFT : LEAN_MAX);
   }

   /** Gear (1-5) for a forward speed. */
   public static int gear(double speed) {
      speed = Math.abs(speed);
      for (int i = 0; i < GEAR_TOP.length; i++) {
         if (speed < GEAR_TOP[i]) {
            return i + 1;
         }
      }
      return GEAR_TOP.length;
   }

   /** Engine speed 0-1 inside a gear: low after a shift, high near the gear's top. */
   public static double gearRpm(double speed, int gear) {
      double lo = gear <= 1 ? 0.0 : GEAR_TOP[gear - 2];
      double hi = GEAR_TOP[gear - 1];
      double f = Math.max(0.0, Math.min(1.0, (Math.abs(speed) - lo) / (hi - lo)));
      return 0.3 + 0.62 * f;
   }
}
