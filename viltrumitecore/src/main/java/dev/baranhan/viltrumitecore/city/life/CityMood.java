package dev.baranhan.viltrumitecore.city.life;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

/**
 * How a part of the city feels (issue #136), per cell of {@link #CELL} blocks: <b>alarm</b> (something is
 * happening right now: a fight, a blast, walls coming down; half-life {@link #ALARM_HALF_LIFE} ticks) and
 * <b>trauma</b> (what people went through; half-life {@link #TRAUMA_HALF_LIFE} ticks, so the street calms down
 * step by step). Hits add with saturation ({@code 1 - (1 - a)(1 - b)}) and fall off with the distance from the
 * place; values decay lazily when read. From the two comes the {@link Mood} the citizens talk and sound in:
 * calm, wary, panic and aftermath. Pure (no Minecraft types), unit tested.
 */
public final class CityMood {
   public static final int CELL = 32;
   public static final double ALARM_HALF_LIFE = 900.0;
   public static final double TRAUMA_HALF_LIFE = 4800.0;
   /** Cells further than this (blocks) beyond a hit's radius feel nothing of it. */
   public static final double SPREAD = 40.0;
   public static final int MAX_CELLS = 4096;

   public enum Mood {
      CALM,
      WARY,
      PANIC,
      AFTER;

      /** The dialogue mood of the catalog (panic has no chats). */
      public int catalog() {
         return switch (this) {
            case CALM, PANIC -> CityLifeCatalog.CALM;
            case WARY -> CityLifeCatalog.WARY;
            case AFTER -> CityLifeCatalog.AFTER;
         };
      }
   }

   static final class Cell {
      float alarm;
      float trauma;
      long at;

      void decay(long now) {
         long dt = now - this.at;
         if (dt > 0) {
            this.alarm *= (float)Math.pow(0.5, dt / ALARM_HALF_LIFE);
            this.trauma *= (float)Math.pow(0.5, dt / TRAUMA_HALF_LIFE);
            this.at = now;
         }
      }
   }

   private final Long2ObjectOpenHashMap<Cell> cells = new Long2ObjectOpenHashMap<>();

   static long key(int cx, int cz) {
      return (long)cx << 32 | cz & 0xFFFFFFFFL;
   }

   static int cell(double v) {
      return (int)Math.floor(v / CELL);
   }

   /** Pure: the share of a hit felt at {@code distance} blocks: all of it inside the radius, then a smooth fade. */
   public static float falloff(double distance, double radius) {
      double out = distance - radius;
      if (out <= 0.0) {
         return 1.0F;
      }

      if (out >= SPREAD) {
         return 0.0F;
      }

      double t = 1.0 - out / SPREAD;
      return (float)(t * t);
   }

   /** Pure: saturating union of two shares 0..1. */
   public static float union(float a, float b) {
      return 1.0F - (1.0F - a) * (1.0F - b);
   }

   /** Something happened at x/z within {@code radius}: alarm and trauma (0..1) at its centre. */
   public void hit(double x, double z, double radius, float alarm, float trauma, long now) {
      double reach = radius + SPREAD;
      int x0 = cell(x - reach);
      int x1 = cell(x + reach);
      int z0 = cell(z - reach);
      int z1 = cell(z + reach);
      for (int cx = x0; cx <= x1; cx++) {
         for (int cz = z0; cz <= z1; cz++) {
            double px = Math.max(cx * CELL, Math.min(x, cx * CELL + CELL));
            double pz = Math.max(cz * CELL, Math.min(z, cz * CELL + CELL));
            float f = falloff(Math.sqrt((px - x) * (px - x) + (pz - z) * (pz - z)), radius);
            if (f <= 0.0F) {
               continue;
            }

            Cell c = this.cells.get(key(cx, cz));
            if (c == null) {
               if (this.cells.size() >= MAX_CELLS) {
                  this.prune(now, 0.05F);
                  if (this.cells.size() >= MAX_CELLS) {
                     continue;
                  }
               }

               c = new Cell();
               c.at = now;
               this.cells.put(key(cx, cz), c);
            }

            c.decay(now);
            c.alarm = union(c.alarm, alarm * f);
            c.trauma = union(c.trauma, trauma * f);
         }
      }
   }

   public float alarm(double x, double z, long now) {
      Cell c = this.cells.get(key(cell(x), cell(z)));
      if (c == null) {
         return 0.0F;
      }

      c.decay(now);
      return c.alarm;
   }

   public float trauma(double x, double z, long now) {
      Cell c = this.cells.get(key(cell(x), cell(z)));
      if (c == null) {
         return 0.0F;
      }

      c.decay(now);
      return c.trauma;
   }

   public Mood mood(double x, double z, long now) {
      return of(this.alarm(x, z, now), this.trauma(x, z, now));
   }

   /** Pure: the mood of an alarm and a trauma level. */
   public static Mood of(float alarm, float trauma) {
      if (alarm >= 0.5F) {
         return Mood.PANIC;
      }

      if (alarm >= 0.15F) {
         return Mood.WARY;
      }

      return trauma >= 0.2F ? Mood.AFTER : Mood.CALM;
   }

   /** Forgets cells that calmed down below {@code eps}. */
   public void prune(long now, float eps) {
      ObjectIterator<Cell> it = this.cells.values().iterator();
      while (it.hasNext()) {
         Cell c = it.next();
         c.decay(now);
         if (c.alarm < eps && c.trauma < eps) {
            it.remove();
         }
      }
   }

   public int size() {
      return this.cells.size();
   }

   public void clear() {
      this.cells.clear();
   }
}
