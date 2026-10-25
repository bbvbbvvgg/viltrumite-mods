package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.render.vfx.PhoenixFlameVfx;
import org.junit.jupiter.api.Test;

/** The revive flames start when the synced Phoenix cooldown jumps up (spec §4.2). */
class PhoenixFlameVfxTest {
   @Test
   void riseIsARevive() {
      assertTrue(PhoenixFlameVfx.revived(0, 600));
      assertTrue(PhoenixFlameVfx.revived(12, 600), "revived again right after the cooldown ended");
   }

   @Test
   void countdownAndFirstSightAreNot() {
      assertFalse(PhoenixFlameVfx.revived(600, 599));
      assertFalse(PhoenixFlameVfx.revived(0, 0));
      assertFalse(PhoenixFlameVfx.revived(null, 600), "start tracking a cooling Reinhard: baseline only");
   }
}
