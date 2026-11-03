package dev.baranhan.viltrumitecore.scp.scp106.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Escape route II: a wall that looks like every other wall but is only a skin. It has no collision; the server
 * makes a cold draft come out of it (particles and a sound), which is the clue.
 */
public class PocketThinWallBlock extends Block {
   public PocketThinWallBlock(Properties p) {
      super(p);
   }

   @Override
   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return Shapes.block();
   }

   @Override
   public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return Shapes.empty();
   }
}
