package dev.baranhan.viltrumitecore.t1000;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Timings, scheduler clamps, the ambush rule, the leap and the wound placement. */
class T1000RulesTest {
   @Test
   void armMorphs() {
      assertEquals(1, T1000Rules.morphTicks(T1000Arm.BLADE, T1000Arm.BLADE, true));
      assertEquals(T1000Rules.MORPH_BACK_TICKS, T1000Rules.morphTicks(T1000Arm.BLADE, T1000Arm.HAND, false));
      assertTrue(T1000Rules.morphTicks(T1000Arm.HAND, T1000Arm.BLADE, true) > T1000Rules.morphTicks(T1000Arm.HAND, T1000Arm.BLADE, false));
      assertEquals(0.0F, T1000Rules.morph(0.0F, 10));
      assertEquals(1.0F, T1000Rules.morph(10.0F, 10));
      assertEquals(1.0F, T1000Rules.woundOpen(0.0F));
      assertEquals(0.0F, T1000Rules.woundOpen(T1000Rules.WOUND_CLOSE_TICKS));
   }

   @Test
   void packedArmsRoundTrip() {
      for (T1000Arm r : T1000Arm.values()) {
         for (T1000Arm l : T1000Arm.values()) {
            int p = T1000Arm.pack(r, l, l, r);
            assertEquals(r, T1000Arm.right(p));
            assertEquals(l, T1000Arm.left(p));
            assertEquals(l, T1000Arm.rightBefore(p));
         }
      }
   }

   @Test
   void schedulerSettingsAreClamped() {
      assertEquals(Math.round(T1000Rules.MIN_CHECK_SECONDS * 20.0), T1000Rules.checkIntervalTicks(0.0));
      assertEquals(Math.round(T1000Rules.MAX_CHECK_SECONDS * 20.0), T1000Rules.checkIntervalTicks(1.0E12));
      assertEquals(6000L, T1000Rules.checkIntervalTicks(Double.NaN));
      assertEquals(0.0, T1000Rules.chance(Double.NaN));
      assertEquals(1.0, T1000Rules.chance(250.0));
      assertEquals(0.05, T1000Rules.chance(5.0), 1.0E-9);
      assertEquals(0L, T1000Rules.respawnDelayTicks(-5.0));
      assertEquals(120L * 1200L, T1000Rules.respawnDelayTicks(Double.POSITIVE_INFINITY));
      assertEquals(1, T1000Rules.maxActive(0));
      assertEquals(T1000Rules.MAX_ACTIVE_LIMIT, T1000Rules.maxActive(1000));
   }

   @Test
   void angles() {
      assertEquals(0.0, T1000Rules.angleDeg(0, 1, 0, 5), 1.0E-6);
      assertEquals(180.0, T1000Rules.angleDeg(0, 1, 0, -5), 1.0E-6);
      assertEquals(90.0, T1000Rules.angleDeg(1, 0, 0, 2), 1.0E-6);
      assertEquals(180.0, T1000Rules.angleDeg(0, 0, 1, 1), 1.0E-6);
   }

   @Test
   void ambushNeedsTheBackTurnedAndAReason() {
      assertFalse(T1000Rules.opportunity(T1000Rules.AMBUSH_RANGE + 1.0, 180.0, true, true, 0.1F));
      assertFalse(T1000Rules.opportunity(5.0, 10.0, true, true, 0.1F));
      assertTrue(T1000Rules.opportunity(5.0, 170.0, true, false, 1.0F));
      assertFalse(T1000Rules.opportunity(5.0, 170.0, false, false, 1.0F));
      assertTrue(T1000Rules.opportunity(3.0, 170.0, false, false, 1.0F));
   }

