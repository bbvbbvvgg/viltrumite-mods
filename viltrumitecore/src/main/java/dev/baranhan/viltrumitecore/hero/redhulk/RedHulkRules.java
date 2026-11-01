package dev.baranhan.viltrumitecore.hero.redhulk;

/**
 * Numbers and pure rules of the Red Hulk (spec docs/design/2026-11-01-red-hulk-design.md). Ticks unless noted.
 * Balance (PR #112 model): tier A melee bruiser, below Reinhard (S+) and Regulus (S); see BalanceTable.RAGE_TITAN.
 * Unit tested by RedHulkRulesTest; the clip lengths are mirrored by tools/assets/red_hulk_clips.py (RedHulkClipsTest).
 */
public final class RedHulkRules {
   // ---- chassis ---------------------------------------------------------------------------------------------
   /** +28 HP: 48 total (Thor 44, Captain 40). */
   public static final double HEALTH_BONUS = 28.0;
   public static final double ARMOR = 10.0;
   public static final double TOUGHNESS = 4.0;
   public static final double SPEED_BONUS = 0.05;
   public static final double KNOCKBACK_RESIST = 0.6;
   /** A 2.5-block body: hitbox height x1.4 (2.52 blocks), width x2 (1.2 blocks), long arms, one-block steps. */
   public static final float BODY_HEIGHT_SCALE = 1.4F;
   public static final float BODY_WIDTH_SCALE = 2.0F;
   public static final double BODY_REACH_BONUS = 0.6;
   public static final double BODY_STEP_BONUS = 0.4;
   /** Falls below this many blocks never hurt; above it a quarter of the rest does. */
   public static final float SAFE_FALL = 24.0F;
   public static final float FALL_FACTOR = 0.25F;
   public static final float LANDING_FALL = 4.0F;
   public static final float LANDING_SHOCK_FALL = 8.0F;
   public static final float LANDING_SHOCK_DAMAGE = 4.0F;
   public static final double LANDING_SHOCK_RADIUS = 2.5;

   // ---- temperature ------------------------------------------------------------------------------------------
   public static final int HEAT_MAX = 1000;
   public static final int WARM = 250;
   public static final int HOT = 500;
   public static final int BLAZING = 800;
   /** Ticks without a heat gain before the body starts to cool; then {@link #HEAT_DECAY} per tick. */
   public static final int HEAT_IDLE = 100;
   public static final int HEAT_DECAY = 5;
   public static final int WET_COOLING = 3;
   /** Max damage factor at full heat (x1.30), fury x1.15, total capped at x1.45. */
   public static final float HEAT_DAMAGE_BONUS = 0.30F;
   public static final float FURY_DAMAGE = 1.15F;
   public static final float MAX_DAMAGE_FACTOR = 1.45F;
   /** Heat from incoming hits: per HP taken, capped per hit. */
   public static final float HEAT_PER_HP_TAKEN = 3.0F;
   public static final int HEAT_HIT_TAKEN_CAP = 60;
   /** Fire, lava and burning: this share is absorbed, the absorbed HP feed the heat. */
   public static final float FIRE_ABSORB = 0.60F;
   public static final float EXPLOSION_ABSORB = 0.30F;
   public static final float HEAT_PER_HP_ABSORBED = 10.0F;
   public static final int HEAT_ABSORB_CAP = 80;
   /** Heat aura (HOT and above): enemies this close are scorched every {@link #AURA_INTERVAL} ticks. */
   public static final double AURA_RADIUS = 2.6;
   public static final int AURA_INTERVAL = 20;
   public static final float AURA_DAMAGE = 1.0F;
   public static final float AURA_DAMAGE_BLAZING = 2.0F;
   public static final int AURA_IGNITE_SECONDS = 2;
   // overheat
   public static final int OVERHEAT_TICKS = 120;
   public static final int HEAT_AFTER_VENT = 600;
   public static final float OVERHEAT_DAMAGE_TAKEN = 1.15F;
   public static final double OVERHEAT_SLOW = -0.15;
   public static final int VENT_BURST_TICK = 6;
   public static final double VENT_RADIUS = 3.5;
   public static final float VENT_DAMAGE = 4.0F;

   // ---- heavy punches (LMB) ---------------------------------------------------------------------------------
   public static final int COMBO_LENGTH = 5;
   public static final float[] COMBO_DAMAGE = {7.0F, 7.0F, 8.0F, 9.0F, 13.0F};
   public static final int[] COMBO_HEAT = {30, 30, 40, 50, 80};
   public static final int HEAT_PER_EXTRA_TARGET = 10;
   public static final double REACH = 3.6;
   public static final double ARC_DEGREES = 90.0;
   public static final int COMBO_WINDOW = 16;
   public static final int BUFFER = 5;
   public static final double UPPERCUT_LIFT = 0.6;
   public static final double HAMMER_RADIUS = 2.8;
   public static final double HAMMER_AHEAD = 2.0;
   public static final float HAMMER_AREA_DAMAGE = 7.0F;
   public static final int HAMMER_STUN = 10;
   public static final int HIT_STOP_HEAVY = 3;

