package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.hero.control.TimeFreeze;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stopped world: animated textures (water, lava, fire, portals, prismarine) hold their frame. */
@Mixin(TextureManager.class)
public abstract class TimeFreezeTextureMixin {
   @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$stoppedAnimation(CallbackInfo ci) {
      if (TimeFreeze.clientWorldStopped()) {
         ci.cancel();
      }
   }
}
