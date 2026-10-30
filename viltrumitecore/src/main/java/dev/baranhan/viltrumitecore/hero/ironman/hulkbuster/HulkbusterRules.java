package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

/**
 * Hulkbuster Mark 48 numbers in one place (rework spec §5, §6, §7, §12). Ticks unless named.
 * Starting values: the owner tunes them after the playtest.
 */
public final class HulkbusterRules {
   // ---- body (spec §2) ----
   /** Hitbox 1.3 x 3.2 blocks: factors on the vanilla standing box 0.6 x 1.8. */
   public static final float WIDTH_SCALE = 1.3F / 0.6F;
   public static final float HEIGHT_SCALE = 3.2F / 1.8F;
   /** One-block step for the heavy suit (vanilla 0.6 + 0.4). */
   public static final double STEP_BONUS = 0.4;
   /** Visual height 3.4 blocks: Sind units (68 px tall) x 32/68 x 1.7. */
   public static final float MODEL_SCALE = 1.7F * 32.0F / 68.0F;
   /** Knock-back from ordinary hits x 0.3 (spec §8). */
   public static final double KNOCKBACK_RESISTANCE = 0.7;

   // ---- combo (spec §6.1) ----
   public static final float[] COMBO_DAMAGE = {13.0F, 14.0F, 20.0F};
   public static final double COMBO_REACH = 4.6;
   /** Pause after a step before the chain restarts at step 1. */
   public static final int COMBO_CHAIN_WINDOW = 14;
   /** LMB held this long is a charged punch instead of a combo step. */
   public static final int HOLD_THRESHOLD = 6;

   // ---- charged punch (spec §6.3) ----
   /** INTRO cap: 2 s of charge + 1 s overhold, then it fires by itself. */
   public static final int CHARGE_FULL = 40;
   public static final int CHARGE_HOLD_MAX = 60;
   public static final float[] CHARGE_DAMAGE = {16.0F, 22.0F, 30.0F};
   public static final double[] CHARGE_LUNGE = {0.6, 1.2, 1.7};
   /** Movement factor while the elbow thruster winds up. */
   public static final double CHARGE_SLOW = -0.55;

   // ---- grab (spec §6.2) ----
   public static final double GRAB_REACH = 4.0;
   /** Reach of a grab inside the window after the seismic rift. */
   public static final double GRAB_REACH_RIFT = 6.0;
   public static final int GRAB_RIFT_WINDOW = 20;
   public static final int GRAB_HOLD_MAX = 120;
   public static final int GRAB_PUNCHES = 4;
   public static final float[] GRAB_PUNCH_DAMAGE = {6.0F, 7.0F, 8.5F, 10.0F};
   public static final float GRAB_FINISH_DAMAGE = 18.0F;
   public static final double GRAB_FINISH_LAUNCH = 2.6;
   public static final double GRAB_THROW_SPEED = 2.4;
   public static final float GRAB_THROW_IMPACT = 9.0F;
   public static final float GRAB_SLAM_DAMAGE = 16.0F;
   public static final int GRAB_COOLDOWN = 160;
   /** Stun of the Hulkbuster when the victim breaks free. */
   public static final int ESCAPE_STAGGER = 20;
   /** Escape meter: 100 = free. Per mash press by a normal / a superstrong victim; per point of damage taken by the holder. */
   public static final int ESCAPE_FULL = 100;
   public static final int ESCAPE_PER_PRESS = 6;
   public static final int ESCAPE_PER_PRESS_STRONG = 12;
   public static final float ESCAPE_PER_DAMAGE = 1.5F;
   /** Mobs struggle on their own: meter per tick. */
   public static final float ESCAPE_MOB_PER_TICK = 0.35F;

   // ---- block (spec §6.4) ----
   public static final int BLOCK_HOLD_MAX = 1200;
   public static final int PERFECT_BLOCK = 5;
   public static final float BLOCK_FACTOR = 1.5F;
   public static final double BLOCK_CONE_COS = 0.2;
   public static final double BLOCK_SLOW = -0.7;
   public static final float COUNTER_DAMAGE = 10.0F;

