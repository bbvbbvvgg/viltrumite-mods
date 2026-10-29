package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.combat.InterruptReason;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** HulkMove rows, the strike table and the interrupt rules (rework spec §6–7, Thor rules §1.3.2–4). */
class HulkMoveTest {
   @Test
   void wireIdsAreUniqueAndRoundTrip() {
      Set<Integer> ids = new HashSet<>();
      for (HulkMove move : HulkMove.values()) {
         assertTrue(ids.add(move.wireId()), move.name());
         assertSame(move, HulkMove.byWire(move.wire()));
         assertTrue(move.wire() >= HulkMove.WIRE_OFFSET);
      }

      assertEquals(null, HulkMove.byWire(3));
   }

   @Test
   void comboStepsMatchTheSpecTimings() {
      // Spec §6.1 / §12: 24 / 26 / 36 ticks.
      assertEquals(24, HulkMove.COMBO.length(0));
      assertEquals(26, HulkMove.COMBO.length(1));
      assertEquals(36, HulkMove.COMBO.length(2));
      // Spec §12: grab reach 16, miss 24, finisher 40.
      assertEquals(16, HulkMove.GRAB_REACH.length(0));
      assertEquals(24, HulkMove.GRAB_MISS.length(0));
      assertEquals(40, HulkMove.GRAB_FINISH.length(0));
      assertEquals(HulkbusterRules.GRAB_HOLD_MAX, HulkMove.GRAB_HOLD.length(0));
      assertEquals(18, HulkMove.GRAB_PUNCH.length(0), "the first face punch is 18 t");
      assertTrue(HulkMove.GRAB_PUNCH.length(3) < HulkMove.GRAB_PUNCH.length(0), "each punch is a little faster");
   }

   @Test
   void everyStrikeContactsAtTheFirstMainTick() {
      for (HulkStrikes.Strike strike : HulkStrikes.ALL) {
         HulkMove move = strike.move();
         assertTrue(strike.step() < move.maxSteps(), move + " step " + strike.step());
         assertEquals(StagedTimeline.phaseStart(move, MovePhase.MAIN, strike.step()), strike.contactTick(), move + " " + strike.step());
         assertEquals(move.ticks(MovePhase.INTRO, strike.step()), move.contactTick(strike.step()));
         assertTrue(move.ticks(MovePhase.MAIN, strike.step()) > 0, "a contact needs a MAIN phase: " + move);
         assertTrue(strike.damage() > 0.0F);
         assertSame(strike, HulkStrikes.of(move, strike.step()));
      }

      // Every combo and face-punch step has its own row.
      for (int step = 0; step < HulkMove.COMBO.maxSteps(); step++) {
         assertNotNull(HulkStrikes.of(HulkMove.COMBO, step));
      }

      for (int step = 0; step < HulkMove.GRAB_PUNCH.maxSteps(); step++) {
         assertNotNull(HulkStrikes.of(HulkMove.GRAB_PUNCH, step));
      }
   }

   @Test
   void lifecycleAlwaysInterruptsAndHyperArmourIsWhereTheSpecSays() {
      for (HulkMove move : HulkMove.values()) {
         for (MovePhase phase : MovePhase.values()) {
            assertFalse(move.resists(InterruptReason.LIFECYCLE, phase), move + " " + phase);
         }
      }

      assertTrue(HulkMove.COMBO.resistsHit(MovePhase.MAIN, 2), "the double hammer's MAIN");
      assertFalse(HulkMove.COMBO.resistsHit(MovePhase.MAIN, 0));
      assertFalse(HulkMove.COMBO.resistsHit(MovePhase.INTRO, 2));
      assertTrue(HulkMove.JET_RAM.resists(InterruptReason.HIT, MovePhase.INTRO));
      assertTrue(HulkMove.JET_RAM.resists(InterruptReason.HIT, MovePhase.MAIN));
      assertFalse(HulkMove.JET_RAM.resists(InterruptReason.HIT, MovePhase.FINISH), "the tail can be punished");
      assertTrue(HulkMove.GRAB_FINISH.resists(InterruptReason.HIT, MovePhase.INTRO));
      assertFalse(HulkMove.GRAB_FINISH.resists(InterruptReason.CONTROL, MovePhase.INTRO));
      assertFalse(HulkMove.GRAB_REACH.resists(InterruptReason.HIT, MovePhase.MAIN));
      assertTrue(HulkMove.HERO_LANDING.resists(InterruptReason.HIT, MovePhase.FINISH));
   }

   @Test
   void prioritiesRunFromUtilityToUltimate() {
      for (HulkMove move : HulkMove.values()) {
         assertTrue(move.priority() >= 1 && move.priority() <= 6, move.name());
      }

      assertEquals(6, HulkMove.JET_RAM.priority());
      assertTrue(HulkMove.BLOCK.priority() < HulkMove.GRAB_PUNCH.priority());
   }

   @Test
   void timelineAdmitsComboBufferAndUltimate() {
      StagedTimeline<HulkMove> timeline = new StagedTimeline<>();
      assertSame(StagedTimeline.Admission.START, timeline.admit(HulkMove.COMBO, -1));
      timeline.requestAt(HulkMove.COMBO, -1, 0);
      assertTrue(timeline.is(HulkMove.COMBO));
      // Before the combo's cancel window the next press is buffered (one press).
      assertSame(StagedTimeline.Admission.QUEUE, timeline.admit(HulkMove.COMBO, -1));
      // The ultimate outranks a combo step.
      assertTrue(timeline.admit(HulkMove.JET_RAM, -1).started());
   }

   @Test
   void hitboxIsOnePointThreeByThreePointTwo() {
      assertEquals(1.3F, 0.6F * HulkbusterRules.WIDTH_SCALE, 1.0E-5F);
      assertEquals(3.2F, 1.8F * HulkbusterRules.HEIGHT_SCALE, 1.0E-5F);
      assertEquals(HulkbusterRules.HEIGHT_SCALE, HulkbusterLayer.SCALE, 1.0E-6F);
      net.minecraft.world.entity.EntityDimensions box = net.minecraft.world.entity.EntityDimensions.scalable(0.6F, 1.8F)
         .scale(HulkbusterRules.WIDTH_SCALE, HulkbusterRules.HEIGHT_SCALE);
      assertEquals(1.3F, box.width, 1.0E-4F);
      assertEquals(3.2F, box.height, 1.0E-4F);
      // Visual 3.4 blocks: 68 Sind px at the model scale.
      assertEquals(3.4F, 68.0F * HulkbusterRules.MODEL_SCALE / 16.0F, 1.0E-4F);
   }
}
