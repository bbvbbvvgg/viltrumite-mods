package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosRules;

/**
 * Numbers of the war for Atheria (pure): the Sanctuary II, its strikes, the Black Order, the
 * Chitauri waves and the destruction budget. Ticks at 20 per second.
 */
public final class ThanosWarRules {
   // ---- Sanctuary II -------------------------------------------------------------------------------
   /** Ticks from the event start until the ship starts to come out of the clouds. */
   public static final int SHIP_DELAY = 40;
   /** Ticks of the glide from the horizon to the hover point. */
   public static final int SHIP_ARRIVAL = 420;
   /** Height of the hover point above the arena ground. */
   public static final double SHIP_ALTITUDE = 112.0;
   /** Horizontal offset of the hover point from the arena centre (seen at about 60 degrees up). */
   public static final double SHIP_OFFSET = 64.0;
   /** Where the glide starts: this far out, this much higher. */
   public static final double SHIP_START_DISTANCE = 720.0;
   public static final double SHIP_START_RISE = 90.0;
   /** Wing span of the model in blocks (a third of the sky from the arena). */
   public static final float SHIP_SPAN = 180.0F;
   /** Ticks of the departure after a victory (it turns and climbs away). */
   public static final int SHIP_LEAVE = 260;
   /** Bob of the hover: amplitude (blocks) and period (ticks). */
   public static final double SHIP_BOB = 1.6;
   public static final int SHIP_BOB_PERIOD = 220;

   // ---- strikes -------------------------------------------------------------------------------------
   /** Warning ring on the ground before a lance lands. */
   public static final int STRIKE_WARNING = 40;
   public static final int STRIKE_WARNING_GAUNTLET = 32;
   public static final double STRIKE_RADIUS = 4.5;
   /** Bombardment strike damage (the Ship Call uses the move's own damage). */
   public static final float STRIKE_DAMAGE = 18.0F;
   public static final double STRIKE_CRATER = 2.6;
   public static final float STRIKE_HARDNESS = 3.0F;
   public static final int MAX_STRIKES = 6;
   /** Bombardment rhythm in the invasion (and slower in the gauntlet phase). */
   public static final int BOMBARD_EVERY = 150;
   public static final int BOMBARD_EVERY_GAUNTLET = 260;
   /** A bombardment shot never lands closer than this to a hero (it is meant to be dodged). */
   public static final double BOMBARD_SAFE = 5.0;
   public static final double BOMBARD_RANGE = 44.0;

   // ---- Black Order ---------------------------------------------------------------------------------
   public static final float MAW_HEALTH = 620.0F;
   public static final float CULL_HEALTH = 980.0F;
   public static final float ORDER_HEALTH_PER_HERO = 0.45F;
   /** A Black Order hit cap, as Thanos's (no one-shot): share of max health per hit. */
   public static final float ORDER_HIT_CAP = 0.06F;
   public static final int BEAM_TICKS = 50;
   /** Ticks a Black Order member stays out of a fight that left him (then beams back). */
   public static final double ORDER_LEASH = 72.0;

   // ---- Chitauri ------------------------------------------------------------------------------------
   public static final int ARMY_BASE_CAP = 12;
   public static final int ARMY_CAP_PER_HERO = 4;
   public static final int ARMY_MAX_CAP = 30;
   /** Every Chitauri the war may bring in one event (then the portals stay shut). */
   public static final int ARMY_TOTAL = 64;
   public static final int WAVE_EVERY = 400;
   public static final int WAVE_EVERY_GAUNTLET = 640;
   public static final int PORTAL_LIFE = 220;
   public static final double PORTAL_HEIGHT = 22.0;
   public static final double PORTAL_RANGE = 34.0;

   // ---- destruction ---------------------------------------------------------------------------------
   /** Blocks the whole war may remove (strikes, rocks, Black Order), past the shared gates. */
   public static final int BLOCK_BUDGET = 3200;
   public static final int STRIKE_BLOCKS = 44;
   public static final int ROCK_BLOCKS = 26;
   public static final int CHARGE_BLOCKS = 54;
   public static final int TOPPLE_BLOCKS = 14;

