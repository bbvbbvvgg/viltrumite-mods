package dev.baranhan.viltrumitecore.worldevent.meteor;

import net.minecraft.util.RandomSource;

/**
 * Pure numbers of the meteor rain: when the next existing meteor falls and where it lands
 * around the player. The rain itself never changes the meteor (fall line, explosion, craters).
 */
public final class MeteorRainRules {
   /** Ticks between two launches: random in [MIN, MAX], so the stream is uneven. */
   public static final int MIN_DELAY_TICKS = 4;
   public static final int MAX_DELAY_TICKS = 24;
   /** Sometimes two or three fall almost together. */
   public static final float BURST_CHANCE = 0.2F;
   public static final int MAX_BURST = 3;
   /** Ring around the player where meteors land. */
   public static final double MIN_RADIUS = 6.0;
   public static final double MAX_RADIUS = 56.0;
   /** Share of meteors aimed close to the player, so standing still is not safe. */
   public static final float NEAR_CHANCE = 0.3F;
   public static final double NEAR_RADIUS = 14.0;
   /** Cap of rain meteors in the air at once per rain: no unbounded entity growth. */
   public static final int MAX_IN_FLIGHT = 10;
   /** A rain meteor that ticked this long without landing (void, odd terrain) is removed. */
   public static final int MAX_METEOR_AGE_TICKS = 600;
   /** Candidate points tried per launch before skipping (unloaded or non-ticking chunks). */
   public static final int PLACEMENT_TRIES = 4;

   private MeteorRainRules() {
   }

   public static int nextDelay(RandomSource random) {
      return MIN_DELAY_TICKS + random.nextInt(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1);
   }

   /** How many meteors to launch at this beat, never above the free in-flight slots. */
   public static int burstSize(RandomSource random, int inFlight) {
      int free = MAX_IN_FLIGHT - inFlight;
      if (free <= 0) {
         return 0;
      }

      int wanted = random.nextFloat() < BURST_CHANCE ? 2 + random.nextInt(MAX_BURST - 1) : 1;
      return Math.min(wanted, free);
   }

   /** Horizontal landing offset from the player: {dx, dz}. Area-uniform inside the chosen ring. */
   public static double[] landingOffset(RandomSource random) {
      double min = MIN_RADIUS;
      double max = random.nextFloat() < NEAR_CHANCE ? NEAR_RADIUS : MAX_RADIUS;
      double r = Math.sqrt(min * min + random.nextDouble() * (max * max - min * min));
      double angle = random.nextDouble() * Math.PI * 2.0;
      return new double[]{Math.cos(angle) * r, Math.sin(angle) * r};
   }
}
