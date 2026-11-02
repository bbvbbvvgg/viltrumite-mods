package dev.baranhan.viltrumitecore.scp.scp096;

/**
 * Pure rules of the natural spawn (covered by Scp096SpawnRulesTest): the ring at the edge of what a player
 * simulates, and the "not in front of a camera" test.
 */
public final class Scp096SpawnRules {
   public static final double RING_MIN = 0.72;
   public static final double RING_MAX = 0.92;
   /** Never closer than this to any player. */
   public static final double MIN_PLAYER_DISTANCE = 48.0;
   /**
    * A site inside this horizontal half angle of a player's look counts as "in front of the camera" (the widest
    * vanilla view, FOV 110 on a 21:9 screen, is about 73 degrees to each side).
    */
   public static final double VIEW_HALF = Math.toRadians(80.0);

   private Scp096SpawnRules() {
   }

   /** Radius R in blocks: the smaller of the view and simulation distances (chunks), at least 4 chunks. */
   public static double radius(int viewChunks, int simulationChunks) {
      int c = Math.max(4, Math.min(Math.max(1, viewChunks), Math.max(1, simulationChunks)));
      return c * 16.0;
   }

   public static double ringMin(double radius) {
      return Math.max(MIN_PLAYER_DISTANCE, radius * RING_MIN);
   }

   public static double ringMax(double radius) {
      return Math.max(ringMin(radius) + 4.0, radius * RING_MAX);
   }

   /** True when a site at horizontal offset (dx, dz) from the eyes is behind or beside a look of (lx, lz). */
   public static boolean outsideView(double lx, double lz, double dx, double dz) {
      return Scp096Sight.horizontalAngle(lx, lz, dx, dz) > VIEW_HALF;
   }

   /** The roll: true when a uniform 0..1 value falls under the chance. */
   public static boolean roll(double uniform, double chance) {
      return chance > 0.0 && uniform < chance;
   }

   /** Yaw (Minecraft degrees) that turns the back of the creature to a point (dx, dz) away from it. */
   public static float yawAwayFrom(double dx, double dz) {
      // facing +direction d has yaw atan2(-dx, dz); away = the opposite direction
      return (float)Math.toDegrees(Math.atan2(dx, -dz));
   }
}
