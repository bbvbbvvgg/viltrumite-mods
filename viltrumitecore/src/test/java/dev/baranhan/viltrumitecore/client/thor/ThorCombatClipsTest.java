package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvents;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorCombatModule;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorStrike;
import dev.baranhan.viltrumitecore.hero.thor.dev.ThorDevCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Session B clips: every B move and every strike pose ends at the server length; poses play only while authored. */
class ThorCombatClipsTest {
   @BeforeEach
   void install() {
      ThorCombatModule.install();
      ThorCombatClips.registerAll();
   }

   @AfterEach
   void reset() {
      ThorMoves.resetForTests();
      ThorEvents.resetForTests();
      ThorDevCommands.resetForTests();
      ThorCombatModule.resetForTests();
   }

   private static void ordered(ThorClips.Clip clip, String name) {
      for (float[][] rows : new float[][][]{clip.rightArm(), clip.leftArm(), clip.body()}) {
         if (rows == null) {
            continue;
         }

         assertEquals(0.0F, rows[0][0], name + " starts at tick 0");
         for (int i = 1; i < rows.length; i++) {
            assertTrue(rows[i][0] > rows[i - 1][0], name + " key " + i);
         }
      }
   }

   @Test
   void everyMoveAndPoseEndsAtTheServerLength() {
      for (ThorMove move : ThorMove.values()) {
         if (!move.authored()) {
            continue;
         }

         for (int step = 0; step < move.maxSteps(); step++) {
            ThorClips.Clip clip = ThorClips.of(move, step);
            assertNotNull(clip, move.name());
            assertEquals(move.length(), clip.lastTick(), 1.0E-6, move + " step " + step);
            ordered(clip, move.name());
         }
      }

      for (ThorStrike strike : ThorStrike.values()) {
         ThorClips.Clip clip = ThorClips.of(strike.move(), 0, strike.pose());
         assertNotNull(clip, strike.name());
         assertEquals(strike.move().length(), clip.lastTick(), 1.0E-6, strike.name());
         ordered(clip, strike.name());
      }

      for (int pose : new int[]{ThorCombatClips.RIGHT_HAND, ThorCombatClips.LEFT_HAND}) {
         assertEquals(ThorMove.ELECTRIC_COMBO.length(), ThorClips.of(ThorMove.ELECTRIC_COMBO, 0, pose).lastTick(), 1.0E-6);
      }
   }

   @Test
   void unknownPosesFallBackAndUnauthoredMovesNeverPose() {
      assertSame(ThorClips.of(ThorMove.HAMMER_COMBO, 0), ThorClips.of(ThorMove.HAMMER_COMBO, 0, 999));
      ThorMoves.resetForTests();
      assertNull(ThorClips.of(ThorMove.HAMMER_COMBO, 0, ThorStrike.SPIN.pose()), "not authored: no pose");
      assertNull(ThorClips.of(ThorMove.WRATH, 0));
   }
}
