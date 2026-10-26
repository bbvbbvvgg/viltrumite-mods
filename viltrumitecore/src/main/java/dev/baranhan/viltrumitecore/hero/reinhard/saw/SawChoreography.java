package dev.baranhan.viltrumitecore.hero.reinhard.saw;

/**
 * Saw Sphere (overhaul item 19): the whole 260-tick score, shared by the server (cuts, hits,
 * collision window) and every client (model spin, saws, shell, sounds). Pure and deterministic:
 * the start packet carries only the centre, the anchor yaw and the age, both sides derive the
 * rest from here. Heights are blocks above Y0 (the activation feet height); angles are radians
 * in the anchor frame, where angle {@code a} points along Minecraft yaw {@code anchorYaw + a}
 * (so the spin and the helical turn go the same way the model spins).
 *
 * <p>Layout (coverage simulated in {@code docs/plans/2026-10-27-reinhard-saw-sphere.md}):
 * 35 horizontal discs of radius {@link #SAW_RADIUS} (5.5-block cut) in a crown and three rings.
 * Each hugs the dome ({@link #offset}) and slowly spirals outward and around the axis
 * ({@link #KAPPA} per block of descent) while it sinks, so the union of the slabs sweeps the
 * whole volume above Y0 inside the shell.
 */
public final class SawChoreography {
   public static final int LENGTH = 260;
   public static final double RADIUS = 12.0;
   public static final double SAW_RADIUS = 2.75;
   public static final double HALF_THICKNESS = 0.22;
   /** Clearance between a disc rim and the shell. */
   public static final double MARGIN = 0.15;
   /** Lowest disc centre: its underside stays 0.02 above Y0. */
   public static final double FLOOR = HALF_THICKNESS + 0.02;
   /** The crown disc stops over Reinhard's head. */
   public static final double CROWN_FLOOR = 6.6;
   /** Feet of the hovering Reinhard above Y0. */
   public static final double HOVER = 3.0;
   public static final int RISE_END = 24;
   public static final int EXPAND_START = 2;
   public static final int EXPAND_END = 22;
   public static final int MATERIALIZE_START = 30;
   public static final int MATERIALIZE_SPREAD = 12;
   public static final int FADE = 8;
   public static final int SWING_PERIOD = 4;
   public static final int LAUNCH_START = 44;
   public static final int LAST_SWING = 224;
   public static final int HOLD_END = 226;
   public static final int FINAL_SWING = 232;
   public static final int PLUNGE_START = 232;
   public static final int PLUNGE_END = 244;
   public static final int CUT_END = 246;
   public static final int DISSOLVE_END = 252;
   public static final int CRACK_START = 234;
   public static final int SHATTER = 248;
   public static final int DESCEND_START = 246;
   public static final int DESCEND_END = 258;
   /**
    * Helical descent: a disc turns around the axis in the spin direction by this many radians per
    * block it sinks, so every disc sweeps a spiral band instead of a flat circle.
    */
   public static final double KAPPA = 0.12;
   /** 2.5 revolutions per second. */
   public static final double SPIN_MAX = 2.5 * 2.0 * Math.PI / 20.0;
   public static final int SPIN_UP_START = 12;
   public static final int SPIN_UP_END = 40;
   public static final int SPIN_DOWN_START = 222;
   public static final int SPIN_DOWN_END = 240;
   /** Own spin of a disc around its axis (rad/tick, render only). */
   public static final double DISC_SPIN = 1.7;

   /** Rings: home offset from the axis, disc count, start height above Y0. */
   static final double[][] RINGS = {{0.0, 1, 11.68}, {4.0, 7, 10.9}, {7.0, 11, 9.0}, {9.2, 16, 7.0}};
   public static final int COUNT;
   private static final int[] RING;
   private static final double[] HOME;
   private static final double[] PHI;
   private static final double[] START_Y;
   /** Launch tick of every disc (one per swing, inner rings first). */
   private static final int[] LAUNCH;
   /** Disc launched by swing k (k &lt; COUNT). */
   private static final int[] BY_SWING;

