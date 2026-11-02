package dev.baranhan.viltrumitecore.worldevent.godzilla;

/**
 * Pure numbers and curves of the Godzilla city catastrophe (docs/plans/2026-11-05-godzilla-city-catastrophe.md),
 * shared by the server (walker, hit zones, breath, budgets, brain) and the client (breath stages, plate glow,
 * tail chain). Lengths are blocks, times ticks, angles degrees. Body-frame offsets are fractions of
 * {@link #HEIGHT}: x = the kaiju's left, y = up, z = forward. The model generator (tools/assets/godzilla.py)
 * builds the skeleton from the same layout; GodzillaAssetsTest checks the bone pivots against it.
 */
public final class GodzillaRules {
   // ------------------------------------------------------------------ size

   /** Height to the top of the tallest dorsal plate. */
   public static final float HEIGHT = 120.0F;
   /** Height of the geo model in model blocks (px / 16); the renderer scales it by {@link #modelScale()}. */
   public static final float MODEL_HEIGHT = 12.0F;

   public static float modelScale() {
      return HEIGHT / MODEL_HEIGHT;
   }

   // ------------------------------------------------------------------ health and damage

   public static final float MAX_HEALTH = 4000.0F;
   /** One hit is capped here before the hit-zone factor (a hero's best blow still counts, a one-shot does not). */
   public static final float MAX_HIT = 90.0F;
   /** All hits of one tick together (area spam). */
   public static final float MAX_DAMAGE_PER_TICK = 180.0F;
   /** Health share below which the kaiju is enraged. */
   public static final float RAGE_HEALTH = 0.55F;
   /** Damage taken within {@link #RAGE_BURST_TICKS} that enrages it early. */
   public static final float RAGE_BURST = 650.0F;
   public static final int RAGE_BURST_TICKS = 600;
   /** Health share of the finale (last stand). */
   public static final float FINALE_HEALTH = 0.12F;
   /** One (capped) hit this big plays the heavy-hit reaction. */
   public static final float HURT_HIT = 45.0F;
   /** Damage inside {@link #STAGGER_TICKS} that knocks the kaiju down (fall and rise). */
   public static final float STAGGER_FALL = 420.0F;
   public static final int STAGGER_TICKS = 80;
   public static final int HURT_COOLDOWN = 120;
   public static final int FALL_COOLDOWN = 1400;

   /** Damage of one hit: capped, scaled by the hit zone; kill commands pass untouched. */
   public static float hit(float amount, float zoneFactor, boolean bypass) {
      if (!Float.isFinite(amount) || amount <= 0.0F) {
         return 0.0F;
      }
      if (bypass) {
         return amount;
      }
      return Math.min(amount, MAX_HIT) * zoneFactor;
   }

   /** What may still land this tick. */
   public static float allowance(float dealtThisTick, float wanted) {
      return Math.max(0.0F, Math.min(wanted, MAX_DAMAGE_PER_TICK - dealtThisTick));
   }

   // ------------------------------------------------------------------ event timing

   public static final int ARRIVAL_TICKS = 700;
   /** Event length before the finale starts on its own (the kaiju retreats). */
   public static final int EVENT_TICKS = 24000;
   /** Retreat: walks away at most this long, then leaves. */
   public static final int RETREAT_TICKS = 900;
   /** The dead body stays this long. */
   public static final int CORPSE_TICKS = 500;
   /** Ticks without a threat before combat falls back to exploring. */
   public static final int COMBAT_MEMORY = 320;
   public static final int ALARM_LEASE = 240;
   public static final int ALARM_REFRESH = 100;
   /** Arrival distance from the start point (clamped to the loaded area). */
   public static final float SPAWN_DISTANCE = 150.0F;
   /** Players closer than this become targets. */
   public static final float AGGRO_RANGE = 110.0F;
   /** Targets further than this are dropped. */
   public static final float LOSE_RANGE = 220.0F;
   /** Without a city: explore points around the start point within this radius. */
   public static final float WANDER_RADIUS = 160.0F;
   /** A waypoint counts as reached within this distance. */
   public static final float WAYPOINT_REACHED = 24.0F;

   // ------------------------------------------------------------------ gait

   // blocks per tick; the stride follows the distance walked, so a slower walk is a slower, heavier cadence
   // (0.30: a step every 3.4 s). Tested on Atheria: 0.40 crossed downtown in under a minute and used up the
   // session's block budget in two.
   public static final float WALK_SPEED = 0.30F;
   public static final float RAGE_SPEED = 0.40F;
   public static final float RETREAT_SPEED = 0.36F;
   /** Blocks per gait cycle (two steps). */
   public static final float GAIT_CYCLE = 0.34F * HEIGHT;
   /** Turning in place: one gait cycle per this many degrees. */
   public static final float TURN_CYCLE = 70.0F;
   public static final float TURN_WALK = 1.1F;
   public static final float TURN_STAND = 0.8F;
   public static final float TURN_RAGE_BONUS = 0.4F;
   /** Ground follow: at most this many blocks of height change per tick. */
   public static final float CLIMB_PER_TICK = 0.35F;

   public static float speed(boolean rage, boolean retreat) {
      return retreat ? RETREAT_SPEED : rage ? RAGE_SPEED : WALK_SPEED;
   }