   // ---- grab and throw (RMB) ------------------------------------------------------------------------------
   public static final double GRAB_REACH = 3.6;
   public static final double GRAB_ARC = 70.0;
   public static final float GRAB_MAX_HEIGHT = 3.2F;
   public static final float GRAB_MAX_WIDTH = 2.0F;
   public static final int HOLD_MAX = 100;
   public static final int SQUEEZE_INTERVAL = 20;
   public static final float SQUEEZE_DAMAGE = 2.0F;
   public static final int SQUEEZE_HEAT = 15;
   public static final float THROW_DAMAGE = 10.0F;
   public static final double THROW_SPEED = 1.7;
   public static final double THROW_LIFT = 0.35;
   public static final int THROW_FLIGHT = 30;
   public static final float THROW_IMPACT_DAMAGE = 6.0F;
   public static final float THROW_WALL_DAMAGE = 4.0F;
   public static final double THROW_IMPACT_RADIUS = 2.5;
   public static final int THROW_HEAT = 60;
   /** Hand height of the hold anchor above the feet (blocks). */
   public static final double HOLD_HAND_HEIGHT = 2.0;
   public static final double HOLD_LOST_DISTANCE = 8.0;

   // ---- abilities ---------------------------------------------------------------------------------------
   public static final int COOLDOWNS = 7;
   public static final int CD_CLAP = 0, CD_SEISMIC = 1, CD_LEAP = 2, CD_BLAST = 3, CD_FURY = 4, CD_ULT = 5, CD_GRAB = 6;
   public static final int[] COOLDOWN_TICKS = {160, 240, 200, 300, 900, 1800, 120};

   public static final double CLAP_REACH = 7.0;
   public static final double CLAP_ARC = 70.0;
   public static final float CLAP_DAMAGE = 10.0F;
   public static final double CLAP_PUSH = 1.4;
   public static final int CLAP_IGNITE_SECONDS = 3;
   public static final int CLAP_HEAT = 60;
   public static final int CLAP_HEAT_PER_TARGET = 15;

   public static final double SEISMIC_RADIUS = 6.0;
   public static final float SEISMIC_DAMAGE = 12.0F;
   public static final float SEISMIC_EDGE_DAMAGE = 6.0F;
   public static final double SEISMIC_LIFT = 0.85;
   public static final int SEISMIC_STUN = 10;
   public static final int SEISMIC_HEAT = 100;

   public static final double LEAP_FORWARD = 1.45;
   public static final double LEAP_UP = 1.1;
   public static final int LEAP_AIR_MAX = 60;
   public static final double LEAP_RADIUS = 5.0;
   public static final float LEAP_DAMAGE = 12.0F;
   public static final float LEAP_EDGE_DAMAGE = 5.0F;
   public static final double LEAP_LIFT = 0.7;
   public static final int LEAP_HEAT = 80;
   /** A second press in the air drives him down: vertical speed. */
   public static final double LEAP_DIVE = -1.6;

   public static final int BLAST_MIN_HEAT = 300;
   public static final float BLAST_SPEND = 0.60F;
   public static final int BLAST_FIRE_START = 12;
   public static final int BLAST_FIRE_END = 24;
   public static final int BLAST_PULSE = 2;
   public static final double BLAST_RANGE = 16.0;
   public static final double BLAST_WIDTH = 1.2;
   public static final float BLAST_PULSE_DAMAGE = 1.6F;
   public static final int BLAST_IGNITE_SECONDS = 4;
   public static final int BLAST_MAX_TARGETS = 5;

   public static final int FURY_TICKS = 240;
   public static final double FURY_ROAR_RADIUS = 8.0;
   public static final int FURY_WEAKNESS_TICKS = 80;
   public static final int FURY_HEAT = 150;
   /** Heat the fury adds every second while it lasts (the rage creeps). */
   public static final int FURY_CREEP = 15;
   public static final float FURY_DAMAGE_TAKEN = 0.85F;
   public static final double FURY_SPEED = 0.10;

   public static final int ULT_MIN_HEAT = 700;
   public static final double ULT_RADIUS = 12.0;
   public static final float ULT_DAMAGE = 30.0F;
   public static final float ULT_EDGE_DAMAGE = 12.0F;
   public static final int ULT_IGNITE_SECONDS = 6;
   public static final double ULT_PUSH = 2.0;
   public static final double ULT_LIFT = 0.7;
   public static final double ULT_PULL = 0.08;
   public static final float ULT_CHARGE_DAMAGE_TAKEN = 0.5F;
   public static final float ULT_RECOVER_DAMAGE_TAKEN = 1.2F;
   public static final double ULT_RECOVER_SLOW = -0.5;
   public static final double OBSERVER_RANGE = 48.0;

   public static final int STAGGER_DAMAGE = 10;