   static {
      int n = 0;
      for (double[] ring : RINGS) n += (int) ring[1];
      COUNT = n;
      RING = new int[n];
      HOME = new double[n];
      PHI = new double[n];
      START_Y = new double[n];
      LAUNCH = new int[n];
      BY_SWING = new int[n];
      int i = 0;
      for (int k = 0; k < RINGS.length; k++) {
         int count = (int) RINGS[k][1];
         for (int j = 0; j < count; j++, i++) {
            RING[i] = k;
            HOME[i] = RINGS[k][0];
            PHI[i] = 2.0 * Math.PI * j / count + 0.37 * k;
            START_Y[i] = RINGS[k][2];
         }
      }

      boolean[] launched = new boolean[n];
      int swing = 0;
      int first = 0;
      for (int k = 0; k < RINGS.length; k++) {
         int count = (int) RINGS[k][1];
         for (int m = 0; m < count; m++, swing++) {
            int tick = LAUNCH_START + swing * SWING_PERIOD;
            double facing = spin(tick);
            int best = -1;
            double bestGap = Double.MAX_VALUE;
            for (int s = first; s < first + count; s++) {
               if (launched[s]) continue;
               double gap = Math.abs(wrap(PHI[s] - facing));
               if (gap < bestGap) {
                  bestGap = gap;
                  best = s;
               }
            }
            launched[best] = true;
            LAUNCH[best] = tick;
            BY_SWING[swing] = best;
         }
         first += count;
      }
   }

   private SawChoreography() {
   }

   /** Angle in (-π, π]. */
   public static double wrap(double a) {
      double r = a % (2.0 * Math.PI);
      if (r > Math.PI) r -= 2.0 * Math.PI;
      if (r <= -Math.PI) r += 2.0 * Math.PI;
      return r;
   }

   public static double clamp01(double v) {
      return v < 0.0 ? 0.0 : v > 1.0 ? 1.0 : v;
   }

   public static double smooth(double u) {
      u = clamp01(u);
      return u * u * (3.0 - 2.0 * u);
   }

   /** Model spin of the caster (rad) at {@code t}: smooth ramp up, 2.5 rev/s, smooth ramp down. Closed form. */
   public static double spin(double t) {
      double up = SPIN_UP_END - SPIN_UP_START;
      double hold = SPIN_DOWN_START - SPIN_UP_END;
      double down = SPIN_DOWN_END - SPIN_DOWN_START;
      if (t <= SPIN_UP_START) return 0.0;
      if (t < SPIN_UP_END) {
         double u = (t - SPIN_UP_START) / up;
         return SPIN_MAX * up * (u * u * u - u * u * u * u / 2.0);
      }
      double base = SPIN_MAX * up / 2.0;
      if (t < SPIN_DOWN_START) return base + SPIN_MAX * (t - SPIN_UP_END);
      base += SPIN_MAX * hold;
      if (t < SPIN_DOWN_END) {
         double u = (t - SPIN_DOWN_START) / down;
         return base + SPIN_MAX * down * (u - u * u * u + u * u * u * u / 2.0);
      }
      return base + SPIN_MAX * down / 2.0;
   }

   /** Spin speed (rad/tick), for motion blur and sound. */
   public static double spinRate(double t) {
      if (t <= SPIN_UP_START || t >= SPIN_DOWN_END) return 0.0;
      if (t < SPIN_UP_END) return SPIN_MAX * smooth((t - SPIN_UP_START) / (SPIN_UP_END - SPIN_UP_START));
      if (t < SPIN_DOWN_START) return SPIN_MAX;
      return SPIN_MAX * (1.0 - smooth((t - SPIN_DOWN_START) / (SPIN_DOWN_END - SPIN_DOWN_START)));
   }

   /** Feet of the caster above Y0: rise, hover, gentle landing. */
   public static double hover(double t) {
      if (t < RISE_END) return HOVER * smooth(t / RISE_END);
      if (t < DESCEND_START) return HOVER;
      return HOVER * (1.0 - smooth((t - DESCEND_START) / (DESCEND_END - DESCEND_START)));
   }

   /** Shell radius: a fast ease-out expansion, then the full 12 blocks. */
   public static double shellRadius(double t) {
      double u = clamp01((t - EXPAND_START) / (EXPAND_END - EXPAND_START));
      double inv = 1.0 - u;
      return RADIUS * (1.0 - inv * inv * inv);
   }

