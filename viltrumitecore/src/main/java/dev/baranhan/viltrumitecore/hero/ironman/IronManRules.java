package dev.baranhan.viltrumitecore.hero.ironman;

/**
 * All Iron Man numbers (spec §5, §7, §8.6, §18). Pure constants: tuning
 * changes only this file. Per-tick values: 20 t = 1 s.
 */
public final class IronManRules {
   // Energy (spec §5)
   public static final float ENERGY_MAX = 100.0F;
   /** ~10 / s. */
   public static final float ENERGY_REGEN_PER_TICK = 0.5F;
   /** Regen pause after the last spend or drain (~1 s). */
   public static final int ENERGY_REGEN_DELAY = 20;
   /** Hover ~1 / s. */
   public static final float DRAIN_HOVER = 0.05F;
   /** Cruise ~1 / s. */
   public static final float DRAIN_CRUISE = 0.05F;
   /** Sonic ~6 / s. */
   public static final float DRAIN_SONIC = 0.3F;
   /** After hitting 0, weapons and shield stay off until this value (spec §5.3). */
   public static final float WEAPONS_UNLOCK = 20.0F;

   // Stage 2+ energy costs (spec §5.2), used by later plans.
   public static final float COST_REPULSOR = 2.0F;
   public static final float COST_REPULSOR_CHARGED = 6.0F;
   public static final float COST_REPULSOR_VOLLEY = 10.0F;
   public static final float COST_UNIBEAM = 35.0F;
   public static final float COST_MISSILES = 15.0F;
   public static final float COST_NANO_WEAPON = 8.0F;
   public static final float COST_SHIELD_HIT = 4.0F;
   /** War Machine gun ~5 / s. */
   public static final float DRAIN_WAR_MACHINE_GUN = 0.25F;

   // Nano suit (spec §4.2)
   public static final int SUIT_DEPLOY_TICKS = 20;
   public static final int SUIT_RETRACT_TICKS = 20;
   public static final double NANO_ARMOR = 28.0;
   public static final double NANO_TOUGHNESS = 8.0;
   /** Extra max health while any suit is on (doubles Tony's 20 HP). */
   public static final double SUIT_HEALTH_BONUS = 20.0;
   public static final double NANO_KNOCKBACK_RES = 0.3;
   /** Permanent Regeneration II (amplifier 1), suit on or off (user decision 2026-10-11). */
   public static final int PASSIVE_REGEN_AMPLIFIER = 1;
   /** Nano punch on the ground (vanilla LMB damage factor). */
   public static final float NANO_MELEE_FACTOR = 1.5F;

   // Fly-by punch (spec §7.4): LMB in flight, player velocity untouched.
   public static final float FLYBY_BASE = 4.0F;
   /** Extra damage per block/tick of flight speed. */
   public static final float FLYBY_PER_SPEED = 1.5F;
   public static final float FLYBY_MAX = 12.0F;
   public static final double FLYBY_RANGE = 3.5;
   /** Target boxes are inflated by this for the ray (a cone-like reach). */
   public static final double FLYBY_RADIUS = 1.0;
   public static final double FLYBY_KNOCKBACK = 1.2;
   /** Minimum ticks between punches (packet spam guard, ~2 punches/s). */
   public static final int FLYBY_COOLDOWN = 10;

   // Sonic ram (spec §7.4): bodies in the swept box while SONIC.
   public static final float RAM_DAMAGE = 6.0F;
   public static final double RAM_KNOCKBACK = 1.5;
   public static final int RAM_REHIT_TICKS = 10;

   // Landings and air strike (spec §8.6).
   /** Airborne ticks before the next touchdown classifies again. */
   public static final int LANDING_REARM_TICKS = 5;
   /** Equivalent fall (blocks) from which a flight touchdown is HEAVY. */
   public static final float HEAVY_FALL = 8.0F;
   /** Kneel pose flag duration after a heavy landing / air strike. */
   public static final int HEAVY_POSE_TICKS = 20;
   public static final dev.baranhan.viltrumitecore.hero.HeroShockwave.Landing HEAVY_LANDING =
      new dev.baranhan.viltrumitecore.hero.HeroShockwave.Landing(HEAVY_FALL, 24.0F, 3.0, 6.0, 3.0F, 8.0F);
   public static final float AIR_STRIKE_PITCH = 35.0F;
   public static final double AIR_STRIKE_ARM_DIST = 8.0;
   public static final int AIR_STRIKE_ARM_TICKS = 30;
   /** Air strike shockwave: always full power (fall passed = fullPowerFall). */
   public static final dev.baranhan.viltrumitecore.hero.HeroShockwave.Landing AIR_STRIKE_LANDING =
      new dev.baranhan.viltrumitecore.hero.HeroShockwave.Landing(0.0F, 16.0F, 5.0, 8.0, 8.0F, 14.0F);
   public static final dev.baranhan.viltrumitecore.hero.HeroDebris.Eruption AIR_STRIKE_CRATER =
      new dev.baranhan.viltrumitecore.hero.HeroDebris.Eruption(3.0, 2.0, 0.0, 24, 1.1, 1.0);
   public static final float AIR_STRIKE_DEBRIS_DAMAGE = 4.0F;

