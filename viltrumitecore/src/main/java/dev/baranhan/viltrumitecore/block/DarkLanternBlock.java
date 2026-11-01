package dev.baranhan.viltrumitecore.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** A street lantern (or soul lantern) of the city with its power cut: the lantern model, darkened, no light. */
public class DarkLanternBlock extends LanternBlock {
   public static final BooleanProperty SOUL = BooleanProperty.create("soul");

   public DarkLanternBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(this.defaultBlockState().setValue(SOUL, false));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder);
      builder.add(SOUL);
   }
}
