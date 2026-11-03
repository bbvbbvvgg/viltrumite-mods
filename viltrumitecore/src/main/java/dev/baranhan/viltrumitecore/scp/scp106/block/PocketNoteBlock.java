package dev.baranhan.viltrumitecore.scp.scp106.block;

import dev.baranhan.viltrumitecore.scp.scp106.Scp106Pocket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A sheet of paper on the floor: a note about the rules of the place or the diary of an earlier victim. */
public class PocketNoteBlock extends Block {
   private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 1, 14);

   public PocketNoteBlock(Properties p) {
      super(p);
   }

   @Override
   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return SHAPE;
   }

   @Override
   public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (!level.isClientSide && player instanceof ServerPlayer sp) {
         Scp106Pocket.readNote(sp, pos);
      }
      return InteractionResult.sidedSuccess(level.isClientSide);
   }
}