   // Flight (spec §7.1–7.2): normal flight is the original flight, same as
   // Homelander (no profile). Only the 0-energy glide uses a profile; with
   // glide on the throttle is forced to 0 (no thrust, no sonic), so only the
   // sink speed matters; the other numbers are unused.
   /** Energy 0: glide down at this speed (blocks per tick). */
   public static final float FLIGHT_GLIDE_SINK = 0.12F;


   // ---- Stage 2: nano combat kit (spec §8, §9, §18; plan Task 1) ----
   // Damage is per hit with an explicit interval. Sustained ranged damage is
   // at best equal to the Homelander laser (1.5 every 4 t = 0.375/t).
   /** Repulsor tap: release before this many ticks of charge. */
   public static final int REPULSOR_TAP_TICKS = 4;
   public static final int REPULSOR_CHARGE_MAX = 20;
   /** Ticks between two repulsor shots (3.0 every 8 t = 0.375/t). */
   public static final int REPULSOR_COOLDOWN = 8;
   public static final float REPULSOR_SHOT = 3.0F;
   public static final float REPULSOR_CHARGED_MIN = 4.0F;
   public static final float REPULSOR_CHARGED_MAX = 8.0F;
   /** Volley total from both hands (two bolts of half each). */
   public static final float REPULSOR_VOLLEY = 12.0F;
   public static final double REPULSOR_SPEED = 3.0;
   public static final double REPULSOR_RANGE = 48.0;
   public static final double REPULSOR_KNOCKBACK_TAP = 0.3;
   public static final double REPULSOR_KNOCKBACK_MAX = 1.6;
   /** Shot against the flight direction brakes: velocity x(1 - BRAKE * power). */
   public static final double REPULSOR_BRAKE = 0.5;
   /** normalized shot · velocity below this counts as a back shot. */
   public static final double REPULSOR_BRAKE_DOT = -0.6;
   public static final double REPULSOR_LIFT = 0.6;
   public static final float REPULSOR_LIFT_PITCH = 60.0F;
   public static final float REPULSOR_SHOCKWAVE_PITCH = 70.0F;
   public static final double REPULSOR_SHOCKWAVE_RADIUS = 4.0;
   public static final float REPULSOR_SHOCKWAVE_DAMAGE = 4.0F;
   public static final double REPULSOR_SHOCKWAVE_KNOCKBACK = 1.2;
   /** Recoil pose flag after a shot. */
   public static final int REPULSOR_RECOIL_TICKS = 6;

   public static final int UNIBEAM_CHARGE = 20;
   public static final int UNIBEAM_MAX = 60;
   public static final int UNIBEAM_OVERHEAT_LOCK = 40;
   public static final int UNIBEAM_HIT_INTERVAL = 4;
   public static final float UNIBEAM_HIT = 1.5F;
   public static final float UNIBEAM_HIT_FAR = 0.75F;
   public static final double UNIBEAM_NEAR = 8.0;
   /** Client scorch marks of repulsors / Unibeam (same budget as Homelander's lasers). */
   public static final int SCORCH_MAX = 256;
   public static final long SCORCH_LIFETIME = 600L;
   public static final double UNIBEAM_RANGE = 48.0;
   public static final double UNIBEAM_RADIUS = 0.6;
   public static final float UNIBEAM_TURN_DEG = 3.0F;
   /** Velocity added along the beam on every damage hit (push, not hold). */
   public static final double UNIBEAM_PUSH = 0.35;
   public static final double UNIBEAM_BLIND_RANGE = 12.0;
   public static final float UNIBEAM_BLIND_ANGLE = 25.0F;
   /** Flash ticks: in the beam / looking into it / owner / PvP cap. */
   public static final int FLASH_BEAM_TICKS = 30;
   public static final int FLASH_LOOK_TICKS = 16;
   public static final int FLASH_OWNER_TICKS = 6;
   public static final int FLASH_PVP_MAX_TICKS = 20;
   public static final int UNIBEAM_MOB_BLIND_TICKS = 40;

   public static final int OVERDRAFT_SPUTTER_AT = 40;
   public static final int OVERDRAFT_EXPLODE_AFTER_SPUTTER = 30;
   public static final float OVERDRAFT_DAMAGE_MULT = 1.5F;
   public static final float CORE_EXPLOSION_POWER = 12.0F;
   public static final float CORE_EXPLOSION_POWER_RELEASED = 8.0F;
   /** HP the player keeps after the own core explosion (2 hearts). */
   public static final float CORE_SURVIVE_HP = 4.0F;
   /** Own core explosion damage is floored for this many ticks after the blast (deferred payouts). */
   public static final int CORE_EXPLOSION_WINDOW = 40;
   public static final double CORE_EXTRA_KNOCKBACK_RADIUS = 10.0;
   public static final double CORE_EXTRA_KNOCKBACK = 2.0;
   public static final double CORE_SELF_LAUNCH = 1.6;
   /** Nano suit lock after a core explosion (~30 s). */
   public static final int NANO_LOST_TICKS = 600;

