package dev.baranhan.viltrumitecore.client.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla {@code HumanoidModel.setupAnim} never resets {@code head.zRot} or {@code body.zRot}, and
 * the PlayerModel is shared by every player of that skin type. A hero layer that adds roll
 * ({@code +=}) every frame (DIO's JoJo pose tilted the head 10° per frame) accumulated it: the head
 * spun past 90° and stayed twisted on every other player. Clear the roll before each frame's layers.
 */
@Mixin(
   value = {PlayerModel.class},
   priority = 900
)
public abstract class PlayerModelNeckResetMixin<T extends LivingEntity> extends HumanoidModel<T> {
   protected PlayerModelNeckResetMixin(ModelPart root) {
      super(root);
   }

   @Inject(
      method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"},
      at = {@At("HEAD")}
   )
   private void viltrumitecore$resetRoll(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
      this.head.zRot = 0.0F;
      this.hat.zRot = 0.0F;
      this.body.zRot = 0.0F;
   }
}
