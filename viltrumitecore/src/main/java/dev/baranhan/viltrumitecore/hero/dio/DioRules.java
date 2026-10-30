package dev.baranhan.viltrumitecore.hero.dio;

/**
 * DIO numbers (spec docs/design/2026-10-28-dio-design.md, "Сводка чисел") and
 * pure functions only: no world access, unit tested in DioRulesTest.
 */
public final class DioRules {
   // --- passives --------------------------------------------------------------
   public static final double BONUS_MAX_HEALTH = 20.0;
   public static final double BONUS_ARMOR = 6.0;
   public static final double BONUS_ATTACK = 5.0;
   /** Movement speed, MULTIPLY_BASE. */
   public static final double BONUS_SPEED = 0.10;
   /** Heal 1 HP every this many ticks outside sunlight. */
   public static final int REGEN_INTERVAL = 40;

   // --- blood -------------------------------------------------------------------
   public static final int BLOOD_MAX = 100;
   public static final int BLOOD_STAND_HIT = 3;
   public static final int BLOOD_BARRAGE_HIT = 1;
   public static final int BLOOD_KNIFE_HIT = 2;
   public static final int BLOOD_DRAIN_PULSE = 8;
   /** Night outside: +1 blood every this many ticks. */
   public static final int BLOOD_NIGHT_INTERVAL = 40;
   /** Sunlight: -1 blood every this many ticks. */
   public static final int BLOOD_SUN_INTERVAL = 20;
   /** Sunlight with no blood left: 1 damage every this many ticks. */
   public static final int SUN_BURN_INTERVAL = 40;
   public static final float SUN_BURN_DAMAGE = 1.0F;
   /** Stand damage factor in sunlight. */
   public static final float SUN_STAND_FACTOR = 0.75F;

   // --- The World ---------------------------------------------------------------
   public static final int SUMMON_TICKS = 14;
   public static final int DISMISS_TICKS = 10;
   public static final double STAND_REACH = 4.2;
   /** Half-angle of the stand's strike cone, degrees. */
   public static final double STAND_CONE_DEG = 32.0;

   /** LMB combo: jab left, jab right, hook. */
   public static final int[] COMBO_LENGTH = {9, 9, 14};
   public static final int[] COMBO_CONTACT = {4, 4, 7};
   public static final float[] COMBO_DAMAGE = {5.0F, 5.0F, 9.0F};
   public static final float[] COMBO_KNOCKBACK = {0.25F, 0.25F, 0.9F};
   /** A press within this many ticks after a strike ends continues the chain. */
   public static final int COMBO_WINDOW = 12;
   /** Pause after the hook before a new chain may start. */
   public static final int COMBO_RECOVER = 8;
   /** A press this many ticks before the current strike ends is buffered. */
   public static final int COMBO_BUFFER = 5;

   // --- MUDA barrage ------------------------------------------------------------
   public static final int BARRAGE_WINDUP = 6;
   public static final int BARRAGE_MAX = 60;
   public static final int BARRAGE_HIT_INTERVAL = 2;
   public static final float BARRAGE_HIT_DAMAGE = 1.5F;
   public static final double BARRAGE_RANGE = 3.6;
   public static final double BARRAGE_CONE_DEG = 35.0;
   public static final int FINISHER_LENGTH = 12;
   public static final int FINISHER_CONTACT = 5;
   public static final float FINISHER_DAMAGE = 8.0F;
   public static final float FINISHER_KNOCKBACK = 1.6F;
   public static final int BARRAGE_COOLDOWN = 140;
   /** Shortest barrage that still ends in a finisher (a tap = windup + a few hits). */
   public static final int BARRAGE_MIN = 10;

   // --- knives ------------------------------------------------------------------
   public static final int KNIFE_COUNT = 5;
   public static final double KNIFE_SPREAD_DEG = 12.0;
   public static final int KNIFE_WINDUP = 6;
   public static final int KNIFE_LENGTH = 14;
   public static final double KNIFE_SPEED = 2.2;
   public static final float KNIFE_DAMAGE = 4.0F;
   public static final int KNIFE_LIFE = 80;
   /** In stopped time a knife flies this far, then hangs in the air. */
   public static final double KNIFE_STOP_DISTANCE = 1.5;
   public static final int KNIFE_COOLDOWN = 120;

