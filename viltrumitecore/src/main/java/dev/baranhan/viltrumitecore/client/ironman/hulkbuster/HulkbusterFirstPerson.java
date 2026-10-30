package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import com.mojang.blaze3d.vertex.PoseStack;

/** Mark 48 first-person arm placement: low and at the screen edges. */
public final class HulkbusterFirstPerson {
   public record Rest(float x, float y, float z, float xRot, float yRot, float zRot) {}
   private HulkbusterFirstPerson() {}

   public static Rest rest(boolean right) {
      return new Rest(right ? -5.0F : 5.0F, 2.0F, 0.0F, 0.0F, 0.0F, right ? 0.1F : -0.1F);
   }

   /** Tuned from the ZIP's CPU coverage preview (not validated in the game client). */
   public static final float OUT = 0.328F;
   public static final float DOWN = 0.383F;
   public static final float FORWARD = -0.227F;
   public static final float YAW = 3.0F;
   public static final float PITCH = 24.0F;
   public static final float ROLL = -29.5F;
   public static final float SCALE = 0.274F;

   public static void place(PoseStack stack, boolean right) {
      float side = right ? 1.0F : -1.0F;
      stack.translate(side * OUT, -DOWN, FORWARD);
      stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-side * YAW));
      stack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(PITCH));
      stack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(side * ROLL));
      stack.scale(SCALE, SCALE, SCALE);
   }
}
