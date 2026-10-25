package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.hero.QuietBlockRemoval;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only scoped clean carve replacements skip old callbacks; ordinary destruction is unchanged. */
@Mixin(LevelChunk.class)
public abstract class QuietBlockRemovalMixin {
   @Unique
   @Redirect(method="setBlockState", at=@At(value="INVOKE", target="Lnet/minecraft/world/level/block/state/BlockState;onRemove(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V"))
   private void viltrumitecore$quietOnRemove(BlockState old, Level level, BlockPos pos, BlockState replacement, boolean moving) {
      QuietBlockRemoval.onRemove(() -> old.onRemove(level,pos,replacement,moving), () -> {
         if(old.hasBlockEntity()) level.removeBlockEntity(pos);
      });
   }
}
