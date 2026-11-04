package dev.baranhan.viltrumitecore.city.citizen;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * Numbers and pure decisions of people falling from broken windows during heavy destruction (unit tested; the
 * server side is {@link WindowPeril}). People upstairs ({@link CitizenEntity#occupant}) in a building shaken by
 * destruction may go to an opening in the facade (a broken window, a blown-out wall) to get air and call for help;
 * there the floor shakes again, a blast reaches them, someone panicking pushes past or they slip on the debris, and
 * they go over the edge: a real fall with vanilla gravity, the scream, maybe a grab at the sill. The chances grow
 * with the destruction around the building, the people on the floor and how close a player is; a level, a building
 * and a window each have their own cooldown, so a big fight gives a few falls, never a rain of bodies.
 */
public final class WindowRules {
   /** The ground beyond the opening is at least this far below the floor (blocks): a fall, not a hop. */
   public static final int MIN_DROP = 9;
   /** Drops are measured down to this depth at most. */
   public static final int MAX_DROP = 96;
   /** Openings are looked for this far (blocks, horizontally) from the person, on its own floor. */
   public static final int SEARCH_R = 9;
   /** A floor has its ceiling within this many blocks above the feet (else it is a roof terrace). */
   public static final int CEILING_UP = 6;
   /** Sky light (0..15) of open air outside a window, also under a cornice or a balcony. */
   public static final int OUTSIDE_SKY_LIGHT = 13;
   /** How often (ticks) the people upstairs are considered. */
   public static final int SCAN_PERIOD = 20;
   /** Destruction below this (0..1) leaves people upstairs where they are. */
   public static final float MIN_DESTRUCTION = 0.12F;
   /** Destruction fades with this half-life (ticks). */
   public static final int HALF_LIFE = 400;
   /** Destruction cells are this wide (blocks); a cell feels its 8 neighbours at half strength. */
   public static final int CELL = 32;
   /** Blocks changed nearby for a destruction of about 0.63. */
   public static final float BLOCKS_SCALE = 300.0F;
   /** At most one window event starts per level in this many ticks, and {@link #PER_MINUTE} per minute. */
   public static final int LEVEL_GAP = 50;
   public static final int PER_MINUTE = 5;
   /** One building (16x16 column) gets a new window event at most this often (ticks). */
   public static final int BUILDING_GAP = 500;
   /** One opening is used at most this often (ticks). */
   public static final int WINDOW_GAP = 2400;
   /** People this close to a player always count; beyond {@link #FAR} nobody is considered (nobody would see it). */
   public static final double NEAR = 48.0;
   public static final double FAR = 128.0;
   /** Walking to the opening gives up after this long (ticks). */
   public static final int APPROACH_MAX = 140;
   /** At the opening: calls for help this long (ticks) plus up to {@link #AT_WINDOW_SPREAD}, then steps back. */
   public static final int AT_WINDOW_MIN = 60;
   public static final int AT_WINDOW_SPREAD = 160;
   /** Balance lost at the edge (arms windmilling) this long (ticks) plus up to 14 before going over. */
   public static final int EDGE_MIN = 10;
   /** A fresh shock wave this strong (0..1) at the window throws the person out. */
   public static final float BLAST_THROW = 0.12F;
   /** People at windows at once per level. */
   public static final int MAX_AT_WINDOWS = 4;

   /** Why a person at a window went over the edge (logged, and the push it gets). */
   public enum Cause {
      /** A shock wave reached the window: thrown out, fast. */
      BLAST(0.55, 0.22),
      /** The floor shook with destruction nearby: lost the footing at the edge. */
      TREMOR(0.24, 0.08),
      /** Another panicking person pushed past at the opening. */
      SHOVE(0.32, 0.1),
      /** Glass and rubble on the sill: slipped. */
      SLIP(0.2, 0.04);

      /** Horizontal speed out of the window (blocks/tick) and the upward part of the push. */
      public final double out;
      public final double up;

      Cause(double out, double up) {
         this.out = out;
         this.up = up;
      }
   }

   private WindowRules() {
   }

   /** Destruction 0..1 from a number of changed blocks (saturating). */
   public static float fromBlocks(double blocks) {
      return blocks <= 0.0 ? 0.0F : (float)(1.0 - Math.exp(-blocks / BLOCKS_SCALE));
   }

   /** Weight of the distance to the nearest player: 1 close, fading to 0 at {@link #FAR}. */
   public static float distanceWeight(double d) {
      if (d <= NEAR) {
         return 1.0F;
      }
      return d >= FAR ? 0.0F : (float)(1.0 - (d - NEAR) / (FAR - NEAR));
   }

   /**
    * Chance (per scan) that a person upstairs goes to an opening: grows with the destruction around and with the
    * people on the same floor (crowding, panic), a braver person keeps calmer; zero below {@link #MIN_DESTRUCTION}.
    */
   public static float approachChance(float destruction, int others, double playerDistance, float bravery) {
      if (destruction < MIN_DESTRUCTION) {
         return 0.0F;
      }
      float crowd = 1.0F + 0.2F * Math.min(5, Math.max(0, others));
      float calm = 1.15F - 0.5F * clamp01(bravery);
      return Math.min(0.3F, 0.12F * destruction * crowd * calm * distanceWeight(playerDistance));
   }

   /**
    * Chance (per second at the opening) to go over the edge and why, or null: a shove needs someone else on the
    * floor, a tremor needs destruction still going on, a slip is rare; braver people hold on better.
    */
   public static Cause edge(float destruction, int others, float bravery, float roll) {
      float steady = 1.0F - 0.45F * clamp01(bravery);
      float tremor = 0.11F * clamp01(destruction) * steady;
      float shove = others > 0 ? 0.035F * Math.min(4, others) * steady : 0.0F;
      float slip = 0.025F * steady;
      if (roll < tremor) {
         return Cause.TREMOR;
      }
      if (roll < tremor + shove) {
         return Cause.SHOVE;
      }
      return roll < tremor + shove + slip ? Cause.SLIP : null;
   }

   /** Ticks at the edge before going over, from a 0..1 roll. */
   public static int edgeTicks(float roll) {
      return EDGE_MIN + Math.round(clamp01(roll) * 14.0F);
   }

   /** Ticks at the window before stepping back, from a 0..1 roll. */
   public static int windowTicks(float roll) {
      return AT_WINDOW_MIN + Math.round(clamp01(roll) * AT_WINDOW_SPREAD);
   }

   static float clamp01(float v) {
      return v < 0.0F ? 0.0F : v > 1.0F ? 1.0F : v;
   }

   /** Key of a 16x16 column: one building. */
   public static long building(int x, int z) {
      return ((long)(x >> 4) << 32) ^ ((z >> 4) & 0xFFFFFFFFL);
   }

   /** Key of a block. */
   public static long window(int x, int y, int z) {
      return ((long)x & 0x3FFFFFFL) << 38 | ((long)z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
   }

   /** Destruction around the city, by cell, fading with {@link #HALF_LIFE}. */
   public static final class Field {
      private final Long2ObjectOpenHashMap<double[]> cells = new Long2ObjectOpenHashMap<>();
      private long lastAdd = Long.MIN_VALUE;

      private static long key(int cx, int cz) {
         return ((long)cx << 32) ^ (cz & 0xFFFFFFFFL);
      }

      private static double decay(double v, long from, long now) {
         return now <= from ? v : v * Math.pow(0.5, (now - from) / (double)HALF_LIFE);
      }

      /** {@code blocks} changed (or the equivalent of a blast) around a point now. */
      public void add(double x, double z, double blocks, long now) {
         if (!(blocks > 0.0) || Double.isNaN(x) || Double.isNaN(z)) {
            return;
         }
         long k = key(Math.floorDiv((int)Math.floor(x), CELL), Math.floorDiv((int)Math.floor(z), CELL));
         double[] c = this.cells.get(k);
         if (c == null) {
            if (this.cells.size() >= 512) {
               this.prune(now);
            }
            c = new double[]{0.0, now};
            this.cells.put(k, c);
         }
         c[0] = decay(c[0], (long)c[1], now) + blocks;
         c[1] = now;
         this.lastAdd = Math.max(this.lastAdd, now);
      }

      /** Destruction 0..1 felt at a point (its cell, the neighbours at half). */
      public float at(double x, double z, long now) {
         if (this.cells.isEmpty()) {
            return 0.0F;
         }
         int cx = Math.floorDiv((int)Math.floor(x), CELL), cz = Math.floorDiv((int)Math.floor(z), CELL);
         double sum = 0.0;
         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               double[] c = this.cells.get(key(cx + dx, cz + dz));
               if (c != null) {
                  sum += decay(c[0], (long)c[1], now) * (dx == 0 && dz == 0 ? 1.0 : 0.5);
               }
            }
         }
         return fromBlocks(sum);
      }

      /** Nothing worth a look anywhere (the scan sleeps). */
      public boolean quiet(long now) {
         return this.cells.isEmpty() || this.lastAdd == Long.MIN_VALUE || now - this.lastAdd > HALF_LIFE * 6L;
      }

      public void prune(long now) {
         this.cells.long2ObjectEntrySet().removeIf(e -> decay(e.getValue()[0], (long)e.getValue()[1], now) < 4.0);
      }

      public int size() {
         return this.cells.size();
      }
   }

   /** The level, building and window cooldowns of window events. */
   public static final class Gate {
      private final LongArrayList recent = new LongArrayList();
      private final Long2LongOpenHashMap buildings = new Long2LongOpenHashMap();
      private final Long2LongOpenHashMap windows = new Long2LongOpenHashMap();

      /** True (and the event counted) when one may start now at this building and window. */
      public boolean take(long now, long building, long window) {
         if (!this.recent.isEmpty() && now - this.recent.getLong(this.recent.size() - 1) < LEVEL_GAP) {
            return false;
         }
         int inMinute = 0;
         for (int i = 0; i < this.recent.size(); i++) {
            if (now - this.recent.getLong(i) < 1200) {
               inMinute++;
            }
         }
         if (inMinute >= PER_MINUTE) {
            return false;
         }
         if (this.buildings.containsKey(building) && now - this.buildings.get(building) < BUILDING_GAP) {
            return false;
         }
         if (this.windows.containsKey(window) && now - this.windows.get(window) < WINDOW_GAP) {
            return false;
         }
         this.recent.add(now);
         while (this.recent.size() > PER_MINUTE * 2) {
            this.recent.removeLong(0);
         }
         if (this.buildings.size() > 256) {
            this.buildings.long2LongEntrySet().removeIf(e -> now - e.getLongValue() >= BUILDING_GAP);
         }
         if (this.windows.size() > 512) {
            this.windows.long2LongEntrySet().removeIf(e -> now - e.getLongValue() >= WINDOW_GAP);
         }
         this.buildings.put(building, now);
         this.windows.put(window, now);
         return true;
      }
   }
}
