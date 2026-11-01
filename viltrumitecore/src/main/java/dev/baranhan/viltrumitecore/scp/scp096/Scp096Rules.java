package dev.baranhan.viltrumitecore.scp.scp096;

/**
 * Numbers and pure rules of SCP-096 (no Minecraft state, covered by Scp096RulesTest). Speeds are blocks per tick,
 * times are ticks.
 */
public final class Scp096Rules {
   // ---- body ----
   public static final float WIDTH = 0.8F;
   public static final float HEIGHT = 2.4F;
   public static final float SIT_WIDTH = 0.9F;
   public static final float SIT_HEIGHT = 1.35F;
   public static final float RUN_HEIGHT = 2.2F;
   public static final float MAX_HEALTH = 600.0F;
   /** Biggest share of one hit it takes (everything but /kill and the void). */
   public static final float MAX_HIT = 12.0F;
   public static final float REGEN_PER_TICK = 0.2F;
   // ---- sight ----
   public static final double FACE_RANGE = 64.0;
   public static final double SPYGLASS_RANGE = 192.0;
   /** Half angles of a player's camera frustum (16:9, FOV 70 plus a small margin). */
   public static final double PLAYER_HALF_H = Math.toRadians(52.0);
   public static final double PLAYER_HALF_V = Math.toRadians(37.0);
   public static final double SPYGLASS_HALF = Math.toRadians(6.5);
   public static final double CITIZEN_RANGE = 32.0;
   public static final double CITIZEN_HALF_H = Math.toRadians(45.0);
   public static final double CITIZEN_HALF_V = Math.toRadians(30.0);
   /** The observer must stand in front of the face: angle to the face normal at most this. */
   public static final double FACE_FRONT = Math.toRadians(75.0);
   public static final double BLIND_RANGE = 5.0;
   public static final double DARKNESS_RANGE = 16.0;
   /** Consecutive positive checks (every 2 ticks) before the face counts as seen. */
   public static final int SEEN_CHECKS = 2;
   public static final int CITIZEN_CHECK_INTERVAL = 10;
   public static final int CITIZEN_MAX_CHECKED = 12;
   public static final int CITIZEN_MAX_RAYS = 4;
   // ---- states ----
   public static final int TRIGGERED_TICKS = 26;
   public static final int CALMING_TICKS = 120;
   public static final int DEFAULT_HYSTERIA_TICKS = 280;
   /** A player target that is not in this level (offline, other dimension) is dropped after this long. */
   public static final int ABSENT_PLAYER_TICKS = 2400;
   /** A citizen target that unloaded (citizens are not saved) is dropped after this long. */
   public static final int ABSENT_CITIZEN_TICKS = 100;
   // ---- chase ----
   public static final double CHASE_SPEED = 0.62;
   public static final double CHASE_ACCEL = 0.12;
   public static final double BURST_MAX = 2.4;
   public static final double WANDER_SPEED_MOD = 0.16;
   public static final double CLIMB_SPEED = 0.45;
   public static final double LEAP_MAX_H = 24.0;
   public static final double LEAP_MAX_UP = 18.0;
   public static final double ATTACK_REACH = 2.4;
   public static final float ATTACK_DAMAGE = 40.0F;
   public static final int ATTACK_INTERVAL = 10;
   public static final int TICKET_RANGE = 256;
   public static final int STUCK_WINDOW = 20;
   public static final double STUCK_PROGRESS = 0.6;
   public static final int SMASH_TICKS = 30;
   public static final int SMASH_PER_TICK = 12;
   public static final float SMASH_MAX_HARDNESS = 50.0F;

   private Scp096Rules() {
   }

   /**
    * Chase speed this tick: the base run, or faster when the target pulls away (flying heroes, sprint boosts):
    * the target's own speed plus a margin that grows with the gap, never above {@link #BURST_MAX}.
    */
   public static double chaseSpeed(double targetSpeed, double distance) {
      double gap = Math.max(0.0, distance - 6.0);
      double wanted = targetSpeed * 1.15 + Math.min(0.6, gap * 0.01);
      return Math.min(BURST_MAX, Math.max(CHASE_SPEED, wanted));
   }

   /** Velocity component after one tick of acceleration toward {@code want}. */
   public static double approach(double current, double want, double accel) {
      double d = want - current;
      return Math.abs(d) <= accel ? want : current + Math.signum(d) * accel;
   }

   /** Ticks of hysteria from the config value in seconds (3..60 s). */
   public static int hysteriaTicks(double seconds) {
      if (!Double.isFinite(seconds)) {
         return DEFAULT_HYSTERIA_TICKS;
      }
      return (int)Math.round(Math.max(3.0, Math.min(60.0, seconds)) * 20.0);
   }

   /** The damage it takes from one hit (the void and /kill go through untouched elsewhere). */
   public static float cappedHit(float amount) {
      return Float.isFinite(amount) ? Math.max(0.0F, Math.min(MAX_HIT, amount)) : 0.0F;
   }

   /** Natural spawn check interval in ticks from seconds (10 s .. 24 h). */
   public static int checkIntervalTicks(double seconds) {
      if (!Double.isFinite(seconds)) {
         return 6000;
      }
      return (int)Math.round(Math.max(10.0, Math.min(86400.0, seconds)) * 20.0);
   }

   /** Natural spawn cooldown in ticks from minutes (0 .. 100 days of real time). */
   public static long cooldownTicks(double minutes) {
      if (!Double.isFinite(minutes)) {
         return 216000L;
      }
      return Math.round(Math.max(0.0, Math.min(144000.0, minutes)) * 1200.0);
   }

   /** Natural spawn chance 0..1 from percent. */
   public static double chance(double percent) {
      if (!Double.isFinite(percent)) {
         return 0.01;
      }
      return Math.max(0.0, Math.min(100.0, percent)) / 100.0;
   }
}
