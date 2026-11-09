package dev.baranhan.viltrumitecore.client.mixin.perf;

import dev.baranhan.viltrumitecore.client.perf.ParticleBudget;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every particle, vanilla or ours, passes the particle budget before it is queued. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineBudgetMixin {
   @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$particleBudget(Particle particle, CallbackInfo ci) {
      if (!ParticleBudget.admit(particle)) {
         ci.cancel();
      }
   }
}
