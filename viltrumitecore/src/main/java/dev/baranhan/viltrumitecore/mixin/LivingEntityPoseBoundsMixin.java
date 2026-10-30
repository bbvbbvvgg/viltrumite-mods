package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.hero.HeroBodySize;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dismount fit checks must use the scaled hero's own box. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPoseBoundsMixin {
   @Inject(method = "getLocalBoundsForPose(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/phys/AABB;",
      at = @At("RETURN"), cancellable = true)
   private void viltrumitecore$heroLocalBounds(Pose pose, CallbackInfoReturnable<AABB> cir) {
      if (!((Object)this instanceof Player player)) return;
      HeroBodySize.Scale scale = HeroBodySize.of(player);
      if (!scale.vanilla()) {
         cir.setReturnValue(HeroBodySize.localBox(
            HeroBodySize.dimensions(player.getDimensions(pose), pose, scale)));
      }
   }
}
