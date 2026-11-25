package dev.baranhan.viltrumitecore.combat.finisher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Scene pick of the finisher (wave1-22 T6): the hero's own shot wins, the shared ones split on a boss. */
class FinisherScenesTest {
   @Test
   void heroSceneWinsAndSharedShotsSplitOnABoss() {
      assertEquals(FinisherScenes.GENERIC_A, FinisherScenes.pick(null, false));
      assertEquals(FinisherScenes.GENERIC_B, FinisherScenes.pick(null, true));
      assertEquals(FinisherScenes.THOR, FinisherScenes.pick(FinisherScenes.THOR, true));
      assertEquals("viltrumitecore", FinisherScenes.GENERIC_A.getNamespace());
   }
}
