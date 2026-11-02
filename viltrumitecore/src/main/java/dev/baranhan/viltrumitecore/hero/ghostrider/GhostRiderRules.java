package dev.baranhan.viltrumitecore.hero.ghostrider;

/**
 * Pure numbers and formulas of the Ghost Rider (spec docs/design/2026-11-02-ghost-rider-design.md, "Сводка чисел").
 * No world access: unit-tested by GhostRiderRulesTest. The Hell Cycle's driving numbers live in HellCycleRules.
 */
public final class GhostRiderRules {
   private GhostRiderRules() {
   }

   // --- the Rider's body (Johnny Blaze keeps a plain human body) ---
   public static final double HEALTH_BONUS = 16.0;
   public static final double ARMOR = 8.0;
   public static final double TOUGHNESS = 4.0;
   public static final double KNOCKBACK_RESIST = 0.3;
   public static final double SPEED_BONUS = 0.1;
   /** Falls up to this many blocks never hurt the Rider; above it half the vanilla damage. */
   public static final float SAFE_FALL = 20.0F;

   public static float fallDamage(float distance) {
      return distance <= SAFE_FALL ? 0.0F : (distance - SAFE_FALL) * 0.5F;
   }

   // --- cooldown indices (state.cooldowns, snapshot extraCooldowns) ---
   public static final int CD_CYCLE = 0;
   public static final int CD_TRANSFORM = 1;
   public static final int CD_DASH = 2;
   public static final int CD_WHIRL = 3;
   public static final int CD_WAVE = 4;
   public static final int CD_STARE = 5;
   public static final int CD_GRAB = 6;
   public static final int CD_LASH = 7;
   public static final int COOLDOWNS = 8;
   public static final int[] COOLDOWN_TICKS = {40, 60, 160, 240, 200, 900, 60, 12};
   /** Hell Dash on foot recovers faster than the bike's. */
   public static final int DASH_FOOT_COOLDOWN = 80;
   /** An innocent verdict gives most of the Penance Stare back. */
   public static final int STARE_INNOCENT_COOLDOWN = 200;

   // --- transformation ---
   public static final int TRANSFORM_TICKS = 40;
   /** The skull bursts into flame here (FX, knock-back ring, no damage). */
   public static final int TRANSFORM_FLARE_TICK = 28;
   public static final int REVERT_TICKS = 20;
   public static final double FLARE_RADIUS = 4.0;
   public static final double FLARE_PUSH = 0.55;
   /** Burn-away frames of the skin (client), 0 = Johnny, FRAMES = bare skull. */
   public static final int BURN_FRAMES = 6;

   /** Skin frame 0..BURN_FRAMES for a transformation clip at {@code elapsed} (reverse for the way back). */
   public static int burnFrame(boolean revert, int elapsed) {
      if (revert) {
         float t = Math.min(1.0F, Math.max(0.0F, elapsed / (float)REVERT_TICKS));
         return Math.round((1.0F - t) * BURN_FRAMES);
      }
      float start = 6.0F;
      float t = Math.min(1.0F, Math.max(0.0F, (elapsed - start) / (TRANSFORM_FLARE_TICK - start)));
      return Math.round(t * BURN_FRAMES);
   }

   // --- Hell Chain on foot: LMB lash combo, RMB hook ---
   public static final double LASH_REACH = 6.5;
   public static final double LASH_ARC = 40.0;
   public static final int LASH_MAX_TARGETS = 3;
   public static final float[] LASH_DAMAGE = {6.0F, 6.0F, 9.0F};
   public static final int COMBO_LENGTH = 3;
   /** The next press continues the combo within this many ticks after a lash. */
   public static final int COMBO_WINDOW = 14;
   public static final double LASH_FINISHER_RADIUS = 2.5;
   public static final int LASH_BURN_TICKS = 40;
   public static final double HOOK_RANGE = 14.0;
   public static final float HOOK_DAMAGE = 4.0F;
   public static final int HOOK_STUN_TICKS = 20;
   /** The hooked body is reeled in over this many ticks, to this distance in front of him. */
   public static final int YANK_TICKS = 8;
   public static final double YANK_STOP = 2.2;
   /** After the yank the chain stays on the body this long: Chain Whirl may swing it. */
   public static final int HOOK_HOLD_TICKS = 40;
   /** Bodies wider or taller than this are not reeled in (the chain only bites and stuns). */
   public static final double PULL_MAX_WIDTH = 1.5;
   public static final double PULL_MAX_HEIGHT = 2.6;

   public static boolean pullable(float width, float height, boolean boss) {
      return !boss && width <= PULL_MAX_WIDTH && height <= PULL_MAX_HEIGHT;
   }

   // --- Hell Chain on the bike ---
   public static final double BIKE_LASH_REACH = 7.0;
   public static final double BIKE_LASH_ARC = 70.0;
   public static final float BIKE_LASH_DAMAGE = 8.0F;
   public static final int BIKE_LASH_BURN_TICKS = 60;
   public static final int TOW_TICKS = 40;
   public static final double TOW_LENGTH = 4.0;
   public static final float TOW_SCRAPE_DAMAGE = 1.0F;
   public static final int TOW_SCRAPE_INTERVAL = 10;

