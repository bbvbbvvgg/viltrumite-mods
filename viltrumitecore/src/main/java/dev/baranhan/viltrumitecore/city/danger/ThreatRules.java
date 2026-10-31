package dev.baranhan.viltrumitecore.city.danger;

/**
 * Pure sizing of the danger a moving source spreads around itself (Living City 2.0).
 * The scan ({@code city.ThreatScan}) only classifies what an entity <i>is</i> (hostile
 * creature, armed fighter, fast projectile, lightning, a hero flying past) and measures
 * it; these rules turn size, speed and height above the street into a danger radius and a
 * notice radius. Nothing here names a hero, an event or a mod entity: a new creature or
 * power is covered by what it is, not by who made it.
 */
public final class ThreatRules {
   /** Danger radius {@code [0]} and notice radius {@code [1]}; radius 0 = curiosity only. */
   public static final int RADIUS = 0;
   public static final int NOTICE = 1;
   /** A body at least this big (blocks) is a "giant" (leviathan, titan): seen from far away, felt on the street. */
   public static final float GIANT = 4.0F;
   /** Projectiles slower than this (blocks/tick) are lying or drifting, not flying at someone. */
   public static final double PROJECTILE_MIN_SPEED = 0.45;
   /** A player moving faster than this (blocks/tick) close above the street is a hero flying past. */
   public static final double FLYBY_MIN_SPEED = 0.9;

   private ThreatRules() {
   }

   private static float clamp(float v, float lo, float hi) {
      return v < lo ? lo : Math.min(v, hi);
   }

   /** A hostile creature of this bounding box at this height above the ground under it. */
   public static float[] hostile(float width, float height, double altitude) {
      float size = Math.max(width, height * 0.5F);
      if (size >= GIANT) {
         // giants: the street under them is dangerous while they are low, everybody sees them
         float radius = altitude < 70.0 ? clamp(14.0F + size * 1.5F, 18.0F, 36.0F) : 0.0F;
         return new float[]{radius, DangerMap.MAX_NOTICE};
      }

      float radius = clamp(9.0F + size * 3.0F, 9.0F, 24.0F);
      float notice = clamp(radius + 22.0F + size * 4.0F, 30.0F, 64.0F);
      // a small flyer high above shoots from there: its bolts are the danger, the flyer is a sight
      return new float[]{altitude < 16.0 ? radius : 0.0F, notice};
   }

   /** A non-hostile mob that is fighting right now (a suit of the legion, a guard, a summon). */
   public static float[] fighter(float width, float height) {
      float size = Math.max(width, height * 0.5F);
      return new float[]{clamp(10.0F + size * 2.0F, 10.0F, 20.0F), clamp(30.0F + size * 4.0F, 30.0F, 56.0F)};
   }

   /** A flying projectile (bolt, arrow, missile, thrown hammer, meteor), null when too slow. */
   public static float[] projectile(double speed, float width) {
      if (!(speed >= PROJECTILE_MIN_SPEED)) {
         return null;
      }

      float radius = clamp(5.0F + width * 5.0F, 5.0F, 32.0F);
      return new float[]{radius, clamp(radius + 14.0F + (float)speed * 2.0F, 20.0F, 80.0F)};
   }

   /** How many ticks ahead of a projectile the danger sphere sits (where it will hit). */
   public static double projectileLead(double speed) {
      return speed > 2.5 ? 3.0 : 5.0;
   }

   /** Lightning strike: a short, loud danger and a shock wave. */
   public static float[] lightning() {
      return new float[]{14.0F, 48.0F};
   }

   /**
    * A player passing fast over the street: only a curiosity (people look up), never a
    * reason to flee by itself; null when slow or high above. Very fast and very low = a
    * blast of wind: a small real danger sphere.
    */
   public static float[] flyby(double speed, double altitude) {
      if (!(speed >= FLYBY_MIN_SPEED) || altitude > 28.0) {
         return null;
      }

      float notice = clamp(12.0F + (float)speed * 6.0F, 14.0F, 40.0F);
      float radius = speed > 2.5 && altitude < 6.0 ? 5.0F : 0.0F;
      return new float[]{radius, notice};
   }

   /** Knock-down radius of an explosion that changed {@code blocks} blocks (0 = none known). */
   public static float explosionBlast(int blocks) {
      return clamp(7.0F + (float)Math.sqrt(Math.max(0, blocks)) * 0.8F, 7.0F, 22.0F);
   }

   /** Knock-down radius of a combat noise of this radius (slams, shock rings): only big ones. */
   public static float noiseBlast(float radius) {
      return radius >= 24.0F ? Math.min(20.0F, radius * 0.45F) : 0.0F;
   }

   /** Knock-down radius of one hit of this damage on a living thing: only heavy blows shake the street. */
   public static float hitBlast(float amount) {
      return amount >= 20.0F ? clamp(3.0F + amount / 10.0F, 4.0F, 9.0F) : 0.0F;
   }
}
