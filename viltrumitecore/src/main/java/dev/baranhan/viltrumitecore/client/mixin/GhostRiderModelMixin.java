package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.client.ghostrider.GhostRiderPoser;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ghost Rider third-person body poses (riding the Hell Cycle, chain moves, stare, transformation): GhostRiderPoser. */
@Mixin(
   value = {PlayerModel.class},
   priority = 1186
)
public abstract class GhostRiderModelMixin<T extends LivingEntity> extends HumanoidModel<T> {
   protected GhostRiderModelMixin(ModelPart root) {
      super(root);
   }

   @Inject(
      method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"},
      at = {@At("TAIL")}
   )
   private void ghostRiderSetupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
      GhostRiderPoser.poseThirdPerson((PlayerModel<?>)(Object)this, entity);
   }
}
