package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ServerRules;
import org.junit.jupiter.api.Test;

/** PR #41 review: the client's RMB grab claim on a player mirrors the server's PvP switch. */
class ReinhardGrabClaimTest {
   @Test
   void playerGrabFollowsTheServerPvpSwitch() {
      assertFalse(ReinhardHero.grabsPlayer(false, false, true), "PvP off: the client must not claim RMB on a player");
      assertTrue(ReinhardHero.grabsPlayer(false, true, true));
      assertFalse(ReinhardHero.grabsPlayer(true, true, true), "creative players are never grabbed");
      assertFalse(ReinhardHero.grabsPlayer(false, true, false), "teams forbid the harm");
   }

   @Test
   void clientAssumesNoPvpUntilTheServerSaysOtherwise() {
      ServerRules.reset();
      assertFalse(ServerRules.pvp(null), "before the login sync the stricter rule holds");
      ServerRules.receive(true);
      assertTrue(ServerRules.pvp(null));
      ServerRules.reset();
      assertFalse(ServerRules.pvp(null), "logging out forgets the old server's rule");
   }
}
