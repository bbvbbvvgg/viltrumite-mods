package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.baranhan.viltrumitecore.hero.reinhard.reid.ReidDrawEvent;
import org.junit.jupiter.api.Test;

/** Stage 4 Task 3: the draw event's timeline in ticks from the press (spec §7.3). */
class ReidDrawTimelineTest {
   @Test
   void timelinePhases() {
      assertEquals(20, ReinhardRules.REID_CRACK_START);
      assertEquals(100, ReinhardRules.REID_CRACK_END);
      assertEquals(110, ReinhardRules.REID_TREMBLE_TICK);
      assertSame(ReidDrawEvent.Stage.GATHER, ReidDrawEvent.stageAt(0));
      assertSame(ReidDrawEvent.Stage.GATHER, ReidDrawEvent.stageAt(19));
      assertSame(ReidDrawEvent.Stage.CRACKS, ReidDrawEvent.stageAt(20));
      assertSame(ReidDrawEvent.Stage.CRACKS, ReidDrawEvent.stageAt(109));
      assertSame(ReidDrawEvent.Stage.TREMBLE, ReidDrawEvent.stageAt(110));
      assertSame(ReidDrawEvent.Stage.TREMBLE, ReidDrawEvent.stageAt(119));
      assertSame(ReidDrawEvent.Stage.FINAL, ReidDrawEvent.stageAt(120));
   }

   @Test
   void waveOnlyWithin24() {
      assertEquals(1.2, ReidDrawEvent.waveKnockback(0.0), 1.0E-9);
      assertEquals(1.2, ReidDrawEvent.waveKnockback(24.0), 1.0E-9);
      assertEquals(0.0, ReidDrawEvent.waveKnockback(24.01), 1.0E-9);
   }

   @Test
   void refreshEvery20() {
      assertEquals(true, ReidDrawEvent.refreshes(20));
      assertEquals(false, ReidDrawEvent.refreshes(21));
      assertEquals(false, ReidDrawEvent.refreshes(0), "tick 0 is the start packet itself");
   }
}
