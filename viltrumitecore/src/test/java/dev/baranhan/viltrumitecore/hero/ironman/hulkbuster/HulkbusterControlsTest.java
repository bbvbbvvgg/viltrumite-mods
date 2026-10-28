package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterControls.Context;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterControls.Decision;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterControls.Input;
import org.junit.jupiter.api.Test;

/** The single input resolver (rework spec §1.3.1, §6): context → move, refusals always carry a HUD key. */
class HulkbusterControlsTest {
   /** Free, grounded, full energy, undamaged, nothing on cooldown. */
   static Context free() {
      return new Context(false, 0, false, false, false, 0, true, 1.0F, false, false, false);
   }

   static Context holding(int punches, boolean slam) {
      return new Context(true, punches, false, false, false, 0, true, 1.0F, false, slam, false);
   }

   @Test
   void lmbIsComboOrDashStrikeOrChargeOrPunchInTheHold() {
      assertSame(HulkMove.COMBO, HulkbusterControls.resolve(Input.LMB, free()).move());
      assertSame(HulkMove.CHARGED, HulkbusterControls.resolve(Input.LMB_HOLD, free()).move());
      Context dashing = new Context(false, 0, false, false, true, 0, true, 1.0F, false, false, false);
      assertSame(HulkMove.DASH_STRIKE, HulkbusterControls.resolve(Input.LMB, dashing).move());
      for (int punches = 0; punches < HulkbusterRules.GRAB_PUNCHES; punches++) {
         Decision d = HulkbusterControls.resolve(Input.LMB, holding(punches, false));
         assertSame(HulkMove.GRAB_PUNCH, d.move());
         assertEquals(punches, d.step(), "escalating face punches start at their own step");
      }

      assertSame(HulkMove.GRAB_FINISH, HulkbusterControls.resolve(Input.LMB, holding(HulkbusterRules.GRAB_PUNCHES, false)).move());
      // In the hold the held LMB is still the next punch, never a charge.
      assertSame(HulkMove.GRAB_PUNCH, HulkbusterControls.resolve(Input.LMB_HOLD, holding(1, false)).move());
   }

   @Test
   void lmbInTheDiveIsSilent() {
      Context diving = new Context(false, 0, true, true, false, 0, true, 1.0F, false, false, false);
      Decision d = HulkbusterControls.resolve(Input.LMB, diving);
      assertTrue(d.silent());
      assertFalse(d.accepted());
      assertTrue(HulkbusterControls.resolve(Input.RMB, diving).silent());
   }

   @Test
   void rmbGrabsThrowsOrSlams() {
      assertSame(HulkMove.GRAB_REACH, HulkbusterControls.resolve(Input.RMB, free()).move());
      assertSame(HulkMove.GRAB_THROW, HulkbusterControls.resolve(Input.RMB, holding(2, false)).move());
      assertSame(HulkMove.GRAB_SLAM, HulkbusterControls.resolve(Input.RMB, holding(2, true)).move());
      Context cooling = new Context(false, 0, false, false, false, 40, true, 1.0F, false, false, false);
      Decision d = HulkbusterControls.resolve(Input.RMB, cooling);
      assertNull(d.move());
      assertEquals(HulkbusterControls.COOLDOWN, d.refusal());
   }

   @Test
   void guardAndAbilitiesRefuseWithFeedback() {
      assertSame(HulkMove.BLOCK, HulkbusterControls.resolve(Input.GUARD, free()).move());
      assertEquals(HulkbusterControls.HANDS_BUSY, HulkbusterControls.resolve(Input.GUARD, holding(0, false)).refusal());
      Context air = new Context(false, 0, false, true, false, 0, true, 1.0F, false, false, false);
      assertEquals(HulkbusterControls.GROUND_ONLY, HulkbusterControls.resolve(Input.GUARD, air).refusal());
      assertSame(HulkMove.HEAVY_REPULSORS, HulkbusterControls.resolve(Input.SLOT_1, free()).move());
      assertSame(HulkMove.SEISMIC_RIFT, HulkbusterControls.resolve(Input.SLOT_2, free()).move());
      assertSame(HulkMove.VERONICA_CAGE, HulkbusterControls.resolve(Input.SLOT_3, free()).move());
      assertSame(HulkMove.JET_RAM, HulkbusterControls.resolve(Input.ULTIMATE, free()).move());
      assertEquals(HulkbusterControls.GROUND_ONLY, HulkbusterControls.resolve(Input.SLOT_2, air).refusal());
      assertEquals(HulkbusterControls.GROUND_ONLY, HulkbusterControls.resolve(Input.ULTIMATE, air).refusal());
      Context empty = new Context(false, 0, false, false, false, 0, false, 1.0F, false, false, false);
      assertEquals(HulkbusterControls.NO_ENERGY, HulkbusterControls.resolve(Input.SLOT_1, empty).refusal());
      assertEquals(HulkbusterControls.HANDS_BUSY, HulkbusterControls.resolve(Input.SLOT_1, holding(0, false)).refusal());
      Context staggered = new Context(false, 0, false, false, false, 0, true, 1.0F, false, false, true);
      for (Input input : Input.values()) {
         assertEquals(HulkbusterControls.BUSY, HulkbusterControls.resolve(input, staggered).refusal(), input.name());
      }
   }

   @Test
   void spareArmOnlyWhenDamagedAndOnce() {
      assertEquals(HulkbusterControls.NOT_DAMAGED, HulkbusterControls.resolve(Input.SLOT_4, free()).refusal());
      Context hurt = new Context(false, 0, false, false, false, 0, true, 0.5F, false, false, false);
      assertSame(HulkMove.SPARE_ARM, HulkbusterControls.resolve(Input.SLOT_4, hurt).move());
      Context used = new Context(false, 0, false, false, false, 0, true, 0.5F, true, false, false);
      assertEquals(HulkbusterControls.SPARE_USED, HulkbusterControls.resolve(Input.SLOT_4, used).refusal());
   }

   @Test
   void everyRefusalHasAKeyAndEveryAcceptHasNone() {
      Context[] contexts = {free(), holding(0, false), holding(4, true),
         new Context(false, 0, true, true, false, 0, true, 1.0F, false, false, false),
         new Context(false, 0, false, true, false, 99, false, 0.2F, true, false, false)};
      for (Context c : contexts) {
         for (Input input : Input.values()) {
            Decision d = HulkbusterControls.resolve(input, c);
            assertTrue(d.accepted() ^ (d.refusal() != null) || d.silent(), input + " " + c);
            if (d.accepted()) {
               assertNull(d.refusal());
            }
         }
      }
   }

   @Test
   void panelSlotsMapToInputs() {
      assertSame(Input.SLOT_1, HulkbusterControls.slot(0));
      assertSame(Input.SLOT_4, HulkbusterControls.slot(3));
      assertSame(Input.ULTIMATE, HulkbusterControls.slot(4));
      assertNull(HulkbusterControls.slot(5));
   }
}
