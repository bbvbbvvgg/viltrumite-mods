package dev.baranhan.viltrumitecore.hero.jinwoo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Playtest: shadows vanished after the ult because the summon tick also drained the ult queue. */
class ShadowArmyQueueTest {
   @Test
   void ultOwnsTheQueueWhileArising() {
      assertTrue(ShadowArmy.ultOwnsQueue(true));
      assertFalse(ShadowArmy.ultOwnsQueue(false));
   }
}
