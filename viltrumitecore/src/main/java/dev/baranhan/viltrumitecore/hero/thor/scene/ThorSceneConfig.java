package dev.baranhan.viltrumitecore.hero.thor.scene;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Session C tunables. Every value marked PLACEHOLDER is a temporary number
 * that the author has not approved (spec: [ОТКРЫТО] resist speed, timings,
 * durations, safe positioning); they are runtime-configurable through the
 * dev command {@code /thor debug scene-config <key> <value>} and must not be
 * read as final decisions. Approved numbers (≈1 s grab window, ~80 block
 * camera, 10 s teleport limit) are marked APPROVED with the decision number.
 * Server main thread (the client reads the copies it needs from the scene
 * packet or the same defaults).
 */
public final class ThorSceneConfig {
   // --- grab window (decisions 35, 52, 57, 58, 106) ---
   /** PLACEHOLDER: chance per landed hit of a normal LMB/RMB combo that the (once per combo) window opens. */
   public static float grabWindowChance = 0.3F;
   /** APPROVED (52, 57): "около 1 секунды". */
   public static int grabWindowTicks = 20;
   /** PLACEHOLDER: a hit after this many idle ticks belongs to a new combo when no COMBO_END arrived. */
   public static int comboIdleTicks = 40;
   /** PLACEHOLDER: how far the remembered window target may be when MMB is pressed (blocks, eye to box). */
   public static double grabReach = 4.5;

   // --- hold and resistance (decisions 59-61, 123, 124, 150, 151) ---
   /** PLACEHOLDER: the longest hold without a chosen action (ticks); the victim drops after it. */
   public static int holdMaxTicks = 300;
   /** PLACEHOLDER: escape meter per accepted press of the shown key (1 = free). */
   public static float resistPerPress = 0.085F;
   /** PLACEHOLDER: meter decay per tick. */
   public static float resistDecay = 0.006F;
   /** PLACEHOLDER: anti-macro cap of accepted presses per second. */
   public static int resistMaxPerSecond = 14;
   /** PLACEHOLDER: the hold ends when the pair drifts this far apart (teleport, knockback by a third party). */
   public static double holdBreakDistance = 8.0;

   // --- grab actions (decisions 62, 64, 87, 122, 125) ---
   /** PLACEHOLDER: LMB ground slam: impact tick, damage, crater radius, recovery. */
   public static int slamImpactTick = 12;
   public static float slamDamage = 12.0F;
   public static int slamCraterRadius = 1;
   /** PLACEHOLDER: RMB far throw: release tick, launch speed (blocks/tick), flight watch, impact damage. */
   public static int throwReleaseTick = 7;
   public static double throwSpeed = 2.4;
   public static int throwFlightTicks = 40;
   public static float throwImpactDamage = 10.0F;
   /** PLACEHOLDER: MMB fist barrage: hits, interval, damage, finisher damage and knock speed. */
   public static int barrageHits = 6;
   public static int barrageInterval = 4;
   public static float barrageDamage = 2.5F;
   public static float barrageFinisherDamage = 7.0F;
   public static double barrageKnockSpeed = 1.4;
   /** PLACEHOLDER: Thor's follow-through after the victim left the hand (ticks). */
   public static int grabRecoverTicks = 8;

   // --- fatality (decisions 53, 66, 68-70, 85-96, 118, 119) ---
   /** PLACEHOLDER: search radius for a safe scene spot when the hit spot does not fit. */
   public static double fatalityRelocateRadius = 6.0;
   /** PLACEHOLDER: intro bounds (ticks) and the fastest a body moves / turns in a contextual intro. */
   public static int fatalityIntroMin = 8;
   public static int fatalityIntroMax = 30;
   public static double fatalityMaxStep = 0.45;
   public static float fatalityTurnRate = 20.0F;
   /** PLACEHOLDER: Thor ignores damage during the cinematic (open question: who may hit a scene). */
   public static boolean fatalityThorInvulnerable = true;

   // --- teleport (decisions 14, 29, 41-48) ---
   /** APPROVED (43): camera rise. */
   public static int teleportCameraHeight = 80;
   /** PLACEHOLDER: rise duration (ticks). */
   public static int teleportRiseTicks = 16;
   /** APPROVED (spec §8): 10 s limit. */
   public static int teleportLimitTicks = 200;
   /** PLACEHOLDER: the farthest selectable point from the camera's column (horizontal blocks). */
   public static double teleportMaxRadius = 96.0;

   // --- dash (decisions 110, 135) ---
   /** PLACEHOLDER: double-tap window, dash speed (blocks/tick), ticks re-applied, cooldown. */
   public static int dashTapWindow = 7;
   public static double dashSpeed = 1.5;
   public static int dashTicks = 4;
   public static int dashCooldown = 16;

   private record Entry(Supplier<String> get, Consumer<String> set) {
   }

   private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

