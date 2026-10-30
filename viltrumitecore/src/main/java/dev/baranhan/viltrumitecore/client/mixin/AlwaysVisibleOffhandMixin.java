package dev.baranhan.viltrumitecore.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.render.animation.RegulusAnimationManager;
import dev.baranhan.viltrumitecore.config.ViltrumiteClientConfig;
import dev.baranhan.viltrumitecore.item.InfinityGunItem;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {ItemInHandRenderer.class},
   priority = 1100
)
public abstract class AlwaysVisibleOffhandMixin {
   @Shadow
   protected abstract void renderPlayerArm(PoseStack var1, MultiBufferSource var2, int var3, float var4, float var5, HumanoidArm var6);

   @Inject(
      method = {"renderArmWithItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z",
         ordinal = 0
      )},
      cancellable = true
   )
   private void forceRenderEmptyOffhand(
      AbstractClientPlayer player,
      float partialTicks,
      float pitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack stack,
      float equipProgress,
      PoseStack poseStack,
      MultiBufferSource buffer,
      int combinedLight,
      CallbackInfo ci
   ) {
      if (hand == InteractionHand.OFF_HAND && stack.isEmpty() && !player.isInvisible()) {
         if (!(player.getMainHandItem().getItem() instanceof InfinityGunItem)) {
            boolean shouldRender = ViltrumiteClientConfig.INSTANCE.alwaysRenderOffhand
               && dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player).showsIdleOffhand(player);
            if (!shouldRender && player instanceof ViltrumiteCorePlayer corePlayer) {
               boolean isBlocking = corePlayer.isBlocking();
               boolean isGrabbing = corePlayer.isTryingToGrab() || corePlayer.getGrabbedTarget() != null;
               boolean isLeftPunching = corePlayer.getPunchTicks() > 0 && corePlayer.isLeftArmPunch();
               boolean isLeftChopping = corePlayer.getChopTicks() > 0 && corePlayer.isLeftChop();
               boolean isThunderclapping = corePlayer.getThunderclapTicks() > 0;
               boolean isBarraging = corePlayer.isBarraging();
               shouldRender = isBlocking || isGrabbing || isLeftPunching || isLeftChopping || isThunderclapping || isBarraging
                  || RegulusAnimationManager.wantsOffhand(player) || dev.baranhan.viltrumitecore.client.ironman.IronManFirstPerson.wantsOffhand(player)
                  || dev.baranhan.viltrumitecore.client.reinhard.ReinhardFirstPerson.wantsOffhand(player)
                  || dev.baranhan.viltrumitecore.client.thor.ThorClipPlayer.wantsOffhand(player);
            }

            if (shouldRender) {
               HumanoidArm offArm = player.getMainArm() == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
               this.renderPlayerArm(poseStack, buffer, combinedLight, equipProgress, swingProgress, offArm);
               poseStack.popPose();
               ci.cancel();
            }
         }
      }
   }
}