   // ---- abilities (spec §6.5–6.8) ----
   public static final int REPULSORS_COOLDOWN = 240;
   public static final float REPULSORS_COST = 35.0F;
   public static final double REPULSORS_RANGE = 25.0;
   public static final float REPULSORS_DAMAGE_PER_TICK = 2.2F;
   public static final int RIFT_COOLDOWN = 200;
   public static final double RIFT_LENGTH = 12.0;
   public static final float RIFT_DAMAGE = 12.0F;
   public static final int CAGE_COOLDOWN = 500;
   public static final double CAGE_RANGE = 30.0;
   public static final int CAGE_DROP_DELAY = 30;
   public static final int CAGE_HOLD = 60;
   public static final int CAGE_EMPTY_LINGER = 40;
   public static final float CAGE_DURABILITY = 60.0F;
   /** Spare arm: only below 66 % durability, once per suit-up; restores 30 %. */
   public static final float SPARE_ARM_BELOW = 0.66F;
   public static final float SPARE_ARM_RESTORE = 0.30F;

   // ---- ultimate (spec §7) ----
   public static final int ULT_COOLDOWN = 1800;
   public static final int ULT_DASH_START = 30;
   public static final int ULT_DASH_END = 46;
   public static final int ULT_RISE_END = 90;
   public static final int ULT_CONTACT = 110;
   public static final double ULT_DASH_DISTANCE = 15.0;
   public static final double ULT_RISE = 25.0;
   public static final float ULT_DAMAGE = 40.0F;
   public static final float ULT_SELF_COST = 0.10F;
   public static final double ULT_RADIUS = 7.0;

   // ---- movement (spec §5) ----
   public static final int ACCEL_TICKS = 15;
   public static final int DECEL_TICKS = 10;
   /** Walk speed factor of the giant (vanilla attribute multiplier, MULTIPLY_TOTAL). */
   public static final double WALK_FACTOR = -0.12;
   public static final int RAM_FULL_TICKS = 30;
   public static final double RAM_BONUS = 0.25;
   public static final int RAM_WALL_STAGGER = 10;
   public static final int DASH_CHARGES = 2;
   public static final int DASH_RECHARGE = 80;
   public static final int DASH_WINDUP = 6;
   public static final int DASH_BURST = 8;
   public static final double DASH_DISTANCE = 7.0;
   public static final float DASH_COST = 6.0F;
   public static final int DASH_TAP_WINDOW = 7;
   public static final int JET_CHARGE_MAX = 30;
   /** Space held longer than this on the ground is a jet jump charge, shorter is a plain heavy jump. */
   public static final int JET_TAP = 5;
   public static final double JET_MIN_HEIGHT = 8.0;
   public static final double JET_MAX_HEIGHT = 20.0;
   public static final float JET_COST = 12.0F;
   public static final int HOVER_MAX = 30;
   public static final float HOVER_COST_PER_TICK = 0.6F;
   public static final double DIVE_MIN_HEIGHT = 3.0;
   public static final double DIVE_SPEED = 2.2;
   public static final float DIVE_DAMAGE_MIN = 8.0F;
   public static final float DIVE_DAMAGE_PER_BLOCK = 1.1F;
   public static final double DIVE_RADIUS_MIN = 3.0;
   public static final double DIVE_RADIUS_PER_BLOCK = 0.22;
   public static final double DIVE_RADIUS_MAX = 9.0;
   public static final double HEAVY_LANDING = 6.0;
   public static final int LANDING_SQUAT = 10;

   private HulkbusterRules() {
   }

   /** Charge level 0..2 of a charged punch held this many INTRO ticks. */
   public static int chargeLevel(int held) {
      return held >= CHARGE_FULL ? 2 : held >= CHARGE_FULL / 2 ? 1 : 0;
   }

   /** Dive damage from the fall height (spec §5: grows from 5 to 20+ blocks). */
   public static float diveDamage(double height) {
      return (float)(DIVE_DAMAGE_MIN + DIVE_DAMAGE_PER_BLOCK * Math.max(0.0, height - DIVE_MIN_HEIGHT));
   }

   public static double diveRadius(double height) {
      return Math.min(DIVE_RADIUS_MAX, DIVE_RADIUS_MIN + DIVE_RADIUS_PER_BLOCK * Math.max(0.0, height - DIVE_MIN_HEIGHT));
   }

   /** Jet jump apex height from the charge ticks (8..20 blocks). */
   public static double jetHeight(int charge) {
      double k = Math.max(0, Math.min(JET_CHARGE_MAX, charge)) / (double)JET_CHARGE_MAX;
      return JET_MIN_HEIGHT + (JET_MAX_HEIGHT - JET_MIN_HEIGHT) * k;
   }

   /** Damage stage 0 / 1 / 2 from the durability fraction (100–66 / 66–33 / &lt; 33 %). */
   public static int damageStage(float fraction) {
      return fraction > 0.66F ? 0 : fraction > 0.33F ? 1 : 2;
   }
}
