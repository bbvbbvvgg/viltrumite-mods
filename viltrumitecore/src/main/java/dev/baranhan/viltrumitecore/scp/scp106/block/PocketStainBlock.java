package dev.baranhan.viltrumitecore.scp.scp106.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A thin smear of ooze on a floor (decor, no collision). */
public class PocketStainBlock extends Block {
   private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 0.5, 16);

   public PocketStainBlock(Properties p) {
      super(p);
   }

   @Override
   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return SHAPE;
   }
}