   /**
    * Wading: the walk slows while the body pushes through dense blocks. {@code load} is the smoothed share of the
    * per-tick block budget used (0 open streets .. 1 a solid block of towers); the walk keeps at least
    * {@code 1 - WADE_DEPTH} of its speed. Measured on Atheria: downtown costs 500..1300 blocks per block walked.
    */
   public static final float WADE_DEPTH = 0.35F;

   public static float wade(float load) {
      return 1.0F - WADE_DEPTH * smooth(load);
   }

   /** Survey pause at each reached waypoint while exploring: it stops, growls and looks over the city (ticks). */
   public static final int SURVEY_MIN = 100;
   public static final int SURVEY_SPREAD = 120;
   /** Waypoint pull of tall blocks (score per block of height, up to 80): towers attract it, not every tower. */
   public static final float TOWER_PULL = 0.45F;

   public static float turnRate(boolean walking, boolean rage) {
      return (walking ? TURN_WALK : TURN_STAND) + (rage ? TURN_RAGE_BONUS : 0.0F);
   }

   /** Gait phase advance for a tick that moved {@code moved} blocks and turned {@code turned} degrees. */
   public static float gaitAdvance(double moved, float turned) {
      return (float)(moved / GAIT_CYCLE) + Math.abs(turned) / TURN_CYCLE;
   }

   /** Footfall when the phase crosses a whole cycle (left foot, 1) or a half (right foot, 2); 0 = none. */
   public static int footfall(double before, double after) {
      long a = (long)Math.floor(before * 2.0);
      long b = (long)Math.floor(after * 2.0);
      if (b <= a) {
         return 0;
      }
      return (b & 1L) == 0L ? 1 : 2;
   }

   /** Forward offset of a foot (fraction of HEIGHT) over the gait phase: planted feet slide back, swings go forward. */
   public static float footForward(double phase, boolean left) {
      double p = phase - Math.floor(phase) + (left ? 0.0 : 0.5);
      return (float)(0.085 * Math.cos(2.0 * Math.PI * p));
   }

   // ------------------------------------------------------------------ hit zones

   public static final int P_HEAD = 0;
   public static final int P_NECK = 1;
   public static final int P_CHEST = 2;
   public static final int P_BELLY = 3;
   public static final int P_HIPS = 4;
   public static final int P_ARM_L = 5;
   public static final int P_ARM_R = 6;
   public static final int P_LEG_L = 7;
   public static final int P_LEG_R = 8;
   public static final int P_TAIL = 9;
   public static final int TAIL_SEGMENTS = 7;
   public static final int PARTS = P_TAIL + TAIL_SEGMENTS;

   /** Box width and height of every part (fractions of HEIGHT). */
   public static final float[][] PART_SIZE = {
      {0.12F, 0.10F}, {0.15F, 0.14F}, {0.28F, 0.20F}, {0.30F, 0.18F}, {0.28F, 0.16F},
      {0.09F, 0.12F}, {0.09F, 0.12F}, {0.14F, 0.30F}, {0.14F, 0.30F},
      {0.19F, 0.16F}, {0.16F, 0.14F}, {0.14F, 0.12F}, {0.12F, 0.10F}, {0.10F, 0.085F}, {0.08F, 0.07F}, {0.065F, 0.055F}};
   /** Damage factor of a hit on each part. */
   public static final float[] PART_FACTOR = {1.35F, 1.2F, 1.0F, 1.1F, 0.9F, 0.8F, 0.8F, 0.85F, 0.85F,
      0.75F, 0.7F, 0.65F, 0.6F, 0.55F, 0.5F, 0.5F};
   /** Rest centres in the body frame of the non-tail parts (the head is placed by the neck and the look). */
   public static final float[][] PART_REST = {
      {0.0F, 0.84F, 0.27F}, {0.0F, 0.73F, 0.15F}, {0.0F, 0.60F, 0.08F}, {0.0F, 0.46F, 0.02F}, {0.0F, 0.36F, -0.06F},
      {0.16F, 0.53F, 0.17F}, {-0.16F, 0.53F, 0.17F}, {0.12F, 0.16F, -0.01F}, {-0.12F, 0.16F, -0.01F}};
   /** Pivot of the head turn (top of the neck). */
   public static final float[] NECK_PIVOT = {0.0F, 0.74F, 0.14F};
   /** From the neck pivot to the head centre and to the mouth (rest, looking straight ahead). */
   public static final float[] HEAD_OFFSET = {0.0F, 0.08F, 0.13F};
   public static final float[] MOUTH_OFFSET = {0.0F, 0.045F, 0.21F};
   /** Shoulder joints (claw sweeps turn about them). */
   public static final float[] SHOULDER_L = {0.15F, 0.58F, 0.13F};
   /** Where the tail leaves the hips. */
   public static final float[] TAIL_ROOT = {0.0F, 0.33F, -0.17F};
   /** Tail segment lengths (sum 1.35 HEIGHT) and their pitch below the horizontal at rest. */
   public static final float[] TAIL_LENGTH = {0.21F, 0.20F, 0.20F, 0.19F, 0.19F, 0.18F, 0.18F};
   public static final float[] TAIL_PITCH = {8.0F, 12.0F, 15.0F, 15.0F, 12.0F, 8.0F, 3.0F};
   /** How fast each segment follows the one before it (share of the gap per tick): the tail's inertia. */
   public static final float[] TAIL_FOLLOW = {0.55F, 0.32F, 0.28F, 0.25F, 0.22F, 0.20F, 0.18F};

   // ------------------------------------------------------------------ tail curves

