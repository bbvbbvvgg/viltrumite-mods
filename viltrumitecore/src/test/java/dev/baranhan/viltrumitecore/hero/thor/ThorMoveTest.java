package dev.baranhan.viltrumitecore.hero.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveHandler;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Move ids: stable wire ordinals, per-move registration instead of a shipped stage. */
class ThorMoveTest {
   private static final ThorMoveDefinition ONE = new ThorMoveDefinition(1, 1, 1, null, 1);

   @AfterEach
   void reset() {
      ThorMoves.resetForTests();
   }

   @Test
   void wireOrdinalsAreStableAndAppendOnly() {
      assertEquals(List.of("DRAW", "HOLSTER", "HAMMER_COMBO", "FIST_COMBO", "GUARD", "HAMMER_THROW", "RECALL", "ELECTRIC_MODE", "ELECTRIC_COMBO",
            "LIGHTNING_CALL", "AIR_STRIKE", "LIGHTNING_DASH", "TELEPORT_STRIKE", "DIVE_SLAM", "GRAB", "GRAB_SLAM", "FATALITY", "WRATH",
            "HAMMER_STRIKE", "HAMMER_PULL", "TELEPORT_SELECT"),
         Arrays.stream(ThorMove.values()).map(Enum::name).toList());
      for (ThorMove move : ThorMove.values()) {
         assertSame(move, ThorMove.byWireId(move.wireId()));
         assertEquals(move.ordinal(), move.wireId());
      }

      assertNull(ThorMove.byWireId(-1));
      assertNull(ThorMove.byWireId(ThorMove.values().length));
   }

   @Test
   void heroActionAndHammerOrdinalsAreStable() {
      HeroAction[] actions = HeroAction.values();
      assertEquals(List.of("DIAGONAL_WORLD_CUT", "CONTEXT_POWER", "CONTEXT_MOBILITY", "ULTIMATE", "CONTEXT_TELEPORT"),
         Arrays.stream(actions, actions.length - 5, actions.length).map(Enum::name).toList());
      assertEquals(0, HeroAction.LIONS_HEART.ordinal());
      assertEquals(14, HeroAction.TOOL_CYCLE.ordinal());
      assertEquals(List.of("HOLSTERED", "IN_HAND", "THROWN", "RETURNING"), Arrays.stream(HammerState.values()).map(Enum::name).toList());
   }

   @Test
   void theFoundationAuthorsOnlyTheTechnicalDrawAndHolster() {
      Set<ThorMove> authored = EnumSet.noneOf(ThorMove.class);
      for (ThorMove move : ThorMove.values()) {
         if (move.authored()) {
            authored.add(move);
            assertEquals(ThorMoves.FOUNDATION, ThorMoves.owner(move));
         }
      }

      assertEquals(EnumSet.of(ThorMove.DRAW, ThorMove.HOLSTER), authored);
      assertEquals(ThorRules.DRAW_INTRO, ThorMove.DRAW.ticks(MovePhase.INTRO));
      assertEquals(ThorRules.DRAW_MAIN, ThorMove.DRAW.ticks(MovePhase.MAIN));
      assertEquals(ThorRules.DRAW_FINISH, ThorMove.DRAW.ticks(MovePhase.FINISH));
      assertEquals(ThorRules.HOLSTER_INTRO + ThorRules.HOLSTER_MAIN + ThorRules.HOLSTER_FINISH, ThorMove.HOLSTER.length());
      assertSame(MovePhase.FINISH, ThorMove.DRAW.cancellableFrom());
      assertSame(ThorMoveHandler.NONE, ThorMoves.handler(ThorMove.DRAW), "no behaviour beyond the hammer place");
   }