   static {
      f("grab_window_chance", () -> grabWindowChance, v -> grabWindowChance = v);
      i("grab_window_ticks", () -> grabWindowTicks, v -> grabWindowTicks = v);
      i("combo_idle_ticks", () -> comboIdleTicks, v -> comboIdleTicks = v);
      d("grab_reach", () -> grabReach, v -> grabReach = v);
      i("hold_max_ticks", () -> holdMaxTicks, v -> holdMaxTicks = v);
      f("resist_per_press", () -> resistPerPress, v -> resistPerPress = v);
      f("resist_decay", () -> resistDecay, v -> resistDecay = v);
      i("resist_max_per_second", () -> resistMaxPerSecond, v -> resistMaxPerSecond = v);
      d("hold_break_distance", () -> holdBreakDistance, v -> holdBreakDistance = v);
      i("slam_impact_tick", () -> slamImpactTick, v -> slamImpactTick = v);
      f("slam_damage", () -> slamDamage, v -> slamDamage = v);
      i("slam_crater_radius", () -> slamCraterRadius, v -> slamCraterRadius = v);
      i("throw_release_tick", () -> throwReleaseTick, v -> throwReleaseTick = v);
      d("throw_speed", () -> throwSpeed, v -> throwSpeed = v);
      i("throw_flight_ticks", () -> throwFlightTicks, v -> throwFlightTicks = v);
      f("throw_impact_damage", () -> throwImpactDamage, v -> throwImpactDamage = v);
      i("barrage_hits", () -> barrageHits, v -> barrageHits = v);
      i("barrage_interval", () -> barrageInterval, v -> barrageInterval = v);
      f("barrage_damage", () -> barrageDamage, v -> barrageDamage = v);
      f("barrage_finisher_damage", () -> barrageFinisherDamage, v -> barrageFinisherDamage = v);
      d("barrage_knock_speed", () -> barrageKnockSpeed, v -> barrageKnockSpeed = v);
      i("grab_recover_ticks", () -> grabRecoverTicks, v -> grabRecoverTicks = v);
      d("fatality_relocate_radius", () -> fatalityRelocateRadius, v -> fatalityRelocateRadius = v);
      i("fatality_intro_min", () -> fatalityIntroMin, v -> fatalityIntroMin = v);
      i("fatality_intro_max", () -> fatalityIntroMax, v -> fatalityIntroMax = v);
      d("fatality_max_step", () -> fatalityMaxStep, v -> fatalityMaxStep = v);
      f("fatality_turn_rate", () -> fatalityTurnRate, v -> fatalityTurnRate = v);
      b("fatality_thor_invulnerable", () -> fatalityThorInvulnerable, v -> fatalityThorInvulnerable = v);
      i("teleport_rise_ticks", () -> teleportRiseTicks, v -> teleportRiseTicks = v);
      d("teleport_max_radius", () -> teleportMaxRadius, v -> teleportMaxRadius = v);
      i("dash_tap_window", () -> dashTapWindow, v -> dashTapWindow = v);
      d("dash_speed", () -> dashSpeed, v -> dashSpeed = v);
      i("dash_ticks", () -> dashTicks, v -> dashTicks = v);
      i("dash_cooldown", () -> dashCooldown, v -> dashCooldown = v);
   }

   private ThorSceneConfig() {
   }

   /** Every PLACEHOLDER key with its current value (dev command listing). */
   public static Map<String, String> values() {
      Map<String, String> out = new LinkedHashMap<>();
      ENTRIES.forEach((key, entry) -> out.put(key, entry.get().get()));
      return out;
   }

   /** Set one PLACEHOLDER value; false for an unknown key or a bad / out-of-range number. */
   public static boolean set(String key, String value) {
      Entry entry = ENTRIES.get(key.toLowerCase(Locale.ROOT));
      if (entry == null) {
         return false;
      }

      try {
         entry.set().accept(value.trim());
         return true;
      } catch (IllegalArgumentException e) {
         return false;
      }
   }

   private static void i(String key, Supplier<Integer> get, Consumer<Integer> set) {
      ENTRIES.put(key, new Entry(() -> String.valueOf(get.get()), v -> set.accept(positive(Integer.parseInt(v)))));
   }

   private static void f(String key, Supplier<Float> get, Consumer<Float> set) {
      ENTRIES.put(key, new Entry(() -> String.valueOf(get.get()), v -> set.accept((float)nonNegative(Float.parseFloat(v)))));
   }

   private static void d(String key, Supplier<Double> get, Consumer<Double> set) {
      ENTRIES.put(key, new Entry(() -> String.valueOf(get.get()), v -> set.accept(nonNegative(Double.parseDouble(v)))));
   }

   private static void b(String key, Supplier<Boolean> get, Consumer<Boolean> set) {
      ENTRIES.put(key, new Entry(() -> String.valueOf(get.get()), v -> {
         if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(v);
         }
         set.accept(Boolean.parseBoolean(v));
      }));
   }

   private static int positive(int value) {
      if (value <= 0) {
         throw new IllegalArgumentException("must be > 0");
      }
      return value;
   }

   private static double nonNegative(double value) {
      if (!Double.isFinite(value) || value < 0) {
         throw new IllegalArgumentException("must be >= 0");
      }
      return value;
   }
}
