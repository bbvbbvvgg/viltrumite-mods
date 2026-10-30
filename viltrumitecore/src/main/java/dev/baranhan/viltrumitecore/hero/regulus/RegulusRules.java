package dev.baranhan.viltrumitecore.hero.regulus;

/**
 * Every number of Regulus Corneas Reborn (spec docs/design/2026-10-30-regulus-reborn-design.md).
 * Ticks are server ticks (20 per second). The pure helpers here are what JUnit covers.
 */
public final class RegulusRules {
   // --- chassis (§4) ---------------------------------------------------------------------------
   public static final double BONUS_HEALTH = 20.0;
   public static final double BONUS_ARMOR = 8.0;
   public static final float SUPER_JUMP_VELOCITY = 1.5F;

   // --- heart strain (§5.0) --------------------------------------------------------------------
   public static final int STRAIN_MAX = 1000;
   public static final int STRAIN_LION_ON = 120;
   public static final int STRAIN_LION_TICK = 10;
   public static final int STRAIN_RECOVERY = 14;
   public static final int STRAIN_BACKLASH = 350;
   /** HUD/VFX warning threshold. */
   public static final int STRAIN_WARN = 700;
   public static final int SEIZURE_TICKS = 30;
   public static final int SEIZURE_LOCK = 160;
   public static final int SEIZURE_RESET = 400;
   public static final float SEIZURE_DAMAGE = 4.0F;

   // --- Lion's Heart (§5.1) --------------------------------------------------------------------
   public static final int LION_ON_TICKS = 8;
   public static final int LION_PROTECT_AT = 4;
   public static final int LION_OFF_TICKS = 10;
   public static final int LION_COOLDOWN = 20;
   public static final double LION_SPEED = 0.30;
   public static final int LION_AIR_STEPS = 2;
   public static final float RECOIL_FACTOR = 0.30F;
   public static final int RECOIL_GAP = 10;

   // --- Greed's Touch (§5.2) -------------------------------------------------------------------
   public static final double TOUCH_REACH = 3.6;
   public static final double TOUCH_CONE_DEGREES = 70.0;
   public static final int TOUCH_MAX_TARGETS = 3;
   public static final int TOUCH_CHAIN_WINDOW = 10;
   public static final float RUPTURE_DAMAGE = 8.0F;
   public static final int RUPTURE_DELAY = 10;

   // --- Stopped Sand (§5.3) --------------------------------------------------------------------
   public static final int SAND_RELEASE = 12;
   public static final int SAND_GRAINS = 9;
   public static final double SAND_SPREAD_DEGREES = 16.0;
   public static final double SAND_SPEED = 3.2;
   public static final double SAND_RANGE = 40.0;
   public static final float SAND_DAMAGE = 4.0F;
   public static final float SAND_DAMAGE_LION = 6.0F;
   public static final int SAND_PIERCE = 3;
   public static final float SAND_BLOCK_HARDNESS = 1.5F;
   public static final int SAND_BLOCK_BUDGET = 24;
   public static final int SAND_COOLDOWN = 120;

   // --- Air Severance (§5.4) -------------------------------------------------------------------
   public static final int SEVER_RELEASE = 15;
   public static final double SEVER_SPEED = 2.4;
   public static final double SEVER_RANGE = 30.0;
   public static final double SEVER_WIDTH_START = 3.0;
   public static final double SEVER_WIDTH_END = 9.0;
   public static final double SEVER_THICKNESS = 0.8;
   public static final float SEVER_DAMAGE = 12.0F;
   public static final float SEVER_DAMAGE_LION = 18.0F;
   public static final float SEVER_BLOCK_HARDNESS = 3.0F;
   public static final int SEVER_BLOCK_BUDGET = 48;
   public static final int SEVER_SCAR_TICKS = 30;
   public static final int SEVER_COOLDOWN = 180;

