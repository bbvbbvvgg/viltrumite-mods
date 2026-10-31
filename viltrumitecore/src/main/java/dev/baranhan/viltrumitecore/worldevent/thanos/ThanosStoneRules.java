package dev.baranhan.viltrumitecore.worldevent.thanos;

/**
 * Geometry and timing of the six stones (pure; the executors in {@code stone/} apply them). Every
 * lingering effect has a hard end, and nothing here touches blocks except the Power stone's
 * destruction (through HeroDestruction, which the city journals like any other damage).
 */
public final class ThanosStoneRules {
   private ThanosStoneRules() {
   }

   /** Purple: charged punch, ground shockwave, destruction, knockback, glowing fissures. */
   public static final class Power {
      public static final double PUNCH_REACH = 9.0;
      /** Cone of the punch: cos of the half angle (about 57 degrees). */
      public static final double PUNCH_CONE = 0.55;
      public static final double PUNCH_KNOCKBACK = 2.4;
      public static final float PUNCH_CRATER = 2.6F;
      public static final double WAVE_SPEED = 0.6;
      public static final double WAVE_MAX = 18.0;
      public static final double WAVE_BAND = 1.8;
      /** The ring runs along the ground: jump or fly above this height to let it pass. */
      public static final double WAVE_HEIGHT = 2.2;
      public static final double WAVE_KNOCKBACK = 1.3;
      public static final int FISSURE_LIFE = 120;

      private Power() {
      }

      public static double waveRadius(int activeTick) {
         return Math.min(WAVE_MAX, Math.max(0, activeTick) * WAVE_SPEED);
      }

      /** Does the ring at {@code activeTick} hit a body {@code distance} away, {@code height} above the ground? */
      public static boolean waveHits(double distance, double height, int activeTick) {
         double r = waveRadius(activeTick);
         return r > 0.5 && Math.abs(distance - r) <= WAVE_BAND / 2 && height <= WAVE_HEIGHT;
      }

      /** In the cone in front of the fist (dot of the facing and the direction to the target). */
      public static boolean inPunchCone(double distance, double dot) {
         return distance <= PUNCH_REACH && (distance < 2.0 || dot >= PUNCH_CONE);
      }

      /** Knockback strength, weaker at the edge of the reach. */
      public static double punchKnockback(double distance) {
         return PUNCH_KNOCKBACK * (1.0 - 0.5 * Math.min(1.0, distance / PUNCH_REACH));
      }
   }

   /** Blue: portals, teleport behind a target, projectile redirect, spatial grab. */
   public static final class Space {
      public static final double STEP_BEHIND = 3.2;
      public static final double PULL_SPEED = 1.3;
      public static final double PULL_STOP = 3.2;
      public static final int PULL_TICKS = 16;
      public static final double WARD_RADIUS = 6.5;
      /** Redirected projectiles keep this share of their speed. */
      public static final double REDIRECT_SPEED = 0.85;

      private Space() {
      }

      /**
       * Exit point behind a target that faces ({@code lookX}, {@code lookZ}); a target without a
       * horizontal facing gets the point opposite to {@code fromX}/{@code fromZ}.
       */
      public static double[] behind(double tx, double tz, double lookX, double lookZ, double fromX, double fromZ) {
         double len = Math.sqrt(lookX * lookX + lookZ * lookZ);
         double dx;
         double dz;
         if (len > 1.0E-3) {
            dx = -lookX / len;
            dz = -lookZ / len;
         } else {
            double ax = tx - fromX;
            double az = tz - fromZ;
            double l = Math.max(1.0E-3, Math.sqrt(ax * ax + az * az));
            dx = ax / l;
            dz = az / l;
         }

         return new double[]{tx + dx * STEP_BEHIND, tz + dz * STEP_BEHIND};
      }

      /** Velocity of a redirected projectile: back toward where it came from. */
      public static double[] redirect(double vx, double vy, double vz) {
         return new double[]{-vx * REDIRECT_SPEED, -vy * REDIRECT_SPEED + 0.05, -vz * REDIRECT_SPEED};
      }

      /** Pull speed this tick for a target {@code distance} away (eases in, stops at the hand). */
      public static double pullSpeed(double distance, int tick) {
         if (distance <= PULL_STOP) {
            return 0.0;
         }

         double ease = Math.min(1.0, (tick + 1) / 4.0);
         return Math.min(PULL_SPEED * ease, distance - PULL_STOP);
      }
   }

   /**
    * Red: unstable-matter zones and false images. Purely entity-side and visual: no block is
    * written, so nothing needs reverting after a crash; a zone simply ends (it shrinks back to
    * nothing over its last ticks).
    */
   public static final class Reality {
      public static final double ZONE_RADIUS = 3.2;
      public static final int ZONE_LIFE = 160;
      public static final int ZONE_GROW = 14;
      public static final int ZONE_FADE = 24;
      public static final int ZONE_PULSE = 10;
      public static final double ZONE_SLOW = 0.45;
      public static final int MIRAGES = 3;
      public static final int MIRAGE_LIFE = 120;
      public static final double MIRAGE_POP = 1.8;
      public static final double MIRAGE_RING = 6.0;

