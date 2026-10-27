package dev.baranhan.viltrumitecore.hero.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveHandler;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Approved routing: one resolver, one move id per input and state; claims follow registrations only. */
class ThorControlsTest {
   @AfterEach
   void reset() {
      ThorMoves.resetForTests();
   }

   private static ThorContext ground(HammerState hammer) {
      return ThorContext.grounded(hammer, ThorMode.NORMAL);
   }

   private static ThorContext with(HammerState hammer, boolean enemyNear, boolean grabWindow) {
      return new ThorContext(hammer, ThorMode.NORMAL, true, false, false, false, enemyNear, grabWindow);
   }

   /** Every context the resolver can see. */
   private static List<ThorContext> everyContext() {
      List<ThorContext> all = new ArrayList<>();
      for (HammerState hammer : HammerState.values()) {
         for (ThorMode mode : ThorMode.values()) {
            for (int bits = 0; bits < 1 << 8; bits++) {
               all.add(new ThorContext(hammer, mode, (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0,
                  (bits & 32) != 0, (bits & 64) != 0, (bits & 128) != 0));
            }
         }
      }

      return all;
   }

   @Test
   void mmbIsGrabWindowThenRecallThenMeleeThenThrow() {
      for (HammerState hammer : HammerState.values()) {
         assertSame(ThorMove.GRAB, ThorControls.resolve(ThorInput.HAMMER, with(hammer, true, true)), "the grab window wins: " + hammer);
      }

      assertSame(ThorMove.RECALL, ThorControls.resolve(ThorInput.HAMMER, with(HammerState.THROWN, true, false)));
      assertSame(ThorMove.RECALL, ThorControls.resolve(ThorInput.HAMMER, with(HammerState.RETURNING, false, false)));
      assertSame(ThorMove.HAMMER_STRIKE, ThorControls.resolve(ThorInput.HAMMER, with(HammerState.IN_HAND, true, false)));
      assertSame(ThorMove.HAMMER_THROW, ThorControls.resolve(ThorInput.HAMMER, with(HammerState.IN_HAND, false, false)));
      // F-M13 (rewritten, it pinned the bug): Mjölnir only on the belt offers no hammer move (no blow, no throw from the belt).
      assertNull(ThorControls.resolve(ThorInput.HAMMER, with(HammerState.HOLSTERED, true, false)));
      assertNull(ThorControls.resolve(ThorInput.HAMMER, with(HammerState.HOLSTERED, false, false)));
   }

   @Test
   void aDetachedHammerIsRecalledByMmbWhateverItsProjection() {
      for (HammerState hammer : HammerState.values()) {
         for (boolean enemy : new boolean[]{false, true}) {
            ThorContext detached = new ThorContext(hammer, ThorMode.NORMAL, true, false, false, false, enemy, false, true, false);
            assertSame(ThorMove.RECALL, ThorControls.resolve(ThorInput.HAMMER, detached), hammer + " enemy=" + enemy);
            ThorContext window = new ThorContext(hammer, ThorMode.NORMAL, true, false, false, false, enemy, true, true, false);
            assertSame(ThorMove.GRAB, ThorControls.resolve(ThorInput.HAMMER, window), "the grab window still wins");
         }
      }
   }

   @Test
   void yPullsOnlyToTheHammerInThrowFlightNotToALyingOne() {
      ThorContext lying = new ThorContext(HammerState.HOLSTERED, ThorMode.NORMAL, true, false, false, false, false, false, true, false);
      assertNull(ThorControls.resolve(ThorInput.MOBILITY, lying));
      assertNull(ThorControls.resolve(ThorInput.MOBILITY, ground(HammerState.RETURNING)), "no pull towards a returning hammer");
      assertSame(ThorMove.HAMMER_PULL, ThorControls.resolve(ThorInput.MOBILITY, ground(HammerState.THROWN)));
   }

   @Test
   void mmbNeverMeansDrawOrHolster() {
      for (ThorContext context : everyContext()) {
         ThorMove move = ThorControls.resolve(ThorInput.HAMMER, context);
         assertTrue(move != ThorMove.DRAW && move != ThorMove.HOLSTER, context.toString());
      }
   }

   @Test
   void keysFollowTheApprovedLayout() {
      for (ThorContext context : everyContext()) {
         assertSame(ThorMove.ELECTRIC_MODE, ThorControls.resolve(ThorInput.POWER, context), "R only toggles the electric mode");
         assertSame(ThorMove.TELEPORT_SELECT, ThorControls.resolve(ThorInput.TELEPORT, context));
         assertSame(context.flying() ? null : ThorMove.WRATH, ThorControls.resolve(ThorInput.ULTIMATE, context), "the ultimate is an attack: none in full flight");
         assertSame(context.hammer() == HammerState.THROWN ? ThorMove.HAMMER_PULL : null, ThorControls.resolve(ThorInput.MOBILITY, context),
            "Y pulls only to the hammer flying after a throw");
         ThorMove rmb = context.flying() ? null : context.mode() == ThorMode.ELECTRIC ? ThorMove.ELECTRIC_WAVE
            : !context.fistChainOpen() && context.hammer() == HammerState.IN_HAND && context.handFree() ? ThorMove.HAMMER_COMBO : ThorMove.FIST_COMBO;
         assertSame(rmb, ThorControls.resolve(ThorInput.STANCE, context), "RMB: combos, the electric wave, nothing in full flight");
      }
   }

   @Test
   void lmbCombosAndNoAttackInFlight() {
      assertSame(ThorMove.HAMMER_COMBO, ThorControls.resolve(ThorInput.STRIKE, ground(HammerState.IN_HAND)));
      assertSame(ThorMove.FIST_COMBO, ThorControls.resolve(ThorInput.STRIKE, ground(HammerState.HOLSTERED)));
      assertSame(ThorMove.ELECTRIC_COMBO, ThorControls.resolve(ThorInput.STRIKE, ThorContext.grounded(HammerState.IN_HAND, ThorMode.ELECTRIC)));
      ThorContext flying = new ThorContext(HammerState.IN_HAND, ThorMode.NORMAL, true, false, true, true, true, false);
      assertNull(ThorControls.resolve(ThorInput.STRIKE, flying));
   }

   @Test
   void fullFlightKeepsOnlyTheRecallOnMmb() {
      for (ThorContext context : everyContext()) {
         if (!context.flying()) {
            continue;
         }

         ThorMove mmb = ThorControls.resolve(ThorInput.HAMMER, context);
         assertSame(context.hammerAway() ? ThorMove.RECALL : null, mmb, "no grab, melee or throw in full flight: " + context);
         assertNull(ThorControls.resolve(ThorInput.STRIKE, context));
         assertNull(ThorControls.resolve(ThorInput.STANCE, context));
         assertNull(ThorControls.resolve(ThorInput.ULTIMATE, context));
         assertSame(ThorMove.TELEPORT_SELECT, ThorControls.resolve(ThorInput.TELEPORT, context), "routed; session C refuses it in flight with its message");
      }

      ThorContext hover = new ThorContext(HammerState.IN_HAND, ThorMode.NORMAL, true, false, true, false, true, false);
      assertSame(ThorMove.HAMMER_STRIKE, ThorControls.resolve(ThorInput.HAMMER, hover), "HOVER is not full flight");
      assertSame(ThorMove.WRATH, ThorControls.resolve(ThorInput.ULTIMATE, hover));
   }

   @Test
   void anItemInTheRightHandMeansFistsNotHammerCombos() {
      ThorContext busy = new ThorContext(HammerState.IN_HAND, ThorMode.NORMAL, false, false, false, false, false, false);
      assertSame(ThorMove.FIST_COMBO, ThorControls.resolve(ThorInput.STRIKE, busy), "Mjölnir rides the belt while an item is in the hand");
      assertSame(ThorMove.FIST_COMBO, ThorControls.resolve(ThorInput.STANCE, busy));
      assertSame(ThorMove.HAMMER_THROW, ThorControls.resolve(ThorInput.HAMMER, busy), "MMB still routes; ThorActions.gate refuses the throw (HANDS_BUSY)");
      assertTrue(ThorControls.needsHand(ThorMove.HAMMER_THROW));
   }

   @Test
   void anOpenUnarmedChainFinishesBeforeHammerStrikes() {
      ThorContext open = new ThorContext(HammerState.IN_HAND, ThorMode.NORMAL, true, false, false, false, false, false, false, true);
      assertSame(ThorMove.FIST_COMBO, ThorControls.resolve(ThorInput.STRIKE, open), "#166: the unarmed chain finishes first");
      assertSame(ThorMove.FIST_COMBO, ThorControls.resolve(ThorInput.STANCE, open));
      assertSame(ThorMove.HAMMER_COMBO, ThorControls.resolve(ThorInput.STRIKE, ground(HammerState.IN_HAND)));
      ThorContext electric = new ThorContext(HammerState.IN_HAND, ThorMode.ELECTRIC, true, false, false, false, false, false, false, true);
      assertSame(ThorMove.ELECTRIC_COMBO, ThorControls.resolve(ThorInput.STRIKE, electric));
      assertSame(ThorMove.ELECTRIC_WAVE, ThorControls.resolve(ThorInput.STANCE, electric));
      ThorContext flying = new ThorContext(HammerState.IN_HAND, ThorMode.ELECTRIC, true, false, true, true, false, false, false, true);
      assertNull(ThorControls.resolve(ThorInput.STANCE, flying), "no attacks in full flight");
      assertSame(ThorMove.HAMMER_STRIKE, ThorControls.resolve(ThorInput.HAMMER, new ThorContext(HammerState.IN_HAND, ThorMode.NORMAL, true, false,
         false, false, true, false, false, true)), "an open chain never changes the MMB branch");
   }

   @Test
   void retiredMovesAreNeverResolved() {
      for (ThorInput input : ThorInput.values()) {
         for (ThorContext context : everyContext()) {
            ThorMove move = ThorControls.resolve(input, context);
            assertTrue(move == null || !move.retired(), input + " → " + move);
         }

         for (ThorMove move : ThorControls.candidates(input)) {
            assertFalse(move.retired(), input + " lists " + move);
         }
      }

      for (ThorMove retired : new ThorMove[]{ThorMove.GUARD, ThorMove.AIR_STRIKE, ThorMove.DIVE_SLAM, ThorMove.TELEPORT_STRIKE,
         ThorMove.LIGHTNING_DASH, ThorMove.LIGHTNING_CALL, ThorMove.GRAB_SLAM}) {
         assertTrue(retired.retired(), retired.name());
      }
   }

   @Test
   void everyResolutionIsADeclaredCandidateAndEveryCandidateIsReachable() {
      for (ThorInput input : ThorInput.values()) {
         Set<ThorMove> candidates = EnumSet.noneOf(ThorMove.class);
         candidates.addAll(Arrays.asList(ThorControls.candidates(input)));
         Set<ThorMove> seen = EnumSet.noneOf(ThorMove.class);
         for (ThorContext context : everyContext()) {
            ThorMove move = ThorControls.resolve(input, context);
            if (move != null) {
               assertTrue(candidates.contains(move), input + " → " + move + " in " + context);
               seen.add(move);
            }
         }

         assertEquals(candidates, seen, input + ": every candidate is reachable");
      }
   }

   @Test
   void inputReachesNoTechnicalOrEventOnlyMove() {
      Set<ThorMove> reachable = EnumSet.noneOf(ThorMove.class);
      for (ThorInput input : ThorInput.values()) {
         reachable.addAll(Arrays.asList(ThorControls.candidates(input)));
      }

      for (ThorMove move : new ThorMove[]{ThorMove.DRAW, ThorMove.HOLSTER, ThorMove.FATALITY}) {
         assertFalse(reachable.contains(move), move + " has no key");
      }
   }

   @Test
   void noMouseClaimInTheFoundation() {
      for (MouseButton button : MouseButton.values()) {
         for (ThorContext context : everyContext()) {
            assertNull(ThorControls.claimedMouse(button, context), button + " stays vanilla until a session authors its move");
         }
      }

      assertNull(ThorControls.claimedMouse(null, ground(HammerState.HOLSTERED)));
      for (ThorInput input : ThorInput.values()) {
         assertFalse(ThorControls.anyAuthored(input), input.name());
      }
   }

   @Test
   void aRegistrationClaimsMmbOnlyWhereItsBranchIsChosen() {
      ThorMoves.register("A", ThorMove.HAMMER_THROW, new ThorMoveDefinition(2, 2, 2, null, 1), ThorMoveHandler.NONE);
      assertSame(HeroAction.TOOL_CYCLE, ThorControls.claimedMouse(MouseButton.MIDDLE, with(HammerState.IN_HAND, false, false)));
      assertNull(ThorControls.claimedMouse(MouseButton.MIDDLE, with(HammerState.IN_HAND, true, false)), "melee branch not authored");
      assertNull(ThorControls.claimedMouse(MouseButton.MIDDLE, with(HammerState.THROWN, false, false)), "recall branch not authored");
      assertNull(ThorControls.claimedMouse(MouseButton.PRIMARY, with(HammerState.IN_HAND, false, false)));
      assertTrue(ThorControls.anyAuthored(ThorInput.HAMMER));
   }

   /** The MMB decision has one home: no other Thor source names the MMB branches or reads the middle button. */
   @Test
   void mmbResolverIsUnique() throws IOException {
      Path root = Path.of("src/main/java/dev/baranhan/viltrumitecore");
      List<Path> sources = new ArrayList<>();
      for (String dir : new String[]{"hero/thor", "client/thor"}) {
         try (Stream<Path> files = Files.walk(root.resolve(dir))) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
         }
      }

      for (Path source : sources) {
         String name = source.getFileName().toString();
         String text = Files.readString(source);
         // Session A owns its moves: it registers / implements / animates them by name, but never routes MMB.
         boolean owner = name.equals("MjolnirModule.java") || name.equals("MjolnirWorld.java") || name.equals("MjolnirClient.java");
         if (!name.equals("ThorControls.java") && !name.equals("ThorMove.java") && !owner) {
            for (String branch : new String[]{"ThorMove.GRAB,", "ThorMove.GRAB;", "ThorMove.GRAB)", "ThorMove.RECALL", "ThorMove.HAMMER_THROW"}) {
               assertFalse(text.contains(branch), name + " decides an MMB branch (" + branch + ")");
            }
         }

         if (!name.equals("ThorInput.java")) {
            assertFalse(text.contains("MouseButton.MIDDLE"), name + " reads MMB outside ThorInput");
         }

         if (!name.equals("ThorControls.java") && !name.equals("ThorInput.java")) {
            assertFalse(text.contains("ThorInput.HAMMER"), name + " routes MMB outside ThorControls");
         }
      }
   }

   @Test
   void inputsMapToStableHeroActions() {
      for (ThorInput input : ThorInput.values()) {
         assertSame(input, ThorInput.of(input.action()));
      }

      assertSame(HeroAction.CONTEXT_POWER, ThorInput.POWER.action());
      assertSame(HeroAction.CONTEXT_MOBILITY, ThorInput.MOBILITY.action());
      assertSame(HeroAction.ULTIMATE, ThorInput.ULTIMATE.action());
      assertSame(HeroAction.CONTEXT_TELEPORT, ThorInput.TELEPORT.action());
      assertSame(ThorInput.STRIKE, ThorInput.of(MouseButton.PRIMARY));
      assertSame(ThorInput.STANCE, ThorInput.of(MouseButton.SECONDARY));
      assertSame(ThorInput.HAMMER, ThorInput.of(MouseButton.MIDDLE));
      assertNull(ThorInput.of(MouseButton.GUARD));
      assertNull(ThorInput.of(HeroAction.DIAGONAL_WORLD_CUT));
      assertNull(ThorInput.of((HeroAction)null));
   }
}
