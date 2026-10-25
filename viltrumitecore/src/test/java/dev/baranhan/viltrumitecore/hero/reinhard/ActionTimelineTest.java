package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** One action timeline at a time drives the snapshot action fields (stage 3 Task 1). */
class ActionTimelineTest {

   @Test
   void idleOnTheWire() {
      ActionTimeline timeline = new ActionTimeline();
      assertFalse(timeline.running());
      assertEquals(-1, timeline.wireId());
      assertEquals(-1, timeline.targetId());
      assertEquals(0, timeline.length());
   }

   @Test
   void timelineRunsLength() {
      ActionTimeline timeline = new ActionTimeline();
      timeline.start(ReinhardAbility.LIGHT_PUNCH, 8, 42);
      assertTrue(timeline.running());
      assertEquals(ReinhardAbility.LIGHT_PUNCH.ordinal(), timeline.wireId());
      assertEquals(42, timeline.targetId());
      for (int i = 0; i < 7; i++) {
         assertEquals(i, timeline.elapsed());
         timeline.tick();
         assertFalse(timeline.done(), "tick " + i);
      }

      timeline.tick();
      assertTrue(timeline.done());
      assertFalse(timeline.running());
      assertEquals(-1, timeline.wireId());
   }

   @Test
   void cancelClears() {
      ActionTimeline timeline = new ActionTimeline();
      timeline.start(ReinhardAbility.STOMP, 14, -1, 2);
      timeline.tick();
      timeline.cancel();
      assertFalse(timeline.running());
      assertNull(timeline.ability());
      assertEquals(-1, timeline.wireId());
      assertEquals(0, timeline.elapsed());
      assertEquals(0, timeline.phase());
   }

   @Test
   void phaseAndRestart() {
      ActionTimeline timeline = new ActionTimeline();
      timeline.start(ReinhardAbility.FIST_COMBO, 8, -1, 1);
      timeline.tick();
      assertEquals(1, timeline.phase());
      timeline.start(ReinhardAbility.FIST_COMBO, 12, -1, 2);
      assertEquals(0, timeline.elapsed(), "a restart begins at 0");
      assertEquals(2, timeline.phase());
      assertTrue(timeline.is(ReinhardAbility.FIST_COMBO));
      assertFalse(timeline.is(ReinhardAbility.STOMP));
   }

   @Test
   void zeroLengthNeverRuns() {
      ActionTimeline timeline = new ActionTimeline();
      timeline.start(ReinhardAbility.LIGHT_PUNCH, 0, -1);
      assertFalse(timeline.running());
   }
}