      private Reality() {
      }

      /** Zones for a group: 3 solo, one more per two extra heroes, at most 6. */
      public static int zones(int heroes) {
         return Math.min(6, 3 + Math.max(0, heroes - 1) / 2);
      }

      /** Visual scale and strength 0..1 of a zone of age {@code age}: grows, holds, shrinks back. */
      public static float zoneScale(int age) {
         if (age < 0 || age >= ZONE_LIFE) {
            return 0.0F;
         }

         if (age < ZONE_GROW) {
            return (age + 1) / (float)ZONE_GROW;
         }

         int left = ZONE_LIFE - age;
         return left < ZONE_FADE ? left / (float)ZONE_FADE : 1.0F;
      }

      public static boolean inZone(double dx, double dy, double dz, float scale) {
         double r = ZONE_RADIUS * scale;
         return scale > 0.05F && dy > -1.5 && dy < 3.0 && dx * dx + dz * dz <= r * r;
      }

      /** Index of the real Thanos among the mirage positions (stable for the seed). */
      public static int realIndex(long seed, int count) {
         return (int)Math.floorMod(seed * 0x9E3779B97F4A7C15L >>> 33, (long)Math.max(1, count));
      }
   }

   /** Green: a dome of nearly stopped time, slowed projectiles, his own rewind. */
   public static final class Time {
      public static final double DOME_RADIUS = 7.0;
      public static final int DOME_LIFE = 100;
      /** Movement multiplier of a hero deep inside the dome. */
      public static final double HERO_SLOW = 0.15;
      /** Projectiles inside keep this share of their speed per tick of entry. */
      public static final double PROJECTILE_FACTOR = 0.12;

      private Time() {
      }

      public static boolean inside(double distanceSq) {
         return distanceSq <= DOME_RADIUS * DOME_RADIUS;
      }

      /** Speed multiplier for a hero at dome age {@code age}: closes in over 6 ticks, opens over the last 10. */
      public static double slowFactor(int age) {
         if (age < 0 || age >= DOME_LIFE) {
            return 1.0;
         }

         double closing = Math.min(1.0, (age + 1) / 6.0);
         double opening = Math.min(1.0, (DOME_LIFE - age) / 10.0);
         double depth = Math.min(closing, opening);
         return 1.0 - (1.0 - HERO_SLOW) * depth;
      }
   }

   /**
    * Yellow: gaze, false targets, pulses. The player's controls are never taken: a caught hero's
    * screen sways and the HUD lies for a moment. Counterplay: look away during the wind-up, jump
    * the rings.
    */
   public static final class Mind {
      public static final double GAZE_RANGE = 28.0;
      /** Looking at him within about 37 degrees when the gaze fires = caught. */
      public static final double GAZE_DOT = 0.80;
      public static final int DISORIENT_TICKS = 70;
      public static final int RINGS = 3;
      public static final int RING_GAP = 12;
      public static final double RING_SPEED = 0.55;
      public static final double RING_MAX = 22.0;
      public static final double RING_BAND = 1.3;
      public static final double RING_HEIGHT = 1.3;
      public static final int FALSE_TARGETS = 4;

      private Mind() {
      }

      public static boolean caught(double lookDot, double distance) {
         return distance <= GAZE_RANGE && lookDot >= GAZE_DOT;
      }

      public static int disorientTicks(double lookDot, double distance) {
         return caught(lookDot, distance) ? DISORIENT_TICKS : 0;
      }

      public static double ringRadius(int ring, int activeTick) {
         double r = (activeTick - ring * RING_GAP) * RING_SPEED;
         return r <= 0 ? 0 : Math.min(RING_MAX, r);
      }

      public static boolean ringHits(double distance, double height, double radius) {
         return radius > 0.5 && radius < RING_MAX && Math.abs(distance - radius) <= RING_BAND / 2 && height <= RING_HEIGHT;
      }
   }

   /** Orange: tethers that drain life, a soul nova with ghostly silhouettes. No creature is made. */
   public static final class Soul {
      public static final double TETHER_RANGE = 20.0;
      /** A tether snaps when its hero gets this far away. */
      public static final double TETHER_BREAK = 24.0;
      public static final int MAX_TETHERS = 3;
      public static final int DRAIN_EVERY = 10;
      /** Thanos heals this many points per drained hit (scaled by the tether count, capped by the rules). */
      public static final float HEAL_PER_DRAIN = 6.0F;
      public static final double NOVA_RADIUS = 10.0;

      private Soul() {
      }

      public static int tethers(int heroes) {
         return Math.max(1, Math.min(MAX_TETHERS, heroes));
      }

      public static boolean tetherHolds(double distance) {
         return distance <= TETHER_BREAK;
      }

      /** Nova damage falls from 100 % at the centre to 50 % at the edge, 0 outside. */
      public static float novaDamage(float base, double distance) {
         if (distance > NOVA_RADIUS) {
            return 0.0F;
         }

         return (float)(base * (1.0 - 0.5 * distance / NOVA_RADIUS));
      }
   }
}
