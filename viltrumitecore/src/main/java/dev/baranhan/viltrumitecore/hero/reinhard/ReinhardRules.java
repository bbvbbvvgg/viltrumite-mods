package dev.baranhan.viltrumitecore.hero.reinhard;

/**
 * Every Reinhard number (spec docs/design/2026-10-11-reinhard-design.md).
 * Pure: no world access. Ticks, 20 t = 1 s. Starting values; tuning only
 * changes constants here.
 */
public final class ReinhardRules {
   // ---- chassis (spec §3.1) ----
   public static final float MAX_HEALTH = 20.0F;
   /** Share of ordinary damage that never lands. */
   public static final float RESIST = 0.999F;
   /** Ordinary damage never takes him below this. */
   public static final float HP_FLOOR = 1.0F;
   public static final float REGEN_PER_SECOND = 1.0F;
   public static final int REGEN_INTERVAL = 20;
   public static final double KNOCKBACK_RESISTANCE = 1.0;
   /** Blocks under the world's bottom at which the air step lifts him out (vanilla void damage starts at 64). */
   public static final int VOID_RESCUE_DEPTH = 8;
   /** Radius of the search for ground when the column above the fall point is empty. */
   public static final int VOID_SEARCH_RADIUS = 64;

   // ---- modes (spec §5) ----
   public static final int MODE_COOLDOWN = 60;

   // ---- Holy Sword (spec §7.2, used in Stage 4) ----
   public static final float CHOP_BASE_DAMAGE = 20.0F;
   /** Reid idle time before the auto-return to the sheath (spec §7.3: 60 s); HUD bar scale. */
   public static final int REID_IDLE_TICKS = 1200;

   // ---- Divine Protection of the Phoenix (spec §4.2) ----
   /** Online ticks before the next revive (10 min). */
   public static final int PHOENIX_COOLDOWN = 12000;
   /** PersistentCooldowns key. */
   public static final String PHOENIX_KEY = "reinhard:phoenix";
   public static final int PHOENIX_MIN_DIST = 6;
   public static final int PHOENIX_MAX_DIST = 10;

   // ---- Protection Acquisition (spec §4.8) ----
   /** Divine hits of one type in a row that make him immune to it (the last one still lands). */
   public static final int ADAPT_HITS = 3;

   // ---- Blessing of the Holy Sword (spec §4.1) ----
   /** Front cone of the parry, horizontal: cos 60° = ±60° around the look. */
   public static final float PARRY_CONE_COS = 0.5F;
   /** Parry pose flag lifetime after a parried hit. */
   public static final int PARRY_POSE_TICKS = 8;

   // ---- Divine Protection of the Cloudy Sky (spec §4.4) ----
   /** Air steps in a row before touching ground, water, lava or a ladder again. */
   public static final int AIR_STEPS = 5;
   /** Vertical velocity of one air step (blocks/tick; ~2.5 blocks from a standstill). */
   public static final double AIR_STEP_VELOCITY = 0.62;
   /** Super jump launch velocity (~22 blocks, HeroSuperJump.apex). */
   public static final float SUPER_JUMP_VELOCITY = 2.2F;

   private ReinhardRules() {
   }

   /** Ordinary damage after the 99.9 % resist. */
   public static float resist(float raw) {
      if (!(raw > 0.0F)) {
         return 0.0F;
      }

      return raw * (1.0F - RESIST);
   }

   /** HP loss allowed by the floor: never below {@link #HP_FLOOR}; NaN and infinity safe. */
   public static float floor(float health, float amount) {
      if (Float.isNaN(amount) || Float.isNaN(health) || !(amount > 0.0F)) {
         return 0.0F;
      }

      return Math.max(0.0F, Math.min(amount, health - HP_FLOOR));
   }
}
