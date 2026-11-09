package dev.baranhan.viltrumitecore.client.perf;

import java.util.Arrays;

/** The last frames' durations: a smoothed frame time, percentiles and the overlay graph. Pure, one thread. */
public final class FrameRing {
   private static final double SMOOTHING = 0.08;
   private final long[] frames;
   private final double initialMs;
   private int head;
   private int count;
   private long last;
   private double emaMs;

   public FrameRing(int size, double initialMs) {
      this.frames = new long[Math.max(1, size)];
      this.initialMs = initialMs;
      this.emaMs = initialMs;
   }

   /** A frame starts now; the gap to the previous start is the previous frame's duration. */
   public void frameStarted(long nanos) {
      if (this.last != 0L) {
         long duration = nanos - this.last;
         if (duration > 0L && duration < 2_000_000_000L) {
            this.add(duration);
         }
      }

      this.last = nanos;
   }

   public void add(long frameNanos) {
      this.frames[this.head] = frameNanos;
      this.head = (this.head + 1) % this.frames.length;
      if (this.count < this.frames.length) {
         this.count++;
      }

      double ms = frameNanos / 1.0E6;
      this.emaMs = this.count == 1 ? ms : this.emaMs + (ms - this.emaMs) * SMOOTHING;
   }

   public double emaMs() {
      return this.emaMs;
   }

   public int count() {
      return this.count;
   }

   public int size() {
      return this.frames.length;
   }

   /** Copies the newest frames into {@code out}, oldest first; returns how many. */
   public int copyOldestFirst(long[] out) {
      int n = Math.min(this.count, out.length);
      int start = this.head - n;
      for (int i = 0; i < n; i++) {
         out[i] = this.frames[Math.floorMod(start + i, this.frames.length)];
      }

      return n;
   }

   /** The {@code q} quantile (0..1) of the remembered frames, ms. */
   public double percentileMs(double q) {
      if (this.count == 0) {
         return 0.0;
      }

      long[] sorted = new long[this.count];
      this.copyOldestFirst(sorted);
      Arrays.sort(sorted);
      int index = (int)Math.ceil(this.count * Math.max(0.0, Math.min(1.0, q))) - 1;
      return sorted[Math.max(0, Math.min(this.count - 1, index))] / 1.0E6;
   }

   public void clear() {
      Arrays.fill(this.frames, 0L);
      this.head = 0;
      this.count = 0;
      this.last = 0L;
      this.emaMs = this.initialMs;
   }
}
