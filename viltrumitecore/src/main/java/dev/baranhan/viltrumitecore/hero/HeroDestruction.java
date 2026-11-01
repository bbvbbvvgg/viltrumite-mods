package dev.baranhan.viltrumitecore.hero;

import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import dev.baranhan.viltrumitecore.world.collapse.CollapseCause;
import dev.baranhan.viltrumitecore.world.collapse.StructuralDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Approved world-destruction boundary for hero abilities (spec 7.2, plan
 * Task 3). Same rules as the Viltrumite impact destruction: unbreakable
 * blocks (bedrock) survive, the mobGriefing game rule is honored, drops use
 * the shared punch drop chance and removals emit block-shard particles.
 */
public final class HeroDestruction {
   private HeroDestruction() {
   }

   /** True when hero abilities may remove the block at pos. */
   public static boolean canDestroy(ServerLevel level, BlockPos pos) {
      if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
         return false;
      }

      BlockState state = level.getBlockState(pos);
      return !state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F;
   }

   /**
    * Removes one block the Viltrumite way: optional resource drop, air fill
    * and a block-particle burst. Returns false when canDestroy rejects it.
    */
   public static boolean destroyBlock(ServerLevel level, BlockPos pos) {
      // structural collapse (world/collapse): hero damage unless an outer scope names a more specific cause
      return StructuralDamage.orDefault(CollapseCause.HERO, () -> destroyBlock0(level, pos));
   }

   private static boolean destroyBlock0(ServerLevel level, BlockPos pos) {
      if (!canDestroy(level, pos)) {
         return false;
      }

      BlockState state = level.getBlockState(pos);
      if (level.random.nextFloat() < ViltrumiteCoreConfig.INSTANCE.punchBlockDropChance / 100.0F) {
         Block.dropResources(state, level, pos);
      }

      level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
      level.sendParticles(
         new BlockParticleOption(ParticleTypes.BLOCK, state),
         (double)pos.getX() + 0.5,
         (double)pos.getY() + 0.5,
         (double)pos.getZ() + 0.5,
         15,
         0.25,
         0.25,
         0.25,
         0.05
      );
      return true;
   }

   /**
    * Removes one block without drops, neighbour updates or particles (carve work,
    * Reinhard stage 4): air with UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS.
    * Only where {@link #canDestroy} allows (mobGriefing, unbreakable) and the chunk is loaded.
    */
   public static boolean clearBlock(ServerLevel level, BlockPos pos) {
      if (!level.isLoaded(pos) || !canDestroy(level, pos)) {
         return false;
      }

      return setAir(level, pos, CLEAR_FLAGS);
   }

   /** Debris and normal carve clearing use the same scoped callback boundary. */
   public static boolean setAir(ServerLevel level, BlockPos pos, int flags) {
      return StructuralDamage.orDefault(CollapseCause.HERO, () -> setAir0(level, pos, flags));
   }

   private static boolean setAir0(ServerLevel level, BlockPos pos, int flags) {
      return flags == CLEAR_FLAGS
         ? QuietBlockRemoval.run(() -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags))
         : level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
   }

   public static final int CLEAR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
}
