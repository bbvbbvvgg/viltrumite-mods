package dev.baranhan.viltrumitecore.client.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only a server-tracked held arrow gets a final-palm presentation correction. */
@Mixin(ArrowRenderer.class)
public abstract class DefendedArrowRendererMixin {
   @Inject(method="render(Lnet/minecraft/world/entity/projectile/AbstractArrow;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("HEAD"))
   private void contact(AbstractArrow arrow,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light,CallbackInfo ci) {
      stack.pushPose();var d=dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts.arrowOffset(arrow,partial);stack.translate(d.x,d.y,d.z);
   }
   @Inject(method="render(Lnet/minecraft/world/entity/projectile/AbstractArrow;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("RETURN"))
   private void restore(AbstractArrow arrow,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light,CallbackInfo ci) {stack.popPose();}
}
