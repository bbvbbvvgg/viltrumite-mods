package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Camera placement of the Mark 48 first-person arms (spec item 1, 2026-10-28): the numbers both the
 * renderer (HulkbusterRenderer.onRenderHand / onRenderArm) and the screen-coverage test read.
 * <ul>
 * <li>{@link #place}: an outer camera-space offset applied before vanilla renderPlayerArm (the same
 * frame vanilla hands use), so the giant arms sit low at the screen edges.</li>
 * <li>{@link #rest}: the arm ModelPart rest pose set in RenderArmEvent (before renderHand's setupAnim).</li>
 * </ul>
 * The hand pass is drawn with a fixed 70 degree projection (GameRenderer.renderItemInHand uses
 * getFov(..., false)), so the coverage does not depend on the FOV setting.
 */
public final class HulkbusterFirstPerson {
   /** Arm ModelPart rest pose (pixels / radians), like ModelPart fields. */
   public record Rest(float x, float y, float z, float xRot, float yRot, float zRot) {
   }

   private HulkbusterFirstPerson() {
   }

   public static Rest rest(boolean right) {
      return new Rest(right ? -5.0F : 5.0F, 2.0F, 0.0F, 0.0F, 0.0F, right ? 0.1F : -0.1F);
   }

   /** Outer offset (blocks, x mirrored per arm): out and down from the vanilla hand position. */
   public static final float OUT = 0.328F;
   public static final float DOWN = 0.383F;
   public static final float FORWARD = -0.227F;
   /** The arms recline (pitch) and roll the forearms in along the lower screen edge. */
   public static final float YAW = 3.0F;
   public static final float PITCH = 24.0F;
   public static final float ROLL = -29.5F;
   /**
    * Shrink about the camera before the offset. Uniform scaling about the eye keeps the picture; with the
    * offset it sets how far the 3.2-block suit's arms reach into the view (and keeps every vertex
    * beyond the near plane).
    */
   public static final float SCALE = 0.274F;

   /** Outer camera-space placement before vanilla renderPlayerArm (blocks). */
   public static void place(PoseStack stack, boolean right) {
      float side = right ? 1.0F : -1.0F;
      stack.translate(side * OUT, -DOWN, FORWARD);
      stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-side * YAW));
      stack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(PITCH));
      stack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(side * ROLL));
      stack.scale(SCALE, SCALE, SCALE);
   }
}
