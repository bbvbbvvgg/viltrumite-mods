package dev.baranhan.viltrumitecore.city.power;

import dev.baranhan.viltrumitecore.city.power.api.AlarmStage;

/**
 * Pure rules of the city power grid (Living City 3.0): geometry of cells and substations, how far an impact
 * reaches into the grid and what it does to each cell, the flicker patterns, emergency fixtures, the cascade
 * between substations, restoration timing, alarm stage resolution and the siren distance curves. No Minecraft
 * state: shared by the server grid, the client audio and the unit tests.
 */
public final class PowerRules {
   /** Side of a grid cell (a street block of the city) in blocks: 3 x 3 chunks. */
   public static final int CELL = 48;
   /** Side of a substation's service area in cells (3 x 3 cells = 144 x 144 blocks). */
   public static final int STATION = 3;
   /** Fixture block writes per server tick, every cell together. */
   public static final int WRITES_PER_TICK = 48;
   /** Fixture state reads per tick while reconciling (cheap, still bounded). */
   public static final int READS_PER_TICK = 768;
   /** Chunks scanned for light fixtures per tick (loaded chunks only, palette pre-check). */
   public static final int SCANS_PER_TICK = 2;
   /** Fixtures remembered per chunk (the rest stays lit: a cap, not a scan of the whole city). */
   public static final int MAX_FIXTURES_PER_CHUNK = 160;
   /** Pending timeline actions (failures travelling with the shock wave, staggered trips and restorations). */
   public static final int MAX_TIMELINE = 4096;
   /** Timeline actions run per tick; the rest waits for the next tick. */
   public static final int ACTIONS_PER_TICK = 64;
   /** Cells one incident may touch (a square of 11 x 11 cells around the impact). */
   public static final int MAX_CELL_REACH = 5;
   /** Substations one incident may trip through its cascade. */
   public static final int MAX_TRIPS_PER_INCIDENT = 6;
   public static final int MAX_CASCADE_DEPTH = 3;
   /** Blocks per tick the wave of failures travels outwards from an impact (a visible rolling blackout). */
   public static final double WAVE_SPEED = 3.5;
   /** One emergency fixture in this many keeps burning through a blackout. */
   public static final int EMERGENCY_ONE_IN = 9;
   /** Ticks of the flicker before a cell goes dark or comes back. */
   public static final int FLICKER_TICKS = 34;
   /** Ticks of a voltage dip (lights stutter, then hold). */
   public static final int SURGE_TICKS = 26;
   /** An all-clear stage lasts this long before the alarm is off. */
   public static final int ALL_CLEAR_TICKS = 600;
   /** Subtitles are rate limited: one blackout line per district per this many ticks. */
   public static final int DISTRICT_LINE_COOLDOWN = 1200;

   private PowerRules() {
   }

   /** Cell or substation effect of one hit, in order of severity. */
   public enum Effect {
      NONE,
      SURGE,
      DARK,
      DAMAGED
   }

   // ------------------------------------------------------------------ geometry

   public static int cellIndex(int block, int origin) {
      return Math.floorDiv(block - origin, CELL);
   }

   public static long key(int ix, int iz) {
      return (long)ix << 32 | iz & 0xFFFFFFFFL;
   }

   public static int keyX(long key) {
      return (int)(key >> 32);
   }

   public static int keyZ(long key) {
      return (int)key;
   }

   public static int stationIndex(int cellIndex) {
      return Math.floorDiv(cellIndex, STATION);
   }

   /** Distance from a point to the nearest point of a cell (0 inside). */
   public static double distanceToCell(double x, double z, int ix, int iz, int originX, int originZ) {
      double x0 = originX + (double)ix * CELL, z0 = originZ + (double)iz * CELL;
      double dx = Math.max(0.0, Math.max(x0 - x, x - (x0 + CELL)));
      double dz = Math.max(0.0, Math.max(z0 - z, z - (z0 + CELL)));
      return Math.sqrt(dx * dx + dz * dz);
   }

   // ------------------------------------------------------------------ damage

