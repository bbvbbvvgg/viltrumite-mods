package dev.baranhan.viltrumitecore.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * A full-body hero renderer that cancels the vanilla player render calls the vanilla
 * setupRotations itself, so its injections (flight tilt and hover in SlowFlyingRendererMixin)
 * still move the replaced body.
 */
@Mixin({LivingEntityRenderer.class})
public interface LivingEntityRendererInvoker {
   @Invoker("setupRotations")
   void viltrumitecore$setupRotations(LivingEntity entity, PoseStack poseStack, float bob, float bodyYaw, float partialTick);

   @Invoker("getBob")
   float viltrumitecore$getBob(LivingEntity entity, float partialTick);
}
