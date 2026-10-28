package dev.baranhan.viltrumitecore.hero.jinwoo;

/**
 * Sung Jinwoo numbers (docs/design/2026-10-28-jinwoo-design.md §9) and pure rules.
 * No world access: every function here is unit-tested.
 */
public final class JinwooRules {
   // §4 passives
   public static final double HEALTH_BONUS = 20.0;
   public static final double ARMOR = 6.0;
   public static final double TOUGHNESS = 2.0;
   public static final double SPEED_BONUS = 0.15;
   public static final float LANDING_BURST_HEIGHT = 6.0F;
   public static final int AIR_JUMPS = 1;
   public static final double AIR_JUMP_VELOCITY = 0.62;
   public static final float MANA_MAX = 100.0F;
   public static final int MANA_REGEN_INTERVAL = 5;
   public static final float MANA_REGEN = 1.0F;
   public static final float MANA_PER_HIT = 3.0F;
   public static final float DOMAIN_MANA_FACTOR = 2.0F;
   public static final int REGEN_INTERVAL = 40;
   public static final int REGEN_DELAY = 100;
   public static final float REGEN_AMOUNT = 1.0F;

   // §5.1 Dance of blades
   public static final float[] COMBO_DAMAGE = {6.0F, 6.0F, 5.0F, 8.0F, 12.0F};
   public static final int[] COMBO_LENGTH = {6, 6, 10, 9, 14};
   public static final int[] COMBO_CONTACT = {3, 3, 5, 5, 9};
   public static final int COMBO_WINDOW = 12;
   public static final int COMBO_BUFFER = 4;
   public static final double REACH = 3.4;
   public static final double ARC_DEGREES = 110.0;
   public static final double SPIN_RADIUS = 2.8;
   public static final double FINISHER_LIFT = 0.45;
   public static final int FINISHER_HIT_STOP = 2;
   public static final float LOW_DAMAGE = 5.0F;
   public static final int LOW_LENGTH = 8;
   public static final int LOW_CONTACT = 4;
   public static final int LOW_SLOW_TICKS = 40;
   public static final int AIR_FLOAT_TICKS = 6;
   public static final double AIR_GRAVITY = 0.25;
   public static final double DIVE_SPEED = 1.6;
   public static final float DIVE_DAMAGE = 10.0F;
   public static final double DIVE_RADIUS = 3.0;
   public static final int DIVE_MAX_TICKS = 40;
   public static final int MARK_TICKS = 100;

   // §5.2 block / parry
   public static final double BLOCK_ARC_DEGREES = 120.0;
   public static final float BLOCK_FACTOR = 0.3F;
   public static final float BLOCK_MANA = 2.0F;
   public static final int PARRY_WINDOW = 6;
   public static final int PARRY_STUN = 20;
   public static final float PARRY_RIPOSTE = 6.0F;
   public static final int BLOCK_COOLDOWN = 10;
   public static final int BLOCK_MAX = 100;

   // §5.3 daggers
   public static final int DRAW_TICKS = 12;
   public static final int SHEATH_TICKS = 8;
   public static final int DAGGER_IDLE = 160;

   // §5.4 Shadow Step
   public static final double STEP_DISTANCE = 8.0;
   public static final int STEP_TICKS = 4;
   public static final float STEP_DAMAGE = 4.0F;
   public static final int STEP_COOLDOWN = 50;
   public static final float STEP_MANA = 10.0F;
   public static final int STEP_IFRAMES = 6;
   public static final float STEP_PITCH_LIMIT = 35.0F;

   // §5.5 Mutilation
   public static final double RUSH_CONE_DEGREES = 25.0;
   public static final double RUSH_RANGE = 10.0;
   public static final float RUSH_DAMAGE = 2.5F;
   public static final int RUSH_HITS = 8;
   public static final int RUSH_HIT_TICKS = 16;
   public static final float RUSH_FINAL = 10.0F;
   public static final double RUSH_FINAL_KNOCKBACK = 1.2;
   public static final int RUSH_COOLDOWN = 240;
   public static final float RUSH_MANA = 30.0F;
   public static final double RUSH_SELF_RADIUS = 3.0;
   /** Step-in (4 t) + 8 hits over 16 t + final cross (6 t). */
   public static final int RUSH_APPROACH = 4;
   public static final int RUSH_LENGTH = RUSH_APPROACH + RUSH_HIT_TICKS + 6;
   public static final int RUSH_FINAL_TICK = RUSH_APPROACH + RUSH_HIT_TICKS + 2;

   // §5.6 Ruler's Authority
   public static final int AUTHORITY_TARGETS = 6;
   public static final double AUTHORITY_RANGE = 14.0;
   public static final double AUTHORITY_CONE_DEGREES = 35.0;
   public static final double AUTHORITY_LIFT = 3.5;
   public static final int AUTHORITY_LIFT_TICKS = 20;
   public static final int AUTHORITY_HOLD = 60;
   public static final float AUTHORITY_DAMAGE = 8.0F;
   public static final int AUTHORITY_COOLDOWN = 300;
   public static final float AUTHORITY_MANA = 35.0F;
   public static final int AUTHORITY_SLAM_TICKS = 10;

