package dev.baranhan.viltrumitecore.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.reinhard.ReinhardFirstPerson;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Normal held-item path only. Forge-cancelled empty-arm path applies at its call site. */
@Mixin(ItemInHandRenderer.class)
public abstract class ReinhardFirstPersonMixin {
   @Inject(method="renderArmWithItem",at=@At(value="INVOKE",
      target="Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",shift=At.Shift.AFTER))
   private void viltrumitecore$reinhardArm(AbstractClientPlayer player,float partial,float pitch,
      InteractionHand hand,float swing,ItemStack item,float equip,PoseStack stack,MultiBufferSource buffers,
      int light,CallbackInfo ci) {
      HumanoidArm arm=hand==InteractionHand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite();
      ReinhardFirstPerson.apply(player,arm,stack);
   }
}
