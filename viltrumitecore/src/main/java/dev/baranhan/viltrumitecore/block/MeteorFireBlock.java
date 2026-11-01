package dev.baranhan.viltrumitecore.block;

import dev.baranhan.viltrumitecore.worldevent.meteor.fire.CityFire;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The flame of the meteor-rain city fire (Meteor Rain 2.0). Unlike vanilla fire it clings to any face with a
 * collision shape (concrete, stone, glass, metal), so burning facades of non-flammable buildings can show
 * real flames. It never spreads, burns or ages by itself and ignores {@code doFireTick}: the server-side
 * {@link CityFire} spots own the spread and the damage. A scheduled tick keeps the flame only while a live
 * spot touches it; without one (spot burnt out, server restart, chunk reloaded later) it goes out within a
 * few seconds, so no flame can stay forever. Rain puts it out. Burns entities like vanilla fire.
 */
public class MeteorFireBlock extends BaseFireBlock {
   public static final BooleanProperty NORTH = PipeBlock.NORTH;
   public static final BooleanProperty EAST = PipeBlock.EAST;
   public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
   public static final BooleanProperty WEST = PipeBlock.WEST;
   public static final BooleanProperty UP = PipeBlock.UP;
   private static final Map<Direction, BooleanProperty> FACES = Map.of(
      Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST, Direction.UP, UP);

   public MeteorFireBlock(Properties properties) {
      super(properties, 1.5F);
      this.registerDefaultState(this.stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
         .setValue(WEST, false).setValue(UP, false));
   }

   @Override
   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(NORTH, EAST, SOUTH, WEST, UP);
   }

   @Override
   protected boolean canBurn(BlockState state) {
      return true;
   }

   /** True when the block at {@code pos} offers a face a flame can cling to. */
   public static boolean holds(BlockGetter level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return !state.isAir() && state.getFluidState().isEmpty() && !(state.getBlock() instanceof BaseFireBlock)
         && !state.getCollisionShape(level, pos).isEmpty();
   }

   /** The flame state for an air cell: on the floor when it has one, else on every holding side. */
   public BlockState stateAt(BlockGetter level, BlockPos pos) {
      BlockState state = this.defaultBlockState();
      if (holds(level, pos.below())) {
         return state;
      }

      for (Map.Entry<Direction, BooleanProperty> e : FACES.entrySet()) {
         state = state.setValue(e.getValue(), holds(level, pos.relative(e.getKey())));
      }

      return state;
   }

   /** True when the flame state of this cell clings to something. */
   public static boolean anchored(BlockState state, BlockGetter level, BlockPos pos) {
      if (holds(level, pos.below())) {
         return true;
      }

      for (Map.Entry<Direction, BooleanProperty> e : FACES.entrySet()) {
         if (holds(level, pos.relative(e.getKey()))) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      return anchored(state, level, pos);
   }

   @Override
   public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
      if (!anchored(state, level, pos)) {
         return Blocks.AIR.defaultBlockState();
      }

      BlockState next = this.stateAt(level, pos);
      return next;
   }

   @Override
   public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
      // no nether portal lighting, no vanilla fire logic: only the keep-alive check
      if (!level.isClientSide) {
         level.scheduleTick(pos, this, CityFire.FLAME_CHECK_TICKS + level.random.nextInt(20));
      }
   }

   @Override
   public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      if (level.isRainingAt(pos)) {
         level.removeBlock(pos, false);
         return;
      }

      if (CityFire.sustains(level, pos)) {
         level.scheduleTick(pos, this, CityFire.FLAME_CHECK_TICKS + random.nextInt(20));
      } else if (random.nextInt(3) == 0) {
         level.removeBlock(pos, false);
      } else {
         level.scheduleTick(pos, this, 10 + random.nextInt(25));
      }
   }

   @Override
   public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
      // own crackle and smoke (client), never the vanilla fire ambience
      dev.baranhan.viltrumitecore.client.meteor.CityFireClient.flameTick(level, pos, random);
   }
}
