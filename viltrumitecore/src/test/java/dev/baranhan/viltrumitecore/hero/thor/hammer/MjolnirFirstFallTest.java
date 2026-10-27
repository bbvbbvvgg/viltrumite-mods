package dev.baranhan.viltrumitecore.hero.thor.hammer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** №186/№192: one first fall per save, armed at the first join, cancelled for good by an earlier creation. */
class MjolnirFirstFallTest {
   @Test
   void armedOnceAndDueAfterTheDelay() {
      MjolnirRecord record = new MjolnirRecord();
      assertFalse(MjolnirFirstFall.due(record, 1_000_000L), "not armed");
      assertTrue(MjolnirFirstFall.arm(record, 100L));
      assertFalse(MjolnirFirstFall.arm(record, 5000L), "the timer never restarts");
      assertEquals(100L, record.fallArmedAt());
      assertFalse(MjolnirFirstFall.due(record, 100L + MjolnirTuning.FIRST_FALL_DELAY - 1));
      assertTrue(MjolnirFirstFall.due(record, 100L + MjolnirTuning.FIRST_FALL_DELAY));
   }

   @Test
   void aHammerCreatedEarlierCancelsTheFallForever() {
      MjolnirRecord record = new MjolnirRecord();
      MjolnirFirstFall.arm(record, 0L);
      record.create(UUID.randomUUID(), MjolnirPhase.IN_HAND);
      record.fallCancelled = true;
      assertFalse(MjolnirFirstFall.due(record, Long.MAX_VALUE / 2));

      MjolnirRecord cancelled = new MjolnirRecord();
      cancelled.fallCancelled = true;
      assertFalse(MjolnirFirstFall.arm(cancelled, 0L), "a cancelled fall is never armed again");

      MjolnirRecord done = new MjolnirRecord();
      done.fallDone = true;
      assertFalse(MjolnirFirstFall.arm(done, 0L));
   }
}
