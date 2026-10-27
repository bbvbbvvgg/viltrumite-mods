package dev.baranhan.viltrumitecore.client.hero;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** F-m3: the claim is read on the press edge only; a held button stays held while the key is down. */
class HeroMouseLatchTest {
   @Test
   void aClaimedPressLatchesUntilTheKeyIsUp() {
      assertTrue(HeroMouseInput.held(false, true, true, false, true), "press with a claim");
      assertFalse(HeroMouseInput.held(false, true, true, false, false), "press without a claim stays vanilla");
      assertTrue(HeroMouseInput.held(false, true, false, true, true), "a click shorter than a tick still presses");
      assertTrue(HeroMouseInput.held(true, true, true, false, false), "the claim dropped mid-hold (block under the crosshair): still held");
      assertFalse(HeroMouseInput.held(true, true, false, false, false), "key up: release");
      assertFalse(HeroMouseInput.held(true, false, true, false, true), "focus lost / screen open: release");
   }
}
