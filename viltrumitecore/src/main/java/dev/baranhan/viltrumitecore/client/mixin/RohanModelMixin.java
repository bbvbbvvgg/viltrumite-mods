package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.client.rohan.RohanPoser;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rohan third-person body layers (posture, warm-up, moves, reading); logic lives in RohanPoser. */
@Mixin(
   value = {PlayerModel.class},
   priority = 1186
)
public abstract class RohanModelMixin<T extends LivingEntity> extends HumanoidModel<T> {
   protected RohanModelMixin(ModelPart root) {
      super(root);
   }

   @Inject(
      method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"},
      at = {@At("TAIL")}
   )
   private void rohanSetupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
      RohanPoser.poseThirdPerson((PlayerModel<?>)(Object)this, entity);
   }
}
