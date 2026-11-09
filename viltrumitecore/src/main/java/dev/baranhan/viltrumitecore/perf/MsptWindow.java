package dev.baranhan.viltrumitecore.perf;

import java.util.Arrays;

/**
 * Tick statistics: MSPT avg/p95/max over a window of tick durations, and real TPS from the wall-clock spacing
 * of tick starts. Pure.
 */
public final class MsptWindow {
   private final long[] starts;
   private int head;
   private int count;

   public MsptWindow(int size) {
      this.starts = new long[Math.max(2, size)];
   }

   public record Stats(double avgMs, double p95Ms, double maxMs, int samples) {
      public static final Stats EMPTY = new Stats(0.0, 0.0, 0.0, 0);
   }

   /** Statistics over the non-zero entries of {@code tickNanos} (e.g. the server's 100-tick history). */
   public static Stats of(long[] tickNanos) {
      if (tickNanos == null) {
         return Stats.EMPTY;
      }

      long[] v = Arrays.stream(tickNanos).filter(n -> n > 0L).sorted().toArray();
      if (v.length == 0) {
         return Stats.EMPTY;
      }

      double sum = 0.0;
      for (long n : v) {
         sum += n;
      }

      int p95 = Math.max(0, (int)Math.ceil(v.length * 0.95) - 1);
      return new Stats(sum / v.length / 1.0E6, v[p95] / 1.0E6, v[v.length - 1] / 1.0E6, v.length);
   }

   public void tickStarted(long nanos) {
      this.starts[this.head] = nanos;
      this.head = (this.head + 1) % this.starts.length;
      this.count = Math.min(this.count + 1, this.starts.length);
   }

   /** Ticks per second over the window, capped at 20 (a server never runs faster than its schedule). */
   public double tps() {
      if (this.count < 2) {
         return 20.0;
      }

      int newest = (this.head - 1 + this.starts.length) % this.starts.length;
      int oldest = (this.head - this.count + this.starts.length) % this.starts.length;
      long span = this.starts[newest] - this.starts[oldest];
      return span <= 0L ? 20.0 : Math.min(20.0, (this.count - 1) * 1.0E9 / span);
   }

   public void clear() {
      this.head = 0;
      this.count = 0;
   }
}