   // §5.7 Extraction
   public static final double EXTRACT_RADIUS = 10.0;
   public static final int EXTRACT_CHANNEL = 30;
   public static final int EXTRACT_COOLDOWN = 60;
   public static final float EXTRACT_MANA = 20.0F;
   public static final int EXTRACT_ATTEMPTS = 3;
   public static final double EXTRACT_FAIL_BONUS = 0.20;
   public static final int REMNANT_LIFE = 600;
   public static final int REMNANT_MAX = 16;

   // §5.8 Exchange
   public static final double EXCHANGE_RANGE = 64.0;
   public static final int EXCHANGE_COOLDOWN = 400;
   public static final float EXCHANGE_MANA = 25.0F;
   public static final int EXCHANGE_SINK = 10;

   // §5.9 ultimate
   public static final float ULT_MANA = 80.0F;
   public static final int ULT_LENGTH = 100;
   public static final double ULT_RADIUS = 22.0;
   public static final float ULT_DAMAGE = 14.0F;
   public static final int ULT_COOLDOWN = 1800;
   public static final int ULT_DOMAIN = 400;
   public static final int ULT_SLOW_TICKS = 60;
   public static final int ULT_RISE_START = 20;
   public static final int ULT_RISE_END = 60;
   public static final int ULT_WAVE = 60;
   public static final int ULT_WAVE_END = 75;
   public static final int ULT_GROW_TICKS = 40;
   public static final int ULT_CANCEL_BEFORE = 20;
   public static final int ULT_GHOSTS = 36;
   public static final float DOMAIN_DAMAGE_BONUS = 1.5F;
   public static final double ULT_OBSERVER_RANGE = 40.0;

   // §5.10-5.13 army
   public static final int ARMY_ACTIVE = 12;
   public static final int ARMY_ROSTER = 24;
   public static final int ARMY_LEVEL = 64;
   public static final float SUMMON_MANA = 5.0F;
   public static final int SUMMON_COOLDOWN = 40;
   public static final int SUMMON_STAGGER = 3;
   public static final int RECALL_TICKS = 14;
   public static final double COMMAND_RANGE = 48.0;
   public static final double COMMAND_TOLERANCE = 1.5;
   public static final double COMMAND_AREA = 8.0;
   public static final int COMMAND_COOLDOWN = 20;
   public static final double GUARD_RADIUS = 3.0;
   public static final int GUARD_MIN = 3;
   public static final float GUARD_FACTOR = 0.7F;
   public static final int SENTINEL_MAX = 4;
   public static final double SENTINEL_ALERT = 10.0;
   public static final int SHADOW_REVIVE = 600;
   public static final double LEASH = 24.0;
   public static final double FOLLOW_TARGET_RANGE = 12.0;
   public static final int CHARGE_LENGTH_BLOCKS = 14;
   public static final double CHARGE_WIDTH = 4.0;
   public static final float CHARGE_DAMAGE = 9.0F;
   public static final float CHARGE_FINISH = 8.0F;
   public static final int CHARGE_COOLDOWN = 400;
   public static final float CHARGE_MANA = 40.0F;
   public static final int CHARGE_TICKS = 16;
   public static final int SHADOW_RISE = 24;
   public static final int SHADOW_DISSOLVE = 14;
   public static final int OWNER_CHECK = 20;
   public static final double LOD_SMOKE = 32.0;
   public static final double LOD_GLOW = 48.0;
   public static final int SMOKE_POOL = 1200;

   private JinwooRules() {
   }

   /** Mana after regen ticks: +1 every 5 t (×2 in the Domain), clamped. */
   public static float regen(float mana, int tick, boolean inDomain) {
      if (tick % MANA_REGEN_INTERVAL != 0) {
         return mana;
      }
      return Math.min(MANA_MAX, mana + MANA_REGEN * (inDomain ? DOMAIN_MANA_FACTOR : 1.0F));
   }

   /** Combo step that a fresh LMB starts: the next one inside the window, else the first. */
   public static int nextComboStep(int lastStep, int ticksSinceEnd) {
      if (lastStep < 0 || lastStep >= COMBO_DAMAGE.length - 1 || ticksSinceEnd > COMBO_WINDOW) {
         return 0;
      }
      return lastStep + 1;
   }

   /** A press during the running step is buffered only in its last {@link #COMBO_BUFFER} ticks. */
   public static boolean buffers(int elapsed, int length) {
      return elapsed >= length - COMBO_BUFFER;
   }

