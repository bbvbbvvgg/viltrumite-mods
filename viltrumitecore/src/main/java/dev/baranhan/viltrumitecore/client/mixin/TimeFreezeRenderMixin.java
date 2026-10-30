package dev.baranhan.viltrumitecore.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.hero.control.TimeFreeze;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A frozen entity is drawn at partial tick 0: its clocks (tickCount + partial)
 * stand exactly still instead of wobbling within one tick every frame.
 */
@Mixin(LevelRenderer.class)
public abstract class TimeFreezeRenderMixin {
   @Shadow
   protected abstract void renderEntity(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack pose, MultiBufferSource buffers);

   @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$frozenPartial(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack pose,
      MultiBufferSource buffers, CallbackInfo ci) {
      if (partialTick != 0.0F && TimeFreeze.clientFrozen(entity.getId()) && entity.isAlive()
         && !(entity instanceof net.minecraft.client.player.LocalPlayer)) {
         ci.cancel();
         this.renderEntity(entity, camX, camY, camZ, 0.0F, pose, buffers);
      }
   }

   /*
    * Stopped world: the level renderer's own clock (rain, clouds, block-breaking cleanup) and the
    * rain splash/sound tick wait, and weather/clouds are drawn at partial tick 0 so they stand
    * exactly still instead of wobbling inside the last tick every frame.
    */
   @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$stoppedClock(CallbackInfo ci) {
      if (TimeFreeze.clientWorldStopped()) {
         ci.cancel();
      }
   }

   @Inject(method = "tickRain", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$stoppedRain(net.minecraft.client.Camera camera, CallbackInfo ci) {
      if (TimeFreeze.clientWorldStopped()) {
         ci.cancel();
      }
   }

   @ModifyVariable(method = "renderSnowAndRain", at = @At("HEAD"), argsOnly = true, ordinal = 0)
   private float viltrumitecore$stoppedWeather(float partialTick) {
      return TimeFreeze.clientWorldStopped() ? 0.0F : partialTick;
   }

   @ModifyVariable(method = "renderClouds", at = @At("HEAD"), argsOnly = true, ordinal = 0)
   private float viltrumitecore$stoppedClouds(float partialTick) {
      return TimeFreeze.clientWorldStopped() ? 0.0F : partialTick;
   }
}