   /** The intact shell blocks movement once fully expanded and until it shatters. */
   public static boolean shellSolid(int age) {
      return age >= EXPAND_END && age < SHATTER;
   }

   public static int ring(int saw) {
      return RING[saw];
   }

   public static double home(int saw) {
      return HOME[saw];
   }

   public static int launchTick(int saw) {
      return LAUNCH[saw];
   }

   /** Disc launched (or, after every disc flies, driven) by swing {@code k}. */
   public static int sawOfSwing(int k) {
      return BY_SWING[Math.floorMod(k, COUNT)];
   }

   /** Materialisation start: in launch order, spread over {@link #MATERIALIZE_SPREAD} ticks. */
   public static double materializeTick(int saw) {
      int order = (LAUNCH[saw] - LAUNCH_START) / SWING_PERIOD;
      return MATERIALIZE_START + MATERIALIZE_SPREAD * (double) order / (COUNT - 1);
   }

   /** Opacity: fade in, fade out after the final pass. */
   public static double alpha(int saw, double t) {
      double in = clamp01((t - materializeTick(saw)) / FADE);
      double out = 1.0 - clamp01((t - PLUNGE_END) / (DISSOLVE_END - PLUNGE_END));
      return in * out;
   }

   /** A disc cuts and hits from half its fade-in to the end of the final pass. */
   public static boolean cutting(int saw, double t) {
      return t >= materializeTick(saw) + FADE / 2.0 && t < CUT_END;
   }

   /** Height of the disc centre above Y0. */
   public static double height(int saw, double t) {
      boolean crown = HOME[saw] == 0.0;
      double end = crown ? CROWN_FLOOR : FLOOR + 1.0;
      double start = START_Y[saw];
      int launch = LAUNCH[saw];
      if (t <= launch) return start;
      if (t <= HOLD_END) {
         double u = (t - launch) / (HOLD_END - launch);
         u = 0.5 * smooth(u) + 0.5 * u;
         return start + (end - start) * u;
      }
      if (crown || t < PLUNGE_START) return end;
      double u = clamp01((t - PLUNGE_START) / (PLUNGE_END - PLUNGE_START));
      u = smooth(u);
      return end + (FLOOR - end) * u;
   }

   /** Half chord of the sphere at height {@code y} above the centre. */
   public static double chord(double y) {
      return Math.sqrt(Math.max(0.0, RADIUS * RADIUS - y * y));
   }

   /** Distance of the disc centre from the axis at height {@code y}: home, pulled in to clear the dome. */
   public static double offset(int saw, double y) {
      double home = HOME[saw];
      if (home == 0.0) return 0.0;
      return Math.min(home, Math.max(0.0, chord(y + HALF_THICKNESS) - SAW_RADIUS - MARGIN));
   }

   /** Anchor-frame angle of the disc centre: home angle plus the helical turn since launch. */
   public static double angle(int saw, double t) {
      return PHI[saw] + KAPPA * (START_Y[saw] - height(saw, t));
   }

   /**
    * World position of the disc centre: {@code out = {x, y, z}} for a sphere centred at
    * {@code (cx, y0, cz)} with the anchor yaw in degrees.
    */
   public static void position(int saw, double t, double cx, double y0, double cz, float anchorYaw, double[] out) {
      double y = height(saw, t);
      double d = offset(saw, y);
      double a = Math.toRadians(anchorYaw) + angle(saw, t);
      out[0] = cx - d * Math.sin(a);
      out[1] = y0 + y;
      out[2] = cz + d * Math.cos(a);
   }

   /** Swing index landing exactly at {@code tick}, or -1 (a swing every 4 ticks from 44 through 224). */
   public static int swingAt(int tick) {
      if (tick < LAUNCH_START || tick > LAST_SWING || (tick - LAUNCH_START) % SWING_PERIOD != 0) return -1;
      return (tick - LAUNCH_START) / SWING_PERIOD;
   }

   public static int swingCount() {
      return (LAST_SWING - LAUNCH_START) / SWING_PERIOD + 1;
   }

   /** Model yaw in degrees for an anchor yaw and a scene age. */
   public static float modelYaw(float anchorYaw, double t) {
      return anchorYaw + (float) Math.toDegrees(spin(t));
   }
}
