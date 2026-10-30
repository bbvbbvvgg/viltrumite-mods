package dev.baranhan.viltrumitecore.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.render.scene.SceneEntityHide;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An entity a cinematic draws itself (model, shadow, name tag) is skipped by the level pass. */
@Mixin(LevelRenderer.class)
public abstract class SceneEntityHideMixin {
   @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$sceneOwned(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack pose,
      MultiBufferSource buffers, CallbackInfo ci) {
      if (SceneEntityHide.hidden(entity)) ci.cancel();
   }
}
