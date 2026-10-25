package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.client.render.vfx.SkyTint;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link SkyTint} weather fade: client levels only (the integrated server shares the class). */
@Mixin(Level.class)
public abstract class LevelRainMixin {
   @Inject(method = "getRainLevel(F)F", at = @At("RETURN"), cancellable = true)
   private void viltrumite$fadeRain(float partialTick, CallbackInfoReturnable<Float> cir) {
      viltrumite$fade(cir);
   }

   @Inject(method = "getThunderLevel(F)F", at = @At("RETURN"), cancellable = true)
   private void viltrumite$fadeThunder(float partialTick, CallbackInfoReturnable<Float> cir) {
      viltrumite$fade(cir);
   }

   private void viltrumite$fade(CallbackInfoReturnable<Float> cir) {
      if (!((Level)(Object)this).isClientSide || !SkyTint.INSTANCE.active()) {
         return;
      }

      cir.setReturnValue(cir.getReturnValueF() * SkyTint.INSTANCE.current().rain());
   }
}
