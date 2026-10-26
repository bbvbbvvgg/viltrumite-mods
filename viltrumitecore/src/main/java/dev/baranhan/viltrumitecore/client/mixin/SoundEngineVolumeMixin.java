package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.client.render.vfx.SoundDuck;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link SoundDuck} on every sound's final volume (the overload with the instance, so the sound id is known). */
@Mixin(SoundEngine.class)
public abstract class SoundEngineVolumeMixin {
   @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
   private void viltrumite$duck(SoundInstance instance, CallbackInfoReturnable<Float> cir) {
      if (SoundDuck.INSTANCE.active()) {
         // Never exactly 0: the engine would stop the channel behind the instance's back (REINHARD 3b item 7).
         cir.setReturnValue(SoundDuck.duck(cir.getReturnValueF(),
            SoundDuck.INSTANCE.volumeFactor(instance.getSource(), instance.getLocation())));
      }
   }
}
