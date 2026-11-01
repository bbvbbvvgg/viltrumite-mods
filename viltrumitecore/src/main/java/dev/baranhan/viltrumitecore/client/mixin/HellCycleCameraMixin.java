package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.client.ghostrider.GhostRiderCamera;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Third-person camera distance on the Hell Cycle (the long bike and its rider need more than vanilla's 4 blocks). */
@Mixin(Camera.class)
public abstract class HellCycleCameraMixin {
   @ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(D)D"), index = 0)
   private double viltrumite$hellCycleZoom(double distance) {
      return GhostRiderCamera.zoom(distance);
   }
}
