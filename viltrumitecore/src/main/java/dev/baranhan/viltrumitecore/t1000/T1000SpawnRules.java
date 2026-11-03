package dev.baranhan.viltrumitecore.t1000;

/**
 * Pure rules of the rare scheduler (covered by T1000SpawnRulesTest): when a natural appearance may roll, when a
 * dormant instance may come back, where a site may be.
 */
public final class T1000SpawnRules {
   /** Natural sites and returns: a pedestrian cell in this ring around the chosen player. */
   public static final double RING_MIN = 36.0;
   public static final double RING_MAX = 64.0;
   /** Never closer than this to any player. */
   public static final double MIN_PLAYER_DISTANCE = 30.0;
   /** It needs a crowd to hide in: citizens within {@link #CROWD_RADIUS} of the site. */
   public static final int MIN_CROWD = 2;
   public static final double CROWD_RADIUS = 18.0;
   /** Horizontal half angle of "in front of a camera" (FOV 110 on 21:9 is about 73 degrees). */
   public static final double VIEW_HALF_DEG = 80.0;

   /** Why a natural attempt did (not) spawn. */
   public enum Result {
      SPAWNED, DISABLED, NO_CITY, PEACEFUL, FULL, RESPAWN_DELAY, STARTUP, ROLL_FAILED, NO_PLAYER, NO_SITE
   }

   private T1000SpawnRules() {
   }

   /** Gate before the roll: everything but the dice and the site. */
   public static Result gate(boolean enabled, boolean city, boolean peaceful, int active, int max, long now, long lastDestroyed,
      long respawnDelay, int serverTicks, boolean force) {
      if (!enabled && !force) {
         return Result.DISABLED;
      }
      if (!city) {
         return Result.NO_CITY;
      }
      if (peaceful) {
         return Result.PEACEFUL;
      }
      if (active >= max) {
         return Result.FULL;
      }
      if (force) {
         return Result.SPAWNED;
      }
      if (lastDestroyed != Long.MIN_VALUE && now - lastDestroyed < respawnDelay) {
         return Result.RESPAWN_DELAY;
      }
      if (serverTicks < T1000Rules.STARTUP_GRACE_TICKS) {
         return Result.STARTUP;
      }
      return Result.SPAWNED;
   }

   /** The roll: true when a uniform 0..1 value falls under the chance. */
   public static boolean roll(double uniform, double chance) {
      return chance > 0.0 && uniform < chance;
   }

   /** A dormant instance may return: delay over, past the startup grace. */
   public static boolean mayWake(long now, long dormantSince, int serverTicks) {
      return serverTicks >= T1000Rules.STARTUP_GRACE_TICKS && now - dormantSince >= T1000Rules.WAKE_DELAY_TICKS;
   }

   /** True when a site at offset (dx, dz) from the eyes is beside or behind a look of (lx, lz). */
   public static boolean outsideView(double lx, double lz, double dx, double dz) {
      return T1000Rules.angleDeg(lx, lz, dx, dz) > VIEW_HALF_DEG;
   }

   /** Expected minutes between appearances of a check every {@code seconds} with {@code percent} chance. */
   public static double expectedMinutes(double seconds, double percent) {
      double c = T1000Rules.chance(percent);
      return c <= 0.0 ? Double.POSITIVE_INFINITY : T1000Rules.checkIntervalTicks(seconds) / 20.0 / 60.0 / c;
   }
}
