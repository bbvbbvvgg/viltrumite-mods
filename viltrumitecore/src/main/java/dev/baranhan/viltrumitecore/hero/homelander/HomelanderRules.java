package dev.baranhan.viltrumitecore.hero.homelander;

/**
 * Homelander numbers (spec §10) and pure functions. No world access.
 * Ticks: 20 t = 1 s. Damage in health points.
 */
public final class HomelanderRules {
   // Eye lasers (§5.1)
   public static final int LASER_CHARGE_TICKS = 4;
   public static final double LASER_RANGE = 64.0;
   public static final float LASER_DAMAGE = 1.5F;
   public static final int LASER_DAMAGE_INTERVAL = 4;
   public static final int LASER_FIRE_TICKS = 80;
   public static final int SCORCH_LIFETIME = 600;
   public static final int SCORCH_MAX = 1500;
   // Eye heat (§6)
   public static final float HEAT_MAX = 100.0F;
   public static final float HEAT_LASER_PER_TICK = 1.0F;
   public static final float HEAT_FOCUS_PER_TICK = 0.25F;
   public static final int HEAT_COOL_DELAY = 20;
   public static final float HEAT_COOL_PER_TICK = 100.0F / 120.0F;
   public static final int OVERHEAT_LOCK_TICKS = 60;
   // Focus (§5.2)
   public static final double FOCUS_ACQUIRE_RADIUS = 24.0;
   public static final double FOCUS_DROP_RADIUS = 48.0;
   public static final int FOCUS_MAX_TARGETS = 8;
   public static final int FOCUS_REFRESH_TICKS = 10;
   public static final int FEAR_LINGER_TICKS = 20;
   /** Fear is re-applied every refresh with this duration, so it lingers FEAR_LINGER_TICKS after a drop. */
   public static final int FEAR_DURATION = FOCUS_REFRESH_TICKS + FEAR_LINGER_TICKS;
   public static final double FEAR_FLEE_DISTANCE = 16.0;
   // Roar (§5.3)
   public static final double ROAR_CONE_DEG = 90.0;
   /** Anything this close is hit unless it is behind the back (beyond ROAR_CLOSE_DEG from the look). */
   public static final double ROAR_CLOSE_RANGE = 3.0;
   public static final double ROAR_CLOSE_DEG = 120.0;
   /** Damage at point blank; scales down to half at the cone edge. */
   public static final float ROAR_DAMAGE = 6.0F;
   public static final double ROAR_RANGE = 12.0;
   public static final int ROAR_SLOW_TICKS = 60;
   public static final int ROAR_COOLDOWN = 300;
   // Regeneration (§4)
   public static final int REGEN_INTERVAL = 40;
   public static final int REGEN_DAMAGE_PAUSE = 100;
   /** Bonus max health (20 = +10 hearts). */
   public static final double BONUS_MAX_HEALTH = 20.0;
   /** Bonus armor points. */
   public static final double BONUS_ARMOR = 10.0;
   // Chassis (user balance pass 2026-10-11): no longer the Viltrumite 19 / 97 %.
   /** Base strength: fist +6 attack damage (7 total); kit punch 12..30, chop 10.5, clap 6, dash 3. */
   public static final float BASE_DAMAGE = 6.0F;
   /** Incoming damage reduction in percent (Viltrumite: 97). */
   public static final float DAMAGE_REDUCTION = 40.0F;
   /** Hits below this after the reduction are ignored. */
   public static final float DAMAGE_IGNORE_THRESHOLD = 0.5F;

   private HomelanderRules() {
   }
}
