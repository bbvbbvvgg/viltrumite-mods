package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode;

/**
 * Pure rules of Reinhard's mode auras (spec §8): Knight — white wind motes circling the feet;
 * Holy Sword — a gold spark about every 15 ticks near the body; Restrained — nothing.
 * The local first-person view keeps only a few feet motes (below the view).
 */
public final class AuraRules {
   public static final int KNIGHT_WIND_MOTES = 8;
   public static final int FIRST_PERSON_WIND_MOTES = 3;
   public static final double WIND_RADIUS = 0.7;
   public static final double WIND_RISE = 0.4;
   public static final double WIND_LIFE_SECONDS = 1.2;
   /** Turns per second of the circling motes. */
   public static final double WIND_TURNS_PER_SECOND = 0.35;
   /** Share of a mote's life spent fading in. */
   private static final double FADE_IN = 0.15;
   /** Gold spark spawn chance per tick (1 per ~15 ticks). */
   public static final double GOLD_SPARK_PER_TICK = 1.0 / 15.0;
   public static final int GOLD_SPARK_LIFE_TICKS = 12;

   private AuraRules() {
   }

   public static int windMotes(ReinhardMode mode, boolean localFirstPerson) {
      if (mode != ReinhardMode.KNIGHT) {
         return 0;
      }

      return localFirstPerson ? FIRST_PERSON_WIND_MOTES : KNIGHT_WIND_MOTES;
   }

   public static double goldSparkChance(ReinhardMode mode, boolean localFirstPerson) {
      return mode == ReinhardMode.HOLY_SWORD && !localFirstPerson ? GOLD_SPARK_PER_TICK : 0.0;
   }

   /**
    * Wind mote {@code index} of {@code count} at {@code seconds} (ticks + partialTick / 20), offset from
    * the feet in blocks; each mote loops: rise 0 → WIND_RISE over its life, fade in then out. Stateless.
    */
   public static Mote windMote(int index, int count, double seconds, int seed) {
      double phase = frac(seed * 0.6180339887);
      double age = frac(seconds / WIND_LIFE_SECONDS + (double) index / count + phase);
      double angle = 2.0 * Math.PI * ((double) index / count + phase + seconds * WIND_TURNS_PER_SECOND);
      float alpha = (float) (age < FADE_IN ? age / FADE_IN : 1.0 - (age - FADE_IN) / (1.0 - FADE_IN));
      return new Mote(Math.cos(angle) * WIND_RADIUS, age * WIND_RISE, Math.sin(angle) * WIND_RADIUS, Math.max(0.0F, Math.min(1.0F, alpha)));
   }

   private static double frac(double v) {
      return v - Math.floor(v);
   }

   public record Mote(double x, double y, double z, float alpha) {
   }
}