   /** Hips twist of the tail strike (degrees, towards {@code side}) at move tick {@code t}. */
   public static float hipsTwist(float t, int side) {
      float w = t < 10.0F ? -0.25F * smooth(t / 10.0F)
         : t < 30.0F ? -0.25F + 1.25F * smooth((t - 10.0F) / 20.0F)
         : t < 46.0F ? 1.0F
         : 1.0F - smooth((t - 46.0F) / 24.0F);
      return side * 38.0F * w;
   }

   /** Tail-strike yaw of segment k relative to the one before it: a whip that runs down the tail. */
   public static float tailStrike(int k, float t, int side) {
      float d = t - 2.5F * k;
      float w = d < 0.0F ? 0.0F
         : d < 18.0F ? -0.35F * smooth(d / 18.0F)
         : d < 32.0F ? -0.35F + 1.35F * smooth((d - 18.0F) / 14.0F)
         : d < 42.0F ? 1.0F
         : 1.0F - smooth((d - 42.0F) / 26.0F);
      return side * (9.0F + 3.0F * k) * w;
   }

   /** Walk sway of tail segment k (relative yaw) at a gait phase; {@code amount} 0 standing .. 1 walking. */
   public static float tailSway(int k, double phase, float amount) {
      return (float)(amount * (1.2F + 0.55F * k) * Math.sin(2.0 * Math.PI * (phase - 0.07 * k)));
   }

   // ------------------------------------------------------------------ look

   public static final float HEAD_YAW_MAX = 55.0F;
   /**
    * Head pitch limits (degrees; the model spreads the pitch over chest, neck and head). Down to 60: the beam reaches
    * the street from about 75 blocks out (playtest: at 35 the mouth could not point below the street 150 blocks out,
    * so a ground hero at 90 blocks was "breathed at" over his head).
    */
   public static final float HEAD_PITCH_UP = 60.0F;
   public static final float HEAD_PITCH_DOWN = 60.0F;
   /** Head turn rate when only looking. */
   public static final float HEAD_TURN = 2.2F;
   /** Head turn rate while the beam fires (the beam lags behind a fast target). */
   public static final float BEAM_TURN = 0.9F;
   public static final float BEAM_TURN_RAGE = 1.3F;
   /** While firing the body turns after the head once the head is this far off. */
   public static final float BODY_FOLLOW = 28.0F;
   public static final float BODY_FOLLOW_TURN = 0.5F;

   /** Moves {@code current} towards {@code target} by at most {@code step}. */
   public static float approach(float current, float target, float step) {
      float d = target - current;
      return Math.abs(d) <= step ? target : current + Math.signum(d) * step;
   }

   /** Degrees wrapped to -180..180. */
   public static float wrap(float deg) {
      float d = deg % 360.0F;
      if (d >= 180.0F) {
         d -= 360.0F;
      }
      if (d < -180.0F) {
         d += 360.0F;
      }
      return d;
   }

   public static float clampYaw(float yaw) {
      return Math.max(-HEAD_YAW_MAX, Math.min(HEAD_YAW_MAX, yaw));
   }

   public static float clampPitch(float pitch) {
      return Math.max(-HEAD_PITCH_DOWN, Math.min(HEAD_PITCH_UP, pitch));
   }

   /**
    * A head-frame offset (fractions of HEIGHT: left, up, forward) turned by the head yaw / pitch (degrees) about the
    * neck pivot, in the body frame (fractions of HEIGHT). The server's mouth, head centre and beam start.
    */
   public static double[] headPoint(float[] off, float yaw, float pitch) {
      double p = Math.toRadians(pitch);
      double y = Math.toRadians(yaw);
      double f1 = off[2] * Math.cos(p) - off[1] * Math.sin(p);
      double u1 = off[2] * Math.sin(p) + off[1] * Math.cos(p);
      double l1 = off[0];
      double l2 = l1 * Math.cos(y) - f1 * Math.sin(y);
      double f2 = l1 * Math.sin(y) + f1 * Math.cos(y);
      return new double[]{NECK_PIVOT[0] + l2, NECK_PIVOT[1] + u1, NECK_PIVOT[2] + f2};
   }

   /**
    * Head pitch (degrees, unclamped) that puts the beam through a point {@code up} blocks above and {@code flat} blocks
    * out from the neck pivot. The beam leaves the mouth along the head, and the mouth sits MOUTH_OFFSET[1] above the
    * head's axis: aimed with the axis, the beam passed 5.4 blocks over its target (wider than the beam and a hero).
    */
   public static float beamPitch(double up, double flat) {
      double d = Math.sqrt(up * up + flat * flat);
      double m = MOUTH_OFFSET[1] * HEIGHT;
      double a = Math.atan2(up, flat);
      return (float)Math.toDegrees(d <= m ? a : a - Math.asin(m / d));
   }

   // ------------------------------------------------------------------ atomic breath

