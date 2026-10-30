package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.client.dio.RollerCamera;
import dev.baranhan.viltrumitecore.client.dio.RollerCinema;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DIO's Road Roller cinematic camera (caster only); logic and lifetime live in RollerCinema/RollerCamera. */
@Mixin(value = Camera.class, priority = 1990)
public abstract class DioSceneCameraMixin {
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
   private void viltrumitecore$dioRoller(BlockGetter level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
      RollerCamera.View view = RollerCinema.apply(entity, partialTick, this.getPosition(), this.getYRot(), this.getXRot());
      if (view != null) {
         this.setRotation(view.yaw(), view.pitch());
         this.setPosition(view.pos());
         // Show DIO's own body once the lens has left his head (not from inside it).
         this.detached = detached || view.pos().distanceTo(entity.getEyePosition(partialTick)) > 0.8;
      }
   }
}
