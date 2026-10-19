package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import org.junit.jupiter.api.Test;

class ReinhardMouseTest {
   @Test void rightClickOnlyClaimedWhileActuallyHolding() {
      assertNull(ReinhardHero.claimedMouse(MouseButton.SECONDARY, false));
      assertEquals(HeroAction.SECONDARY_USE, ReinhardHero.claimedMouse(MouseButton.SECONDARY, true));
      assertEquals(HeroAction.PRIMARY_ATTACK,ReinhardHero.claimedMouse(MouseButton.PRIMARY, true));
      assertEquals(HeroAction.MODE_CYCLE, ReinhardHero.claimedMouse(MouseButton.MIDDLE, false));
   }
   @Test void repeatedGrabKeyDoesNotReleaseTheHold() {
      ReinhardState state = new ReinhardState();
      var target = new dev.baranhan.viltrumitecore.hero.reinhard.ability.GrabThrow.Candidate(7, java.util.UUID.randomUUID(), true, true);
      var control = new dev.baranhan.viltrumitecore.hero.reinhard.ability.GrabThrow.Control() {
         public boolean acquire(java.util.UUID id) { return true; }
         public void release(java.util.UUID target, java.util.UUID id, dev.baranhan.viltrumitecore.hero.control.ReleaseReason reason) {
            fail("Z must not release the hold");
         }
      };
      var handler = new dev.baranhan.viltrumitecore.hero.reinhard.ability.GrabThrow();
      assertTrue(dev.baranhan.viltrumitecore.hero.reinhard.ability.GrabThrow.grab(state, target, control));
      assertTrue(handler.claimsPress(state));
      handler.secondPress(null, state);
      assertTrue(dev.baranhan.viltrumitecore.hero.reinhard.ability.GrabThrow.holding(state));
      assertTrue(ReinhardFlags.has(ReinhardHero.stateFlags(state, 0), ReinhardFlags.GRAB_HOLDING));
   }
}
