package dev.baranhan.viltrumitecore.hero.captain;

/**
 * Every number of Captain America (spec docs/design/2026-11-03-captain-america-design.md §8). Pure: no world
 * access, unit-tested in CaptainRulesTest. Times are ticks, distances blocks.
 */
public final class CaptainRules {
   // --- chassis (§4) ---
   public static final double HEALTH_BONUS = 20.0;
   public static final double ARMOR = 8.0;
   public static final double TOUGHNESS = 4.0;
   public static final double SPEED_BONUS = 0.12;
   public static final double KNOCKBACK_RESIST = 0.3;
   public static final int REGEN_DELAY = 100;
   public static final int REGEN_INTERVAL = 40;
   public static final float REGEN_AMOUNT = 1.0F;
   public static final float SAFE_FALL = 14.0F;
   public static final float FALL_FACTOR = 0.3F;
   public static final float ROLL_FALL = 5.0F;

   // --- kinetic charge (§1.2) ---
   public static final int KINETIC_MAX = 100;
   public static final int EMPOWER_COST = 50;
   public static final int GAIN_BLOCK = 6;
   public static final int GAIN_PARRY = 30;
   public static final int GAIN_RICOCHET = 8;
   /** Gain of the 4th and later enemy hit of one flight, and of every wall bounce (see bounceGain). */
   public static final int GAIN_RICOCHET_TAIL = 2;
   public static final int GAIN_WALL = 2;
   public static final int GAIN_COMBO = 3;
   public static final int KINETIC_IDLE = 120;
   public static final int KINETIC_DECAY_INTERVAL = 20;

   // --- combo (§5.1) ---
   public static final int COMBO_WINDOW = 10;
   public static final int BUFFER = 5;
   public static final double REACH = 3.4;
   public static final double ARC_DEGREES = 110.0;
   public static final float[] SHIELD_DAMAGE = {7.0F, 7.0F, 8.0F, 12.0F};
   public static final float[] FIST_DAMAGE = {5.0F, 6.0F, 9.0F};
   public static final float FINISHER_AREA_DAMAGE = 6.0F;
   public static final double FINISHER_RADIUS = 2.5;
   public static final double UPPERCUT_LIFT = 0.55;
   public static final double KICK_KNOCKBACK = 1.2;
   public static final float COUNTER_DAMAGE = 16.0F;
   public static final int COUNTER_STUN = 30;
   public static final int COUNTER_WINDOW = 40;
   public static final float RETURN_STRIKE_DAMAGE = 14.0F;
   public static final double RETURN_STRIKE_KNOCKBACK = 1.4;
   public static final int CATCH_WINDOW = 40;

   // --- guard (§5.2) ---
   public static final float GUARD_REDUCTION = 0.85F;
   public static final float ARM_GUARD_REDUCTION = 0.4F;
   public static final double GUARD_ARC_DEGREES = 120.0;
   public static final int PARRY_WINDOW = 5;
   public static final int PARRY_STUN = 20;
   public static final double PARRY_KNOCKBACK = 0.9;
   public static final double GUARD_SLOW = -0.4;

   // --- ricochet throw (§5.3) ---
   public static final double SHIELD_SPEED = 1.4;
   public static final double RETURN_SPEED = 1.5;
   // Ricochet chain (rework §16): 5 bounces, 8 empowered (was 3 / 5). With the 15 % falloff a full chain deals
   // 9 + 7.7 + 6.5 + 5.5 + 4.7 ≈ 33 spread over five different enemies (≈ 44 over eight when empowered) — enough to
   // break up a small pack, while the per-flight hit set keeps every enemy at one outgoing hit, so a lone boss still
   // takes a single 9. The kinetic gain of the extra bounces is cut (bounceGain), so the longer chain cannot refill
   // the charge it spends: an empowered throw returns at most ≈ 21 of its 50.
   public static final int BOUNCES = 5;
   public static final int BOUNCES_EMPOWERED = 8;
   public static final double BOUNCE_RADIUS = 10.0;
   /** Next-target assist: half-angle of the cone around the incoming flight line the next enemy must be in. */
   public static final double BOUNCE_ASSIST_DEGREES = 135.0;
   /** ... at most this far above / below the hit point ... */
   public static final double BOUNCE_MAX_DY = 6.0;
   /** ... and never further than THROW_RANGE + this from Steve (no homing across the map). */
   public static final double BOUNCE_LEASH = 6.0;
   /** Soft homing: the flying shield bends at most this much per tick toward its target. */
   public static final double SHIELD_TURN_DEGREES = 20.0;
   /** A target that stays further than this off the flight line for SHIELD_GIVE_UP_TICKS is skipped (no U-turns). */
   public static final double SHIELD_GIVE_UP_DEGREES = 80.0;
   public static final int SHIELD_GIVE_UP_TICKS = 6;
   /** Longest chase of one target before the shield gives up on it. */
   public static final int SHIELD_CHASE_TICKS = 40;
   public static final double THROW_RANGE = 24.0;
   public static final float SHIELD_HIT_DAMAGE = 9.0F;
   public static final float BOUNCE_FALLOFF = 0.15F;
   public static final float RECALL_DAMAGE = 6.0F;
   public static final int AIM_THRESHOLD = 6;
   public static final int AIM_MAX = 80;
   public static final int WALL_BOUNCES = 4;
   public static final double CATCH_RADIUS = 1.6;
   public static final double ROLL_CATCH_BONUS = 1.0;
   public static final int SHIELD_LOST_TICKS = 120;
   public static final int SHIELD_MAX_FLIGHT = 200;
   public static final int THROW_COOLDOWN = 16;
   public static final int THROW_RELEASE_TICK = 4;