   // --- Trample (§5.5) -------------------------------------------------------------------------
   public static final int TRAMPLE_TICKS = 22;
   /** The foot lands: crater, sound, the ring starts. */
   public static final int TRAMPLE_IMPACT = 8;
   public static final double TRAMPLE_START_RADIUS = 0.5;
   /** Ring speed, blocks per tick. */
   public static final double TRAMPLE_SPEED = 0.9;
   public static final double TRAMPLE_RADIUS = 7.0;
   public static final double TRAMPLE_RADIUS_LION = 10.0;
   /** Horizontal slack of the ring front (entity width). */
   public static final double TRAMPLE_FRONT = 0.6;
   /** Feet height window over the impact plane: the ring hugs the ground, a jump clears it. */
   public static final double TRAMPLE_BAND_BELOW = 1.0;
   public static final double TRAMPLE_BAND_ABOVE = 1.25;
   /** Stamped on stopped air: the ring is a flat shock in the air, as thick both ways. */
   public static final double TRAMPLE_AIR_BAND = 1.5;
   public static final float TRAMPLE_DAMAGE = 8.0F;
   public static final float TRAMPLE_DAMAGE_LION = 12.0F;
   public static final double TRAMPLE_PUSH = 0.45;
   public static final double TRAMPLE_LIFT = 0.8;
   public static final int TRAMPLE_SLOW_TICKS = 30;
   public static final double TRAMPLE_CRATER = 2.5;
   public static final double TRAMPLE_CRATER_LION = 3.5;
   public static final float TRAMPLE_HARDNESS = 0.8F;
   public static final float TRAMPLE_HARDNESS_LION = 2.0F;
   public static final int TRAMPLE_BUDGET = 14;
   public static final int TRAMPLE_BUDGET_LION = 30;
   public static final int TRAMPLE_COOLDOWN = 160;

   public static double trampleRadius(boolean lion) {
      return lion ? TRAMPLE_RADIUS_LION : TRAMPLE_RADIUS;
   }

   public static float trampleDamage(boolean lion) {
      return lion ? TRAMPLE_DAMAGE_LION : TRAMPLE_DAMAGE;
   }

   public static double trampleCraterRadius(boolean lion) {
      return lion ? TRAMPLE_CRATER_LION : TRAMPLE_CRATER;
   }

   public static float trampleHardness(boolean lion) {
      return lion ? TRAMPLE_HARDNESS_LION : TRAMPLE_HARDNESS;
   }

   public static int trampleBlockBudget(boolean lion) {
      return lion ? TRAMPLE_BUDGET_LION : TRAMPLE_BUDGET;
   }

   /**
    * Pure: does the ring front, moving this tick from {@code from} to {@code to} (blocks from the
    * centre), catch something {@code distance} away whose feet are {@code feet} above the impact plane.
    */
   public static boolean trampleCatches(double distance, double feet, double from, double to, double below, double above) {
      return distance >= from - TRAMPLE_FRONT && distance <= to + TRAMPLE_FRONT && feet >= -below && feet <= above;
   }

   /** Ticks the ring needs to reach its full radius. */
   public static int trampleTravelTicks(boolean lion) {
      return (int) Math.ceil((trampleRadius(lion) - TRAMPLE_START_RADIUS) / TRAMPLE_SPEED);
   }

   // --- Right of Reply (§5.6) ------------------------------------------------------------------
   public static final int REPLY_TICKS = 16;
   public static final int REPLY_WINDOW_START = 4;
   public static final int REPLY_WINDOW_END = 14;
   public static final int REPLY_STRIKE_TICKS = 10;
   public static final int REPLY_STRIKE_CONTACT = 4;
   public static final float REPLY_DAMAGE = 8.0F;
   public static final float REPLY_RETURN_FACTOR = 0.5F;
   public static final int REPLY_COOLDOWN_HIT = 120;
   public static final int REPLY_COOLDOWN_MISS = 200;

   // --- Little King (§5.7) ---------------------------------------------------------------------
   public static final double KING_RANGE = 6.0;
   public static final int KING_CAST = 16;
   public static final int KING_BIND_AT = 10;
   public static final int KING_MAX_SUBJECTS = 4;
   public static final int KING_HOP_TICKS = 400;
   public static final double KING_LEASH = 48.0;
   public static final double KING_REVEAL_RANGE = 24.0;