   // --- time stop ---------------------------------------------------------------
   public static final int TIME_STOP_MIN_BLOOD = 25;
   public static final int TIME_STOP_BASE = 60;
   public static final int TIME_STOP_BONUS = 120;
   public static final int TIME_STOP_CAST = 20;
   /** Cast tick at which the world stops (wave front leaves The World). */
   public static final int TIME_STOP_CAPTURE = 12;
   public static final double TIME_STOP_RADIUS = 40.0;
   public static final int TIME_STOP_RESCAN = 5;
   /** The last ticks of the field are the resume (line + reverse wave). */
   public static final int TIME_STOP_RESUME = 20;
   public static final int TIME_STOP_COOLDOWN = 900;
   /** Ledger payout cap for player victims, fraction of max health. */
   public static final float LEDGER_PLAYER_CAP = 0.6F;
   public static final float LEDGER_MAX_KNOCKBACK = 2.4F;

   // --- vampire drain -------------------------------------------------------------
   public static final double DRAIN_RANGE = 3.2;
   public static final double DRAIN_BREAK_DISTANCE = 4.5;
   public static final int DRAIN_LENGTH = 40;
   public static final int DRAIN_PULSE = 10;
   public static final float DRAIN_DAMAGE = 3.0F;
   public static final float DRAIN_HEAL = 2.0F;
   /** One hit at least this strong on DIO breaks the grip. */
   public static final float DRAIN_BREAK_DAMAGE = 4.0F;
   public static final int DRAIN_COOLDOWN = 300;

   // --- road roller (ultimate) ----------------------------------------------------
   public static final int ROLLER_BLOOD = 100;
   public static final int ROLLER_COOLDOWN = 2400;
   /**
    * The 9-second ult (180 ticks), one authoritative timeline shared by the server
    * hits/sounds and every client view: cast + capture, crouch, a big visible leap,
    * the roller materialising at the top of the arc, a short hang, the fall, the
    * slam, MUDA pulses on the roller, WRYYY, the resume blast and DIO hopping off.
    */
   public static final int ROLLER_LENGTH = 180;
   public static final int ROLLER_CAPTURE = 10;
   /** Knees bend from here to the leap (anticipation). */
   public static final int ROLLER_CROUCH = 14;
   public static final int ROLLER_LEAP = 26;
   /** Server tick of the appear sound ("ROAD ROLLER DA!"); the model shows two ticks later. */
   public static final int ROLLER_APPEAR = 46;
   public static final int ROLLER_APEX = 60;
   /** End of the hang at the top; the fall starts here. */
   public static final int ROLLER_HANG = 70;
   public static final int ROLLER_SLAM = 96;
   public static final int ROLLER_BARRAGE_END = 148;
   public static final int ROLLER_PULSE = 4;
   public static final int ROLLER_RESUME = 160;
   /** DIO's feet touch the ground again after the hop off the roller. */
   public static final int ROLLER_LAND = 174;
   public static final double ROLLER_RADIUS = 32.0;
   public static final double ROLLER_APEX_HEIGHT = 16.0;
   /** Extra float at the top of the arc during the hang. */
   public static final double ROLLER_HANG_FLOAT = 0.45;
   public static final double ROLLER_REACH = 24.0;
   public static final double ROLLER_FALLBACK = 10.0;
   public static final float ROLLER_SLAM_DAMAGE = 14.0F;
   public static final double ROLLER_SLAM_RADIUS = 3.5;
   public static final float ROLLER_PULSE_DAMAGE = 2.0F;
   public static final float ROLLER_BLAST_DAMAGE = 10.0F;
   public static final double ROLLER_BLAST_RADIUS = 5.0;
   /** Height of DIO's feet over the landing point while he sits on the roller. */
   public static final double ROLLER_SEAT = 2.3;

