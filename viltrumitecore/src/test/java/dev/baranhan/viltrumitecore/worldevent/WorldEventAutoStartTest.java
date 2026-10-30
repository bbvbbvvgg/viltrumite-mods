package dev.baranhan.viltrumitecore.worldevent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Viltrumite NPC events start only by command unless a server opts back into automatic starts. */
class WorldEventAutoStartTest {
   @Test
   void commandEventsAlwaysSurvive() {
      assertTrue(WorldEventManager.keepsEvent(true, false));
      assertTrue(WorldEventManager.keepsEvent(true, true));
   }

   @Test
   void legacyAutomaticEventsAreDroppedWhileAutoStartIsOff() {
      assertFalse(WorldEventManager.keepsEvent(false, false));
      assertTrue(WorldEventManager.keepsEvent(false, true));
   }
}
