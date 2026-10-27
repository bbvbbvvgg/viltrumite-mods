package dev.baranhan.viltrumitecore.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.thor.MjolnirFirstPerson;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Thor session A: first-person recall reach and catch kick of the right arm; logic in MjolnirFirstPerson. */
@Mixin({ItemInHandRenderer.class})
public abstract class FirstPersonThorMixin {
   @Inject(
      method = {"renderArmWithItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
         ordinal = 0,
         shift = Shift.AFTER
      )}
   )
   private void viltrumitecore$thorRecall(
      AbstractClientPlayer player,
      float partialTicks,
      float pitch,
      InteractionHand hand,
      float attackAnim,
      ItemStack stack,
      float equipAnim,
      PoseStack poseStack,
      MultiBufferSource buffer,
      int combinedLight,
      CallbackInfo ci
   ) {
      if (hand == InteractionHand.MAIN_HAND) {
         MjolnirFirstPerson.apply(player, partialTicks, poseStack);
      }
   }
}
