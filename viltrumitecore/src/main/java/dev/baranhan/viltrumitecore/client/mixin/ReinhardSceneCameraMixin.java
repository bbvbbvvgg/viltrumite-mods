package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.client.reinhard.finalpunch.FinalPunchCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reinhard's Final Punch camera after the other camera tweaks; logic lives in FinalPunchCamera. */
@Mixin(value = Camera.class, priority = 1950)
public abstract class ReinhardSceneCameraMixin {
   @Shadow
   private boolean detached;

   @Shadow
   protected abstract void setPosition(Vec3 pos);

   @Shadow
   protected abstract void setRotation(float yRot, float xRot);

   @Shadow
   public abstract Vec3 getPosition();

   @Shadow
   public abstract float getYRot();

   @Shadow
   public abstract float getXRot();

   @Inject(method = "setup", at = @At("TAIL"))
   private void viltrumitecore$finalPunch(BlockGetter level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
      FinalPunchCamera.View vanilla = new FinalPunchCamera.View(this.getPosition(), this.getYRot(), this.getXRot());
      FinalPunchCamera.View view = FinalPunchCamera.apply(entity, partialTick, vanilla);
      if (view != null) {
         this.setRotation(view.yaw(), view.pitch());
         this.setPosition(view.pos());
         // From / back to first person the lens leaves and re-enters the head: his own body is drawn
         // only once the lens is clear of it, so the blend never looks out from inside the head.
         this.detached = detached || !FinalPunchCamera.nearEye(view.pos(), vanilla.pos());
      }
   }
}