   /** How far an impact reaches into the grid: the cables run under the streets, so well past the crater. */
   public static double reach(double radius, double severity) {
      double r = Math.max(0.0, radius) * 2.2 + 24.0 * Math.max(0.2, Math.min(1.0, severity));
      return Math.min(r, (MAX_CELL_REACH + 0.5) * CELL);
   }

   /** Share of the severity that arrives at {@code distance} (1 at the impact, 0 at the edge of the reach). */
   public static double falloff(double distance, double reach) {
      if (reach <= 0.0 || distance >= reach) {
         return 0.0;
      }
      double f = 1.0 - Math.max(0.0, distance) / reach;
      return Math.pow(f, 1.25);
   }

   public static Effect effect(double hit) {
      if (hit >= 0.62) {
         return Effect.DAMAGED;
      } else if (hit >= 0.3) {
         return Effect.DARK;
      } else if (hit >= 0.07) {
         return Effect.SURGE;
      }
      return Effect.NONE;
   }

   /** Ticks until the failure reaches a cell {@code distance} blocks from the impact (plus a little jitter). */
   public static int waveDelay(double distance, long seed) {
      return (int)(Math.max(0.0, distance) / WAVE_SPEED) + (int)Math.floorMod(mix(seed), 4L);
   }

   /** Severity of a burst of destroyed blocks in one chunk and tick (Chitauri smashes, hero fights, collapses). */
   public static double burstSeverity(int blocks) {
      if (blocks < 6) {
         return 0.0;
      }
      return Math.min(0.75, 0.12 + Math.log(blocks / 6.0 + 1.0) * 0.17);
   }

   public static double burstRadius(int blocks) {
      return Math.min(16.0, 3.0 + Math.sqrt(Math.max(0, blocks)) * 0.9);
   }

   /** Severity of an explosion that destroyed {@code blocks} blocks (an explosion of unknown origin). */
   public static double explosionSeverity(int blocks) {
      return Math.min(0.8, 0.18 + blocks / 320.0);
   }

   public static double explosionRadius(int blocks) {
      return Math.min(14.0, 3.0 + Math.sqrt(Math.max(0, blocks)) * 0.55);
   }

   /** How much one hit on a cell wears down its substation (0..1 health). */
   public static double stationWear(double hit) {
      return Math.max(0.0, hit) * 0.34;
   }

   /** A substation trips when it is worn out or most of its area is already dark. */
   public static boolean stationTrips(double health, int darkCells, int cells) {
      return health <= 0.42 || cells > 0 && darkCells * 9 >= cells * 6;
   }

   /** The load thrown onto a neighbour {@code depth} steps away from the first tripped substation. */
   public static double cascadeLoad(double severity, int depth) {
      return Math.max(0.0, Math.min(1.0, severity)) * Math.pow(0.66, Math.max(0, depth));
   }

   /**
    * True when the neighbour cannot carry the load (deterministic in the seed). A fresh grid usually holds after
    * one strong hit (about one neighbour in three gives way next to a meteor strike); worn substations, whose
    * weakness the caller adds to the load, are what turns a hit into a chain. Tuned in game: the first curve
    * (load x 1.35) tripped the per-incident cap of six substations on every meteor-sized hit.
    */
   public static boolean cascades(double load, int depth, long seed) {
      if (depth > MAX_CASCADE_DEPTH || load < 0.3) {
         return false;
      }
      return unit(seed) < Math.min(0.85, (load - 0.3) * 1.2);
   }

   /** Substations tripped by one incident from which the city calls the situation critical (a real chain). */
   public static final int CRITICAL_TRIPS = 3;

   /** Ticks between a substation tripping and the next one along the cascade. */
   public static int cascadeDelay(long seed) {
      return 45 + (int)Math.floorMod(mix(seed), 50L);
   }

   /** Ticks until a cell gets power back after {@code effect}. */
   public static int restoreDelay(Effect effect, double severity, long seed) {
      int jitter = (int)Math.floorMod(mix(seed), 240L);
      return switch (effect) {
         case DAMAGED -> 2400 + (int)(Math.min(1.0, severity) * 1800) + jitter;
         case DARK -> 700 + (int)(Math.min(1.0, severity) * 900) + jitter;
         default -> 0;
      };
   }

