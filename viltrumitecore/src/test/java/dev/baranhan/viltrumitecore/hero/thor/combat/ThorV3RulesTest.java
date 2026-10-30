package dev.baranhan.viltrumitecore.hero.thor.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.thor.ThorCue;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import org.junit.jupiter.api.Test;

/** Audit v3: guard, wrath staging and the windup sound sync, pure halves. */
class ThorV3RulesTest {
   @Test
   void theGuardBlocksMoreWithMjolnirAndParriesOnlyRightAfterTheRaise() {
      assertTrue(ThorGuard.factor(true) < ThorGuard.factor(false), "the hammer blocks more than the fists");
      assertTrue(ThorGuard.factor(false) < 1.0F && ThorGuard.factor(true) > 0.0F, "a block reduces, never nullifies");
      assertTrue(ThorGuard.parrying(0, true));
      assertTrue(ThorGuard.parrying(ThorGuard.PARRY_WINDOW - 1, true));
      assertFalse(ThorGuard.parrying(ThorGuard.PARRY_WINDOW, true), "a held guard is a block, not a parry");
      assertFalse(ThorGuard.parrying(0, false), "spent parry (rearm) does not parry again");
      assertFalse(ThorGuard.parrying(-1, true), "no guard, no parry");
      assertTrue(ThorGuard.allowed(true, false, false, false));
      assertFalse(ThorGuard.allowed(true, false, false, true), "no guard in full flight");
      assertFalse(ThorGuard.allowed(true, false, true, false), "no guard in a scene");
      assertFalse(ThorGuard.allowed(false, false, false, false));
   }

   @Test
   void wrathCallsBoltsThroughTheBuildUpAndKeepsAQuietBeatBeforeTheStrike() {
      int windup = ThorAttacks.envelope(ThorMove.WRATH).phases().windup();
      int calls = 0;
      int last = -1;
      for (int t = 0; t < windup; t++) {
         if (WrathMove.calls(t, windup)) {
            calls++;
            last = t;
         }
      }

      assertTrue(calls >= 3, "the build-up is staged, not a silent wait: " + calls);
      assertTrue(last < windup - WrathMove.CALL_QUIET, "a quiet beat before the column");
      assertFalse(WrathMove.calls(0, windup), "no bolt on the very first tick");
      assertEquals(WrathMove.SHROUD_FACTOR, WrathMove.shroud(ThorMove.WRATH, MovePhase.INTRO));
      assertEquals(WrathMove.SHROUD_FACTOR, WrathMove.shroud(ThorMove.WRATH, MovePhase.MAIN));
      assertEquals(1.0F, WrathMove.shroud(ThorMove.WRATH, MovePhase.FINISH), "the tail is open to punishment");
      assertEquals(1.0F, WrathMove.shroud(ThorMove.HAMMER_COMBO, MovePhase.INTRO));
      assertEquals(1.0F, WrathMove.shroud(null, null));
   }

   @Test
   void theWindupWhooshPeaksOnTheContact() {
      for (ThorAttacks.Attack row : ThorAttacks.ALL) {
         if (row.strike() == null) {
            continue;
         }

         ThorCue cue = ThorCue.windup(row.strike());
         int windup = row.phases().windup();
         int delay = ThorCue.windupDelay(cue, windup);
         float peak = delay + ThorCue.whooshPeakSeconds(cue) * 20.0F;
         assertTrue(delay >= 0, row.clip());
         assertTrue(peak <= windup + 0.5F && peak >= windup - 2.0F, row.clip() + " whoosh peak at " + peak + " t, contact " + windup);
      }
   }
}