   // --- Chain Whirl ---
   public static final int WHIRL_TICKS = 30;
   public static final double WHIRL_RADIUS = 4.5;
   public static final int WHIRL_HIT_INTERVAL = 8;
   public static final float WHIRL_DAMAGE = 3.0F;
   public static final int WHIRL_BURN_TICKS = 40;
   public static final int SLAM_TICKS = 14;
   public static final int SLAM_CONTACT = 5;
   public static final double SLAM_RADIUS = 6.0;
   public static final double SLAM_FORWARD = 3.0;
   public static final float SLAM_DAMAGE = 10.0F;
   /** Extra damage for the body swung on the chain and smashed down. */
   public static final float SLAM_HELD_BONUS = 6.0F;
   public static final int SLAM_BURN_TICKS = 80;
   public static final double SLAM_LIFT = 0.5;
   public static final double SWING_RADIUS = 3.5;

   /** Falloff of the slam: full damage in the inner half, down to 60 % at the rim. */
   public static float slamDamage(double distance) {
      double inner = SLAM_RADIUS * 0.5;
      if (distance <= inner) {
         return SLAM_DAMAGE;
      }
      double t = Math.min(1.0, (distance - inner) / (SLAM_RADIUS - inner));
      return (float)(SLAM_DAMAGE * (1.0 - 0.4 * t));
   }

   // --- Hellfire (strikes, wave, burn) ---
   /** The burn: one point of hellfire damage every BURN_INTERVAL ticks (water puts it out). */
   public static final float BURN_DAMAGE = 1.0F;
   public static final int BURN_INTERVAL = 10;
   public static final int BURN_MAX_TICKS = 200;
   public static final int WAVE_TICKS = 18;
   public static final int WAVE_RELEASE = 8;
   public static final double WAVE_REACH = 11.0;
   public static final double WAVE_ARC = 55.0;
   /** Blocks the wave front travels per tick after the release. */
   public static final double WAVE_SPEED = 1.25;
   public static final float WAVE_DAMAGE = 8.0F;
   public static final int WAVE_BURN_TICKS = 100;
   public static final double WAVE_PUSH = 0.6;

   /** Total burn damage of {@code ticks} of hellfire (for the tooltip numbers and the tests). */
   public static float burnTotal(int ticks) {
      return (Math.min(ticks, BURN_MAX_TICKS) / BURN_INTERVAL) * BURN_DAMAGE;
   }

   // --- Hell Dash on foot ---
   public static final int DASH_TICKS = 10;
   public static final double DASH_IMPULSE = 1.55;
   public static final float DASH_DAMAGE = 5.0F;
   public static final int DASH_BURN_TICKS = 60;
   public static final double DASH_WIDTH = 1.2;

   // --- the bike's contacts (HellCycleContacts) ---
   public static final float RAM_DAMAGE = 9.0F;
   public static final int RAM_BURN_TICKS = 80;
   /** Normal riding only hits hostiles, and only this fast (b/t); everybody else is nudged aside unharmed. */
   public static final double HIT_SPEED = 0.6;
   public static final float HIT_DAMAGE_MIN = 4.0F;
   public static final float HIT_DAMAGE_MAX = 7.0F;
   public static final int HIT_REPEAT_TICKS = 20;
   public static final int NUDGE_REPEAT_TICKS = 6;
   public static final double NUDGE_PUSH = 0.35;

   /** Damage of a hostile hit by the bike at {@code speed} b/t (0 below HIT_SPEED). */
   public static float bikeHitDamage(double speed) {
      if (speed < HIT_SPEED) {
         return 0.0F;
      }
      double t = Math.min(1.0, (speed - HIT_SPEED) / (0.85 - HIT_SPEED));
      return (float)(HIT_DAMAGE_MIN + (HIT_DAMAGE_MAX - HIT_DAMAGE_MIN) * t);
   }

   // --- Penance Stare ---
   public static final double STARE_RANGE = 10.0;
   public static final double STARE_CONE = 12.0;
   public static final double STARE_BREAK_RANGE = 14.0;
   public static final int STARE_TICKS = 50;
   public static final int STARE_LOCK = 10;
   public static final int STARE_VERDICT = 45;
   public static final float STARE_CAP = 40.0F;
   public static final float STARE_BOSS_CAP = 60.0F;
   public static final float STARE_BOSS_FRACTION = 0.2F;
   public static final float STARE_BASE = 6.0F;
   public static final float STARE_PER_SIN = 2.5F;
   public static final int SIN_PLAYER_KILL = 5;
   public static final int SIN_INNOCENT_KILL = 3;
   /** One sin for every this much damage dealt to players and innocents. */
   public static final float SIN_HARM = 10.0F;

   /**
    * Sins from recorded deeds only: players killed, innocents killed (villagers, traders, city folk, pets), and the
    * damage dealt to players and innocents. Monsters slain are no sin. Nothing is invented: 0 deeds, 0 sins.
    */
   public static int sins(int playerKills, int innocentKills, float harm) {
      long kills = (long)Math.max(0, playerKills) * SIN_PLAYER_KILL + (long)Math.max(0, innocentKills) * SIN_INNOCENT_KILL;
      // each term saturates on its own: a huge harm record must not wrap the sum round to a negative verdict
      long harmed = (long)Math.min(Integer.MAX_VALUE, Math.floor(Math.max(0.0F, harm) / SIN_HARM));
      return (int)Math.min(Integer.MAX_VALUE, kills + harmed);
   }

   /** The verdict: 0 for the innocent; else base + per sin, capped (bosses: a fifth of their health, at most 60). */
   public static float penanceDamage(int sins, boolean boss, float maxHealth) {
      if (sins <= 0) {
         return 0.0F;
      }
      float raw = STARE_BASE + STARE_PER_SIN * sins;
      float cap = boss ? Math.min(STARE_BOSS_CAP, Math.max(1.0F, maxHealth) * STARE_BOSS_FRACTION) : STARE_CAP;
      return Math.min(raw, cap);
   }
}