   // --- guard -------------------------------------------------------------------
   public static final double GUARD_FRONT_DEG = 70.0;
   public static final float GUARD_FACTOR = 0.3F;

   // --- snapshot ------------------------------------------------------------------
   /** Cooldown slots (HeroPublicSnapshot.cooldowns, panel order). */
   public static final int CD_STAND = 0, CD_BARRAGE = 1, CD_KNIVES = 2, CD_TIME_STOP = 3, CD_DRAIN = 4, CD_ROLLER = 5;
   /** extraCooldowns indices. */
   public static final int EX_STAND_PHASE = 0, EX_STAND_ELAPSED = 1, EX_FIELD_LEFT = 2, EX_FIELD_TOTAL = 3, EX_COMBO_STEP = 4;
   public static final int EXTRA_COUNT = 5;
   /** heroFlags bits. */
   public static final int FLAG_STAND = 0, FLAG_TIME_STOP = 1, FLAG_SUN = 2, FLAG_GUARD = 3, FLAG_RESUMING = 4, FLAG_ULT = 5, FLAG_FINISHER = 6, FLAG_DRAIN_HOLD = 7,
      FLAG_INFINITE_BLOOD = 8;
   /** Stand phases (extraCooldowns[EX_STAND_PHASE]). */
   public static final int STAND_NONE = 0, STAND_SUMMONING = 1, STAND_OUT = 2, STAND_DISMISSING = 3;

   private DioRules() {
   }

   /** Stopped time from the blood spent: 3 s at the minimum, 9 s at full. */
   public static int timeStopDuration(int blood) {
      int b = Math.max(TIME_STOP_MIN_BLOOD, Math.min(BLOOD_MAX, blood));
      return TIME_STOP_BASE + Math.round(TIME_STOP_BONUS * (b - TIME_STOP_MIN_BLOOD) / (float)(BLOOD_MAX - TIME_STOP_MIN_BLOOD));
   }

   public static int clampBlood(int blood) {
      return Math.max(0, Math.min(BLOOD_MAX, blood));
   }

   /** Next combo step for a press {@code sinceEnd} ticks after the previous strike ended (-1 = none yet). */
   public static int nextComboStep(int previousStep, int sinceEnd) {
      if (previousStep < 0 || sinceEnd < 0 || sinceEnd > COMBO_WINDOW) {
         return 0;
      }

      return (previousStep + 1) % COMBO_LENGTH.length;
   }

   /** True when a strike may start now: the hook leaves a short recovery before a new chain. */
   public static boolean comboReady(int previousStep, int sinceEnd) {
      return previousStep != COMBO_LENGTH.length - 1 || sinceEnd < 0 || sinceEnd >= COMBO_RECOVER;
   }

   /** Inside a cone of {@code halfDeg} around a unit look vector (pure vector math). */
   public static boolean inCone(double lookX, double lookY, double lookZ, double toX, double toY, double toZ, double halfDeg) {
      double len = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
      if (len < 1.0E-6) {
         return true;
      }

      double dot = (lookX * toX + lookY * toY + lookZ * toZ) / len;
      return dot >= Math.cos(Math.toRadians(halfDeg));
   }

   /** Frontal guard: the attacker direction lies within GUARD_FRONT_DEG of the look (horizontal). */
   public static boolean guardCovers(double lookX, double lookZ, double toAttackerX, double toAttackerZ) {
      double l = Math.sqrt(lookX * lookX + lookZ * lookZ), a = Math.sqrt(toAttackerX * toAttackerX + toAttackerZ * toAttackerZ);
      if (l < 1.0E-6 || a < 1.0E-6) {
         return true;
      }

      return (lookX * toAttackerX + lookZ * toAttackerZ) / (l * a) >= Math.cos(Math.toRadians(GUARD_FRONT_DEG));
   }