   /** Pure arc test: forward look, offset to the target (horizontal plane), half-arc in degrees. */
   public static boolean inArc(double lookX, double lookZ, double dx, double dz, double arcDegrees) {
      double lookLen = Math.sqrt(lookX * lookX + lookZ * lookZ);
      double len = Math.sqrt(dx * dx + dz * dz);
      if (len < 1.0E-4) {
         return true;
      }
      if (lookLen < 1.0E-4) {
         return false;
      }
      double cos = (lookX * dx + lookZ * dz) / (lookLen * len);
      return cos >= Math.cos(Math.toRadians(arcDegrees / 2.0));
   }

   /** Cone test in 3D (unit look). */
   public static boolean inCone(double lx, double ly, double lz, double dx, double dy, double dz, double coneDegrees) {
      double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (len < 1.0E-4) {
         return true;
      }
      double cos = (lx * dx + ly * dy + lz * dz) / len;
      return cos >= Math.cos(Math.toRadians(coneDegrees));
   }

   /** Base extraction chance by creature tier. */
   public static double extractChance(ShadowTier tier, int failures) {
      double base = switch (tier) {
         case WEAK -> 1.0;
         case NORMAL -> 0.75;
         case STRONG -> 0.45;
      };
      return Math.min(1.0, base + EXTRACT_FAIL_BONUS * Math.max(0, failures));
   }

   /** Creature tier by its max health (decision: ≤ 12 weak, ≥ 40 strong). */
   public static ShadowTier tierOf(float maxHealth, boolean boss) {
      if (boss || maxHealth >= 40.0F) {
         return ShadowTier.STRONG;
      }
      return maxHealth <= 12.0F ? ShadowTier.WEAK : ShadowTier.NORMAL;
   }

   /** Step pitch clamp (±35°). */
   public static float stepPitch(float pitch) {
      return Math.max(-STEP_PITCH_LIMIT, Math.min(STEP_PITCH_LIMIT, pitch));
   }

   /** Domain radius during the ultimate: 0→22 over the first 40 ticks (ease-out). */
   public static double domainRadius(int ultTick) {
      if (ultTick >= ULT_GROW_TICKS) {
         return ULT_RADIUS;
      }
      double t = Math.max(0, ultTick) / (double) ULT_GROW_TICKS;
      return ULT_RADIUS * (1.0 - (1.0 - t) * (1.0 - t));
   }

   /** Ult phase by tick (0-20 call, 20-60 rise, 60-75 wave, 75-100 settle). */
   public static UltPhase ultPhase(int tick) {
      if (tick < ULT_RISE_START) {
         return UltPhase.CALL;
      }
      if (tick < ULT_RISE_END) {
         return UltPhase.RISE;
      }
      if (tick < ULT_WAVE_END) {
         return UltPhase.WAVE;
      }
      return tick < ULT_LENGTH ? UltPhase.SETTLE : UltPhase.DONE;
   }

   /** Mana refunded when the ultimate is broken before its rise (half the cost). */
   public static float ultRefund() {
      return ULT_MANA / 2.0F;
   }

   /** Mutilation hit tick: hit i (0-based) of 8 lands at approach + 2*i. */
   public static boolean rushHitTick(int elapsed) {
      int t = elapsed - RUSH_APPROACH;
      return t >= 0 && t < RUSH_HIT_TICKS && t % (RUSH_HIT_TICKS / RUSH_HITS) == 0;
   }

   /** Charge progress in blocks after {@code tick} ticks (ease-out over CHARGE_TICKS). */
   public static double chargeDistance(int tick) {
      double t = Math.min(1.0, Math.max(0, tick) / (double) CHARGE_TICKS);
      return CHARGE_LENGTH_BLOCKS * (1.0 - Math.pow(1.0 - t, 2.0));
   }

   /** Formation slot behind the owner (follow mode): rows of 4, 2 blocks apart. Returns {right, back}. */
   public static double[] followSlot(int index) {
      int row = index / 4;
      int col = index % 4;
      double right = (col - 1.5) * 1.8;
      double back = 2.2 + row * 1.9;
      return new double[]{right, back};
   }

   /** Guard ring slot around the owner. Returns {x, z} offsets. */
   public static double[] guardSlot(int index, int count) {
      double a = Math.PI * 2.0 * index / Math.max(1, count);
      return new double[]{Math.sin(a) * GUARD_RADIUS, Math.cos(a) * GUARD_RADIUS};
   }

   /** Charge wedge slot: apex at the owner, rows widen backwards. Returns {right, back}. */
   public static double[] wedgeSlot(int index) {
      int row = 1;
      int used = 0;
      while (used + row * 2 <= index) {
         used += row * 2;
         row++;
      }
      int inRow = index - used;
      double side = inRow % 2 == 0 ? 1.0 : -1.0;
      int pair = inRow / 2;
      return new double[]{side * (0.9 + pair * 1.4) * Math.min(1.0, row / 2.0 + 0.5), row * 1.4};
   }

   public enum ShadowTier {
      WEAK, NORMAL, STRONG
   }

   public enum UltPhase {
      CALL, RISE, WAVE, SETTLE, DONE
   }
}
