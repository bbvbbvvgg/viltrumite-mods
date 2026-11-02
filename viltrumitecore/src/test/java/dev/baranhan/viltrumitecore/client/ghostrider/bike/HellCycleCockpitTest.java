package dev.baranhan.viltrumitecore.client.ghostrider.bike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** First-person grips of the Hell Cycle: where the fists sit on the bars as they steer. */
class HellCycleCockpitTest {
   private static final float EPS = 1.0E-4F;

   @Test
   void gripsSitOnTheBarsWhenStraight() {
      Matrix4f bike = new Matrix4f();
      Quaternionf view = new Quaternionf();
      Vector3f left = HellCycleCockpit.grip(-1.0F, 0.0F, bike, view);
      Vector3f right = HellCycleCockpit.grip(1.0F, 0.0F, bike, view);
      assertEquals(-HellCycleCockpit.GRIP_X / 16.0F, left.x, EPS);
      assertEquals(HellCycleCockpit.GRIP_X / 16.0F, right.x, EPS);
      assertEquals(HellCycleCockpit.GRIP_Y / 16.0F, left.y, EPS);
      assertEquals(HellCycleCockpit.GRIP_Z / 16.0F, right.z, EPS);
   }

   @Test
   void steeringTurnsBothGripsRoundTheAxis() {
      Matrix4f bike = new Matrix4f();
      Quaternionf view = new Quaternionf();
      float axisZ = HellCycleCockpit.AXIS_Z / 16.0F;
      for (float steer = -30.0F; steer <= 30.0F; steer += 5.0F) {
         Vector3f l = HellCycleCockpit.grip(-1.0F, steer, bike, view);
         Vector3f r = HellCycleCockpit.grip(1.0F, steer, bike, view);
         float straight = (float)Math.hypot(HellCycleCockpit.GRIP_X / 16.0F, (HellCycleCockpit.GRIP_Z - HellCycleCockpit.AXIS_Z) / 16.0F);
         assertEquals(straight, (float)Math.hypot(l.x, l.z - axisZ), EPS, "the bars are rigid");
         assertEquals(straight, (float)Math.hypot(r.x, r.z - axisZ), EPS, "the bars are rigid");
         assertEquals(l.y, r.y, EPS, "both grips stay at bar height");
         if (steer != 0.0F) {
            assertTrue(Math.signum(l.z - HellCycleCockpit.GRIP_Z / 16.0F) == -Math.signum(r.z - HellCycleCockpit.GRIP_Z / 16.0F),
               "one grip comes back as the other goes forward");
         }
      }
   }

   @Test
   void gripsFollowTheBikeMatrix() {
      Matrix4f bike = new Matrix4f().translate(1.0F, 2.0F, 3.0F);
      Vector3f g = HellCycleCockpit.grip(1.0F, 0.0F, bike, new Quaternionf());
      assertEquals(1.0F + HellCycleCockpit.GRIP_X / 16.0F, g.x, EPS);
      assertEquals(2.0F + HellCycleCockpit.GRIP_Y / 16.0F, g.y, EPS);
      assertEquals(3.0F + HellCycleCockpit.GRIP_Z / 16.0F, g.z, EPS);
   }
}
