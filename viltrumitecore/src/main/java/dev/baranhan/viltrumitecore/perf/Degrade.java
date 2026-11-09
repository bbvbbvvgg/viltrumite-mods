package dev.baranhan.viltrumitecore.perf;

/**
 * Smooth degradation helpers: ramps that turn a load measure into a 0..1 pressure, and deterministic
 * stochastic thinning so that "keep 37% of 10 particles" averages to 3.7 instead of always rounding to 4. Pure.
 */
public final class Degrade {
   private Degrade() {
   }

   /** 0 at or below {@code start}, 1 at or above {@code full}, linear between. */
   public static double ramp(double value, double start, double full) {
      if (value <= start) {
         return 0.0;
      }

      if (full <= start || value >= full) {
         return 1.0;
      }

      return (value - start) / (full - start);
   }

   /** Scale for a pressure 0..1: 1 without pressure, {@code floor} at full pressure. */
   public static double scale(double pressure, double floor) {
      double p = Math.max(0.0, Math.min(1.0, pressure));
      return 1.0 - p * (1.0 - floor);
   }

   /** Rounds {@code expected} down or up with the probability of its fraction; {@code u} is uniform in [0, 1). */
   public static int stochasticRound(double expected, double u) {
      if (!(expected > 0.0)) {
         return 0;
      }

      int whole = (int)Math.floor(expected);
      return whole + (u < expected - whole ? 1 : 0);
   }

   /** A tiny xorshift stream for thinning; deterministic for a seed, no allocation per call. */
   public static final class Thinner {
      private long state;

      public Thinner(long seed) {
         this.state = seed == 0L ? 0x9E3779B97F4A7C15L : seed;
      }

      public double next() {
         long x = this.state;
         x ^= x << 13;
         x ^= x >>> 7;
         x ^= x << 17;
         this.state = x;
         return (x >>> 11) * 0x1.0p-53;
      }

      /** How many of {@code wanted} to keep at {@code factor} (0..1). */
      public int keep(int wanted, double factor) {
         if (wanted <= 0 || factor <= 0.0) {
            return 0;
         }

         return factor >= 1.0 ? wanted : stochasticRound(wanted * factor, this.next());
      }

      public boolean keepOne(double factor) {
         return factor >= 1.0 || factor > 0.0 && this.next() < factor;
      }
   }
}
