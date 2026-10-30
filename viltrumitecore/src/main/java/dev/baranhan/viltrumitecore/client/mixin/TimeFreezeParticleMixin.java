package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.hero.control.TimeFreeze;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stopped world: particles neither move, age nor animate (their tick waits), and they are drawn
 * at partial tick 0 so the last tick's motion does not wobble every frame. New particles queue
 * until the world resumes.
 */
@Mixin(ParticleEngine.class)
public abstract class TimeFreezeParticleMixin {
   @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$stoppedParticles(CallbackInfo ci) {
      if (TimeFreeze.clientWorldStopped()) {
         ci.cancel();
      }
   }

   @ModifyVariable(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V",
      at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
   private float viltrumitecore$stoppedPartial(float partialTick) {
      return TimeFreeze.clientWorldStopped() ? 0.0F : partialTick;
   }
}
