package dev.baranhan.viltrumitecore.worldevent.aftermath;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Which block writes count as destruction (the block hook ignores everything else). */
class AftermathTrackerTest {
   @BeforeAll
   static void boot() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @Test
   void solidBlocksTurnedToAirFireFluidOrCraterAreDestroyed() {
      assertTrue(AftermathTracker.destroyed(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.AIR.defaultBlockState()));
      assertTrue(AftermathTracker.destroyed(Blocks.GLASS.defaultBlockState(), Blocks.FIRE.defaultBlockState()));
      assertTrue(AftermathTracker.destroyed(Blocks.OAK_PLANKS.defaultBlockState(), Blocks.WATER.defaultBlockState()));
      assertTrue(AftermathTracker.destroyed(Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState()));
   }

   @Test
   void buildingPlantsFluidsAndFireAreNot() {
      assertFalse(AftermathTracker.destroyed(Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState()), "placing");
      assertFalse(AftermathTracker.destroyed(Blocks.STONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()), "replacing");
      assertFalse(AftermathTracker.destroyed(Blocks.GRASS.defaultBlockState(), Blocks.AIR.defaultBlockState()), "grass is replaceable");
      assertFalse(AftermathTracker.destroyed(Blocks.FIRE.defaultBlockState(), Blocks.AIR.defaultBlockState()), "fire going out");
      assertFalse(AftermathTracker.destroyed(Blocks.WATER.defaultBlockState(), Blocks.AIR.defaultBlockState()), "fluid draining");
      assertFalse(AftermathTracker.destroyed(Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState()));
   }
}
