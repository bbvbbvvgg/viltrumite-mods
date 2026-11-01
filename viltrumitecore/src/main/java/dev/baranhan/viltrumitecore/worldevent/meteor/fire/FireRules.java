package dev.baranhan.viltrumitecore.worldevent.meteor.fire;

import java.util.Random;

/**
 * Pure numbers of the meteor-rain city fire (no Minecraft statics, unit tested). A fire is a set of hot
 * spots: one solid block surface each, with heat (0..1), fuel (ticks of open burning) and a material
 * class. Spots warm toward their material's heat (hotter in a crowd of spots, so fires that meet merge
 * into hotter zones), throw new spots onto nearby surfaces (biased upward: fire climbs facades) and,
 * when hot, burning brands across streets. Out of fuel a spot smoulders, then is forgotten.
 */
public final class FireRules {
   // material classes
   public static final int INERT = 0;
   public static final int FLAMMABLE = 1;
   public static final int FRAGILE = 2;
   public static final int MINERAL = 3;
   public static final int GROUND = 4;
   public static final int METAL = 5;

   /** Hard cap of spots per level (memory and CPU bound for an endless rain). */
   public static final int MAX_SPOTS = 1600;
   /** Spots visited per server tick per level; a spot is revisited every size/VISITS ticks. */
   public static final int VISITS_PER_TICK = 48;
   /** Block writes (flames, damage, char) per server tick per level. */
   public static final int WRITES_PER_TICK = 40;
   /** Writes a meteor impact may spend at once on its first flames. */
   public static final int IMPACT_WRITES = 28;
   public static final int MAX_BRANDS = 24;
   /** Minimum ticks between two new brands of one level. */
   public static final int BRAND_GAP_TICKS = 8;
   /** Burnt positions remembered (FIFO) so a fire front moves on instead of re-burning the same wall. */
   public static final int BURNT_MEMORY = 8192;
   public static final int SMOULDER_TICKS = 700;
   /** Spots a rain meteor lights around its crater, plus horizontal rays that look for facades. */
   public static final int IMPACT_SPOTS_MIN = 7;
   public static final int IMPACT_SPOTS_MAX = 11;
   public static final double IMPACT_RING_MIN = 3.0;
   public static final double IMPACT_RING_MAX = 13.0;
   public static final int IMPACT_FACADE_RAYS = 7;
   public static final int FACADE_RAY_RANGE = 20;
   /** Brand flight: horizontal distance and the ticks in the air. */
   public static final double BRAND_MIN = 5.0;
   public static final double BRAND_MAX = 16.0;
   public static final float BRAND_HEAT = 0.62F;
   /** Zone summary for clients and the citizens' danger map. */
   public static final int ZONE_CELL = 10;
   public static final int ZONE_SYNC_TICKS = 20;
   public static final double ZONE_RANGE = 320.0;
   public static final int DANGER_TICKS = 40;

   private FireRules() {
   }

   /** Heat a spot of this material settles at when alone. */
   public static float maxHeat(int material) {
      return switch (material) {
         case FLAMMABLE -> 1.0F;
         case FRAGILE -> 0.8F;
         case MINERAL -> 0.74F;
         case METAL -> 0.6F;
         case GROUND -> 0.45F;
         default -> 0.0F;
      };
   }

   /** How readily a surface of this material catches from a neighbour spot (0..1). */
   public static float catchFactor(int material) {
      return switch (material) {
         case FLAMMABLE -> 1.0F;
         case FRAGILE -> 0.9F;
         case MINERAL -> 0.55F;
         case METAL -> 0.35F;
         case GROUND -> 0.12F;
         default -> 0.0F;
      };
   }

   /** Open-burning ticks of a fresh spot. Non-flammable surfaces burn their contents, debris and finish. */
   public static int fuelTicks(int material, Random random) {
      return switch (material) {
         case FLAMMABLE -> 900 + random.nextInt(600);
         case FRAGILE -> 300 + random.nextInt(200);
         case MINERAL -> 600 + random.nextInt(400);
         case METAL -> 400 + random.nextInt(300);
         case GROUND -> 200 + random.nextInt(200);
         default -> 0;
      };
   }

   /** Target heat with {@code neighbours} burning spots around (merging fires burn hotter), capped at 1. */
   public static float targetHeat(int material, int neighbours) {
      return Math.min(1.0F, maxHeat(material) + 0.05F * Math.min(5, Math.max(0, neighbours)));
   }

   /** One easing step of heat toward the target over {@code dt} ticks. */
   public static float heatStep(float heat, float target, int dt) {
      float k = Math.min(1.0F, 0.004F * Math.max(0, dt));
      return heat + (target - heat) * k;
   }

   /** Chance that one visit after {@code dt} ticks throws a new spot. */
   public static float spreadChance(float heat, int dt) {
      return clamp01(0.0075F * heat * Math.max(0, dt));
   }

   /** Chance that one visit throws a burning brand; only hot spots do. */
   public static float brandChance(float heat, int dt) {
      return heat < BRAND_HEAT ? 0.0F : clamp01(0.00012F * heat * Math.max(0, dt));
   }

   /** Chance that a fragile block (glass, leaves, carpets) gives way this visit. */
   public static float shatterChance(float heat, int dt) {
      return heat < 0.5F ? 0.0F : clamp01(0.01F * (heat - 0.4F) * Math.max(0, dt));
   }

   /**
    * A spread offset {dx, dy, dz} from a spot: up to 2 blocks sideways, -1..3 vertically with weights
    * 1/3/4/3/1, never (0, 0, 0). Fire climbs facades and crosses roofs faster than it creeps down.
    */
   public static int[] spreadOffset(Random random) {
      while (true) {
         int dx = random.nextInt(5) - 2;
         int dz = random.nextInt(5) - 2;
         int w = random.nextInt(12);
         int dy = w < 1 ? -1 : w < 4 ? 0 : w < 8 ? 1 : w < 11 ? 2 : 3;
         if (dx != 0 || dy != 0 || dz != 0) {
            return new int[]{dx, dy, dz};
         }
      }
   }

   /** Brand heading: the wind direction plus up to +-75 degrees. */
   public static double brandAngle(double wind, Random random) {
      return wind + (random.nextDouble() * 2.0 - 1.0) * Math.toRadians(75.0);
   }

   /** Brand distance: longer when it flies with the wind (cos of the angle to the wind). */
   public static double brandDistance(double alongWind, Random random) {
      double base = BRAND_MIN + random.nextDouble() * (BRAND_MAX - BRAND_MIN);
      return Math.min(BRAND_MAX * 1.3, base * (1.0 + 0.3 * Math.max(0.0, alongWind)));
   }

   /** Flight ticks of a brand over a distance: a slow tumbling arc. */
   public static int brandFlightTicks(double distance) {
      return (int)Math.round(12.0 + distance * 1.6);
   }

   /** Zone intensity 0..1 from the spot count and mean heat of a zone cell. */
   public static float zoneIntensity(int spots, float meanHeat) {
      return clamp01((float)Math.sqrt(spots / 40.0) * (0.4F + 0.6F * meanHeat));
   }

   /** Danger radius for the citizens around a zone. */
   public static float dangerRadius(int spots) {
      return Math.max(4.0F, Math.min(20.0F, 3.0F + (float)Math.sqrt(spots) * 1.5F));
   }

   private static float clamp01(float v) {
      return v < 0.0F ? 0.0F : Math.min(1.0F, v);
   }
}
