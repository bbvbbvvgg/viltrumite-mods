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
 * The same hook feeds the disaster aftermath tracker (destroyed blocks, any server level).
 */
@Mixin(LevelChunk.class)
public abstract class CityJournalMixin {
   @Inject(method = "setBlockState", at = @At("HEAD"))
   private void viltrumitecore$journal(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> cir) {
      LevelChunk self = (LevelChunk)(Object)this;
      CityManager.onSetBlock(self.getLevel(), self, pos, state);
      // structural damage ledger (world/collapse): records only inside a destruction cause scope
      dev.baranhan.viltrumitecore.world.collapse.StructuralDamage.onSetBlock(self.getLevel(), self, pos, state);
      // City disaster aftermath: bursts of destroyed blocks become ruin zones (any server level, cheap when off)
      dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathTracker.onSetBlock(self.getLevel(), self, pos, state);
   }
}
