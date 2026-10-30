package dev.baranhan.viltrumitecore.hero.control;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.junit.jupiter.api.Test;

/** Stopped-time seam: view-lock drift (yaw wraps) and the client frozen-id set lifecycle. */
class TimeFreezeTest {
   @Test
   void viewDriftWrapsYaw() {
      assertFalse(TimeFreeze.viewDrifted(90.0F, 10.0F, 90.2F, 10.3F));
      assertFalse(TimeFreeze.viewDrifted(450.0F, 0.0F, 90.0F, 0.0F), "a full turn is the same view");
      assertFalse(TimeFreeze.viewDrifted(-179.9F, 0.0F, 179.9F, 0.0F), "across the seam");
      assertTrue(TimeFreeze.viewDrifted(95.0F, 0.0F, 90.0F, 0.0F));
      assertTrue(TimeFreeze.viewDrifted(90.0F, -30.0F, 90.0F, 0.0F));
   }

   @Test
   void clientSetIsReplacedAndCleared() {
      IntOpenHashSet ids = new IntOpenHashSet(new int[]{3, 7});
      TimeFreeze.setClientFrozen(ids);
      assertTrue(TimeFreeze.clientFrozen(3));
      assertTrue(TimeFreeze.clientFrozen(7));
      TimeFreeze.setClientFrozen(new IntOpenHashSet(new int[]{7}));
      assertFalse(TimeFreeze.clientFrozen(3), "a new packet replaces, it does not accumulate");
      TimeFreeze.clearClient();
      assertFalse(TimeFreeze.clientFrozen(7), "logout/level change forgets everything");
   }
}
