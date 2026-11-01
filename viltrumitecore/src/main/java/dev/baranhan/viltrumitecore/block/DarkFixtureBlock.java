package dev.baranhan.viltrumitecore.block;

import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A light block of the city with its power cut (Living City 3.0 blackout): glowstone, a sea lantern, a
 * shroomlight or a froglight that went dark. Same texture, darkened by a block tint; no light, no drops. The
 * power grid writes it in place of the lit block and puts the exact original state back when power returns
 * (or on /city reset), so the map is never changed for good.
 */
public class DarkFixtureBlock extends Block {
   public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
   public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

   public enum Kind implements StringRepresentable {
      GLOWSTONE,
      SEA_LANTERN,
      SHROOMLIGHT,
      OCHRE_FROGLIGHT,
      VERDANT_FROGLIGHT,
      PEARLESCENT_FROGLIGHT;

      @Override
      public String getSerializedName() {
         return this.name().toLowerCase(Locale.ROOT);
      }

      public boolean column() {
         return this == OCHRE_FROGLIGHT || this == VERDANT_FROGLIGHT || this == PEARLESCENT_FROGLIGHT;
      }
   }

   public DarkFixtureBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(this.stateDefinition.any().setValue(KIND, Kind.GLOWSTONE).setValue(AXIS, Direction.Axis.Y));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(KIND, AXIS);
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return this.defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
   }
}
