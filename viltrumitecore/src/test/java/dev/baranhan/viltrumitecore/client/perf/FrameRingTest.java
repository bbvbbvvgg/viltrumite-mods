package dev.baranhan.viltrumitecore.client.perf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FrameRingTest {
   private static final long MS = 1_000_000L;

   @Test
   void framesAreTheGapsBetweenStarts() {
      FrameRing ring = new FrameRing(8, 16.7);
      ring.frameStarted(1000 * MS);
      ring.frameStarted(1010 * MS);
      ring.frameStarted(1030 * MS);
      ring.frameStarted(5000 * MS);
      assertEquals(2, ring.count(), "a gap of seconds (a pause) is not a frame");
      long[] out = new long[8];
      assertEquals(2, ring.copyOldestFirst(out));
      assertEquals(10 * MS, out[0]);
      assertEquals(20 * MS, out[1]);
   }

   @Test
   void keepsTheNewestFramesOldestFirst() {
      FrameRing ring = new FrameRing(4, 16.7);
      for (int i = 1; i <= 6; i++) {
         ring.add(i * MS);
      }

      long[] out = new long[4];
      assertEquals(4, ring.copyOldestFirst(out));
      assertArrayEquals(new long[]{3 * MS, 4 * MS, 5 * MS, 6 * MS}, out);
   }

   @Test
   void percentilesAndSmoothing() {
      FrameRing ring = new FrameRing(240, 16.7);
      for (int i = 0; i < 99; i++) {
         ring.add(10 * MS);
      }

      ring.add(50 * MS);
      assertEquals(10.0, ring.percentileMs(0.99), 1.0E-9);
      assertEquals(50.0, ring.percentileMs(1.0), 1.0E-9);
      for (int i = 0; i < 200; i++) {
         ring.add(20 * MS);
      }

      assertEquals(20.0, ring.emaMs(), 0.05);
      ring.clear();
      assertEquals(0, ring.count());
      assertEquals(0.0, ring.percentileMs(0.5));
   }
}
