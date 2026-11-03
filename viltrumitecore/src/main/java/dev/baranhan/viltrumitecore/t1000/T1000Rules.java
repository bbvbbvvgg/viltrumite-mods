package dev.baranhan.viltrumitecore.t1000;

/**
 * Pure numbers of the T-1000 (covered by T1000RulesTest): timings of the forms, the arm morph, the attacks,
 * the AI distances and the clamps of the operator settings. No Minecraft classes, so the tests load it.
 */
public final class T1000Rules {
   // --- body -------------------------------------------------------------------------------------------------
   public static final float WIDTH = 0.6F;
   public static final float HEIGHT = 1.8F;
   /** Hitbox height of the puddle. */
   public static final float PUDDLE_HEIGHT = 0.3F;
   public static final float PUDDLE_WIDTH = 1.4F;
   public static final int REVEAL_TICKS = 26;
   public static final int CONCEAL_TICKS = 34;
   public static final int MELT_TICKS = 16;
   public static final int PUDDLE_TICKS = 90;
   public static final int REFORM_TICKS = 30;
   public static final int DEATH_TICKS = 80;
   /** Throes first, then the body sinks into a puddle that boils away. */
   public static final int DEATH_MELT_START = 34;
   /** Hand to weapon: slow, visible (the reveal); in a fight the morph is quicker. */
   public static final int MORPH_SLOW_TICKS = 30;
   public static final int MORPH_FAST_TICKS = 10;
   public static final int MORPH_BACK_TICKS = 16;
   /** A bullet hole or a cut closes over this many ticks. */
   public static final int WOUND_CLOSE_TICKS = 60;
   public static final int MAX_WOUNDS = 8;

   // --- AI ---------------------------------------------------------------------------------------------------
   /** Calm citizen time before the first hunt after a spawn, ticks (min, random extra). */
   public static final int FIRST_HUNT_MIN = 600;
   public static final int FIRST_HUNT_RANDOM = 1200;
   /** Calm citizen time after a lost or finished hunt. */
   public static final int HUNT_COOLDOWN_MIN = 2400;
   public static final int HUNT_COOLDOWN_RANDOM = 2400;
   /** Targets are picked inside this radius. */
   public static final double ACQUIRE_RANGE = 64.0;
   /** Stalking band around the target. */
   public static final double STALK_MIN = 7.0;
   public static final double STALK_MAX = 14.0;
   /** Behind the target at this distance during the search. */
   public static final double SEARCH_BEHIND = 12.0;
   /** Being looked at: inside this half angle of the target's view, line of sight clear, closer than this. */
   public static final double GAZE_HALF_DEG = 22.0;
   public static final double GAZE_RANGE = 26.0;
   /** Suspicion: + per watched tick (more when close), - per unwatched tick; reveal at the top. */
   public static final float SUSPICION_MAX = 70.0F;
   /** The target walked right up to it. */
   public static final double TOO_CLOSE = 2.6;
   /** Ambush: the target's back is turned (angle of its look away from us) and we are this close. */
   public static final double AMBUSH_RANGE = 6.5;
   public static final double BACK_TURNED_DEG = 110.0;
   public static final int STALK_MAX_TICKS = 2400;
   public static final int SEARCH_MAX_TICKS = 2400;
   /** Chase: lose the target after this long without seeing it. */
   public static final int LOST_TICKS = 600;
   /** Repath at most this often (ticks) while chasing, and only when the target moved. */
   public static final int CHASE_REPATH = 10;
   public static final int STALK_REPATH = 40;
   /** Speed modifiers on the 0.25 citizen base speed (ground speed grows with the square of the product). */
   public static final double STALK_SPEED = 0.6;
   public static final double SEARCH_SPEED = 0.72;
   public static final double CHASE_SPEED = 1.36;
   public static final double CHASE_SPEED_FAR = 1.46;
   /** Thin obstacles it flows through, at most this deep. */
   public static final int FLOW_MAX_DEPTH = 2;
   /** Concealment: a body this far from every camera (and unseen) may change its face. */
   public static final double CONCEAL_UNSEEN = 20.0;
   public static final int CONCEAL_MAX_TICKS = 900;
   /** Far from every player for this long: it slips away (dormant record, reappears near a player later). */
   public static final double FAR_FROM_PLAYERS = 96.0;
   public static final int FAR_TICKS = 1200;

   // --- combat -----------------------------------------------------------------------------------------------
   public static final float STAB_DAMAGE = 7.0F;
   public static final float SLASH_DAMAGE = 8.0F;
   public static final float HOOK_DAMAGE = 5.0F;
   public static final float GRAB_DAMAGE = 4.0F;
   public static final float THROW_DAMAGE = 6.0F;
   public static final float COUNTER_DAMAGE = 9.0F;
   public static final double STAB_REACH = 2.7;
   public static final double SLASH_REACH = 3.1;
   public static final double SLASH_ARC_DEG = 65.0;
   public static final double HOOK_MIN = 3.5;
   public static final double HOOK_MAX = 8.0;
   public static final double GRAB_REACH = 2.2;
   public static final int STAB_COOLDOWN = 16;
   public static final int SLASH_COOLDOWN = 30;
   public static final int HOOK_COOLDOWN = 90;
   public static final int GRAB_COOLDOWN = 140;
   public static final int COUNTER_COOLDOWN = 100;
   public static final int LEAP_COOLDOWN = 50;
   public static final int FLOW_COOLDOWN = 30;
   /** Chance (0..1) to parry a melee hit while the blade is out and the counter is ready. */
   public static final double COUNTER_CHANCE = 0.35;

