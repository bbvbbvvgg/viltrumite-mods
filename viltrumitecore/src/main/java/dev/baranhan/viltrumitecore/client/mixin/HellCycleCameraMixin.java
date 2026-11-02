package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.client.ghostrider.GhostRiderCamera;
import dev.baranhan.viltrumitecore.client.ghostrider.bike.HellCycleRiderPose;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The camera on the Hell Cycle: a longer third-person distance (the long bike and its rider need more than vanilla's
 * 4 blocks), and in first person the eye leans, pitches and lifts with the bike like the drawn body (otherwise a hard
 * lean leaves the camera hanging a block outside the tilted rider, looking at his own bike from the side; playtest #4).
 */
@Mixin(Camera.class)
public abstract class HellCycleCameraMixin {
   @Shadow
   protected abstract void setPosition(Vec3 pos);

   @Shadow
   public abstract Vec3 getPosition();

   @ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(D)D"), index = 0)
   private double viltrumite$hellCycleZoom(double distance) {
      return GhostRiderCamera.zoom(distance);
   }

   @Inject(method = "setup", at = @At("TAIL"))
   private void viltrumite$hellCycleLean(BlockGetter level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
      if (!detached && entity != null) {
         Vec3 eye = HellCycleRiderPose.firstPersonEye(entity, this.getPosition(), partialTick);
         if (eye != null) {
            this.setPosition(eye);
         }
      }
   }
}