   public static final int MISSILE_FLAPS = 10;
   public static final int MISSILE_MARKS = 4;
   public static final double MISSILE_LOCK_RANGE = 64.0;
   public static final float MISSILE_LOCK_DEG = 4.0F;
   public static final float MISSILE_HIT = 8.0F;
   public static final float MISSILE_SPLASH = 4.0F;
   public static final double MISSILE_SPLASH_RADIUS = 3.0;
   public static final double MISSILE_SPEED = 1.3;
   /** Homing turn per tick (fraction of the way to the target direction). */
   public static final double MISSILE_TURN = 0.22;
   public static final int MISSILE_LIFETIME = 100;
   /** Fan spread of the launch (degrees between missiles). */
   public static final float MISSILE_FAN_DEG = 14.0F;
   /** Mark brackets stay this long after the last sweep over the target. */
   public static final int MISSILE_MARK_SHOW = 60;

   /** Nano weapon forming wave from the wrist. */
   public static final int NANO_FORM_TICKS = 8;
   public static final float BLADE = 7.0F;
   public static final float HAMMER = 9.0F;
   public static final float HAMMER_SLAM = 5.0F;
   public static final double BLADE_REACH = 3.5;
   public static final float BLADE_ARC_DEG = 100.0F;
   public static final int BLADE_SWING_TICKS = 8;
   public static final int HAMMER_SWING_TICKS = 14;
   public static final int BLADE_SUNDER_TICKS = 100;
   public static final double HAMMER_REACH = 3.5;
   public static final double HAMMER_KNOCKBACK = 2.2;
   public static final double HAMMER_SLAM_RADIUS = 4.0;
   public static final double HAMMER_SLAM_LIFT = 0.9;
   /** Airborne target hit by the hammer is driven down with this vy. */
   public static final double HAMMER_SLAM_DOWN = -2.0;
   public static final int HAMMER_CHARGE_MAX = 20;
   public static final double HAMMER_LAUNCH_MIN = 1.2;
   public static final double HAMMER_LAUNCH_MAX = 2.6;
   public static final int HAMMER_LAUNCH_TICKS = 20;
   /** Soft blocks a launched target breaks (destroy speed at most this). */
   public static final float HAMMER_SOFT_HARDNESS = 1.5F;

   public static final int PERFECT_BLOCK_TICKS = 5;
   /** Front cone of the shield: full angle. */
   public static final float SHIELD_CONE_DEG = 120.0F;
   public static final double SHIELD_PERFECT_KNOCKBACK = 2.0;

   /** Heavy hit (after armor) that shows nano damage zones. */
   public static final float NANO_DAMAGE_HIT = 6.0F;
   public static final int NANO_REPAIR_TICKS = 60;

   // ---- Stage 3: helmet, JARVIS, scan, countermeasures (spec §10, §11) ----
   /** Nanite fold / unfold of the helmet. */
   public static final int HELMET_TOGGLE_TICKS = 12;
   /** Threat list refresh period and range (spec §11.1). */
   public static final int THREAT_SCAN_INTERVAL = 5;
   public static final double THREAT_RANGE = 32.0;
   /** A player looking at us: cos of the view cone half angle. */
   public static final double THREAT_LOOK_DOT = 0.95;
   /** Projectile arrows: path passes this close to the player. */
   public static final double THREAT_PROJECTILE_MISS = 2.0;
   /** JARVIS hints: low energy warning below this. */
   public static final float JARVIS_LOW_ENERGY = 20.0F;
   /** Voice: global gap after a line ends, ticks (≥ 4 s). */
   public static final int JARVIS_GLOBAL_GAP = 80;
   /** Scan (spec §11.2): aim this long on the same target. */
   public static final int SCAN_TICKS = 30;
   public static final double SCAN_RANGE = 48.0;
   /** One-press scan: half-angle of the auto-lock cone around the view (degrees). */
   public static final double SCAN_LOCK_CONE_DEG = 25.0;
   /** Line of sight / aim may be lost this long before the scan is cancelled. */
   public static final int SCAN_LOS_GRACE = 10;
   /** A started scan without any target gives up after this. */
   public static final int SCAN_IDLE_TIMEOUT = 200;
   /** Through-wall highlight and card lifetime after a scan. */
   public static final int SCAN_HIGHLIGHT_TICKS = 200;
   /** Countermeasures (spec §11.3). */
   public static final int COUNTERMEASURES_COOLDOWN = 400;
   public static final int FLARE_COUNT = 8;
   public static final int FLARE_LIFE = 60;
   public static final double FLARE_HOMING_RANGE = 24.0;
   public static final double FLARE_MOB_RANGE = 32.0;
   /** Mobs lose the player for this long (~2 s). */
   public static final int FLARE_FORGET_TICKS = 40;

   private static final dev.baranhan.viltrumiteflight.util.FlightProfile GLIDE_PROFILE = new dev.baranhan.viltrumiteflight.util.FlightProfile(
      1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, true, FLIGHT_GLIDE_SINK);

   public static dev.baranhan.viltrumiteflight.util.FlightProfile glideProfile() {
      return GLIDE_PROFILE;
   }

   private IronManRules() {
   }
}