   /** Charge stages: stop and brace, plates ignite at the tail, the glow runs up the spine, energy in the mouth. */
   public static final int CHARGE_BRACE = 16;
   public static final int CHARGE_IGNITE = 46;
   public static final int CHARGE_SPINE = 78;
   public static final int CHARGE_TICKS = 100;
   public static final int FIRE_TICKS = 70;
   public static final int FIRE_TICKS_RAGE = 110;
   public static final int FIRE_TICKS_FINALE = 140;
   public static final int BREATH_END_TICKS = 36;
   /** Ticks between two breaths (never an endless beam, rage included). */
   public static final int BREATH_COOLDOWN = 640;
   public static final int BREATH_COOLDOWN_RAGE = 420;
   public static final float BEAM_RANGE = 240.0F;
   /** Radius of the entity hit capsule along the beam. */
   public static final float BEAM_RADIUS = 2.4F;
   public static final float BEAM_RADIUS_RAGE = 3.2F;
   /** Radius of the hole the beam drills where it hits. */
   public static final float DRILL_RADIUS = 3.4F;
   public static final float DRILL_RADIUS_RAGE = 4.4F;
   /** Blocks the beam may remove per tick (inside the global budget). */
   public static final int DRILL_BLOCKS = 260;
   public static final int DRILL_BLOCKS_RAGE = 360;
   public static final float BEAM_DAMAGE = 5.0F;
   public static final float BEAM_DAMAGE_RAGE = 7.0F;
   public static final float IMPACT_RADIUS = 6.0F;
   public static final float IMPACT_DAMAGE = 4.0F;
   /** Ticks a beam impact keeps the ground hot (aftermath hot zone, smoke). */
   public static final int HOT_TICKS = 1200;

   public static int fireTicks(boolean rage, boolean finale) {
      return finale ? FIRE_TICKS_FINALE : rage ? FIRE_TICKS_RAGE : FIRE_TICKS;
   }

   public static int breathCooldown(boolean rage) {
      return rage ? BREATH_COOLDOWN_RAGE : BREATH_COOLDOWN;
   }

   /** No breath at what is this close to the mouth (blocks; a hero in its face gets the claws or a roar). */
   public static final double BEAM_MIN = 20.0;
   /** The breath is only chosen this far (degrees) inside the head's pitch limits: the target keeps moving. */
   public static final float BEAM_AIM_MARGIN = 3.0F;

   /**
    * Whether the breath can burn a body-frame point (blocks: left, up from the ground, forward): the head turns the
    * beam through it within its yaw and pitch limits (the body turns to face a target before it charges), the point
    * is in front of the mouth and not in its face, within range. A hero over the head or behind the neck is out of
    * the beam's reach (playtest: it charged at a flier straight above and at a hero on the street 90 blocks out, and
    * the beam passed over both).
    */
   public static boolean beamReaches(double left, double up, double forward) {
      double dl = left - NECK_PIVOT[0] * HEIGHT;
      double du = up - NECK_PIVOT[1] * HEIGHT;
      double df = forward - NECK_PIVOT[2] * HEIGHT;
      double flat = Math.sqrt(dl * dl + df * df);
      double d = Math.sqrt(flat * flat + du * du);
      double m = MOUTH_OFFSET[1] * HEIGHT;
      if (d <= m || Math.abs(Math.toDegrees(Math.atan2(dl, df))) > HEAD_YAW_MAX) {
         return false;
      }
      float pitch = beamPitch(du, flat);
      if (pitch > HEAD_PITCH_UP - BEAM_AIM_MARGIN || pitch < -HEAD_PITCH_DOWN + BEAM_AIM_MARGIN) {
         return false;
      }
      double along = Math.sqrt(d * d - m * m) - MOUTH_OFFSET[2] * HEIGHT;
      return along > BEAM_MIN && along < BEAM_RANGE - 8.0;
   }

   /** Charge stage at a charge tick: 0 brace, 1 ignite, 2 spine, 3 mouth. */
   public static int chargeStage(float t) {
      return t < CHARGE_BRACE ? 0 : t < CHARGE_IGNITE ? 1 : t < CHARGE_SPINE ? 2 : 3;
   }

   /** The glow front along the spine (0 = tail tip, 1 = head) at a charge tick. */
   public static float glowFront(float t) {
      if (t < CHARGE_BRACE) {
         return 0.0F;
      }
      if (t < CHARGE_IGNITE) {
         return 0.38F * smooth((t - CHARGE_BRACE) / (CHARGE_IGNITE - CHARGE_BRACE));
      }
      if (t < CHARGE_SPINE) {
         return 0.38F + 0.62F * smooth((t - CHARGE_IGNITE) / (CHARGE_SPINE - CHARGE_IGNITE));
      }
      return 1.0F;
   }

   /** Glow of a plate at spine position {@code s} (0 tail tip .. 1 head) behind a front: soft leading edge. */
   public static float plateGlow(float s, float front) {
      if (front <= 0.0F) {
         return 0.0F;
      }
      return smooth((front - s) / 0.12F + 1.0F);
   }

   /** Energy in the mouth at a charge tick (0 before the spine is lit, 1 when the beam fires). */
   public static float mouthGlow(float t) {
      return smooth((t - (CHARGE_SPINE - 6)) / (CHARGE_TICKS - CHARGE_SPINE + 6));
   }

   public static float beamTurn(boolean rage) {
      return rage ? BEAM_TURN_RAGE : BEAM_TURN;
   }

   // ------------------------------------------------------------------ melee and steps

