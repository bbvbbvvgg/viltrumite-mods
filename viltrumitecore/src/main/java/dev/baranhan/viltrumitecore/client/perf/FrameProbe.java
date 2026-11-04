package dev.baranhan.viltrumitecore.client.perf;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/**
 * Frame-time probe for performance QA (issue #150). Records the wall time between rendered frames into a ring
 * buffer and summarises it as average FPS, 1 % / 0.1 % low (FPS of the slowest 1 % / 0.1 % of frames), median,
 * p99 and worst frame time, plus GC time and counts, heap, particles and entities since the last reset. Driven by
 * the opt-in QA harness ({@code perfreset}, {@code perfstate}); costs one {@code nanoTime} per frame when unused.
 */
public final class FrameProbe {
   private static final int CAPACITY = 1 << 15;
   private static final long[] FRAMES = new long[CAPACITY];
   private static int count;
   private static long last;
   private static long gcTimeAtReset;
   private static long gcCountAtReset;
   private static long resetAt;

   private FrameProbe() {
   }

   /** One rendered frame (render tick END). */
   public static void frame() {
      long now = System.nanoTime();
      if (last != 0L) {
         FRAMES[count & (CAPACITY - 1)] = now - last;
         count++;
      }
      last = now;
   }

   public static void reset() {
      count = 0;
      last = 0L;
      resetAt = System.nanoTime();
      dev.baranhan.viltrumitecore.client.particle.MeteorSmokeParticle.resetQuadStats();
      gcTimeAtReset = gcTime();
      gcCountAtReset = gcCount();
   }

   /** Pure: summary of frame times in nanoseconds {avgFps, low1, low01, p50ms, p99ms, maxMs}. */
   public static double[] summarize(long[] frames, int n) {
      if (n <= 0) {
         return new double[6];
      }

      long[] f = Arrays.copyOf(frames, n);
      Arrays.sort(f);
      double total = 0.0;
      for (long v : f) {
         total += v;
      }

      return new double[]{
         n / (total / 1.0E9),
         lowFps(f, 0.01),
         lowFps(f, 0.001),
         f[(int)Math.min(n - 1, Math.floor(n * 0.5))] / 1.0E6,
         f[(int)Math.min(n - 1, Math.floor(n * 0.99))] / 1.0E6,
         f[n - 1] / 1.0E6
      };
   }

   /** FPS over the slowest {@code share} of frames (sorted ascending), at least one frame. */
   static double lowFps(long[] sorted, double share) {
      int k = Math.max(1, (int)Math.round(sorted.length * share));
      double sum = 0.0;
      for (int i = sorted.length - k; i < sorted.length; i++) {
         sum += sorted[i];
      }
      return k / (sum / 1.0E9);
   }

   public static String describe(Minecraft mc) {
      int n = Math.min(count, CAPACITY);
      double[] s = summarize(FRAMES, n);
      Runtime rt = Runtime.getRuntime();
      long usedMb = (rt.totalMemory() - rt.freeMemory()) >> 20;
      return String.format(Locale.ROOT,
         "frames=%d secs=%.1f avgFps=%.1f low1=%.1f low01=%s p50ms=%.2f p99ms=%.2f maxMs=%.2f gcMs=%d gcCount=%d heapMb=%d/%d particles=%s entities=%d smokeQuads=%s",
         n, (System.nanoTime() - resetAt) / 1.0E9, s[0], s[1], n >= 1000 ? String.format(Locale.ROOT, "%.1f", s[2]) : "n/a", s[3], s[4], s[5],
         gcTime() - gcTimeAtReset, gcCount() - gcCountAtReset, usedMb, rt.maxMemory() >> 20, mc.particleEngine.countParticles(),
         mc.level == null ? 0 : mc.level.getEntityCount(), dev.baranhan.viltrumitecore.client.particle.MeteorSmokeParticle.quadStats());
   }

   private static long gcTime() {
      long t = 0L;
      for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
         t += Math.max(0L, gc.getCollectionTime());
      }
      return t;
   }

   private static long gcCount() {
      long c = 0L;
      for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
         c += Math.max(0L, gc.getCollectionCount());
      }
      return c;
   }
}
