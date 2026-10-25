package dev.baranhan.viltrumitecore.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Name tag of a player whose vanilla render is cancelled (Reinhard's whole-body model). */
@Mixin({EntityRenderer.class})
public interface EntityRendererNameTagInvoker {
   @Invoker("shouldShowName")
   boolean viltrumitecore$shouldShowName(Entity entity);

   @Invoker("renderNameTag")
   void viltrumitecore$renderNameTag(Entity entity, Component name, PoseStack poseStack, MultiBufferSource buffers, int light);
}
