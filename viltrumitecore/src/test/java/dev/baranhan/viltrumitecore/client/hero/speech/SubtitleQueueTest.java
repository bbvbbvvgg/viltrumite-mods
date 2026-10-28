package dev.baranhan.viltrumitecore.client.hero.speech;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubtitleQueueTest {
   record Line(int serial, UUID speaker, String lineId, int priority, boolean own) implements SubtitleQueue.Cue {
   }

   static final UUID A = UUID.randomUUID();
   static final UUID B = UUID.randomUUID();
   static final UUID C = UUID.randomUUID();

   private static int length(Line line) {
      return 100;
   }

   @Test
   void oneLineAtATimeEqualPrioritiesTakeTurns() {
      SubtitleQueue<Line> queue = new SubtitleQueue<>();
      assertTrue(queue.offer(new Line(1, A, "a:x", 1, false), 0));
      assertTrue(queue.offer(new Line(2, B, "b:x", 1, false), 1));
      assertEquals(1, queue.update(2, SubtitleQueueTest::length).cue().serial());
      assertEquals(1, queue.waitingCount());
      assertEquals(1, queue.update(99, SubtitleQueueTest::length).cue().serial());
      assertEquals(2, queue.update(102, SubtitleQueueTest::length).cue().serial(), "the next line starts after the first ends");
   }

   @Test
   void theSameLineIsNotQueuedTwice() {
      SubtitleQueue<Line> queue = new SubtitleQueue<>();
      assertTrue(queue.offer(new Line(1, A, "a:x", 1, false), 0));
      assertFalse(queue.offer(new Line(1, A, "a:x", 1, false), 0), "same serial");
      assertFalse(queue.offer(new Line(5, A, "a:x", 1, false), 1), "same speaker and line while it plays");
      queue.update(1, SubtitleQueueTest::length);
      assertFalse(queue.offer(new Line(6, A, "a:x", 1, false), 2));
   }

   @Test
   void higherPriorityOrTheSameSpeakerCutsTheShownLineShort() {
      SubtitleQueue<Line> queue = new SubtitleQueue<>();
      queue.offer(new Line(1, A, "a:x", 1, false), 0);
      queue.update(0, SubtitleQueueTest::length);
      queue.offer(new Line(2, B, "b:ult", 2, false), 10);
      assertTrue(queue.active().exiting(), "fast fade of the interrupted line");
      assertEquals(1, queue.update(12, SubtitleQueueTest::length).cue().serial(), "no two panels: the old one fades first");
      assertEquals(2, queue.update(10 + SubtitleTiming.FORCED_EXIT, SubtitleQueueTest::length).cue().serial());
      queue.offer(new Line(3, B, "b:other", 1, false), 20);
      assertTrue(queue.active().exiting(), "a speaker's newer line replaces its own");
      assertEquals(3, queue.update(20 + SubtitleTiming.FORCED_EXIT, SubtitleQueueTest::length).cue().serial());
   }

   @Test
   void ownLinesGoFirstAndStaleOrExcessLinesAreDropped() {
      SubtitleQueue<Line> queue = new SubtitleQueue<>();
      queue.offer(new Line(1, A, "a:x", 2, false), 0);
      queue.update(0, SubtitleQueueTest::length);
      queue.offer(new Line(2, B, "b:x", 1, false), 1);
      queue.offer(new Line(3, C, "c:x", 1, true), 2);
      assertEquals(3, queue.update(100, SubtitleQueueTest::length).cue().serial(), "the local player's own line first");
      SubtitleQueue<Line> full = new SubtitleQueue<>();
      full.offer(new Line(1, A, "a:x", 1, false), 0);
      full.update(0, SubtitleQueueTest::length);
      for (int i = 0; i < 6; i++) {
         full.offer(new Line(10 + i, UUID.randomUUID(), "o:x", 1, false), 1);
      }

      assertEquals(SubtitleQueue.MAX_WAITING, full.waitingCount());
      full.update(1 + SubtitleQueue.maxWait(1) + 1, SubtitleQueueTest::length);
      assertEquals(0, full.waitingCount(), "stale lines are not shown late");
   }

   @Test
   void stopFadesTheShownLineAndForgetsWaitingOnes() {
      SubtitleQueue<Line> queue = new SubtitleQueue<>();
      queue.offer(new Line(1, A, "a:x", 1, true), 0);
      queue.offer(new Line(2, B, "b:x", 1, false), 0);
      queue.update(0, SubtitleQueueTest::length);
      queue.stop(2, 1);
      assertEquals(0, queue.waitingCount());
      queue.stopIf(Line::own, 5);
      assertTrue(queue.active().exiting());
      assertNull(queue.update(5 + SubtitleTiming.FORCED_EXIT, SubtitleQueueTest::length));
      assertTrue(queue.isEmpty());
      queue.offer(new Line(3, A, "a:y", 1, false), 20);
      queue.clear();
      assertTrue(queue.isEmpty());
   }
}
