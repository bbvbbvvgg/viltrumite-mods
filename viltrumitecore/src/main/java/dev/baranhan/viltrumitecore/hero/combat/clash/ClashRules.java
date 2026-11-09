package dev.baranhan.viltrumitecore.hero.combat.clash;

/**
 * Tuning for the three clash mechanics (wave 1, session 23): beam struggle, clinch and sky brawl.
 * All durations are server ticks. Pure constants so the pure state machines stay testable.
 */
public final class ClashRules {
   // ---- beam struggle -------------------------------------------------------------------------
   /** A reported beam lives this many ticks without a fresh {@link ClashBeams#report} call. */
   public static final int BEAM_EXPIRY_TICKS = 2;
   /** Beams count as head-on when the dot product of their directions is below this. */
   public static final double HEAD_ON_DOT = -0.5;
   /** Largest gap between two beams that still makes them collide, in blocks. */
   public static final double CONTACT_RADIUS = 1.25;
   /** The meeting point must be at least this far from each muzzle, in blocks. */
   public static final double MIN_BEAM_LENGTH = 1.0;
   /** Opening ticks in which the collision point holds still (both screens show the meter, ping settles). */
   public static final int GRACE_TICKS = 10;
   /** Hard limit; nobody pushed through, so the collision point explodes. */
   public static final int MAX_TICKS = 200;
   /** Collision point travel per tick at full advantage, as a fraction of the gap between muzzles. */
   public static final double POINT_SPEED = 0.012;
   /** The collision point closer than this fraction to a muzzle ends the struggle. */
   public static final double WIN_MARGIN = 0.12;
   /** Sliding window for counting mash pulses. */
   public static final int PULSE_WINDOW = 20;
   /** Accepted pulses per window (10 per second); extra pulses are ignored, macros gain nothing. */
   public static final int MAX_PULSES_PER_WINDOW = 10;
   /** Extra pressure per accepted pulse in the window. */
   public static final double PULSE_BONUS = 0.10;
   /** A beat every second; hitting it grants a surge. */
   public static final int BEAT_PERIOD = 20;
   public static final int BEAT_EARLY = 3;
   public static final int BEAT_LATE = 3;
   /** Lateness forgiven for ping, in ticks (one-way trip, capped). */
   public static final int MAX_LATENCY_TICKS = 6;
   public static final int SURGE_TICKS = 8;
   public static final double SURGE_MULTIPLIER = 1.5;
   /** Pressure of a beam owner that cannot mash (bosses, NPC), on top of its power. */
   public static final double AUTO_PRESSURE = 1.0;
   /** The loser takes this much damage per unit of the winner's beam power. */
   public static final float LOSER_DAMAGE_PER_POWER = 6.0F;
   /** A neutral explosion (time-out) hurts both sides this much. */
   public static final float DRAW_DAMAGE = 4.0F;
   /** The loser cannot fire a reported beam for this long. */
   public static final int BEAM_LOCK_TICKS = 40;

   // ---- shared ---------------------------------------------------------------------------------
   /** The same two fighters cannot clash again this soon (any mechanic). */
   public static final int PAIR_COOLDOWN_TICKS = 100;
   /** Concurrent clashes per level; further encounters resolve as ordinary hits. */
   public static final int MAX_CLASHES_PER_LEVEL = 8;
   /** State packets go to watchers within this distance. */
   public static final double WATCH_DISTANCE = 96.0;

   private ClashRules() {
   }
}