   // ---- city ------------------------------------------------------------------------------------------
   public static final int ALARM_LEASE = 200;
   public static final float ALARM_RADIUS = 160.0F;
   /** Grid hits of the invasion: blackouts in the districts around the arena. */
   public static final int BLACKOUT_EVERY = 300;
   public static final float BLACKOUT_SEVERITY = 0.7F;
   public static final float BLACKOUT_RADIUS = 18.0F;

   // ---- tactics ---------------------------------------------------------------------------------------
   /** Thanos lost this share of his max health within {@link #HEAVY_WINDOW} ticks: defensive tactic. */
   public static final float HEAVY_DAMAGE = 0.05F;
   public static final int HEAVY_WINDOW = 80;
   public static final int DEFENSIVE_TICKS = 200;
   /** Share of a hero's damage dealt from range that makes him "the sniper". */
   public static final double RANGED_SHARE = 0.55;
   /** A hit from this far (or by a projectile) counts as ranged. */
   public static final double RANGED_DISTANCE = 9.0;

   private ThanosWarRules() {
   }

   /** Health scale of a Black Order member for the heroes counted. */
   public static float orderHealth(float base, int heroes) {
      int counted = Math.max(1, Math.min(ThanosRules.MAX_COUNTED_HEROES, heroes));
      return base * (1.0F + ORDER_HEALTH_PER_HERO * (counted - 1));
   }

   /** Chitauri alive at once for the heroes counted. */
   public static int armyCap(int heroes) {
      int counted = Math.max(1, Math.min(ThanosRules.MAX_COUNTED_HEROES, heroes));
      return Math.min(ARMY_MAX_CAP, ARMY_BASE_CAP + ARMY_CAP_PER_HERO * (counted - 1));
   }

   /** Soldiers one wave brings (before the cap). */
   public static int waveSize(int heroes, int wave) {
      int counted = Math.max(1, Math.min(ThanosRules.MAX_COUNTED_HEROES, heroes));
      return Math.min(10, 4 + 2 * (counted - 1) + Math.min(2, wave / 3));
   }

   /** Room for a wave: what the cap and the event total still allow. */
   public static int room(int alive, int spawned, int heroes) {
      return Math.max(0, Math.min(armyCap(heroes) - alive, ARMY_TOTAL - spawned));
   }

   public static int waveEvery(ThanosPhase phase) {
      return phase == ThanosPhase.GAUNTLET ? WAVE_EVERY_GAUNTLET : WAVE_EVERY;
   }

   public static int bombardEvery(ThanosPhase phase) {
      return phase == ThanosPhase.GAUNTLET ? BOMBARD_EVERY_GAUNTLET : BOMBARD_EVERY;
   }

   public static int strikeWarning(ThanosPhase phase) {
      return phase == ThanosPhase.GAUNTLET ? STRIKE_WARNING_GAUNTLET : STRIKE_WARNING;
   }

   /** Does the army fight in this phase? */
   public static boolean armyPhase(ThanosPhase phase) {
      return phase.atLeast(ThanosPhase.INVASION);
   }

   /** Does the ship bombard the city in this phase? */
   public static boolean bombardPhase(ThanosPhase phase) {
      return phase.atLeast(ThanosPhase.INVASION);
   }

   /** Do Ebony Maw and Cull Obsidian fight in this phase? */
   public static boolean orderPhase(ThanosPhase phase) {
      return phase.atLeast(ThanosPhase.BLACK_ORDER);
   }

   /** Ease of the ship's glide (0..1 -> 0..1, slow start, long slow end). */
   public static double glide(double t) {
      double x = Math.max(0.0, Math.min(1.0, t));
      return 1.0 - Math.pow(1.0 - x, 3.0) * (1.0 - 0.35 * x);
   }

   /** Ship height offset of the hover bob at a tick. */
   public static double bob(long tick) {
      return SHIP_BOB * Math.sin(tick * Math.PI * 2.0 / SHIP_BOB_PERIOD);
   }

   /** Lance damage falloff from the centre of the ring (1 at the centre, 0.35 at the rim). */
   public static float strikeFalloff(double distance, double radius) {
      if (distance > radius) {
         return 0.0F;
      }

      return (float)(1.0 - 0.65 * (distance / Math.max(0.01, radius)));
   }
}
