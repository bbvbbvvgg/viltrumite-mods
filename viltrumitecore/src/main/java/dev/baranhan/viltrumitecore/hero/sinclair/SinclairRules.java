package dev.baranhan.viltrumitecore.hero.sinclair;

/**
 * Sinclair numbers (spec docs/design/2026-10-28-sinclair-design.md §9) and pure rules.
 * Ticks (20 = 1 s), blocks, damage in half hearts. No world access: unit-tested.
 */
public final class SinclairRules {
   // Chassis (§4)
   public static final double BONUS_HEALTH = 6.0;
   public static final double BONUS_ARMOR = 4.0;
   // Prestige (§5.0)
   public static final float SPARK_DAMAGE = 2.0F;
   public static final float SPARK_SPEED = 3.2F;
   public static final float SPARK_RANGE = 48.0F;
   public static final float SPARK_SPREAD_DEGREES = 0.7F;
   public static final float ASSIST_SPARK_DAMAGE = 1.5F;
   public static final int BURST_SHOTS = 2;
   public static final int BURST_SHOT_INTERVAL = 3;
   public static final int BURST_INTERVAL = 9;
   public static final int MAGAZINE = 12;
   public static final int RELOAD_TICKS = 32;
   // Vexing Bolt (§5.1)
   public static final int BOLT_WINDUP = 8;
   public static final int BOLT_RECOVERY = 6;
   public static final float BOLT_SPEED = 0.75F;
   public static final int BOLT_LIFE = 44;
   public static final float BOLT_DAMAGE_MIN = 3.0F;
   public static final float BOLT_DAMAGE_MAX = 7.0F;
   public static final int BOLT_RAMP_TICKS = 40;
   public static final float BOLT_RADIUS = 2.0F;
   public static final int BOLT_REDIRECT_MIN_AGE = 4;
   public static final float BOLT_ASSIST_FACTOR = 0.5F;
   public static final int BOLT_SLOW_TICKS = 20;
   public static final double BOLT_SLOW = -0.25;
   public static final int BOLT_COOLDOWN = 160;
   // Spectral Assistant (§5.2)
   public static final int ASSIST_CAST = 12;
   public static final int ASSIST_APPEAR_TICK = 8;
   public static final float ASSIST_RANGE = 15.0F;
   public static final double ASSIST_BLOCK_BACKOFF = 0.6;
   public static final double ASSIST_SAFE_RADIUS = 2.0;
   public static final int ASSIST_LIFE = 200;
   public static final float ASSIST_HEALTH = 12.0F;
   public static final double ASSIST_HOVER = 0.3;
   public static final double ASSIST_LEASH = 48.0;
   public static final int ASSIST_LEAVE_TICKS = 20;
   public static final int ASSIST_APPEAR_TICKS = 16;
   public static final int ASSIST_COOLDOWN = 600;
   public static final int SWAP_COUNT = 2;
   public static final int SWAP_FIRST_MIN_AGE = 10;
   public static final int SWAP_INTERVAL = 40;
   public static final int SWAP_TICKS = 6;
   public static final int SWAP_TELEPORT_TICK = 3;
   // Rabbit Hex (§5.4)
   public static final int HEX_CAST = 10;
   public static final int HEX_HAT_TICK = 6;
   public static final float HEX_RANGE = 24.0F;
   public static final float HEX_AIR_DROP = 8.0F;
   public static final int HEX_DELAY = 18;
   public static final double HEX_HAT_HEIGHT = 3.0;
   public static final double HEX_RADIUS = 3.5;
   public static final double HEX_VERTICAL = 2.5;
   public static final int HEX_PLAYER_TICKS = 50;
   public static final int HEX_MOB_TICKS = 70;
   public static final double HEX_SPEED = 0.36;
   public static final float HEX_DAMAGE_TAKEN = 1.15F;
   public static final float HEX_WIDTH = 0.45F;
   public static final float HEX_HEIGHT = 0.55F;
   public static final int HEX_COOLDOWN = 700;
   // Misdirection (§5.5)
   public static final int MIS_CAST = 8;
   public static final int MIS_SNAP_TICK = 4;
   public static final int VEIL_TICKS = 50;
   public static final int DECOY_TICKS = 50;
   public static final double DECOY_SPEED = 0.26;
   public static final double VEIL_AGGRO_RANGE = 24.0;
   public static final float VEIL_STRIKE_BONUS = 3.0F;
   public static final int DAZE_TICKS = 30;
   public static final double DAZE_SLOW = -0.40;
   public static final int DAZE_MOB_TARGET_TICKS = 40;
   public static final double DECOY_REVEAL_RADIUS = 3.0;
   public static final int MIS_COOLDOWN = 400;
   // Audience Participation (§5.6)
   public static final float ULT_RANGE = 20.0F;
   public static final float ULT_CONE_DEGREES = 6.0F;
   public static final int ULT_CAPTURE = 20;
   public static final int ULT_CARD_TICK = 14;
   public static final int ULT_WINDOW = 240;
   public static final int ULT_COOLDOWN = 1400;
   public static final int ULT_UNUSED_COOLDOWN = 600;
   public static final float ACT_MIN_POWER = 0.6F;
   public static final float ACT_MAX_POWER = 1.2F;
   public static final float ACT_MIN_REACH = 4.0F;
   public static final float ACT_MAX_REACH = 32.0F;
   // Ovation (§4)
   public static final int OVATION_REFUND = 60;
   public static final double OVATION_RANGE = 8.0;
   /** Refusal pose length (a short "no" with the wand). */
   public static final int REFUSE_TICKS = 8;

