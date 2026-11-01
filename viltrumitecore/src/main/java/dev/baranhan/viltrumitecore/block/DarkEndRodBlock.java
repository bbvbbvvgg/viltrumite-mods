package dev.baranhan.viltrumitecore.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.state.BlockState;

/** An end rod light of the city with its power cut: the rod model, darkened, no light and no particles. */
public class DarkEndRodBlock extends EndRodBlock {
   public DarkEndRodBlock(Properties properties) {
      super(properties);
   }

   @Override
   public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
   }
}
