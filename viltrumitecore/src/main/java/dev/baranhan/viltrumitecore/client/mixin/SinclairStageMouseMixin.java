package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.client.sinclair.stage.StageCamera;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The copy cinematic owns the look of both participants: mouse movement is dropped (not stored) while it runs. */
@Mixin(MouseHandler.class)
public abstract class SinclairStageMouseMixin {
   @Shadow
   private double accumulatedDX;

   @Shadow
   private double accumulatedDY;

   @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$sinclairStageLook(CallbackInfo ci) {
      if (StageCamera.locksLook()) {
         this.accumulatedDX = 0.0;
         this.accumulatedDY = 0.0;
         ci.cancel();
      }
   }
}
