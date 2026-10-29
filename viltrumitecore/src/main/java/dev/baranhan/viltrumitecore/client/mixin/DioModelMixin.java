package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.client.dio.DioPoser;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DIO third-person body layers (posture, JoJo pose, casts, ult); logic lives in DioPoser. */
@Mixin(
   value = {PlayerModel.class},
   priority = 1185
)
public abstract class DioModelMixin<T extends LivingEntity> extends HumanoidModel<T> {
   protected DioModelMixin(ModelPart root) {
      super(root);
   }

   @Inject(
      method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"},
      at = {@At("TAIL")}
   )
   private void dioSetupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
      DioPoser.poseThirdPerson((PlayerModel<?>)(Object)this, entity);
   }
}
