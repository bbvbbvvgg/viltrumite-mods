package dev.baranhan.viltrumitecore.combat.feel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Behavioural coverage of the wave1-22 feel weights and of the once-per-attacker-per-tick
 * fan-out that keeps an area hit from firing the shared shake / impact frame / ring once per body.
 * The seam wiring is not asserted from source text (project rule): the pure rules it relies on are.
 */
class FeelCallersTest {

   @Test
   void damageMapsToTheWeightLadder() {
      assertEquals(HitWeight.LIGHT, HitWeight.forDamage(0.0F));
      assertEquals(HitWeight.LIGHT, HitWeight.forDamage(4.99F));
      assertEquals(HitWeight.MEDIUM, HitWeight.forDamage(HitWeight.MEDIUM_DAMAGE));
      assertEquals(HitWeight.MEDIUM, HitWeight.forDamage(9.99F));
      assertEquals(HitWeight.HEAVY, HitWeight.forDamage(HitWeight.HEAVY_DAMAGE));
      assertEquals(HitWeight.HEAVY, HitWeight.forDamage(19.99F));
      assertEquals(HitWeight.CRUSHING, HitWeight.forDamage(HitWeight.CRUSHING_DAMAGE));
      assertEquals(HitWeight.CRUSHING, HitWeight.forDamage(200.0F));
      assertEquals(HitWeight.LIGHT, HitWeight.forDamage(-5.0F), "negative damage is LIGHT, never NaN");
   }

   @Test
   void heavierNeverStopsShorterThanLighter() {
      HitWeight[] ladder = { HitWeight.LIGHT, HitWeight.MEDIUM, HitWeight.HEAVY, HitWeight.CRUSHING, HitWeight.FINISHER };
      for (int i = 1; i < ladder.length; i++) {
         assertTrue(ladder[i].hitStopTicks >= ladder[i - 1].hitStopTicks, ladder[i - 1] + " must not stop longer than " + ladder[i]);
      }
   }

   @Test
   void sharedEffectsFanOutOncePerAttackerPerTick() {
      FeelFanout gate = new FeelFanout();
      assertTrue(gate.admit(7, 100, HitWeight.HEAVY.ordinal()), "first body of an area hit fans out");
      assertFalse(gate.admit(7, 100, HitWeight.HEAVY.ordinal()), "second body of the same hit does not repeat the shared effects");
      assertFalse(gate.admit(7, 100, HitWeight.HEAVY.ordinal()));
      assertTrue(gate.admit(7, 101, HitWeight.HEAVY.ordinal()), "the next tick is a fresh event");
      assertTrue(gate.admit(8, 100, HitWeight.HEAVY.ordinal()), "another attacker in the same tick fans out on its own");
   }

   @Test
   void aHeavierHitInTheSameTickStillFansOut() {
      FeelFanout gate = new FeelFanout();
      assertTrue(gate.admit(7, 100, HitWeight.LIGHT.ordinal()), "a light wave fans out first");
      assertTrue(gate.admit(7, 100, HitWeight.CRUSHING.ordinal()), "the crushing blow later in the tick still lands its impact frame and ring");
      assertFalse(gate.admit(7, 100, HitWeight.MEDIUM.ordinal()), "a lighter hit after a heavier one is swallowed");
   }

   @Test
   void aLighterHitAfterAHeavierOneIsSwallowed() {
      FeelFanout gate = new FeelFanout();
      assertTrue(gate.admit(7, 100, HitWeight.CRUSHING.ordinal()));
      assertFalse(gate.admit(7, 100, HitWeight.LIGHT.ordinal()), "CRUSHING then LIGHT: the light hit is swallowed");
      assertFalse(gate.admit(7, 100, HitWeight.CRUSHING.ordinal()), "an equal-weight repeat is swallowed too");
   }

   @Test
   void fanOutTableStaysBounded() {
      FeelFanout gate = new FeelFanout();
      for (int id = 0; id < FeelFanout.MAX_TRACKED * 3; id++) {
         assertTrue(gate.admit(id, id + 1, HitWeight.HEAVY.ordinal()));
      }
      assertTrue(gate.tracked() <= FeelFanout.MAX_TRACKED, "the table is dropped before it grows without bound");

      gate.clear();
      assertEquals(0, gate.tracked());
   }
}