   public static final float STEP_DAMAGE = 14.0F;
   public static final float STEP_RADIUS = 0.075F * HEIGHT;
   public static final float CLAW_DAMAGE = 34.0F;
   /** Claw sweep: radius of the hand arc about the shoulder, hit sphere radius. */
   public static final float CLAW_ARC = 0.30F * HEIGHT;
   public static final float CLAW_RADIUS = 0.10F * HEIGHT;
   public static final float TAIL_DAMAGE = 40.0F;
   public static final float STOMP_DAMAGE = 30.0F;
   public static final float STOMP_WAVE = 0.34F * HEIGHT;
   public static final float SLAM_DAMAGE = 36.0F;
   public static final float SLAM_RADIUS = 0.17F * HEIGHT;
   /** Centre of the slam's hit sphere (body frame, fractions of HEIGHT): the chest, carried forward by the lunge. */
   public static final float[] SLAM_CENTRE = {0.0F, 0.45F, 0.32F};
   /** The slam hurts from its first contact tick through this many ticks more. */
   public static final int SLAM_HURT = 10;
   /** How far the body slam lunges forward. */
   public static final float SLAM_LUNGE = 0.12F * HEIGHT;
   public static final float FALL_DAMAGE = 40.0F;
   public static final float ROAR_WAVE = 0.6F * HEIGHT;

   /** Claw sweep angle about the shoulder (degrees, + towards the arm's own side) over the contact window. */
   public static float clawSweep(float t, GodzillaMove move) {
      float u = (t - move.contact + 3.0F) / (move.contactEnd - move.contact + 6.0F);
      float a = 55.0F - 105.0F * smooth(u);
      return a;
   }

   /** Linear falloff of a shockwave: 1 at the centre, 0 at the edge. */
   public static float falloff(double distance, double radius) {
      return (float)Math.max(0.0, 1.0 - distance / radius);
   }

   // ------------------------------------------------------------------ brain

   public static final int CLAW_COOLDOWN = 50;
   public static final int TAIL_COOLDOWN = 150;
   public static final int STOMP_COOLDOWN = 110;
   public static final int SLAM_COOLDOWN = 300;
   public static final int ROAR_COOLDOWN = 560;
   /** Rage shortens every cooldown by this factor. */
   public static final float RAGE_COOLDOWN = 0.7F;

   public static int cooldown(int base, boolean rage) {
      return rage ? Math.round(base * RAGE_COOLDOWN) : base;
   }

   /** Where a target is, seen from the body. CLOSE: near, but no move lands from here (close in, turn, ram). */
   public enum Zone {
      NONE, FEET, FRONT, SIDE_L, SIDE_R, BEHIND, AIR_NEAR, AIR_FAR, FAR, CLOSE
   }

   // ------------------------------------------------------------------ reach: a melee move is chosen only where it lands

   /** The stomp's wave hurts bodies at most this high above the stomping foot (blocks, GodzillaActions.stomp). */
   public static final float STOMP_RISE = 10.0F;
   /** Ground heroes are closed in to this distance (fraction of HEIGHT): inside the stomp's wave at any gait phase. */
   public static final float STOMP_CLOSE = 0.20F;
   /** The target keeps moving through the wind-up: a claw or the tail is swung only at what is this far inside its hit sphere. */
   public static final float REACH_SLACK = 2.0F;
   /** Tail segments that carry hit spheres in the strike (the root segments only turn). */
   public static final int TAIL_STRIKE_FIRST = 2;

   /** Body-frame centre of the swiping hand (fractions of HEIGHT: left, up, forward) at move tick {@code t}: an arc
    * about the shoulder, high to low across the front (GodzillaActions.clawHand places the server hit sphere here). */
   public static float[] clawHand(GodzillaMove arm, float t) {
      int side = arm == GodzillaMove.CLAW_L ? 1 : -1;
      double a = Math.toRadians(clawSweep(t, arm));
      float u = Math.max(0.0F, Math.min(1.0F, (t - arm.contact) / (float)(arm.contactEnd - arm.contact)));
      float arc = CLAW_ARC / HEIGHT;
      return new float[]{(float)(side * (SHOULDER_L[0] + Math.sin(a) * arc)), 0.56F - 0.2F * u, (float)(SHOULDER_L[2] + Math.cos(a) * arc)};
   }

   /** Closest approach (blocks) of an arm's hand path over its contact window to a body-frame point (blocks). */
   public static double clawMiss(GodzillaMove arm, double left, double up, double forward) {
      double best = Double.MAX_VALUE;
      for (float t = arm.contact; t <= arm.contactEnd + 1.0E-3F; t += 0.5F) {
         float[] h = clawHand(arm, t);
         double dl = left - h[0] * HEIGHT;
         double du = up - h[1] * HEIGHT;
         double df = forward - h[2] * HEIGHT;
         best = Math.min(best, Math.sqrt(dl * dl + du * du + df * df));
      }
      return best;
   }

   /**
    * The arm whose swipe lands on a body-frame point (blocks), or NONE: the hand sweeps 31..79 blocks up across the
    * front, so a hero on the street, above the head or at the flank is never clawed (playtest: it swiped at air).
    */
   public static GodzillaMove clawFor(double left, double up, double forward) {
      double l = clawMiss(GodzillaMove.CLAW_L, left, up, forward);
      double r = clawMiss(GodzillaMove.CLAW_R, left, up, forward);
      if (Math.min(l, r) > CLAW_RADIUS - REACH_SLACK) {
         return GodzillaMove.NONE;
      }
      return l < r ? GodzillaMove.CLAW_L : GodzillaMove.CLAW_R;
   }

