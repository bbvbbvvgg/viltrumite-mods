package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroDebris;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.FistCombo;
import org.junit.jupiter.api.Test;

/** Spec §6.2 Fist Combo (stage 3 Task 5). */
class FistComboTest {
   /** One server tick in router order: the ability, then the timeline. */
   private static void tick(ReinhardState state) {
      FistCombo.tickChain(state);
      state.action.tick();
   }

   private static void finishStep(ReinhardState state) {
      while (state.action.running()) {
         tick(state);
      }
   }

   @Test
   void damageSequence10_10_16() {
      assertArrayEquals(new float[]{10.0F, 10.0F, 16.0F}, ReinhardRules.COMBO_DAMAGE);
      assertArrayEquals(new int[]{4, 4, 6}, ReinhardRules.COMBO_HIT_TICKS);
      assertArrayEquals(new int[]{10, 10, 14}, ReinhardRules.COMBO_STEP_TICKS);
      assertEquals(3.5, ReinhardRules.COMBO_REACH);
      ReinhardState state = new ReinhardState();
      for (int step = 0; step < 3; step++) {
         assertEquals(step, FistCombo.press(state), "each press = the next hit");
         assertEquals(step, state.action.phase(), "the phase picks the clip");
         assertEquals(ReinhardRules.COMBO_STEP_TICKS[step], state.action.length());
         assertEquals(ReinhardRules.COMBO_DAMAGE[step], FistCombo.damage(step));
         finishStep(state);
      }
   }

   @Test
   void chainWindowTwenty() {
      assertEquals(20, ReinhardRules.COMBO_CHAIN_WINDOW);
      assertEquals(1, FistCombo.next(0, 20));
      assertEquals(2, FistCombo.next(1, 20));
      ReinhardState state = new ReinhardState();
      FistCombo.press(state);
      finishStep(state);
      for (int i = 0; i < ReinhardRules.COMBO_CHAIN_WINDOW; i++) {
         tick(state);
      }

      assertEquals(1, FistCombo.press(state), "a press on the 20th idle tick still continues");
   }

   @Test
   void lateRestarts() {
      assertEquals(0, FistCombo.next(0, 21));
      assertEquals(0, FistCombo.next(2, 0), "after the third hit the chain starts over");
      assertEquals(0, FistCombo.next(-1, 0));
      ReinhardState state = new ReinhardState();
      FistCombo.press(state);
      finishStep(state);
      for (int i = 0; i < ReinhardRules.COMBO_CHAIN_WINDOW + 1; i++) {
         tick(state);
      }

      assertEquals(-1, state.comboStep, "the chain dropped");
      assertEquals(0, FistCombo.press(state));
   }

   @Test
   void waveOnlyOnThird() {
      assertFalse(FistCombo.wave(0));
      assertFalse(FistCombo.wave(1));
      assertTrue(FistCombo.wave(2));
      assertEquals(4.0, ReinhardRules.COMBO_WAVE_RADIUS);
      assertEquals(8.0F, ReinhardRules.COMBO_WAVE_DAMAGE);
      // Knight: moderate destruction (top layer, few flying blocks); Restrained would break nothing.
      HeroDebris.Eruption spec = FistCombo.eruption(DestructionLevel.of(ReinhardMode.KNIGHT));
      assertNotNull(spec);
      assertTrue(spec.depth() <= DestructionLevel.MODERATE_MAX_DEPTH);
      assertTrue(spec.radius() <= ReinhardRules.COMBO_WAVE_RADIUS);
      assertEquals(null, FistCombo.eruption(DestructionLevel.NONE));
   }

   @Test
   void resetOnModeChange() {
      ReinhardState state = new ReinhardState();
      FistCombo.press(state);
      finishStep(state);
      FistCombo.press(state);
      assertEquals(1, state.comboStep);
      // The mode switch goes through the router: every action ends, the chain resets, no pose stays.
      ReinhardActions.onModeChange(null, state);
      assertEquals(-1, state.comboStep);
      assertFalse(state.comboQueued);
      assertFalse(state.action.running(), "no stuck pose");
      assertEquals(0, FistCombo.press(state));
   }

   @Test
   void anotherActionBreaksTheChain() {
      // A hit-stop comes from another action (Sky Kick, throw): its timeline ends the chain.
      ReinhardState state = new ReinhardState();
      FistCombo.press(state);
      finishStep(state);
      state.action.start(ReinhardAbility.SKY_KICK, 10, 5);
      tick(state);
      assertEquals(-1, state.comboStep);
   }

   @Test
   void pressDuringAHitIsQueued() {
      ReinhardState state = new ReinhardState();
      FistCombo.press(state);
      assertTrue(FistCombo.claims(state), "a press during the hit belongs to the combo (no refusal spam)");
      FistCombo.queue(state);
      int ticks = 0;
      while (state.comboStep == 0 && ticks < 50) {
         tick(state);
         ticks++;
      }

      assertEquals(1, state.comboStep, "the queued press starts hit 2 when hit 1 ends");
      assertTrue(state.action.is(ReinhardAbility.FIST_COMBO));
      assertFalse(state.comboQueued);
   }
}
