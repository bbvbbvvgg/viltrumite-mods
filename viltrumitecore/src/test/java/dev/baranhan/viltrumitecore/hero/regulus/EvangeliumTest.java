package dev.baranhan.viltrumitecore.hero.regulus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The Evangelium ritual is a 60t held-book channel cancelable by early release,
 * not movement or damage. It grants 900t of madness paid at 0.6 HP per second,
 * and the 1800t cooldown starts when madness ends.
 */
class EvangeliumTest {

   @Test
   void incompleteRitualCannotGrantMadness() {
      RegulusState state = new RegulusState();
      state.ritualTicks = 59;
      assertFalse(Evangelium.beginMadnessState(state));
      assertEquals(0, state.madnessTicksLeft);
      assertEquals(59, state.ritualTicks);
   }

   @Test
   void ritualOccupiesSixtyTicks() {
      RegulusState state = new RegulusState();
      state.ritualTicks = RegulusRules.RITUAL_TICKS - 1;
      assertFalse(Evangelium.ritualFinished(state));

      state.ritualTicks = RegulusRules.RITUAL_TICKS;
      assertTrue(Evangelium.ritualFinished(state));
   }

   @Test
   void ritualNeedsAnIdleNonMadHero() {
      RegulusState state = new RegulusState();
      assertTrue(Evangelium.canBegin(state));

      state.madnessTicksLeft = 10;
      assertFalse(Evangelium.canBegin(state), "no ritual re-entry during madness");
      state.madnessTicksLeft = 0;

      state.cooldowns.put(RegulusAbilities.EVANGELIUM, 5);
      assertFalse(Evangelium.canBegin(state), "a running cooldown blocks re-entry");
      state.cooldowns.clear();

      state.ritualTicks = RegulusRules.RITUAL_TICKS;
      assertFalse(Evangelium.canBegin(state), "ritual already running");
      state.ritualTicks = -1;

      state.beginAction(RegulusHero.ACTION_DEBRIS, 10, 5, 5);
      assertFalse(Evangelium.canBegin(state), "a busy hero cannot ritual");
      state.clearAction();

      state.channelTargetId = UUID.randomUUID();
      assertFalse(Evangelium.canBegin(state), "an open Mania channel blocks the ritual");
      state.channelTargetId = null;

      assertFalse(Evangelium.canBegin(state, true), "an anchored Regulus cannot ritual (spec 8.2)");
   }

   @Test
   void onlyReleaseCancelsBeforeCompletion() {
      RegulusState state = new RegulusState();
      state.ritualTicks = 59;
      assertTrue(Evangelium.interruptState(state));
      assertFalse(Evangelium.beginMadnessState(state));
      assertEquals(0, state.madnessTicksLeft);
   }

   @Test
   void interruptChargedFourHundredSampledAtInterrupt() {
      RegulusState state = new RegulusState();
      state.ritualTicks = 20;

      assertTrue(Evangelium.interruptState(state));
      assertEquals(-1, state.ritualTicks);
      assertEquals(RegulusRules.RITUAL_CANCEL_COOLDOWN, state.cooldownOf(RegulusAbilities.EVANGELIUM));

      state.cooldowns.clear();
      assertFalse(Evangelium.interruptState(state), "interrupt without a running ritual is a no-op");
      assertEquals(0, state.cooldownOf(RegulusAbilities.EVANGELIUM));
   }

   @Test
   void interruptCooldownScalesWithHearts() {
      RegulusState state = new RegulusState();
      for (int i = 0; i < 10; i++) {
         state.carriers.add(UUID.randomUUID());
      }
      state.ritualTicks = 5;

      Evangelium.interruptState(state);
      assertEquals(
         RegulusRules.cooldown(RegulusRules.RITUAL_CANCEL_COOLDOWN, 10),
         state.cooldownOf(RegulusAbilities.EVANGELIUM),
         "hearts are sampled at the interrupt, not at the press"
      );
   }

   @Test
   void completionGrantsNineHundredMadnessTicks() {
      RegulusState state = new RegulusState();
      state.ritualTicks = RegulusRules.RITUAL_TICKS;

      assertTrue(Evangelium.beginMadnessState(state));
      assertEquals(RegulusRules.MADNESS_TICKS, state.madnessTicksLeft);
      assertEquals(-1, state.ritualTicks);
      assertFalse(Evangelium.beginMadnessState(state), "idempotent once the ritual is over");
   }

   @Test
   void bloodPriceTicksEverySecondOfMadness() {
      int hits = 0;
      for (int elapsed = 1; elapsed <= RegulusRules.MADNESS_TICKS; elapsed++) {
         if (Evangelium.bloodPriceDue(elapsed)) {
            hits++;
         }
      }

      assertEquals(45, hits, "0.6 HP each second over 45s = 27 HP total");
      assertFalse(Evangelium.bloodPriceDue(0), "no price on the starting tick");
      assertTrue(Evangelium.bloodPriceDue(20));
      assertTrue(Evangelium.bloodPriceDue(RegulusRules.MADNESS_TICKS), "the last second still costs blood");
   }

   @Test
   void madnessEndChargesEighteenHundred() {
      RegulusState state = new RegulusState();
      state.madnessTicksLeft = 1;

      assertTrue(Evangelium.endMadnessState(state));
      assertEquals(0, state.madnessTicksLeft);
      assertEquals(RegulusRules.EVANGELIUM_COOLDOWN, state.cooldownOf(RegulusAbilities.EVANGELIUM));
      assertFalse(Evangelium.endMadnessState(state), "already over");

      RegulusState rich = new RegulusState();
      for (int i = 0; i < RegulusRules.MAX_HEARTS; i++) {
         rich.carriers.add(UUID.randomUUID());
      }
      rich.madnessTicksLeft = 1;
      Evangelium.endMadnessState(rich);
      assertEquals(
         RegulusRules.cooldown(RegulusRules.EVANGELIUM_COOLDOWN, RegulusRules.MAX_HEARTS),
         rich.cooldownOf(RegulusAbilities.EVANGELIUM),
         "hearts sampled when madness ends"
      );
   }

}