   @Test
   void theLeapLandsNearTheTarget() {
      double[][] targets = {{4, 0, 0}, {0, 1, 6}, {-3, -1, 3}, {5, 2, 5}};
      for (double[] t : targets) {
         double[] v = T1000Rules.leap(t[0], t[1], t[2]);
         // mob ballistics: gravity 0.08, drag 0.98 vertical, 0.91 horizontal in air
         double x = 0, y = 0, z = 0;
         double vx = v[0], vy = v[1], vz = v[2];
         double best = Double.MAX_VALUE;
         for (int i = 0; i < 80; i++) {
            x += vx;
            y += vy;
            z += vz;
            vy = (vy - 0.08) * 0.98;
            vx *= 0.91;
            vz *= 0.91;
            if (vy < 0 && y <= t[1] + 0.5) {
               best = Math.min(best, Math.hypot(x - t[0], z - t[2]));
            }
            if (y < t[1] - 3) {
               break;
            }
         }
         assertTrue(best < 2.5, "leap to " + t[0] + "," + t[1] + "," + t[2] + " misses by " + best);
         assertTrue(v[1] > 0 && v[1] <= 1.05);
      }
   }

   @Test
   void woundsSitOnTheSurfaceOfTheHitPart() {
      assertEquals(T1000Entity.PART_HEAD, T1000WoundMath.partAt(0, -4));
      assertEquals(T1000Entity.PART_BODY, T1000WoundMath.partAt(0, 6));
      assertEquals(T1000Entity.PART_RIGHT_ARM, T1000WoundMath.partAt(-6, 6));
      assertEquals(T1000Entity.PART_LEFT_ARM, T1000WoundMath.partAt(6, 6));
      assertEquals(T1000Entity.PART_RIGHT_LEG, T1000WoundMath.partAt(-1, 18));
      assertEquals(T1000Entity.PART_LEFT_LEG, T1000WoundMath.partAt(1, 18));
      // a shot from the front at the chest lands on the front face of the torso
      T1000Entity.Wound w = T1000WoundMath.onSurface(0.0, 5.0F, -10.0, 1.0F, 0, 0, 0.5F);
      assertEquals(T1000Entity.PART_BODY, w.part());
      assertEquals(-2.0F, w.z(), 1.0E-4F);
      // from behind: the back face; from the right: the right arm's outer face
      assertEquals(2.0F, T1000WoundMath.onSurface(0.0, 5.0F, 10.0, 1.0F, 0, 0, 0.5F).z(), 1.0E-4F);
      T1000Entity.Wound side = T1000WoundMath.onSurface(-7.9, 6.0F, 0.0, 1.0F, 0, 0, 0.5F);
      assertEquals(T1000Entity.PART_RIGHT_ARM, side.part());
      assertEquals(-8.0F, side.x(), 1.0E-4F);
      // never outside the model, whatever the jitter
      for (float j = 0.0F; j <= 1.0F; j += 0.25F) {
         T1000Entity.Wound h = T1000WoundMath.onSurface(0.0, -20.0F, -9.0, 1.0F, 0, 0, j);
         assertTrue(h.y() >= -8.0F && h.y() <= 0.0F, "head wound y " + h.y());
         T1000Entity.Wound f = T1000WoundMath.onSurface(1.0, 40.0F, -9.0, 1.0F, 0, 0, j);
         assertTrue(f.y() <= 24.0F && f.y() >= 12.0F, "leg wound y " + f.y());
      }
   }

   @Test
   void formsKnowTheirMetal() {
      assertEquals(0.0F, T1000Form.HUMAN.metal(100.0F));
      assertEquals(1.0F, T1000Form.LIQUID.metal(0.0F));
      assertTrue(T1000Form.REVEALING.metal(T1000Rules.REVEAL_TICKS * 0.5F) > 0.0F);
      assertTrue(T1000Form.REVEALING.metal(T1000Rules.REVEAL_TICKS * 0.5F) < 1.0F);
      assertEquals(1.0F, T1000Form.PUDDLE.melt(0.0F));
      assertEquals(0.0F, T1000Form.LIQUID.melt(0.0F));
      assertTrue(T1000Form.PUDDLE.grounded());
      assertFalse(T1000Form.HUMAN.revealed());
      assertTrue(T1000Form.LIQUID.revealed());
      assertEquals(T1000Form.HUMAN, T1000Form.of(99));
      assertEquals(T1000Action.NONE, T1000Action.of(-1));
      for (T1000Action a : T1000Action.values()) {
         assertTrue(a.hitTick <= a.ticks, a + " hits after it ends");
      }
   }
}