   // --- operator settings ------------------------------------------------------------------------------------
   public static final double MIN_CHECK_SECONDS = 10.0;
   public static final double MAX_CHECK_SECONDS = 86400.0;
   public static final int MAX_ACTIVE_LIMIT = 8;
   /** Dormant instances wait this long before they come back near a player. */
   public static final int WAKE_DELAY_TICKS = 2400;
   /** After a server start nothing comes back for this long (no burst of returns after a restart). */
   public static final int STARTUP_GRACE_TICKS = 3600;
   /** Dormant and natural checks run this often (ticks). */
   public static final int SCHEDULER_PERIOD = 400;

   private T1000Rules() {
   }

   public static float clamp01(float v) {
      return v < 0.0F ? 0.0F : v > 1.0F ? 1.0F : v;
   }

   public static float smooth(float t) {
      return t * t * (3.0F - 2.0F * t);
   }

   /** Morph length of one arm change: to a weapon slowly during the reveal, quickly in a fight, back to a hand. */
   public static int morphTicks(T1000Arm from, T1000Arm to, boolean slow) {
      if (from == to) {
         return 1;
      }
      if (to == T1000Arm.HAND) {
         return MORPH_BACK_TICKS;
      }
      return slow ? MORPH_SLOW_TICKS : MORPH_FAST_TICKS;
   }

   /** 0..1 progress of a morph started {@code elapsed} ticks ago. */
   public static float morph(float elapsed, int ticks) {
      return smooth(clamp01(elapsed / Math.max(1, ticks)));
   }

   /** Wound openness 1 (fresh) .. 0 (closed), slower while burning (no regeneration). */
   public static float woundOpen(float elapsed) {
      return 1.0F - smooth(clamp01((elapsed - 6.0F) / (WOUND_CLOSE_TICKS - 6.0F)));
   }

   public static long checkIntervalTicks(double seconds) {
      double s = Double.isFinite(seconds) ? Math.max(MIN_CHECK_SECONDS, Math.min(MAX_CHECK_SECONDS, seconds)) : 300.0;
      return Math.round(s * 20.0);
   }

   /** Percent 0..100 to a 0..1 chance (NaN and junk read as 0). */
   public static double chance(double percent) {
      return Double.isFinite(percent) ? Math.max(0.0, Math.min(100.0, percent)) / 100.0 : 0.0;
   }

   public static long respawnDelayTicks(double minutes) {
      double m = Double.isFinite(minutes) ? Math.max(0.0, Math.min(144000.0, minutes)) : 120.0;
      return Math.round(m * 1200.0);
   }

   public static int maxActive(int configured) {
      return Math.max(1, Math.min(MAX_ACTIVE_LIMIT, configured));
   }

   /** Angle in degrees between a look (lx, lz) and the direction (dx, dz); 180 when either is zero. */
   public static double angleDeg(double lx, double lz, double dx, double dz) {
      double ll = Math.sqrt(lx * lx + lz * lz);
      double dl = Math.sqrt(dx * dx + dz * dz);
      if (ll < 1.0E-6 || dl < 1.0E-6) {
         return 180.0;
      }
      double cos = (lx * dx + lz * dz) / (ll * dl);
      return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
   }

   /** Suspicion change for one tick: watched closer = faster, unwatched decays. */
   public static float suspicionStep(boolean watched, double distance) {
      if (!watched) {
         return -0.5F;
      }
      double near = 1.0 - Math.max(0.0, Math.min(1.0, (distance - 4.0) / (GAZE_RANGE - 4.0)));
      return (float)(0.35 + 1.4 * near);
   }

   /**
    * Ambush opportunity while stalking: close enough and the back turned, plus at least one reason (alone,
    * indoors, hurt) — or simply very close behind.
    */
   public static boolean opportunity(double distance, double lookAwayDeg, boolean alone, boolean indoors, float healthShare) {
      if (distance > AMBUSH_RANGE || lookAwayDeg < BACK_TURNED_DEG) {
         return false;
      }
      return alone || indoors || healthShare < 0.5F || distance < 3.5;
   }

   /** Ballistic leap: initial velocity {vx, vy, vz} to land at (dx, dy, dz), drag and gravity of a mob. */
   public static double[] leap(double dx, double dy, double dz) {
      double horiz = Math.sqrt(dx * dx + dz * dz);
      double apex = Math.max(1.1, dy + 1.1);
      double g = 0.08;
      double vy = Math.min(1.05, Math.sqrt(2.0 * g * apex) + 0.02);
      // ticks up + ticks down (rough, vertical drag 0.98)
      double up = vy / g;
      double down = Math.sqrt(2.0 * Math.max(0.2, apex - dy) / g);
      double t = Math.max(6.0, up + down);
      // horizontal drag 0.91 per tick in the air: distance = v * (1 - 0.91^t) / 0.09
      double k = (1.0 - Math.pow(0.91, t)) / 0.09;
      double v = Math.min(1.25, horiz / k);
      double s = horiz < 1.0E-6 ? 0.0 : v / horiz;
      return new double[]{dx * s, vy, dz * s};
   }
}