   // --- Kingdom of One (§6) --------------------------------------------------------------------
   public static final double ULT_RANGE = 32.0;
   public static final int ULT_LENGTH = 150;
   public static final int ULT_SCOOP = 24;
   public static final int ULT_SENTENCE = 50;
   public static final int ULT_FLICK = 106;
   public static final int ULT_RESOLVE = 126;
   public static final int ULT_GRAINS = 64;
   public static final double ULT_DOME_RADIUS = 6.0;
   public static final int ULT_WAVES = 5;
   public static final float ULT_WAVE_DAMAGE = 4.0F;
   public static final float ULT_FLICK_DAMAGE = 16.0F;
   public static final int ULT_FLICK_CONTACT = 112;
   public static final double ULT_CRATER_RADIUS = 3.0;
   public static final double ULT_LEASH = 48.0;
   public static final int ULT_COOLDOWN = 2000;
   public static final int ULT_BLEND = 6;

   private RegulusRules() {
   }

   /**
    * One tick of heart strain (§5.0, pure). The heart that beats in a living subject costs nothing;
    * a stopped heart without one strains every tick; a beating one recovers.
    */
   public static int strainStep(int strain, boolean lionActive, boolean hostAlive) {
      int next;
      if (lionActive) {
         next = hostAlive ? strain : strain + STRAIN_LION_TICK;
      } else {
         next = strain - STRAIN_RECOVERY;
      }

      return Math.max(0, Math.min(STRAIN_MAX, next));
   }

   /** Strain after an activation or a backlash, clamped (pure). */
   public static int strainAdd(int strain, int amount) {
      return Math.max(0, Math.min(STRAIN_MAX, strain + amount));
   }

   public static boolean seizes(int strain) {
      return strain >= STRAIN_MAX;
   }

   /** Ticks a hostless Lion can still hold before the seizure (HUD countdown, pure). */
   public static int hostlessTicksLeft(int strain) {
      return Math.max(0, (STRAIN_MAX - strain + STRAIN_LION_TICK - 1) / STRAIN_LION_TICK);
   }

   /** Sand / severance damage with and without Lion's Heart (pure). */
   public static float sandDamage(boolean lion) {
      return lion ? SAND_DAMAGE_LION : SAND_DAMAGE;
   }

   public static float severDamage(boolean lion) {
      return lion ? SEVER_DAMAGE_LION : SEVER_DAMAGE;
   }

   /** Air Severance width at a travelled distance: widening from 3 to 9 blocks over the range (pure). */
   public static double severWidth(double travelled) {
      double k = Math.max(0.0, Math.min(1.0, travelled / SEVER_RANGE));
      return SEVER_WIDTH_START + (SEVER_WIDTH_END - SEVER_WIDTH_START) * k;
   }

   /** Melee recoil returned to an attacker that strikes the Lion (pure). */
   public static float recoil(float incoming) {
      return Math.max(0.0F, incoming) * RECOIL_FACTOR;
   }

   /** Right of Reply strike damage after blocking {@code blocked} (pure). */
   public static float replyDamage(float blocked) {
      return REPLY_DAMAGE + Math.max(0.0F, blocked) * REPLY_RETURN_FACTOR;
   }

   public static boolean replyWindow(int moveTick) {
      return moveTick >= REPLY_WINDOW_START && moveTick < REPLY_WINDOW_END;
   }

   /** Hardness gate shared by every block-breaking attack (pure; negative = unbreakable). */
   public static boolean breakable(float hardness, float limit) {
      return hardness >= 0.0F && hardness <= limit;
   }

   /** Ult phase at a scene tick (pure). */
   public static UltPhase ultPhase(int tick) {
      if (tick < ULT_SCOOP) {
         return UltPhase.ACCUSE;
      }

      if (tick < ULT_SENTENCE) {
         return UltPhase.SCOOP;
      }

      if (tick < ULT_FLICK) {
         return UltPhase.SENTENCE;
      }

      return tick < ULT_RESOLVE ? UltPhase.FLICK : UltPhase.RESOLVE;
   }

   /** Ticks of the five sentence waves (pure). */
   public static boolean ultWaveTick(int tick) {
      if (tick < ULT_SENTENCE + 8 || tick >= ULT_FLICK) {
         return false;
      }

      int span = (ULT_FLICK - 6 - (ULT_SENTENCE + 8)) / (ULT_WAVES - 1);
      int rel = tick - (ULT_SENTENCE + 8);
      return rel % span == 0 && rel / span < ULT_WAVES;
   }

   public enum UltPhase {
      ACCUSE,
      SCOOP,
      SENTENCE,
      FLICK,
      RESOLVE
   }
}
