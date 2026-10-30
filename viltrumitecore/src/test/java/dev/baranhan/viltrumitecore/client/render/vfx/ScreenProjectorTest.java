package dev.baranhan.viltrumitecore.client.render.vfx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Playtest: Iron Man ESP brackets hung in empty space / did not cover the entity. */
class ScreenProjectorTest {
   @Test
   void onlyTheMainViewFeedsTheGuiProjection() {
      assertTrue(ScreenProjector.captures(false, true));
      assertFalse(ScreenProjector.captures(true, true), "shader shadow pass");
      assertFalse(ScreenProjector.captures(false, false), "another camera / off-screen render");
   }

   @Test
   void boundsCoverEveryCorner() {
      float[] r = ScreenProjector.bounds(new ScreenProjector.Point(10, 40, false), new ScreenProjector.Point(30, 5, false),
         new ScreenProjector.Point(20, 25, false));
      assertArrayEquals(new float[]{10, 5, 30, 40}, r);
   }

   @Test
   void aCornerBehindTheCameraFallsBackToTheEdgeArrow() {
      assertNull(ScreenProjector.bounds(new ScreenProjector.Point(10, 40, false), new ScreenProjector.Point(30, 5, true)));
      assertNull(ScreenProjector.bounds());
   }

   /** Forge's AFTER_LEVEL pose is the bare projection: the view rotation is rebuilt from the camera. */
   @Test
   void viewRotationPutsTheLookDirectionInFront() {
      org.joml.Vector3f ahead = viewRotation(0.0F, 0.0F, 0.0F).transformPosition(new org.joml.Vector3f(0.0F, 0.0F, 5.0F));
      assertTrue(Math.abs(ahead.z + 5.0F) < 1.0E-4F && Math.abs(ahead.x) < 1.0E-4F, "yaw 0 looks at +Z");
      org.joml.Vector3f down = viewRotation(0.0F, 90.0F, 0.0F).transformPosition(new org.joml.Vector3f(0.0F, -5.0F, 0.0F));
      assertTrue(Math.abs(down.z + 5.0F) < 1.0E-4F && Math.abs(down.y) < 1.0E-4F, "pitch 90 looks down");
      org.joml.Vector3f east = viewRotation(0.0F, 0.0F, -90.0F).transformPosition(new org.joml.Vector3f(5.0F, 0.0F, 0.0F));
      assertTrue(Math.abs(east.z + 5.0F) < 1.0E-4F, "yaw -90 looks at +X");
   }

   private static org.joml.Matrix4f viewRotation(float roll, float pitch, float yaw) {
      return ScreenProjector.viewRotation(roll, pitch, yaw);
   }
}
