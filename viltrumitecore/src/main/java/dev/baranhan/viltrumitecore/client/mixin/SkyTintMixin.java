package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.client.render.vfx.SkyTint;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link SkyTint} on the sky and cloud colours. */
@Mixin(ClientLevel.class)
public abstract class SkyTintMixin {
   @Inject(method = "getSkyColor(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
   private void viltrumite$tintSky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
      viltrumite$tint(cir);
   }

   @Inject(method = "getCloudColor(F)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
   private void viltrumite$tintClouds(float partialTick, CallbackInfoReturnable<Vec3> cir) {
      viltrumite$tint(cir);
   }

   private static void viltrumite$tint(CallbackInfoReturnable<Vec3> cir) {
      if (!SkyTint.INSTANCE.active()) {
         return;
      }

      Vec3 color = cir.getReturnValue();
      double[] rgb = SkyTint.apply(color.x, color.y, color.z, SkyTint.INSTANCE.current());
      cir.setReturnValue(new Vec3(rgb[0], rgb[1], rgb[2]));
   }
}
