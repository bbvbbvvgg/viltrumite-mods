package dev.baranhan.viltrumitecore.hero.thor.hammer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.thor.HammerState;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The world state machine of the one Mjölnir (spec §3.2). */
class MjolnirPhaseTest {
   @Test
   void keysAreStableAndUnique() {
      Set<String> keys = new HashSet<>();
      for (MjolnirPhase phase : MjolnirPhase.values()) {
         assertTrue(keys.add(phase.key()), phase.name());
         assertSame(phase, MjolnirPhase.fromKey(phase.key()));
      }

      assertEquals("world_falling", MjolnirPhase.WORLD_FALLING.key());
      assertNull(MjolnirPhase.fromKey("magnetized_to_hammer"), "the pull is a Thor state, not a hammer phase");
      assertNull(MjolnirPhase.fromKey(null));
   }

   @Test
   void theFirstAppearanceIsTheSkyFallOrTheHand() {
      assertTrue(MjolnirPhase.NOT_CREATED.canGo(MjolnirPhase.WORLD_FALLING));
      assertTrue(MjolnirPhase.NOT_CREATED.canGo(MjolnirPhase.IN_HAND), "the first Thor before the fall gets it in the hand");
      for (MjolnirPhase phase : MjolnirPhase.values()) {
         assertFalse(phase.canGo(MjolnirPhase.NOT_CREATED), phase + ": a created hammer never disappears");
         assertFalse(phase.canGo(phase), phase + ": no self transition");
         if (phase != MjolnirPhase.NOT_CREATED) {
            assertFalse(phase.canGo(MjolnirPhase.WORLD_FALLING), phase + ": the first fall happens once");
         }
      }
   }

   @Test
   void onlyTheReturnOrAPickUpPutsItInTheHand() {
      for (MjolnirPhase phase : MjolnirPhase.values()) {
         boolean allowed = phase == MjolnirPhase.NOT_CREATED || phase == MjolnirPhase.RETURNING || phase == MjolnirPhase.RESTING;
         assertEquals(allowed, phase.canGo(MjolnirPhase.IN_HAND), phase.name());
      }

      assertFalse(MjolnirPhase.THROWN.canGo(MjolnirPhase.IN_HAND), "a thrown hammer comes back through RETURNING");
      assertTrue(MjolnirPhase.RETURNING.canGo(MjolnirPhase.DEATH_IMPACT), "№169");
      assertTrue(MjolnirPhase.DEATH_IMPACT.canGo(MjolnirPhase.RESTING));
      assertTrue(MjolnirPhase.DIMENSION_TRANSFER.canGo(MjolnirPhase.RETURNING), "№205");
      assertFalse(MjolnirPhase.DIMENSION_TRANSFER.canGo(MjolnirPhase.IN_HAND), "no teleport into the hand");
   }

   @Test
   void projectionOntoTheThorState() {
      assertSame(HammerState.IN_HAND, MjolnirPhase.IN_HAND.personal());
      assertSame(HammerState.THROWN, MjolnirPhase.THROWN.personal());
      assertSame(HammerState.RETURNING, MjolnirPhase.RETURNING.personal());
      assertSame(HammerState.RETURNING, MjolnirPhase.DIMENSION_TRANSFER.personal());
      for (MjolnirPhase phase : new MjolnirPhase[]{MjolnirPhase.WORLD_FALLING, MjolnirPhase.RESTING, MjolnirPhase.FALLING, MjolnirPhase.DEATH_IMPACT,
         MjolnirPhase.VOID_RECOVERY}) {
         assertTrue(phase.detached(), phase.name());
         assertTrue(phase.recallable(), phase.name());
         assertTrue(phase.physical(), phase.name());
      }

      assertFalse(MjolnirPhase.NOT_CREATED.detached());
      assertFalse(MjolnirPhase.NOT_CREATED.recallable());
      assertFalse(MjolnirPhase.IN_HAND.recallable());
      assertFalse(MjolnirPhase.IN_HAND.physical(), "attached: drawn by the player layer");
      assertFalse(MjolnirPhase.DIMENSION_TRANSFER.physical());
   }

   @Test
   void yPullsOnlyAThrownHammer() {
      for (MjolnirPhase phase : MjolnirPhase.values()) {
         assertEquals(phase == MjolnirPhase.THROWN, phase.pullable(), phase.name());
         assertEquals(phase == MjolnirPhase.THROWN, MjolnirPull.allowed(phase), phase.name());
      }
   }
}
