package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.regulus.RegulusMove;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusState;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Lifecycle contract: change is a no-op on the same id, restore resets state
 * only when the id differs, and cleanup wipes every transient combat field
 * while the cooldowns stay with the player.
 */
class HeroLifecycleTest {

   @Test
   void changeIsANoOpOnTheSameId() {
      assertFalse(HeroRegistry.isRealTransition(HeroId.REGULUS, HeroId.REGULUS), "re-selecting the current hero is never an exit/re-entry");
      assertTrue(HeroRegistry.isRealTransition(HeroId.REGULUS, HeroId.VILTRUMITE));
      assertTrue(HeroRegistry.isRealTransition(HeroId.HUMAN, HeroId.REGULUS));
   }

   @Test
   void restoreResetsStateOnlyWhenIdDiffers() {
      assertFalse(HeroRegistry.shouldResetState(HeroId.REGULUS, HeroId.REGULUS), "same-id restore keeps the loaded state");
      assertTrue(HeroRegistry.shouldResetState(HeroId.HUMAN, HeroId.REGULUS));
      assertTrue(HeroRegistry.shouldResetState(HeroId.REGULUS, HeroId.VILTRUMITE));
   }

   @Test
   void cleanupWipesTransientCombatState() {
      RegulusState state = filled();

      state.resetTransient();
      assertClean(state);
      assertEquals(77, state.sandCooldown, "cooldowns belong to the player, not the body");
      assertEquals(650, state.strain, "strain cools on its own; a reset is not a free cure");
   }

   @Test
   void respawnCopyKeepsOnlyCooldowns() {
      RegulusState copy = filled().respawnCopy();
      assertClean(copy);
      assertEquals(77, copy.sandCooldown);
      assertEquals(1500, copy.ultCooldown);
      assertEquals(0, copy.strain);
      assertEquals(0, copy.lionLock);
   }

   @Test
   void resetIsIdempotent() {
      RegulusState state = filled();
      state.resetTransient();
      state.resetTransient();
      assertClean(state);
   }

   private static RegulusState filled() {
      RegulusState state = new RegulusState();
      state.startMove(RegulusMove.ULT);
      state.moveTick = 40;
      state.lion = true;
      state.lionTicks = 300;
      state.comboStep = 1;
      state.chainLeft = 5;
      state.touchQueued = true;
      state.subjects.add(UUID.randomUUID());
      state.subjects.add(UUID.randomUUID());
      state.host = state.subjects.get(0);
      state.hopTimer = 120;
      state.replyAttacker = UUID.randomUUID();
      state.replyBlocked = 6.0F;
      state.replyLanded = true;
      state.sandCooldown = 77;
      state.ultCooldown = 1500;
      state.strain = 650;
      state.lionLock = 40;
      return state;
   }

   private static void assertClean(RegulusState state) {
      assertNull(state.move);
      assertEquals(0, state.moveTick);
      assertFalse(state.lion);
      assertEquals(0, state.lionTicks);
      assertEquals(-1, state.comboStep);
      assertEquals(0, state.chainLeft);
      assertFalse(state.touchQueued);
      assertTrue(state.subjects.isEmpty());
      assertNull(state.host);
      assertEquals(0, state.hopTimer);
      assertNull(state.replyAttacker);
      assertEquals(0.0F, state.replyBlocked);
      assertFalse(state.replyLanded);
      assertFalse(state.busy());
   }
}