   /** One tick of the tail chain (world yaws, degrees): each segment follows the one before it (inertia), with the walk
    * sway and the tail strike ({@code strikeT} below 0: none). Shared by the entity (both sides) and the reach test. */
   public static void tailStep(float[] yaw, float body, float strikeT, int side, double gait, float walk) {
      boolean strike = strikeT >= 0.0F;
      float twist = strike ? hipsTwist(strikeT, side) : 0.0F;
      for (int k = 0; k < yaw.length; k++) {
         float base = k == 0 ? body + twist : yaw[k - 1];
         float target = base + tailSway(k, gait, walk) + (strike ? tailStrike(k, strikeT, side) : 0.0F);
         yaw[k] += wrap(target - yaw[k]) * TAIL_FOLLOW[k];
      }
   }

   /** Body-frame centres (fractions of HEIGHT: left, up, forward) of the tail segments for world yaws about a body yaw. */
   public static void tailCentres(float[] yaw, float body, double[][] out) {
      double l = TAIL_ROOT[0];
      double u = TAIL_ROOT[1];
      double f = TAIL_ROOT[2];
      for (int k = 0; k < TAIL_SEGMENTS; k++) {
         double rel = Math.toRadians(wrap(yaw[k] - body));
         double p = Math.toRadians(TAIL_PITCH[k]);
         double len = TAIL_LENGTH[k];
         double dl = Math.sin(rel) * Math.cos(p) * len;
         double du = -Math.sin(p) * len;
         double df = -Math.cos(rel) * Math.cos(p) * len;
         out[k][0] = l + dl * 0.5;
         out[k][1] = Math.max(0.03, u + du * 0.5);
         out[k][2] = f + df * 0.5;
         l += dl;
         u = Math.max(0.03, u + du);
         f += df;
      }
   }

   /** Radius (blocks) of the block smash a striking tail segment carries; living things are hit 2 blocks further. */
   public static float tailSmashRadius(int k) {
      return PART_SIZE[P_TAIL + k][0] * HEIGHT * 0.6F;
   }

   /** Whether a tail strike to {@code side} (+1 left) sweeps a body-frame point (blocks), from the tail at rest. */
   public static boolean tailReaches(int side, double left, double up, double forward) {
      float[] yaw = new float[TAIL_SEGMENTS];
      double[][] c = new double[TAIL_SEGMENTS][3];
      GodzillaMove move = GodzillaMove.TAIL;
      for (int t = 0; t <= move.contactEnd; t++) {
         tailStep(yaw, 0.0F, t, side, 0.0, 0.0F);
         if (!move.inContact(t)) {
            continue;
         }
         tailCentres(yaw, 0.0F, c);
         for (int k = TAIL_STRIKE_FIRST; k < TAIL_SEGMENTS; k++) {
            double r = tailSmashRadius(k) + 2.0F - REACH_SLACK;
            double dl = left - c[k][0] * HEIGHT;
            double du = up - c[k][1] * HEIGHT;
            double df = forward - c[k][2] * HEIGHT;
            if (dl * dl + du * du + df * df <= r * r) {
               return true;
            }
         }
      }
      return false;
   }

   /**
    * What lands on a target: the arm whose swipe reaches it (or NONE), the tail strike to its side, the stomp's wave,
    * the body slam, the breath.
    */
   public record Reach(GodzillaMove claw, boolean tail, boolean stomp, boolean slam, boolean beam) {
      public static final Reach NONE = new Reach(GodzillaMove.NONE, false, false, false, false);

      /** Some move lands from here (ready or not). */
      public boolean any() {
         return this.claw != GodzillaMove.NONE || this.tail || this.stomp || this.slam || this.beam;
      }
   }

   /**
    * Whether the body slam (GodzillaActions.slam: a sphere about the chest, carried forward by the lunge while it
    * hurts) covers a body-frame point (blocks): the answer to a flier in front of the chest, below the claws' arc
    * (playtest: a hero hovering 60 blocks up in front of it was never attacked).
    */
   public static boolean slamReaches(double left, double up, double forward) {
      GodzillaMove slam = GodzillaMove.SLAM;
      double step = SLAM_LUNGE / (slam.contactEnd - slam.contact + 1);
      double r = SLAM_RADIUS - REACH_SLACK;
      for (int k = 0; k <= SLAM_HURT; k++) {
         double dl = left - SLAM_CENTRE[0] * HEIGHT;
         double du = up - SLAM_CENTRE[1] * HEIGHT;
         double df = forward - (SLAM_CENTRE[2] * HEIGHT + step * k);
         if (dl * dl + du * du + df * df <= r * r) {
            return true;
         }
      }
      return false;
   }

   /** Over this height (blocks) nothing but the breath reaches a flier (the claws' arc tops out under it). */
   public static final float MELEE_TOP = 0.64F * HEIGHT;
   /** Too close by this much (blocks) for every move: it walks off to its stand-off distance. */
   public static final float BACK_OFF = 8.0F;

   /**
    * Flat distance (blocks, body centre to target) the kaiju closes in to, or walks back to, for a target this high
    * (blocks above its ground): into the stomp's wave on the street, through the building under a hero on a low roof,
    * the slam and the claws in front of the chest, and for a flier over that the nearest spot the beam can pitch to
    * (playtest: it closed in under fliers, where nothing it has reaches).
    */
   public static double standOff(double up) {
      if (up <= STOMP_RISE) {
         return STOMP_CLOSE * HEIGHT;
      }
      if (up < 0.30 * HEIGHT) {
         return 0.12 * HEIGHT;
      }
      if (up <= MELEE_TOP) {
         return SLAM_CENTRE[2] * HEIGHT + SLAM_LUNGE * 0.3;
      }
      for (double f = 0.25 * HEIGHT; f < BEAM_RANGE; f += 2.0) {
         if (beamReaches(0.0, up, f)) {
            return f + 4.0;
         }
      }
      return 0.25 * HEIGHT;
   }