   // --- rush (§5.4) ---
   public static final int RUSH_TICKS = 12;
   public static final double RUSH_SPEED = 0.78;
   public static final float RUSH_PASS_DAMAGE = 6.0F;
   public static final float RUSH_BASH_DAMAGE = 10.0F;
   public static final float RUSH_TACKLE_DAMAGE = 8.0F;
   public static final int RUSH_STUN = 25;
   public static final int RUSH_COOLDOWN = 100;
   public static final int RUSH_FOLLOW_WINDOW = 10;

   // --- slam (§5.5) ---
   public static final double SLAM_UP = 0.95;
   public static final double SLAM_FORWARD = 0.55;
   public static final double SLAM_DIVE = -1.6;
   public static final int SLAM_RISE_TICKS = 9;
   public static final int SLAM_MAX_TICKS = 50;
   public static final double SLAM_RADIUS = 4.0;
   public static final double SLAM_RADIUS_EMPOWERED = 6.0;
   public static final float SLAM_DAMAGE = 10.0F;
   public static final float SLAM_DAMAGE_EMPOWERED = 14.0F;
   public static final int SLAM_COOLDOWN = 160;

   // --- roll (§5.6) ---
   public static final int ROLL_TICKS = 10;
   public static final double ROLL_SPEED = 0.62;
   public static final int ROLL_IFRAMES = 8;
   public static final int ROLL_FOLLOW_WINDOW = 10;
   public static final int ROLL_COOLDOWN = 50;

   // --- ultimate (§5.7) ---
   public static final int ULT_COOLDOWN = 6000;
   public static final int CALL_TICKS = 30;
   public static final int QUINJET_START = 20;
   public static final int QUINJET_OVERHEAD = 90;
   public static final int QUINJET_JUMP = 92;
   public static final int QUINJET_END = 200;
   public static final int IRONMAN_START = 30;
   public static final int IRONMAN_LAND = 88;
   public static final int THOR_BOLT = 70;
   public static final int HULK_START = 55;
   public static final int HULK_LAND = 100;
   public static final int FORM_START = 112;
   public static final int FORM_DEADLINE = 150;
   public static final int CIRCLE_TICKS = 80;
   public static final int FIGHT_TICKS = 1200;
   public static final int DEPART_TICKS = 90;
   public static final double CIRCLE_RADIUS = 3.4;
   public static final double CIRCLE_RADIUS_MIN = 2.2;
   public static final double CIRCLE_AHEAD = 3.2;
   public static final float CEREMONY_DAMAGE_FACTOR = 0.5F;
   public static final double LEASH = 24.0;
   public static final double LEASH_TELEPORT = 40.0;
   public static final double TARGET_RANGE = 24.0;
   // --- Avengers' targets (out of reach = filtered out, not only scored lower) ---
   /** Search radius around the Avenger itself; {@link #TARGET_RANGE} around Steve bounds the field. */
   public static final double TARGET_SEARCH = 26.0;
   /** Height window around Steve's feet: below it are cave and under-floor mobs, above it cliff tops. */
   public static final double TARGET_DROP = 6.0;
   public static final double TARGET_RISE = 8.0;
   /** Height window around a walking Avenger's own feet (it can drop down a ledge, not climb a wall). */
   public static final double TARGET_SELF_DROP = 8.0;
   public static final double TARGET_SELF_RISE = 5.0;
   /** Iron Man and Thor fly: a wide window around themselves (Steve's window still applies). */
   public static final double TARGET_FLYER_WINDOW = 14.0;
   /** A current target out of sight (of the Avenger and of Steve) this long is dropped and skipped for a while. */
   public static final int TARGET_UNSEEN_TICKS = 40;
   public static final double OBSERVER_RANGE = 96.0;

   // --- ability cooldown slots (extraCooldowns indices) ---
   public static final int CD_THROW = 0;
   public static final int CD_RUSH = 1;
   public static final int CD_SLAM = 2;
   public static final int CD_ROLL = 3;
   public static final int CD_ULT = 4;
   public static final int COOLDOWNS = 5;

   private CaptainRules() {
   }

   /** Next combo step of a chain of {@code length} steps: continues inside the window, else restarts. */
   public static int nextStep(int previous, int idle, int length) {
      if (previous < 0 || idle > COMBO_WINDOW || previous + 1 >= length) {
         return 0;
      }
      return previous + 1;
   }

