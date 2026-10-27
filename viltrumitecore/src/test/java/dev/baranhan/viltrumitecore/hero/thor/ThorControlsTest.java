package dev.baranhan.viltrumitecore.hero.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Context controls (spec §5): one key, a move by state; claims follow shipped moves only. */
class ThorControlsTest {
   private static ThorContext ground(HammerState hammer) {
      return ThorContext.grounded(hammer, ThorMode.NORMAL);
   }

   private static ThorContext with(HammerState hammer, ThorMode mode, boolean sneaking, boolean airborne, boolean flying, boolean hasTarget,
                                   boolean guarding, boolean grabbing) {
      return new ThorContext(hammer, mode, true, sneaking, airborne, flying, hasTarget, guarding, grabbing);
   }

   /** Every context the resolver can see (hand free or not). */
   private static List<ThorContext> everyContext() {
      List<ThorContext> all = new ArrayList<>();
      for (HammerState hammer : HammerState.values()) {
         for (ThorMode mode : ThorMode.values()) {
            for (int bits = 0; bits < 1 << 7; bits++) {
               all.add(new ThorContext(hammer, mode, (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0,
                  (bits & 32) != 0, (bits & 64) != 0));
            }
         }
      }

      return all;
   }

   @Test
   void hammerKeyDrawsHolstersOrRecalls() {
      assertSame(ThorMove.DRAW, ThorControls.resolve(ThorInput.HAMMER, ground(HammerState.HOLSTERED)));
      assertSame(ThorMove.HOLSTER, ThorControls.resolve(ThorInput.HAMMER, ground(HammerState.IN_HAND)));
      assertSame(ThorMove.RECALL, ThorControls.resolve(ThorInput.HAMMER, ground(HammerState.THROWN)));
      assertSame(ThorMove.RECALL, ThorControls.resolve(ThorInput.HAMMER, ground(HammerState.RETURNING)));
   }

   @Test
   void strikeFollowsTheContextPriority() {
      HammerState hand = HammerState.IN_HAND;
      assertSame(ThorMove.HAMMER_COMBO, ThorControls.resolve(ThorInput.STRIKE, ground(hand)));
      assertSame(ThorMove.FIST_COMBO, ThorControls.resolve(ThorInput.STRIKE, ground(HammerState.HOLSTERED)));
      assertSame(ThorMove.ELECTRIC_COMBO, ThorControls.resolve(ThorInput.STRIKE, ThorContext.grounded(hand, ThorMode.ELECTRIC)));
      assertSame(ThorMove.AIR_STRIKE, ThorControls.resolve(ThorInput.STRIKE, with(hand, ThorMode.ELECTRIC, false, true, true, false, false, false)));
      assertSame(ThorMove.HAMMER_THROW, ThorControls.resolve(ThorInput.STRIKE, with(hand, ThorMode.NORMAL, false, true, true, false, true, false)));
      assertSame(ThorMove.GRAB_SLAM, ThorControls.resolve(ThorInput.STRIKE, with(hand, ThorMode.NORMAL, false, false, false, true, true, true)));
      assertSame(ThorMove.FATALITY, ThorControls.resolve(ThorInput.STRIKE, with(hand, ThorMode.NORMAL, true, false, false, true, false, true)));
   }

   @Test
   void stanceGuardsWithTheHammerOrGrabsATarget() {
      assertSame(ThorMove.GUARD, ThorControls.resolve(ThorInput.STANCE, ground(HammerState.IN_HAND)));
      assertSame(ThorMove.GRAB, ThorControls.resolve(ThorInput.STANCE, with(HammerState.HOLSTERED, ThorMode.NORMAL, false, false, false, true, false, false)));
      assertNull(ThorControls.resolve(ThorInput.STANCE, ground(HammerState.HOLSTERED)), "empty hands and no target: RMB means nothing");
   }

   @Test
   void powerTogglesTheModeOrCallsLightning() {
      assertSame(ThorMove.ELECTRIC_MODE, ThorControls.resolve(ThorInput.POWER, ground(HammerState.IN_HAND)));
      assertSame(ThorMove.LIGHTNING_CALL, ThorControls.resolve(ThorInput.POWER, ThorContext.grounded(HammerState.IN_HAND, ThorMode.ELECTRIC)));
      assertSame(ThorMove.ELECTRIC_MODE, ThorControls.resolve(ThorInput.POWER, with(HammerState.IN_HAND, ThorMode.ELECTRIC, true, false, false, false, false, false)),
         "sneak + power leaves the electric mode");
      assertSame(ThorMove.ELECTRIC_MODE, ThorControls.resolve(ThorInput.POWER, ThorContext.grounded(HammerState.HOLSTERED, ThorMode.ELECTRIC)));
   }

   @Test
   void mobilityDependsOnAirAndTarget() {
      assertSame(ThorMove.LIGHTNING_DASH, ThorControls.resolve(ThorInput.MOBILITY, ground(HammerState.IN_HAND)));
      assertSame(ThorMove.TELEPORT_STRIKE, ThorControls.resolve(ThorInput.MOBILITY, with(HammerState.IN_HAND, ThorMode.NORMAL, false, false, false, true, false, false)));
      assertSame(ThorMove.DIVE_SLAM, ThorControls.resolve(ThorInput.MOBILITY, with(HammerState.IN_HAND, ThorMode.NORMAL, false, true, false, true, false, false)));
      assertSame(ThorMove.WRATH, ThorControls.resolve(ThorInput.ULTIMATE, ground(HammerState.HOLSTERED)));
   }

   @Test
   void everyResolutionIsADeclaredCandidate() {
      for (ThorInput input : ThorInput.values()) {
         Set<ThorMove> candidates = EnumSet.copyOf(Arrays.asList(ThorControls.candidates(input)));
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
   void everyMoveBelongsToSomeInput() {
      Set<ThorMove> reachable = EnumSet.noneOf(ThorMove.class);
      for (ThorInput input : ThorInput.values()) {
         reachable.addAll(Arrays.asList(ThorControls.candidates(input)));
      }

      assertEquals(EnumSet.allOf(ThorMove.class), reachable);
   }

   @Test
   void onlyTheHammerKeyIsLiveInStageOne() {
      assertTrue(ThorControls.anyShipped(ThorInput.HAMMER));
      for (ThorInput input : ThorInput.values()) {
         if (input != ThorInput.HAMMER) {
            assertFalse(ThorControls.anyShipped(input), input.name());
         }
      }
   }

   @Test
   void mouseClaimsOnlyShippedMoves() {
      for (ThorContext context : everyContext()) {
         assertNull(ThorControls.claimedMouse(MouseButton.PRIMARY, context), "LMB stays vanilla attack");
         assertNull(ThorControls.claimedMouse(MouseButton.SECONDARY, context), "RMB stays vanilla use");
         assertNull(ThorControls.claimedMouse(MouseButton.GUARD, context), "F stays vanilla swap");
         HeroAction middle = ThorControls.claimedMouse(MouseButton.MIDDLE, context);
         if (context.hammer().away()) {
            assertNull(middle, "recall is not shipped");
         } else {
            assertSame(HeroAction.TOOL_CYCLE, middle);
         }
      }

      assertNull(ThorControls.claimedMouse(null, ground(HammerState.HOLSTERED)));
   }

   @Test
   void inputsMapToStableHeroActions() {
      for (ThorInput input : ThorInput.values()) {
         assertSame(input, ThorInput.of(input.action()));
      }

      assertSame(ThorInput.STRIKE, ThorInput.of(MouseButton.PRIMARY));
      assertSame(ThorInput.STANCE, ThorInput.of(MouseButton.SECONDARY));
      assertSame(ThorInput.HAMMER, ThorInput.of(MouseButton.MIDDLE));
      assertNull(ThorInput.of(MouseButton.GUARD));
      assertNull(ThorInput.of(HeroAction.DIAGONAL_WORLD_CUT));
      assertNull(ThorInput.of((HeroAction)null));
   }
}
