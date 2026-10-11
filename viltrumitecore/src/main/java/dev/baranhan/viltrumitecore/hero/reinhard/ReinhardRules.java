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