   /**
    * Whether the stomp's wave (GodzillaActions.stomp: the foot on the target's side) covers a body-frame point
    * (blocks) at a gait phase; NaN: at every phase (the feet are where the walk left them).
    */
   public static boolean stompReaches(double left, double up, double forward, double gait) {
      if (up > STOMP_RISE) {
         return false;
      }
      boolean side = left >= 0.0;
      float[] leg = PART_REST[side ? P_LEG_L : P_LEG_R];
      double fl = leg[0] * HEIGHT;
      for (int i = 0; i < 20; i++) {
         double phase = Double.isNaN(gait) ? i / 20.0 : gait;
         double ff = (leg[2] + footForward(phase, side)) * HEIGHT;
         if (Math.hypot(left - fl, forward - ff) > STOMP_WAVE - REACH_SLACK) {
            return false;
         }
         if (!Double.isNaN(gait)) {
            break;
         }
      }
      return true;
   }

   /** Reach of the melee moves on a body-frame point (blocks: left, up from the ground, forward), feet at any phase. */
   public static Reach reach(double left, double up, double forward) {
      return reach(left, up, forward, Double.NaN);
   }

   /** Reach with the feet where the gait phase put them (the server's body). */
   public static Reach reach(double left, double up, double forward, double gait) {
      return new Reach(clawFor(left, up, forward), tailReaches(left >= 0.0 ? 1 : -1, left, up, forward), stompReaches(left, up, forward, gait),
         slamReaches(left, up, forward), beamReaches(left, up, forward));
   }

   /**
    * Classifies a target from its body-frame offset (blocks: left, up from the ground, forward) and what reaches it.
    * Low and within the stomp's wave = FEET; behind within the tail's length = BEHIND; high = AIR_NEAR (a claw lands)
    * or AIR_FAR; a claw lands = FRONT, or SIDE_L / SIDE_R when only that arm reaches; near but out of every move's
    * reach (a roof below the claws, the flank) = CLOSE; else FAR.
    */
   public static Zone classify(double left, double up, double forward) {
      return classify(left, up, forward, reach(left, up, forward));
   }

   public static Zone classify(double left, double up, double forward, Reach reach) {
      double flat = Math.sqrt(left * left + forward * forward);
      double angle = Math.toDegrees(Math.atan2(left, forward));
      if (up > 0.62 * HEIGHT || up > 0.3 * HEIGHT && flat > 0.45 * HEIGHT) {
         return reach.claw() != GodzillaMove.NONE ? Zone.AIR_NEAR : Zone.AIR_FAR;
      }
      if (reach.stomp()) {
         return Zone.FEET;
      }
      if (Math.abs(angle) > 115.0 && flat < 1.4 * HEIGHT) {
         return Zone.BEHIND;
      }
      if (reach.claw() != GodzillaMove.NONE) {
         // FRONT: both arms land (the right one swings); SIDE_*: only that arm reaches
         double reachable = CLAW_RADIUS - REACH_SLACK;
         boolean both = clawMiss(GodzillaMove.CLAW_L, left, up, forward) <= reachable && clawMiss(GodzillaMove.CLAW_R, left, up, forward) <= reachable;
         if (both && Math.abs(angle) <= 50.0) {
            return Zone.FRONT;
         }
         return reach.claw() == GodzillaMove.CLAW_L ? Zone.SIDE_L : Zone.SIDE_R;
      }
      if (flat < 0.5 * HEIGHT) {
         return Zone.CLOSE;
      }
      return Zone.FAR;
   }

   /** Cooldown state the move choice reads (ticks left, 0 = ready). */
   public record Cooldowns(int claw, int tail, int stomp, int slam, int roar, int breath) {
   }

   /**
    * The move for a target zone (NONE: walk towards it / keep tracking). Breath for dangerous or far targets and
    * fliers out of the claws' reach, the tail where it sweeps, a claw only where the swipe lands, a stomp at the feet,
    * a roar now and then. A melee move never goes where its own hit geometry does not reach ({@link #reach}).
    */
   public static GodzillaMove choose(Zone zone, Cooldowns cd, boolean rage, float threat, float healthShare) {
      return choose(zone, defaultReach(zone), cd, rage, threat, healthShare);
   }

   /** The reach a zone implies when only the zone is known (tests, commands). */
   static Reach defaultReach(Zone zone) {
      return switch (zone) {
         case FEET -> new Reach(GodzillaMove.NONE, false, true, false, false);
         case BEHIND -> new Reach(GodzillaMove.NONE, true, false, false, false);
         case FRONT, AIR_NEAR -> new Reach(GodzillaMove.CLAW_R, false, false, false, true);
         case SIDE_L -> new Reach(GodzillaMove.CLAW_L, true, false, false, false);
         case SIDE_R -> new Reach(GodzillaMove.CLAW_R, true, false, false, false);
         case CLOSE, FAR, AIR_FAR -> new Reach(GodzillaMove.NONE, false, false, false, true);
         default -> Reach.NONE;
      };
   }

