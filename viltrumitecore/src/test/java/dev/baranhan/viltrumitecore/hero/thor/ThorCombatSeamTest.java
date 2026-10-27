package dev.baranhan.viltrumitecore.hero.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvent;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEventType;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvents;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveContext;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import dev.baranhan.viltrumitecore.hero.thor.combat.CombatHits;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorCombatModule;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorCombatTimings;
import dev.baranhan.viltrumitecore.hero.thor.dev.ThorDevCommands;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Session B on the foundation bridge: registrations, routing seams, combo window, snapshot pose, events. */
class ThorCombatSeamTest {
   private static final Set<ThorMove> B_MOVES = EnumSet.of(ThorMove.HAMMER_COMBO, ThorMove.FIST_COMBO, ThorMove.HAMMER_STRIKE, ThorMove.ELECTRIC_MODE,
      ThorMove.ELECTRIC_COMBO, ThorMove.ELECTRIC_WAVE, ThorMove.ELECTRIC_STORM, ThorMove.WRATH);

   @BeforeEach
   void install() {
      ThorCombatModule.install();
   }

   @AfterEach
   void reset() {
      ThorMoves.resetForTests();
      ThorEvents.resetForTests();
      ThorDevCommands.resetForTests();
      ThorCombatModule.resetForTests();
   }

   @Test
   void registersOnlyItsApprovedMovesOnce() {
      for (ThorMove move : ThorMove.values()) {
         if (B_MOVES.contains(move)) {
            assertEquals(ThorCombatModule.OWNER, ThorMoves.owner(move), move.name());
            assertEquals(ThorCombatTimings.of(move), ThorMoves.definition(move));
            assertFalse(move.retired(), move.name());
         } else if (move != ThorMove.DRAW && move != ThorMove.HOLSTER) {
            assertNull(ThorMoves.owner(move), move + " belongs to another session (or is retired)");
         }
      }

      ThorCombatModule.install();
      assertThrows(IllegalStateException.class, () -> ThorMoves.register("A", ThorMove.HAMMER_COMBO, ThorCombatTimings.of(ThorMove.HAMMER_COMBO),
         dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveHandler.NONE), "one owner per move");
      assertEquals(List.of(ThorCombatModule.OWNER), ThorEvents.owners(ThorEventType.COMBO_END), "B's own sky lightning listens");
      assertEquals(List.of(ThorCombatModule.OWNER), ThorEvents.owners(ThorEventType.THROW));
      assertEquals(List.of(ThorCombatModule.OWNER), ThorEvents.owners(ThorEventType.RECALL));
      assertEquals(List.of(ThorCombatModule.OWNER), ThorEvents.owners(ThorEventType.CATCH));
      assertTrue(ThorEvents.owners(ThorEventType.MELEE_HIT).isEmpty() && ThorEvents.owners(ThorEventType.LETHAL_HIT).isEmpty(), "B publishes them");
      assertEquals(ThorCombatModule.OWNER, ThorDevCommands.owner("debug combat"));
      assertFalse(ThorCombatModule.OWNER.equals(ThorBridge.hammerAccessOwner()), "B never installs the hammer access");
   }

   @Test
   void theStormCanTakeOverTheHeldWaveAndCombosChain() {
      assertTrue(ThorMove.ELECTRIC_STORM.priority() > ThorMove.ELECTRIC_WAVE.priority(), "a held RMB grows the wave into the storm");
      for (ThorMove move : List.of(ThorMove.HAMMER_COMBO, ThorMove.FIST_COMBO, ThorMove.ELECTRIC_COMBO, ThorMove.ELECTRIC_WAVE)) {
         assertTrue(move.maxSteps() > 1, move + " chains");
      }

      ThorState state = new ThorState();
      state.mode = ThorMode.ELECTRIC;
      assertSame(ThorActions.Refusal.NONE, ThorActions.start(state, ThorInput.STANCE, ThorMove.ELECTRIC_WAVE, true));
      assertSame(ThorActions.Refusal.NONE, ThorActions.start(state, ThorMove.ELECTRIC_STORM, true), "outside the wave's cancel window too");
      assertTrue(state.timeline.is(ThorMove.ELECTRIC_STORM));
      ThorActions.Release release = ThorActions.release(state, ThorInput.STANCE);
      assertSame(ThorMove.ELECTRIC_WAVE, release.move(), "the RMB release reaches the wave handler, which ends the storm");
   }