   /** Ticks a tripped substation needs before the crews close the breakers again. */
   public static int stationRestoreDelay(int depth, long seed) {
      return 1500 + depth * 300 + (int)Math.floorMod(mix(seed), 400L);
   }

   /** Ticks between two cells of one substation coming back (restoration runs substation by substation). */
   public static final int RESTORE_STAGGER = 16;

   // ------------------------------------------------------------------ lights

   /** One fixture in {@link #EMERGENCY_ONE_IN} is on the emergency circuit and keeps burning. */
   public static boolean emergency(long blockPos) {
      return Math.floorMod(mix(blockPos ^ 0x5EED_CAFEL), (long)EMERGENCY_ONE_IN) == 0L;
   }

   /**
    * Lights of a cell during a flicker ({@code age} ticks since it started): irregular off and on runs like a
    * failing supply, ending dark when {@code endsLit} is false and lit when it is true.
    */
   public static boolean flickerLit(int age, long seed, boolean endsLit) {
      if (age >= FLICKER_TICKS) {
         return endsLit;
      }
      if (age < 0) {
         return !endsLit;
      }
      // runs of 2..6 ticks, alternating, starting from the state before the flicker
      int t = 0;
      boolean lit = !endsLit;
      long s = seed;
      while (true) {
         s = mix(s + 0x9E3779B97F4A7C15L);
         int run = 2 + (int)Math.floorMod(s, 5L);
         if (age < t + run) {
            return lit;
         }
         t += run;
         lit = !lit;
         if (t >= FLICKER_TICKS) {
            return endsLit;
         }
      }
   }

   /** Lights during a voltage dip: two or three short drop-outs, then steady again. */
   public static boolean surgeLit(int age, long seed) {
      if (age < 0 || age >= SURGE_TICKS) {
         return true;
      }
      int a = 3 + (int)Math.floorMod(mix(seed), 4L);
      int b = a + 6 + (int)Math.floorMod(mix(seed + 1), 5L);
      return !(age >= a && age < a + 2 || age >= b && age < b + 3 || age >= b + 8 && age < b + 9);
   }

   // ------------------------------------------------------------------ alarm

   /** The stage the city shows for the strongest lease and the previous stage (sirens never step down silently). */
   public static AlarmStage resolve(AlarmStage strongestLease, AlarmStage previous, int ticksInStage) {
      if (strongestLease != AlarmStage.NONE) {
         return strongestLease;
      }
      if (previous == AlarmStage.ACTIVE || previous == AlarmStage.CRITICAL) {
         return AlarmStage.ALL_CLEAR;
      }
      if (previous == AlarmStage.ALL_CLEAR && ticksInStage < ALL_CLEAR_TICKS) {
         return AlarmStage.ALL_CLEAR;
      }
      return AlarmStage.NONE;
   }

   // ------------------------------------------------------------------ sirens (client audio)

   /** Overall loudness of one siren at {@code distance} blocks (a siren on a mast carries far across a city). */
   public static float sirenLevel(double distance) {
      double d = Math.max(0.0, distance);
      if (d >= 900.0) {
         return 0.0F;
      }
      double level = 1.0 / (1.0 + Math.pow(d / 110.0, 1.5));
      double edge = Math.min(1.0, (900.0 - d) / 200.0);
      return (float)(level * edge);
   }

   /** Share of the near (full band) recording; the rest plays the far, filtered and echoing variant. */
   public static float sirenNear(double distance) {
      double t = (distance - 70.0) / 150.0;
      return (float)Math.max(0.0, Math.min(1.0, 1.0 - t));
   }

   /** Speed of sound in blocks per tick (343 m/s). */
   public static final double SOUND_SPEED = 343.0 / 20.0;

   /** Delay of a one-shot heard {@code distance} blocks away, capped at three seconds. */
   public static int soundDelay(double distance) {
      return (int)Math.min(60.0, Math.max(0.0, distance) / SOUND_SPEED);
   }

   // ------------------------------------------------------------------ hashing

   public static long mix(long z) {
      z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
      z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
      return z ^ z >>> 31;
   }

   /** A deterministic number in [0, 1). */
   public static double unit(long seed) {
      return (mix(seed) >>> 11) * 0x1.0p-53;
   }
}
