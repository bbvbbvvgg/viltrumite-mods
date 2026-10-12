package dev.baranhan.viltrumitecore.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's empty-hand arm render (it fires RenderArmEvent), for a hero that hides the held item but keeps the arm. */
@Mixin(ItemInHandRenderer.class)
public interface ItemInHandRendererInvoker {
   @Invoker("renderPlayerArm")
   void viltrumite$renderPlayerArm(PoseStack stack, MultiBufferSource buffers, int light, float equipProgress, float swingProgress, HumanoidArm arm);
}