   @Test
   void unregisteredMovesCannotStartAnywhere() {
      for (ThorMove move : ThorMove.values()) {
         if (!move.authored()) {
            assertEquals(0, move.length(), move.name());
            assertNull(move.cancellableFrom());
            assertEquals(1, move.maxSteps());
            assertSame(ThorMoveHandler.NONE, ThorMoves.handler(move));
            assertEquals(StagedTimeline.Admission.REFUSE, new StagedTimeline<ThorMove>().request(move, 1), move.name());
            assertEquals(ThorActions.Refusal.NOT_READY, ThorActions.gate(new ThorState(), move, true), move.name());
         }
      }
   }

   @Test
   void registrationGivesTheMoveItsTimingAndOneOwner() {
      ThorMoves.register("A", ThorMove.HAMMER_THROW, new ThorMoveDefinition(3, 2, 4, MovePhase.FINISH, 2), ThorMoveHandler.NONE);
      assertTrue(ThorMove.HAMMER_THROW.authored());
      assertEquals(9, ThorMove.HAMMER_THROW.length());
      assertEquals(2, ThorMove.HAMMER_THROW.maxSteps());
      assertEquals("A", ThorMoves.owner(ThorMove.HAMMER_THROW));
      assertThrows(IllegalStateException.class, () -> ThorMoves.register("B", ThorMove.HAMMER_THROW, ONE, ThorMoveHandler.NONE),
         "a second session cannot overwrite a move");
      assertThrows(IllegalStateException.class, () -> ThorMoves.register("B", ThorMove.DRAW, ONE, ThorMoveHandler.NONE));
      assertEquals(9, ThorMove.HAMMER_THROW.length(), "the failed registration changed nothing");
   }

   @Test
   void retiredMovesCanNeverBeRegistered() {
      for (ThorMove move : ThorMove.values()) {
         if (move.retired()) {
            assertThrows(IllegalArgumentException.class, () -> ThorMoves.register("A", move, ONE, ThorMoveHandler.NONE), move.name());
            assertFalse(move.authored());
         }
      }
   }

   @Test
   void definitionsAreValidated() {
      assertThrows(IllegalArgumentException.class, () -> new ThorMoveDefinition(0, 0, 0, null, 1));
      assertThrows(IllegalArgumentException.class, () -> new ThorMoveDefinition(-1, 2, 0, null, 1));
      assertThrows(IllegalArgumentException.class, () -> new ThorMoveDefinition(1, 1, 1, null, 0));
      assertThrows(IllegalArgumentException.class, () -> ThorMoves.register("", ThorMove.RECALL, ONE, ThorMoveHandler.NONE));
      assertThrows(IllegalArgumentException.class, () -> ThorMoves.register("A", ThorMove.RECALL, ONE, null));
   }

   @Test
   void sessionsRegisterIndependently() {
      // A (hammer), B (combos), C (mobility) in any order give the same result; one never changes another's move.
      ThorMoves.register("C", ThorMove.TELEPORT_SELECT, ONE, ThorMoveHandler.NONE);
      ThorMoves.register("A", ThorMove.HAMMER_THROW, ONE, ThorMoveHandler.NONE);
      ThorMoves.register("B", ThorMove.HAMMER_COMBO, ONE, ThorMoveHandler.NONE);
      assertEquals("A", ThorMoves.owner(ThorMove.HAMMER_THROW));
      assertEquals("B", ThorMoves.owner(ThorMove.HAMMER_COMBO));
      assertEquals("C", ThorMoves.owner(ThorMove.TELEPORT_SELECT));
      assertFalse(ThorMove.RECALL.authored());
      assertFalse(ThorMove.HAMMER_PULL.authored());
      ThorMoves.resetForTests();
      ThorMoves.register("B", ThorMove.HAMMER_COMBO, ONE, ThorMoveHandler.NONE);
      assertFalse(ThorMove.HAMMER_THROW.authored(), "without A its move stays unauthored");
      assertFalse(ThorMove.TELEPORT_SELECT.authored());
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
      assertEquals("hero.viltrumitecore.thor.move.hammer_pull", ThorMove.HAMMER_PULL.translationKey());
   }
}