   @Test
   void comboWindowRoutesAnOpenUnarmedChainAndClosesByItself() {
      ThorState state = new ThorState();
      state.hammer = HammerState.IN_HAND;
      assertFalse(ThorActions.fistChainOpen(state));
      ThorBridge.openComboWindow(state, ThorMove.FIST_COMBO, 3);
      assertTrue(ThorActions.fistChainOpen(state));
      assertSame(ThorMove.FIST_COMBO, ThorBridge.comboWindow(state));
      for (int i = 0; i < 3; i++) {
         ThorActions.advance(state, true, false);
      }

      assertNull(ThorBridge.comboWindow(state));
      assertFalse(ThorActions.fistChainOpen(state));
      ThorBridge.openComboWindow(state, ThorMove.HAMMER_COMBO, 5);
      assertFalse(ThorActions.fistChainOpen(state), "only an unarmed chain changes the routing");
      ThorActions.stop(state);
      assertNull(ThorBridge.comboWindow(state), "lifecycle stop clears the window");
   }

   @Test
   void theSnapshotCarriesTheStrikePoseOnlyWhileAMoveRuns() {
      ThorState state = new ThorState();
      state.combatPose = 7;
      assertEquals(0, ThorHero.composeSnapshot(state).resource(), "idle: no pose");
      state.hammer = HammerState.IN_HAND;
      assertSame(ThorActions.Refusal.NONE, ThorActions.start(state, ThorInput.STRIKE, ThorMove.HAMMER_COMBO, true));
      assertEquals(7, ThorHero.composeSnapshot(state).resource());
      assertEquals(ThorMove.HAMMER_COMBO.wireId(), ThorHero.composeSnapshot(state).actionId());
   }

   @Test
   void claimsFollowTheRegistrations() {
      ThorContext hand = ThorContext.grounded(HammerState.IN_HAND, ThorMode.NORMAL);
      assertSame(ThorInput.STRIKE.action(), ThorControls.claimedMouse(dev.baranhan.viltrumitecore.hero.MouseButton.PRIMARY, hand));
      assertSame(ThorInput.STANCE.action(), ThorControls.claimedMouse(dev.baranhan.viltrumitecore.hero.MouseButton.SECONDARY, hand));
      assertTrue(ThorControls.anyAuthored(ThorInput.POWER) && ThorControls.anyAuthored(ThorInput.ULTIMATE));
      assertFalse(ThorControls.anyAuthored(ThorInput.MOBILITY), "Y is A's");
      assertFalse(ThorControls.anyAuthored(ThorInput.TELEPORT), "V is C's");
      assertFalse(ThorHero.INSTANCE.claimsBlockUse(null), "RMB on blocks stays vanilla");
      assertFalse(ThorHero.INSTANCE.claimsBlockAttack(null), "LMB on blocks mines");
   }

   @Test
   void handlersWithoutAPlayerAreInert() {
      ThorState state = new ThorState();
      for (ThorMove move : B_MOVES) {
         ThorMoveContext context = new ThorMoveContext(null, state, move, null, ThorInput.STRIKE);
         assertTrue(ThorMoves.handler(move).admits(context), move.name());
         ThorMoves.handler(move).onPhase(context, StagedTimeline.Event.ENTER_MAIN);
         ThorMoves.handler(move).onRelease(context, 5);
         ThorMoves.handler(move).onStop(context);
      }

      assertEquals(0, state.combatPose);
   }

   @Test
   void eventsCarryTheHitTimeHammerSnapshot() {
      List<ThorEvent> seen = new ArrayList<>();
      ThorEvents.subscribe("C", ThorEventType.LETHAL_HIT, seen::add);
      UUID thor = UUID.randomUUID();
      UUID victim = UUID.randomUUID();
      ThorEvent lethal = CombatHits.event(ThorEventType.LETHAL_HIT, thor, victim, ThorMove.HAMMER_STRIKE, 4, true, 20.0F, true);
      assertTrue(ThorEvents.post(lethal));
      assertFalse(ThorEvents.post(lethal), "deduped by event id");
      assertEquals(List.of(lethal), seen);
      assertTrue(seen.get(0).hammerInHand());
      assertTrue(CombatHits.MELEE.contains(ThorMove.HAMMER_STRIKE) && CombatHits.MELEE.contains(ThorMove.FIST_COMBO));
      assertFalse(CombatHits.MELEE.contains(ThorMove.ELECTRIC_COMBO), "lightning hits are not melee hits");
      assertTrue(CombatHits.event(ThorEventType.MELEE_HIT, thor, victim, ThorMove.HAMMER_COMBO, 1, false, 9.0F, true).eventId() != lethal.eventId());
   }
}
