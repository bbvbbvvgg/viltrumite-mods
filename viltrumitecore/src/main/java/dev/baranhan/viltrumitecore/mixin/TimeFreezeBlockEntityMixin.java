package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.hero.control.TimeFreeze;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stopped world (both sides): block entities (pistons in motion, hoppers, furnaces, chest lids,
 * enchanting books, bells) wait with everything else. Only a world pause stops a level.
 */
@Mixin(Level.class)
public abstract class TimeFreezeBlockEntityMixin {
   @Inject(method = "tickBlockEntities", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$stoppedBlockEntities(CallbackInfo ci) {
      if (TimeFreeze.worldStopped((Level)(Object)this)) {
         ci.cancel();
      }
   }
}
