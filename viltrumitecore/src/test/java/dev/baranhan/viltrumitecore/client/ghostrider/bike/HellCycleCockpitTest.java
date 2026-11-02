package dev.baranhan.viltrumitecore.client.ghostrider.bike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleRules;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** First-person cockpit of the Hell Cycle: where the shoulders and fists sit in the bike's frame as the bars steer. */
class HellCycleCockpitTest {
   private static final float EPS = 1.0E-4F;

   @Test
   void gripsSitOnTheBarsWhenStraight() {
      Vector3f left = HellCycleCockpit.grip(-1.0F, 0.0F);
      Vector3f right = HellCycleCockpit.grip(1.0F, 0.0F);
      assertEquals(-HellCycleCockpit.GRIP_X / 16.0F, left.x, EPS);
      assertEquals(HellCycleCockpit.GRIP_X / 16.0F, right.x, EPS);
      assertEquals(HellCycleCockpit.GRIP_Y / 16.0F, left.y, EPS);
      assertEquals(HellCycleCockpit.GRIP_Z / 16.0F, right.z, EPS);
   }

   @Test
   void steeringTurnsBothGripsRoundTheAxis() {
      float axisZ = HellCycleCockpit.AXIS_Z / 16.0F;
      float straight = (float)Math.hypot(HellCycleCockpit.GRIP_X / 16.0F, (HellCycleCockpit.GRIP_Z - HellCycleCockpit.AXIS_Z) / 16.0F);
      for (float steer = -30.0F; steer <= 30.0F; steer += 5.0F) {
         Vector3f l = HellCycleCockpit.grip(-1.0F, steer);
         Vector3f r = HellCycleCockpit.grip(1.0F, steer);
         assertEquals(straight, (float)Math.hypot(l.x, l.z - axisZ), EPS, "the bars are rigid");
         assertEquals(straight, (float)Math.hypot(r.x, r.z - axisZ), EPS, "the bars are rigid");
         assertEquals(l.y, r.y, EPS, "both grips stay at bar height");
         if (steer != 0.0F) {
            assertEquals(-Math.signum(r.z - HellCycleCockpit.GRIP_Z / 16.0F), Math.signum(l.z - HellCycleCockpit.GRIP_Z / 16.0F),
               "one grip comes back as the other goes forward");
         }
      }
   }

   @Test
   void shouldersSitBehindTheBarsBelowTheEyes() {
      float eyeY = (float)HellCycleRules.SEAT_Y + 1.62F;
      for (float side : new float[]{-1.0F, 1.0F}) {
         Vector3f s = HellCycleCockpit.shoulder(side);
         Vector3f g = HellCycleCockpit.grip(side, 0.0F);
         assertTrue(s.y < eyeY, "shoulders under the camera");
         assertTrue(s.z > g.z, "shoulders behind the grips (front is -Z)");
         assertEquals(Math.signum(side), Math.signum(s.x), "each arm on its own side");
      }
   }

   @Test
   void armsReachTheGripsWithoutVisibleStretch() {
      for (float steer = -30.0F; steer <= 30.0F; steer += 5.0F) {
         for (float side : new float[]{-1.0F, 1.0F}) {
            float d = new Vector3f(HellCycleCockpit.grip(side, steer)).sub(HellCycleCockpit.shoulder(side)).length();
            float k = HellCycleCockpit.stretch(d);
            assertTrue(k > 0.8F && k < 1.3F, "arm scale " + k + " at steer " + steer);
         }
      }
      assertEquals(0.6F, HellCycleCockpit.stretch(0.0F), EPS, "clamped short");
      assertEquals(1.5F, HellCycleCockpit.stretch(5.0F), EPS, "clamped long");
   }
}
