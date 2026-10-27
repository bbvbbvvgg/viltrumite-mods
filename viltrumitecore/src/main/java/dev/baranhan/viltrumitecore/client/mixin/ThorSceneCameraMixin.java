package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.client.thor.scene.ThorSceneCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Thor scene cameras (fatality shots, V teleport view) after every other camera tweak; logic lives in ThorSceneCamera. */
@Mixin(value = Camera.class, priority = 2000)
public abstract class ThorSceneCameraMixin {
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
   private void viltrumitecore$thorScene(BlockGetter level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
      ThorSceneCamera.Shot shot = ThorSceneCamera.apply(entity, partialTick, new ThorSceneCamera.Shot(this.getPosition(), this.getYRot(), this.getXRot()));
      if (shot != null) {
         this.setRotation(shot.yaw(), shot.pitch());
         this.setPosition(shot.pos());
         this.detached = true;
      }
   }
}
