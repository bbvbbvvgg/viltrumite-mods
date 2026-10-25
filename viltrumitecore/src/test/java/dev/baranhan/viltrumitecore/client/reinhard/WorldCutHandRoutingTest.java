package dev.baranhan.viltrumitecore.client.reinhard;
import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

class WorldCutHandRoutingTest {
   @Test void emptyAndOccupiedHandsBothRouteThroughPosedPhysicalArms() {
      for(HumanoidArm main:HumanoidArm.values()) for(InteractionHand hand:InteractionHand.values()) {
         HumanoidArm physical=ReinhardPoses.physicalArm(main,hand);
         assertEquals(hand==InteractionHand.MAIN_HAND ? main : main.getOpposite(),physical);
         // Policy has no ItemStack input: occupied and empty slots take the same path.
         assertFalse(ReinhardPoses.drawsHeldItem(physical,ReinhardPoses.ReidPhase.DRAWN,ReinhardAbility.WORLD_CUT));
      }
   }
   @Test void leftHandItemReturnsOutsideWorldCut() {
      assertTrue(ReinhardPoses.drawsHeldItem(HumanoidArm.LEFT,ReinhardPoses.ReidPhase.DRAWN,null));
      assertFalse(ReinhardPoses.drawsHeldItem(HumanoidArm.RIGHT,ReinhardPoses.ReidPhase.DRAWN,null));
      assertTrue(ReinhardPoses.drawsHeldItem(HumanoidArm.RIGHT,ReinhardPoses.ReidPhase.SHEATHED,null));
   }
}