   private RedHulkRules() {
   }

   // ---- pure rules ------------------------------------------------------------------------------------------

   public static int clampHeat(int heat) {
      return Math.max(0, Math.min(HEAT_MAX, heat));
   }

   /** 0 cool, 1 warm, 2 hot, 3 blazing. */
   public static int tier(int heat) {
      return heat >= BLAZING ? 3 : heat >= HOT ? 2 : heat >= WARM ? 1 : 0;
   }

   /** Outgoing damage factor: heat (up to x1.30), fury (x1.15), capped at {@link #MAX_DAMAGE_FACTOR}. */
   public static float damageFactor(int heat, boolean fury) {
      float f = 1.0F + HEAT_DAMAGE_BONUS * clampHeat(heat) / (float)HEAT_MAX;
      if (fury) {
         f *= FURY_DAMAGE;
      }
      return Math.min(MAX_DAMAGE_FACTOR, f);
   }

   /** Incoming damage factor of the current state (fury, overheat, ultimate charge / recovery). */
   public static float damageTakenFactor(boolean fury, boolean overheated, boolean ultCharge, boolean ultRecover) {
      float f = 1.0F;
      if (fury) f *= FURY_DAMAGE_TAKEN;
      if (overheated) f *= OVERHEAT_DAMAGE_TAKEN;
      if (ultCharge) f *= ULT_CHARGE_DAMAGE_TAKEN;
      if (ultRecover) f *= ULT_RECOVER_DAMAGE_TAKEN;
      return f;
   }

   /**
    * Heat after one tick of cooling: nothing until {@link #HEAT_IDLE} quiet ticks, none in fury or overheat; water
    * and rain cool him at once, {@link #WET_COOLING} times faster (steam).
    */
   public static int cool(int heat, int sinceGain, boolean fury, boolean overheated, boolean wet) {
      if (fury || overheated) {
         return heat;
      }
      if (wet) {
         return Math.max(0, heat - HEAT_DECAY * WET_COOLING);
      }
      return sinceGain < HEAT_IDLE ? heat : Math.max(0, heat - HEAT_DECAY);
   }

   /** Heat from an incoming hit of {@code amount} HP. */
   public static int heatFromHit(float amount) {
      return Math.max(0, Math.min(HEAT_HIT_TAKEN_CAP, Math.round(amount * HEAT_PER_HP_TAKEN)));
   }

   /** Heat from HP absorbed out of fire / explosions. */
   public static int heatFromAbsorbed(float absorbed) {
      return Math.max(0, Math.min(HEAT_ABSORB_CAP, Math.round(absorbed * HEAT_PER_HP_ABSORBED)));
   }

   /** Next combo step: restarts after {@link #COMBO_WINDOW} idle ticks or after the finisher. */
   public static int nextStep(int previous, int idle) {
      if (previous < 0 || idle > COMBO_WINDOW || previous + 1 >= COMBO_LENGTH) {
         return 0;
      }
      return previous + 1;
   }

   /** LMB in the last {@link #BUFFER} ticks of a combo move is queued. */
   public static boolean buffers(int elapsed, int length) {
      return elapsed >= length - BUFFER;
   }

   /** Linear falloff from {@code center} at the middle to {@code edge} at {@code radius}. */
   public static float falloff(double distance, double radius, float center, float edge) {
      double k = Math.max(0.0, Math.min(1.0, distance / radius));
      return (float)(center + (edge - center) * k);
   }

   /** Fall damage of a landing (no damage up to {@link #SAFE_FALL}). */
   public static float fallDamage(float distance) {
      return distance <= SAFE_FALL ? 0.0F : (distance - SAFE_FALL) * FALL_FACTOR;
   }

   /** Heat Blast damage per pulse for the heat it was fired with (x1 at 0, x2 at full heat). */
   public static float blastPulseDamage(int heatAtFire) {
      return BLAST_PULSE_DAMAGE * (1.0F + clampHeat(heatAtFire) / (float)HEAT_MAX);
   }

   /** Is the tick a Heat Blast pulse? */
   public static boolean blastPulse(int elapsed) {
      return elapsed >= BLAST_FIRE_START && elapsed <= BLAST_FIRE_END && (elapsed - BLAST_FIRE_START) % BLAST_PULSE == 0;
   }

   /** Heat left after firing the blast. */
   public static int afterBlast(int heat) {
      return clampHeat(Math.round(heat * (1.0F - BLAST_SPEND)));
   }

   /** Can a body of this size be lifted with one hand? */
   public static boolean liftable(float width, float height) {
      return width <= GRAB_MAX_WIDTH && height <= GRAB_MAX_HEIGHT;
   }

   /** Heat thresholds of the abilities. */
   public static boolean canBlast(int heat, boolean overheated) {
      return !overheated && heat >= BLAST_MIN_HEAT;
   }

   public static boolean canUlt(int heat, boolean overheated) {
      return !overheated && heat >= ULT_MIN_HEAT;
   }
}
