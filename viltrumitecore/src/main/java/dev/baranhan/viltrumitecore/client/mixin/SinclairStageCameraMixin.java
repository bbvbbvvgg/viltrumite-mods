package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.client.sinclair.stage.StageCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sinclair's staged cameras (copy cinematic shot/reverse shot, act cameras) at the end of Camera.setup;
 * the logic lives in StageCamera. Priority below the Thor scene camera: a Thor fatality (a control on the
 * same body) can never run together with a copy cinematic, and Thor's TAIL hook still wins if it did.
 */
@Mixin(value = Camera.class, priority = 1900)
public abstract class SinclairStageCameraMixin {
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
   private void viltrumitecore$sinclairStage(BlockGetter level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
      StageCamera.Shot shot = StageCamera.apply(entity, partialTick, new StageCamera.Shot(this.getPosition(), this.getYRot(), this.getXRot()));
      if (shot != null) {
         this.setRotation(shot.yaw(), shot.pitch());
         this.setPosition(shot.pos());
         this.detached = true;
      }
   }
}
