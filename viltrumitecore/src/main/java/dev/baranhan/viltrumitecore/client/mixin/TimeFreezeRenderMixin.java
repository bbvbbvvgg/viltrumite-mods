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
}
