package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.hero.HeroBodySize;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pose fit checks (Player.canEnterPose: stand / crouch / crawl, LocalPlayer crouch, dismount) use
 * the scaled hero box, not the vanilla one: under a 2-block ceiling a 3-block hero crouches or
 * crawls instead of standing up into the blocks. Same table as the size event (HeroBodySize).
 */
@Mixin(Entity.class)
public abstract class EntityPoseBoxMixin {
   @Inject(method = "getBoundingBoxForPose(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/phys/AABB;", at = @At("RETURN"), cancellable = true)
   private void viltrumitecore$heroPoseBox(Pose pose, CallbackInfoReturnable<AABB> cir) {
      if (!((Object)this instanceof Player player)) {
         return;
      }

      HeroBodySize.Scale scale = HeroBodySize.of(player);
      if (!scale.vanilla()) {
         cir.setReturnValue(HeroBodySize.box(player.position(), HeroBodySize.dimensions(player.getDimensions(pose), pose, scale)));
      }
   }
}
