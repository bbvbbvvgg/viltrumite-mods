package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class HeldItemAnchorTest {
   @Test
   void rightHandOffset() {
      // Vanilla ItemInHandLayer after translateToHand (arm pivot frame, model space y down):
      // rotX(-90), rotY(180), translate(1/16, 0.125, -0.625) for the right hand.
      Matrix4f vanilla = new Matrix4f().rotateX((float) Math.toRadians(-90.0)).rotateY((float) Math.PI).translate(1.0F / 16.0F, 0.125F, -0.625F);
      Vector3f item = vanilla.transformPosition(new Vector3f());
      assertEquals(-1.0F / 16.0F, item.x, 1.0E-6F);
      assertEquals(10.0F / 16.0F, item.y, 1.0E-6F, "10 px below the arm pivot (y down)");
      assertEquals(-2.0F / 16.0F, item.z, 1.0E-6F);

      // The geo arm bone frame (y up, x mirrored) turned by rotZ(180) is vanilla's arm frame:
      // the anchor sits 10 px below the right_arm pivot in the geo frame too.
      Vector3f geo = HeldItemAnchor.local(HumanoidArm.RIGHT).transformPosition(new Vector3f());
      assertEquals(1.0F / 16.0F, geo.x, 1.0E-6F);
      assertEquals(-10.0F / 16.0F, geo.y, 1.0E-6F);
      assertEquals(-2.0F / 16.0F, geo.z, 1.0E-6F);
      Vector3f left = HeldItemAnchor.local(HumanoidArm.LEFT).transformPosition(new Vector3f());
      assertEquals(-1.0F / 16.0F, left.x, 1.0E-6F, "left hand mirrors x");
   }
}
