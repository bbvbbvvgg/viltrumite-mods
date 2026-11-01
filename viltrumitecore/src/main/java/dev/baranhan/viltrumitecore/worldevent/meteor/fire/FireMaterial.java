package dev.baranhan.viltrumitecore.worldevent.meteor.fire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

/**
 * Material class of a block for the city fire ({@link FireRules} constants) and what heat leaves of it.
 * Never touched: air, fluids, fire, unbreakable blocks (bedrock, barriers, command / structure / jigsaw
 * blocks, portal frames, light) and blocks with a block entity (chests, signs, banners, spawners).
 */
public final class FireMaterial {
   private FireMaterial() {
   }

   public static int of(BlockState state, BlockGetter level, BlockPos pos) {
      if (state.isAir() || !state.getFluidState().isEmpty() || state.getBlock() instanceof BaseFireBlock) {
         return FireRules.INERT;
      }

      if (state.getDestroySpeed(level, pos) < 0.0F || state.hasBlockEntity()) {
         return FireRules.INERT;
      }

      if (state.getCollisionShape(level, pos).isEmpty()) {
         // plants, carpets, torches, snow layers: they burn away if fragile, else ignore
         return isFragile(state) ? FireRules.FRAGILE : FireRules.INERT;
      }

      if (isFragile(state)) {
         return FireRules.FRAGILE;
      }

      if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
         return FireRules.GROUND;
      }

      if (state.isFlammable(level, pos, Direction.UP)) {
         return FireRules.FLAMMABLE;
      }

      SoundType sound = state.getSoundType();
      if (sound == SoundType.METAL || sound == SoundType.COPPER || sound == SoundType.NETHERITE_BLOCK || sound == SoundType.ANVIL
         || sound == SoundType.CHAIN || sound == SoundType.LANTERN) {
         return FireRules.METAL;
      }

      if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(BlockTags.BASE_STONE_OVERWORLD)
         || state.is(Blocks.COBBLESTONE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.CLAY) || state.is(BlockTags.ICE)) {
         return FireRules.GROUND;
      }

      return FireRules.MINERAL;
   }

   private static boolean isFragile(BlockState state) {
      return state.is(Tags.Blocks.GLASS) || state.is(Tags.Blocks.GLASS_PANES) || state.is(BlockTags.LEAVES) || state.is(BlockTags.WOOL_CARPETS)
         || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.FLOWER_POTS) || state.is(BlockTags.CANDLES)
         || state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)
         || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.SCAFFOLDING) || state.is(Blocks.VINE)
         || state.is(Blocks.COBWEB) || state.is(Blocks.SEA_LANTERN) || state.is(Blocks.GLOWSTONE) || state.is(Blocks.SHROOMLIGHT);
   }

   /**
    * What a burnt-out spot leaves: air for burnt wood, wool, glass and plants; a charred variant for
    * mineral surfaces (light concrete greys or blackens, bricks crack, quartz and calcite turn to basalt);
    * dirt for grass. Null = the block stays as it is.
    */
   public static BlockState burnt(BlockState state, int material, java.util.Random random, boolean roof) {
      Block b = state.getBlock();
      switch (material) {
         case FireRules.FRAGILE:
            return Blocks.AIR.defaultBlockState();
         case FireRules.FLAMMABLE:
            if (state.is(BlockTags.LOGS) && b instanceof RotatedPillarBlock && random.nextInt(2) == 0) {
               return Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
            }
            return Blocks.AIR.defaultBlockState();
         case FireRules.GROUND:
            if (b == Blocks.GRASS_BLOCK || b == Blocks.MOSS_BLOCK || b == Blocks.MYCELIUM) {
               return random.nextInt(3) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState();
            }
            if (b == Blocks.PODZOL) {
               return Blocks.COARSE_DIRT.defaultBlockState();
            }
            return null;
         case FireRules.MINERAL:
            if (roof && random.nextInt(16) == 0) {
               // a glowing heap of hot debris on a roof or a street
               return Blocks.MAGMA_BLOCK.defaultBlockState();
            }
            return charred(b, random);
         default:
            return null;
      }
   }

   private static BlockState charred(Block b, java.util.Random random) {
      String id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(b) == null ? "" : net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(b).toString();
      if (id.endsWith("_concrete")) {
         if (b == Blocks.BLACK_CONCRETE) {
            return null;
         }
         return b == Blocks.GRAY_CONCRETE || random.nextInt(3) == 0 ? Blocks.BLACK_CONCRETE.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState();
      }
      if (id.endsWith("_concrete_powder")) {
         return b == Blocks.BLACK_CONCRETE_POWDER ? null : Blocks.GRAY_CONCRETE_POWDER.defaultBlockState();
      }
      if (b == Blocks.TERRACOTTA || id.endsWith("_terracotta") && !id.endsWith("glazed_terracotta")) {
         return b == Blocks.BLACK_TERRACOTTA ? null : random.nextInt(2) == 0 ? Blocks.BLACK_TERRACOTTA.defaultBlockState() : Blocks.BROWN_TERRACOTTA.defaultBlockState();
      }
      if (b == Blocks.STONE_BRICKS || b == Blocks.MOSSY_STONE_BRICKS) {
         return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
      }
      if (b == Blocks.DEEPSLATE_BRICKS) {
         return Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
      }
      if (b == Blocks.DEEPSLATE_TILES) {
         return Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
      }
      if (b == Blocks.POLISHED_BLACKSTONE_BRICKS) {
         return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
      }
      if (b == Blocks.NETHER_BRICKS) {
         return Blocks.CRACKED_NETHER_BRICKS.defaultBlockState();
      }
      if (b == Blocks.QUARTZ_BLOCK || b == Blocks.SMOOTH_QUARTZ || b == Blocks.QUARTZ_BRICKS || b == Blocks.CALCITE || b == Blocks.WHITE_WOOL) {
         return random.nextInt(5) < 2 ? Blocks.SMOOTH_BASALT.defaultBlockState() : null;
      }
      if (b == Blocks.POLISHED_ANDESITE || b == Blocks.POLISHED_DIORITE || b == Blocks.POLISHED_GRANITE || b == Blocks.SMOOTH_STONE) {
         return random.nextInt(3) == 0 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : null;
      }
      if (b == Blocks.BRICKS || b == Blocks.MUD_BRICKS) {
         return random.nextInt(4) == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : null;
      }
      if (b == Blocks.SANDSTONE || b == Blocks.SMOOTH_SANDSTONE || b == Blocks.CUT_SANDSTONE) {
         return random.nextInt(3) == 0 ? Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState() : null;
      }
      return null;
   }
}
