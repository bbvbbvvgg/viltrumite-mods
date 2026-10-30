package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.city.CityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Living city change journal: every block write of a loaded chunk (players, hero
 * abilities, explosions, fluids, fire, falling blocks) passes LevelChunk.setBlockState,
 * so the original is recorded here before it is replaced. No-op outside a city arena.
 */
@Mixin(LevelChunk.class)
public abstract class CityJournalMixin {
   @Inject(method = "setBlockState", at = @At("HEAD"))
   private void viltrumitecore$journal(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> cir) {
      LevelChunk self = (LevelChunk)(Object)this;
      CityManager.onSetBlock(self.getLevel(), self, pos, state);
   }
}
