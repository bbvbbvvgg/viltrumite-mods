package dev.baranhan.viltrumitecore.client.reinhard;

import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;

/**
 * Held item transform relative to a geo arm bone's pivot frame. The geo frame (y up, x mirrored)
 * turned by rotZ(180) is vanilla's arm frame; then vanilla ItemInHandLayer's rotX(-90), rotY(180),
 * translate(±1/16, 0.125, -0.625): the item sits 10 px below the arm pivot.
 */
public final class HeldItemAnchor {
   private HeldItemAnchor() {
   }

   public static Matrix4f local(HumanoidArm arm) {
      float side = arm == HumanoidArm.LEFT ? -1.0F : 1.0F;
      return new Matrix4f().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(-90.0)).rotateY((float) Math.PI).translate(side / 16.0F, 0.125F, -0.625F);
   }
}