   private SinclairRules() {
   }

   /** §5.1: 3.0 at the throw to 7.0 after 40 ticks of flight, linear, held after. */
   public static float boltDamage(int age) {
      float t = Math.max(0.0F, Math.min(1.0F, age / (float) BOLT_RAMP_TICKS));
      return BOLT_DAMAGE_MIN + (BOLT_DAMAGE_MAX - BOLT_DAMAGE_MIN) * t;
   }

   /** §5.2: a swap is allowed by count, the first-swap age and the interval since the last swap. */
   public static boolean swapAllowed(int swapsUsed, int assistantAge, int ticksSinceLastSwap) {
      if (swapsUsed >= SWAP_COUNT || assistantAge < SWAP_FIRST_MIN_AGE) {
         return false;
      }

      return swapsUsed == 0 || ticksSinceLastSwap >= SWAP_INTERVAL;
   }

   /** §5.4: hex length by target kind. */
   public static int hexTicks(boolean player) {
      return player ? HEX_PLAYER_TICKS : HEX_MOB_TICKS;
   }

   /** §5.4: inside the hex cylinder (radius 3.5, vertical ±2.5) around the hat's ground point. */
   public static boolean inHexArea(double dx, double dy, double dz) {
      return Math.abs(dy) <= HEX_VERTICAL && dx * dx + dz * dz <= HEX_RADIUS * HEX_RADIUS;
   }

   /** §4: an Ovation refunds 60 ticks from the ultimate cooldown, never below zero. */
   public static int ovation(int ultCooldown) {
      return Math.max(0, ultCooldown - OVATION_REFUND);
   }

   /** §6.1: act power is clamped to 0.6-1.2. */
   public static float actPower(float power) {
      return Float.isFinite(power) ? Math.max(ACT_MIN_POWER, Math.min(ACT_MAX_POWER, power)) : 1.0F;
   }

   /** §5.6: the cone test of the capture (degrees between look and target direction). */
   public static boolean inCone(double lookX, double lookY, double lookZ, double toX, double toY, double toZ, double coneDegrees) {
      double a = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
      double b = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
      if (a < 1.0E-6 || b < 1.0E-6) {
         return false;
      }

      double cos = (lookX * toX + lookY * toY + lookZ * toZ) / (a * b);
      return cos >= Math.cos(Math.toRadians(coneDegrees));
   }
}