   public static GodzillaMove choose(Zone zone, Reach reach, Cooldowns cd, boolean rage, float threat, float healthShare) {
      boolean dangerous = threat >= (rage ? 60.0F : 110.0F) || healthShare < 0.35F;
      // the breath only where the head can point the beam through the target
      boolean breath = cd.breath() <= 0 && reach.beam();
      GodzillaMove claw = cd.claw() <= 0 ? reach.claw() : GodzillaMove.NONE;
      GodzillaMove tail = reach.tail() && cd.tail() <= 0 ? GodzillaMove.TAIL : GodzillaMove.NONE;
      GodzillaMove slam = reach.slam() && cd.slam() <= 0 ? GodzillaMove.SLAM : GodzillaMove.NONE;
      switch (zone) {
         case FEET:
            if (reach.stomp() && cd.stomp() <= 0) {
               return GodzillaMove.STOMP;
            }
            return tail;
         case BEHIND:
            return tail;
         case FRONT:
            if (breath && dangerous) {
               return GodzillaMove.CHARGE;
            }
            return claw != GodzillaMove.NONE ? claw : slam;
         case SIDE_L:
         case SIDE_R:
            return claw != GodzillaMove.NONE ? claw : tail;
         case AIR_NEAR:
            if (breath && dangerous) {
               return GodzillaMove.CHARGE;
            }
            if (claw != GodzillaMove.NONE) {
               return claw;
            }
            if (slam != GodzillaMove.NONE) {
               return slam;
            }
            return cd.roar() <= 0 ? GodzillaMove.ROAR : GodzillaMove.NONE;
         case CLOSE:
            if (breath && dangerous) {
               return GodzillaMove.CHARGE;
            }
            return slam != GodzillaMove.NONE ? slam : tail;
         case AIR_FAR:
         case FAR:
            if (breath) {
               return GodzillaMove.CHARGE;
            }
            if (slam != GodzillaMove.NONE) {
               return slam;
            }
            return cd.roar() <= 0 && threat > 20.0F ? GodzillaMove.ROAR : GodzillaMove.NONE;
         default:
            return GodzillaMove.NONE;
      }
   }

   // ------------------------------------------------------------------ destruction budgets

   /** Blocks the kaiju may remove per tick, all sources together. */
   public static final int BLOCKS_PER_TICK = 360;
   public static final int BLOCKS_PER_TICK_RAGE = 520;
   /**
    * Blocks per session (all sources; the city journal keeps every original for /city reset: about 45 bytes each in
    * memory, a palette per chunk on disk, and the reset puts back 6000 per tick).
    */
   public static final int MAX_BLOCKS = 900000;
   /**
    * The last blocks of the session budget are kept for the way out: when the rampage has used the rest, the city is
    * in ruins and the kaiju leaves (it must still push through what stands between it and the edge).
    */
   public static final int RETREAT_RESERVE = 60000;
   /** Flying debris entities alive at once. */
   public static final int MAX_DEBRIS = 48;
   /** FX packets per tick. */
   public static final int FX_PER_TICK = 24;
   /** Aftermath reports: at most one per cell of this size per interval. */
   public static final int REPORT_CELL = 24;
   public static final int AFTERMATH_INTERVAL = 60;
   public static final int INFRA_INTERVAL = 80;
   /** FX range: full effects, and the far range of the simplified ones. */
   public static final double FX_NEAR = 192.0;
   public static final double FX_FAR = 512.0;

   public static int blockBudget(boolean rage) {
      return rage ? BLOCKS_PER_TICK_RAGE : BLOCKS_PER_TICK;
   }

   /** Session blocks available in a phase: all of them on the way out, the rest keeps the reserve. */
   public static int blockCap(boolean retreat) {
      return retreat ? MAX_BLOCKS : MAX_BLOCKS - RETREAT_RESERVE;
   }

   /** The rampage has used its share: the kaiju leaves the ruined city. */
   public static boolean sated(int destroyed) {
      return destroyed >= blockCap(false);
   }

   /**
    * Farthest spawn distance at which the body still ticks: entities only tick inside the players' simulation
    * distance (chunks), so a kaiju spawned beyond it would stand frozen until the event lost it.
    */
   public static double spawnReach(int simulationChunks) {
      return Math.max(40.0, (simulationChunks - 1) * 16.0 - 8.0);
   }

   /** Severity (0..1) of an aftermath report for a number of destroyed blocks. */
   public static float severity(int blocks) {
      return Math.max(0.2F, Math.min(1.0F, blocks / 600.0F));
   }


   // ------------------------------------------------------------------ fall, rise and death

   /** Roll of the whole body onto its left side (0 standing .. 1 lying) during the ground moves. */
   public static float fallTilt(GodzillaMove move, float t) {
      return switch (move) {
         case FALL -> smooth((t - 6.0F) / 28.0F);
         case DOWN -> 1.0F;
         case RISE -> 1.0F - smooth((t - 10.0F) / 56.0F);
         case DEATH -> smooth((t - 20.0F) / 42.0F);
         default -> 0.0F;
      };
   }

   /** Roll angle at full tilt and the pivot of the roll (the left foot edge, fraction of HEIGHT). */
   public static final float FALL_ROLL = 82.0F;
   public static final float FALL_PIVOT = 0.11F;

   // ------------------------------------------------------------------ helpers

   public static float smooth(float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      return t * t * (3.0F - 2.0F * t);
   }

   private GodzillaRules() {
   }
}
