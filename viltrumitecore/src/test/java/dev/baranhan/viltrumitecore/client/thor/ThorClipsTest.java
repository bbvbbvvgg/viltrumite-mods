package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorRules;
import org.junit.jupiter.api.Test;

/** The animation extension point: every authored move with a clip ends at its server length; unauthored moves never pose. */
class ThorClipsTest {
   @Test
   void everyAuthoredMoveHasAClipOfItsServerLength() {
      for (ThorMove move : ThorMove.values()) {
         ThorClips.Clip clip = ThorClips.of(move, 0);
         if (move.authored()) {
            assertNotNull(clip, move + " needs a clip");
            for (int step = 0; step < move.maxSteps(); step++) {
               assertEquals(move.length(), ThorClips.of(move, step).lastTick(), 1.0E-6, move + " step " + step);
            }
         } else {
            assertNull(clip, move + " is not authored: it must not pose");
         }
      }

      assertNull(ThorClips.of(null, 0));
   }

   @Test
   void keysAreOrderedInTime() {
      for (ThorMove move : ThorMove.values()) {
         ThorClips.Clip clip = ThorClips.of(move, 0);
         if (clip == null) {
            continue;
         }

         for (float[][] rows : new float[][][]{clip.rightArm(), clip.leftArm(), clip.body()}) {
            if (rows == null) {
               continue;
            }

            assertEquals(0.0F, rows[0][0], move + " starts at tick 0");
            for (int i = 1; i < rows.length; i++) {
               assertTrue(rows[i][0] > rows[i - 1][0], move + " key " + i);
            }
         }
      }
   }

   @Test
   void theGripKeyLandsWhenTheServerMovesTheHammer() {
      int grip = StagedTimeline.phaseStart(ThorMove.DRAW, MovePhase.MAIN);
      assertEquals(ThorRules.DRAW_INTRO, grip);
      float[][] draw = ThorClips.of(ThorMove.DRAW, 0).rightArm();
      assertTrue(PoseKeys.isUnder(draw[0]), "the draw starts from the pose underneath");
      assertEquals(grip, draw[1][0], 1.0E-6);
      assertEquals(ThorClips.HOLD_PITCH, draw[draw.length - 1][1], 1.0E-6, "the draw ends in the hold pose");

      float[][] holster = ThorClips.of(ThorMove.HOLSTER, 0).rightArm();
      assertEquals(ThorClips.HOLD_PITCH, holster[0][1], 1.0E-6, "the holster starts from the hold pose");
      assertTrue(PoseKeys.isUnder(holster[holster.length - 1]), "and returns to locomotion");
   }

   @Test
   void laterStagesRegisterPerStepClips() {
      assertThrows(IllegalArgumentException.class, () -> ThorClips.register(ThorMove.WRATH));
      ThorClips.Clip draw = ThorClips.of(ThorMove.DRAW, 0);
      assertSame(draw, ThorClips.of(ThorMove.DRAW, 5), "the last step repeats");
      assertSame(draw, ThorClips.of(ThorMove.DRAW, -1));
   }
}