   /** Ledger payout: players are capped at a share of their max health, mobs take everything. */
   public static float ledgerPayout(float owed, float maxHealth, boolean player) {
      if (owed <= 0.0F) {
         return 0.0F;
      }

      return player ? Math.min(owed, LEDGER_PLAYER_CAP * maxHealth) : owed;
   }

   /** Stand damage with the sunlight penalty. */
   public static float standDamage(float base, boolean inSun) {
      return inSun ? base * SUN_STAND_FACTOR : base;
   }

   /** Barrage hit ticks after the windup: every BARRAGE_HIT_INTERVAL ticks. */
   public static boolean barrageHitTick(int elapsed) {
      return elapsed >= BARRAGE_WINDUP && (elapsed - BARRAGE_WINDUP) % BARRAGE_HIT_INTERVAL == 0;
   }

   /** Knife yaw offsets of the fan, degrees, symmetric around 0. */
   public static double knifeYawOffset(int index, int count) {
      if (count <= 1) {
         return 0.0;
      }

      return (index - (count - 1) / 2.0) * (KNIFE_SPREAD_DEG * 2.0 / (count - 1));
   }

   /** Road Roller pulses on top of the roller (ROLLER_SLAM, exclusive, to ROLLER_BARRAGE_END). */
   public static boolean rollerPulseTick(int elapsed) {
      return elapsed > ROLLER_SLAM && elapsed <= ROLLER_BARRAGE_END && (elapsed - ROLLER_SLAM) % ROLLER_PULSE == 0;
   }

   public static int rollerPulseCount() {
      int n = 0;
      for (int t = 0; t <= ROLLER_LENGTH; t++) {
         if (rollerPulseTick(t)) {
            n++;
         }
      }

      return n;
   }

   /**
    * DIO's scripted feet height during the ult ({@code t} in action ticks): on the
    * ground until the leap, a fast ease-out rise to ROLLER_APEX_HEIGHT, a short
    * floating hang, an accelerating fall onto the seat at the slam, the seat until
    * the resume, then a small hop back to the ground. Continuous everywhere; the
    * slope changes at the apex/hang joints stay under 0.15 block/tick (no hitch).
    */
   public static double rollerHeight(double t, double startY, double landY) {
      if (t <= ROLLER_LEAP) {
         return startY;
      }

      double apexY = Math.max(startY, landY) + ROLLER_APEX_HEIGHT;
      double seatY = landY + ROLLER_SEAT;
      if (t <= ROLLER_APEX) {
         double u = (t - ROLLER_LEAP) / (double)(ROLLER_APEX - ROLLER_LEAP);
         double e = 1.0 - (1.0 - u) * (1.0 - u);
         return startY + (apexY - startY) * e;
      }

      if (t <= ROLLER_HANG) {
         double u = (t - ROLLER_APEX) / (double)(ROLLER_HANG - ROLLER_APEX);
         return apexY + ROLLER_HANG_FLOAT * Math.sin(u * Math.PI) * (1.0 - 0.5 * u);
      }

      if (t <= ROLLER_SLAM) {
         double u = (t - ROLLER_HANG) / (double)(ROLLER_SLAM - ROLLER_HANG);
         return apexY + (seatY - apexY) * u * u;
      }

      if (t <= ROLLER_RESUME) {
         return seatY;
      }

      double u = Math.min(1.0, (t - ROLLER_RESUME) / (double)(ROLLER_LAND - ROLLER_RESUME));
      // Hop off: a small arc back down to the ground.
      return seatY + Math.sin(u * Math.PI) * 1.6 - (seatY - landY) * u;
   }

   /** Horizontal progress 0..1 from the start to the landing point. */
   public static double rollerTravel(double t) {
      if (t <= ROLLER_LEAP) {
         return 0.0;
      }

      if (t >= ROLLER_SLAM) {
         return 1.0;
      }

      double u = (t - ROLLER_LEAP) / (double)(ROLLER_SLAM - ROLLER_LEAP);
      return u * u * (3.0 - 2.0 * u);
   }
}
