package dev.baranhan.viltrumitecore.hero.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Move catalogue: stable wire ids, only stage 1 authored, the rest safely unstartable. */
class ThorMoveTest {
   @Test
   void wireIdsAreStable() {
      assertEquals(0, ThorMove.DRAW.wireId());
      assertEquals(1, ThorMove.HOLSTER.wireId());
      assertEquals(17, ThorMove.WRATH.wireId());
      for (ThorMove move : ThorMove.values()) {
         assertSame(move, ThorMove.byWireId(move.wireId()));
      }

      assertNull(ThorMove.byWireId(-1));
      assertNull(ThorMove.byWireId(ThorMove.values().length));
   }

   @Test
   void onlyStageOneShips() {
      assertEquals(1, ThorRules.SHIPPED_STAGE);
      Set<ThorMove> shipped = EnumSet.noneOf(ThorMove.class);
      for (ThorMove move : ThorMove.values()) {
         if (move.shipped()) {
            shipped.add(move);
         }
      }

      assertEquals(EnumSet.of(ThorMove.DRAW, ThorMove.HOLSTER), shipped);
   }

   @Test
   void laterMovesCannotStartAnywhere() {
      for (ThorMove move : ThorMove.values()) {
         if (move.stage() > ThorRules.SHIPPED_STAGE) {
            assertEquals(0, move.length(), move + " is not authored yet");
            assertEquals(StagedTimeline.Admission.REFUSE, new StagedTimeline<ThorMove>().request(move, 1), move.name());
            assertEquals(ThorActions.Refusal.NOT_READY, ThorActions.gate(move, true), move.name());
         }

         assertTrue(move.stage() >= 1 && move.stage() <= 8, move.name());
      }
   }

   @Test
   void stageOneMovesUseTheirRuleTicks() {
      assertEquals(ThorRules.DRAW_INTRO, ThorMove.DRAW.ticks(MovePhase.INTRO));
      assertEquals(ThorRules.DRAW_MAIN, ThorMove.DRAW.ticks(MovePhase.MAIN));
      assertEquals(ThorRules.DRAW_FINISH, ThorMove.DRAW.ticks(MovePhase.FINISH));
      assertEquals(ThorRules.HOLSTER_INTRO + ThorRules.HOLSTER_MAIN + ThorRules.HOLSTER_FINISH, ThorMove.HOLSTER.length());
      assertSame(MovePhase.FINISH, ThorMove.DRAW.cancellableFrom(), "the settle can be cut by the next press");
      assertEquals(1, ThorMove.DRAW.maxSteps());
      assertFalse(ThorMove.DRAW.paired());
   }

   @Test
   void scenesArePairedAndTiersOrdered() {
      for (ThorMove move : new ThorMove[]{ThorMove.GRAB, ThorMove.GRAB_SLAM, ThorMove.FATALITY}) {
         assertTrue(move.paired(), move.name());
      }

      assertTrue(ThorMove.WRATH.priority() > ThorMove.FATALITY.priority());
      assertTrue(ThorMove.FATALITY.priority() > ThorMove.GRAB.priority());
      assertTrue(ThorMove.GRAB.priority() > ThorMove.HAMMER_THROW.priority());
      assertTrue(ThorMove.HAMMER_THROW.priority() > ThorMove.HAMMER_COMBO.priority());
      assertTrue(ThorMove.HAMMER_COMBO.priority() > ThorMove.DRAW.priority());
   }

   @Test
   void translationKeysFollowTheName() {
      assertEquals("hero.viltrumitecore.thor.move.lightning_call", ThorMove.LIGHTNING_CALL.translationKey());
   }
}
