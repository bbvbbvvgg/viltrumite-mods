package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.client.venom.VenomPoser;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Venom poses on the vanilla player model: the convulsing human while the coat spreads, the ridden host's hunched
 * symbiote gait, the husk slumped where the symbiote left it (VenomPoser).
 */
@Mixin(value = {PlayerModel.class}, priority = 1187)
public abstract class VenomModelMixin<T extends LivingEntity> extends HumanoidModel<T> {
   protected VenomModelMixin(ModelPart root) {
      super(root);
   }

   @Inject(method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"}, at = {@At("TAIL")})
   private void venomSetupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
      VenomPoser.pose((PlayerModel<?>)(Object)this, entity, limbSwing, limbSwingAmount, ageInTicks);
   }
}
