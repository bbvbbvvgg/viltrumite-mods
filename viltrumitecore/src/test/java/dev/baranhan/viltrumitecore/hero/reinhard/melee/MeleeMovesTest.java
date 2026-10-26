package dev.baranhan.viltrumitecore.hero.reinhard.melee;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MeleeMovesTest {
   private static void timeline(MeleeMoves.Move move) {
      MeleeArc.Swing s = move.swing();
      assertTrue(s.contactStart() >= 2, move.clip() + ": a visible wind-up precedes contact");
      assertTrue(s.contactEnd() >= s.contactStart(), move.clip());
      assertTrue(s.contactEnd() < move.ticks() - 1, move.clip() + ": recovery after the contact window");
      assertTrue(move.chainFrom() >= s.contactStart() && move.chainFrom() < move.ticks(), move.clip() + ": buffer opens after the hit");
      assertTrue(s.reach() >= 3.7 && s.reach() <= 5.5, move.clip() + ": reach");
      assertTrue(s.width() > 0 && s.maxTargets() >= 1, move.clip());
      assertTrue(move.damage() > 0 && move.knockback() > 0, move.clip());
   }

   @Test
   void everyMoveHasAPlayableTimeline() {
      for (MeleeMoves.Move move : MeleeMoves.FIST) timeline(move);
      for (MeleeMoves.Move move : MeleeMoves.REID) timeline(move);
      for (MeleeArc.Swing swing : MeleeMoves.KNIGHT) assertTrue(swing.contactEnd() >= swing.contactStart());
   }

   @Test
   void clipsAreDistinctAndTheChainAlternatesSides() {
      Set<String> clips = new HashSet<>();
      for (MeleeMoves.Move move : MeleeMoves.FIST) assertTrue(clips.add(move.clip()));
      for (MeleeMoves.Move move : MeleeMoves.REID) assertTrue(clips.add(move.clip()));
      assertEquals(-MeleeMoves.FIST[0].swing().yawFrom(), MeleeMoves.FIST[1].swing().yawFrom(), "left jab mirrors the right one");
      assertNotEquals(MeleeMoves.reid(MeleeMoves.SLASH_DOWN).swing().side(), MeleeMoves.reid(MeleeMoves.SLASH_RISE).swing().side(),
         "the second stroke returns the blade");
      assertTrue(MeleeMoves.reid(MeleeMoves.SLASH_SWEEP).swing().maxTargets() > MeleeMoves.reid(MeleeMoves.SLASH_DOWN).swing().maxTargets(),
         "the wide sweep is the crowd hit");
   }

   @Test
   void onlyTheFinisherIsHeavyAndHitsHardest() {
      MeleeMoves.Move finish = MeleeMoves.reid(MeleeMoves.SLASH_FINISH);
      assertTrue(finish.heavy());
      for (MeleeMoves.Move move : MeleeMoves.REID) {
         if (move != finish) {
            assertFalse(move.heavy(), move.clip());
            assertTrue(move.damage() < finish.damage(), move.clip());
            assertTrue(move.hitStop() <= finish.hitStop(), move.clip());
         }
      }
      for (MeleeMoves.Move move : MeleeMoves.FIST) {
         assertFalse(move.heavy());
         assertTrue(move.damage() < MeleeMoves.reid(MeleeMoves.SLASH_DOWN).damage(), "Reid outclasses the fists");
      }
   }

   @Test
   void chainAdvancesWithinTheWindowAndRestartsAfterAPause() {
      assertEquals(0, MeleeMoves.next(-1, 0, 3));
      assertEquals(1, MeleeMoves.next(0, 0, 3));
      assertEquals(2, MeleeMoves.next(1, MeleeMoves.CHAIN_WINDOW, 3));
      assertEquals(0, MeleeMoves.next(2, 0, 3), "the last strike wraps");
      assertEquals(0, MeleeMoves.next(1, MeleeMoves.CHAIN_WINDOW + 1, 3), "a pause restarts the chain");
   }

   @Test
   void crouchForcesTheLowSweepWithoutAdvancingTheChain() {
      assertEquals(MeleeMoves.SLASH_LOW, MeleeMoves.reidMove(MeleeMoves.SLASH_RISE, 0, true));
      assertEquals(MeleeMoves.SLASH_DOWN, MeleeMoves.reidMove(MeleeMoves.SLASH_LOW, 0, false), "after the low sweep the chain starts over");
      assertEquals(MeleeMoves.SLASH_SWEEP, MeleeMoves.reidMove(MeleeMoves.SLASH_RISE, 3, false));
      assertEquals(MeleeMoves.SLASH_DOWN, MeleeMoves.reidMove(MeleeMoves.SLASH_FINISH, 3, false), "the finisher closes the chain");
      for (int i = -1; i < MeleeMoves.REID.length; i++) {
         assertNotEquals(MeleeMoves.SLASH_LOW, MeleeMoves.reidMove(i, 0, false), "standing presses never pick the low sweep");
      }
   }

   @Test
   void phaseLookupWraps() {
      assertSame(MeleeMoves.FIST[0], MeleeMoves.fist(3));
      assertSame(MeleeMoves.REID[4], MeleeMoves.reid(-1));
   }
}
