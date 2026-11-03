package dev.baranhan.viltrumitecore.client.venom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.venom.VenomMove;
import dev.baranhan.viltrumitecore.hero.venom.VenomRules;
import org.junit.jupiter.api.Test;

/** Review of PR #144: the symbiote skin only on the grown body. */
class VenomSkinTest {
   @Test
   void theHumanKeepsHisSkin() {
      assertTrue(VenomClient.humanBody(false, false, null, 0), "before the transformation");
      assertTrue(VenomClient.humanBody(false, false, VenomMove.TRANSFORM, 1), "the coat spreads over the human");
      assertFalse(VenomClient.humanBody(false, true, null, 0), "Venom form");
      assertFalse(VenomClient.humanBody(false, true, VenomMove.TRANSFORM, VenomRules.STAGE_GROW + 1), "the grow stage");
      assertTrue(VenomClient.humanBody(false, false, VenomMove.REVERT, VenomRules.REVERT_TICKS - 1), "after the revert");
      assertTrue(VenomClient.humanBody(true, true, null, 0), "the possession husk");
      assertTrue(VenomClient.humanBody(null), "no snapshot");
   }
}