   /** Input during the last {@link #BUFFER} ticks of a move is queued. */
   public static boolean buffers(int elapsed, int length) {
      return elapsed >= length - BUFFER;
   }

   /** Kinetic charge after a gain, clamped. */
   public static int gain(int charge, int amount) {
      return Math.max(0, Math.min(KINETIC_MAX, charge + amount));
   }

   public static boolean empowered(int charge) {
      return charge >= EMPOWER_COST;
   }

   /** Damage of the {@code bounce}-th shield hit (0 = first). */
   public static float bounceDamage(int bounce) {
      return SHIELD_HIT_DAMAGE * (float)Math.pow(1.0F - BOUNCE_FALLOFF, Math.max(0, bounce));
   }

   public static int maxBounces(boolean empowered) {
      return empowered ? BOUNCES_EMPOWERED : BOUNCES;
   }

   /** Kinetic gain of the {@code hit}-th enemy hit of one flight (0 = first): 8 / 8 / 8, then 2; halved when empowered. */
   public static int bounceGain(int hit, boolean empowered) {
      int g = hit < 3 ? GAIN_RICOCHET : GAIN_RICOCHET_TAIL;
      return empowered ? Math.max(1, g / 2) : g;
   }

   /** Kinetic gain of a wall bounce. */
   public static int wallGain(boolean empowered) {
      return empowered ? Math.max(1, GAIN_WALL / 2) : GAIN_WALL;
   }

   /**
    * Next-bounce score of an enemy (lower is better): the nearest one wins, enemies ahead of the flight a little more.
    * +∞ outside the assist limits — further than {@link #BOUNCE_RADIUS}, outside the {@link #BOUNCE_ASSIST_DEGREES}
    * cone around the incoming flight line or more than {@link #BOUNCE_MAX_DY} above / below the hit point.
    */
   public static double bounceScore(double distance, double angleDegrees, double dy) {
      if (distance > BOUNCE_RADIUS || angleDegrees > BOUNCE_ASSIST_DEGREES || Math.abs(dy) > BOUNCE_MAX_DY) {
         return Double.POSITIVE_INFINITY;
      }
      return distance * (1.0 + 0.5 * angleDegrees / 90.0);
   }

   /** Fall damage factor: none up to {@link #SAFE_FALL}, then reduced. */
   public static float fallFactor(float distance) {
      return distance <= SAFE_FALL ? 0.0F : FALL_FACTOR;
   }

   /** True when the attacker stands in the frontal guard arc. {@code cos} is dot(look, toAttacker). */
   public static boolean inGuardArc(double cos) {
      return cos >= Math.cos(Math.toRadians(GUARD_ARC_DEGREES / 2.0));
   }

   /** Guard result: the damage that passes through. Parry = 0. */
   public static float guarded(float raw, boolean shield, boolean frontal, int guardTicks) {
      if (!frontal) {
         return raw;
      }
      if (shield && guardTicks < PARRY_WINDOW) {
         return 0.0F;
      }
      return raw * (1.0F - (shield ? GUARD_REDUCTION : ARM_GUARD_REDUCTION));
   }

   public static boolean isParry(boolean shield, boolean frontal, int guardTicks) {
      return shield && frontal && guardTicks < PARRY_WINDOW;
   }

   /** Slam shape. */
   public static double slamRadius(boolean empowered) {
      return empowered ? SLAM_RADIUS_EMPOWERED : SLAM_RADIUS;
   }

   public static float slamDamage(boolean empowered, double distance, double radius) {
      float base = empowered ? SLAM_DAMAGE_EMPOWERED : SLAM_DAMAGE;
      double f = 1.0 - 0.5 * Math.max(0.0, Math.min(1.0, distance / radius));
      return (float)(base * f);
   }

   // --- ultimate timeline ---

   public enum Phase { CALL, ARRIVAL, FORMATION, CIRCLE, FIGHT, DEPART, DONE }

   /** Scene phase at {@code t}; {@code circleStart} is when the circle actually began (or -1 while forming). */
   public static Phase phase(int t, int circleStart) {
      if (t < CALL_TICKS) {
         return Phase.CALL;
      }
      if (t < FORM_START) {
         return Phase.ARRIVAL;
      }
      if (circleStart < 0) {
         return Phase.FORMATION;
      }
      int c = t - circleStart;
      if (c < CIRCLE_TICKS) {
         return Phase.CIRCLE;
      }
      if (c < CIRCLE_TICKS + FIGHT_TICKS) {
         return Phase.FIGHT;
      }
      if (c < CIRCLE_TICKS + FIGHT_TICKS + DEPART_TICKS) {
         return Phase.DEPART;
      }
      return Phase.DONE;
   }

   /** The circle starts when everyone stands in place, but never later than the deadline. */
   public static boolean circleMayStart(int t, boolean allReady) {
      return t >= FORM_START && (allReady || t >= FORM_DEADLINE);
   }

   /** Circle slot angle (degrees, 0 = Steve) of the i-th avenger (0..4): six even places. */
   public static double slotAngle(int index) {
      return 60.0 * (index + 1);
   }
}
