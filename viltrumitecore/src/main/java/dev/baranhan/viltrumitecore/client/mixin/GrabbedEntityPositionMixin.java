package dev.baranhan.viltrumitecore.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import dev.baranhan.viltrumiteflight.client.ViltrumiteFlightClient;
import dev.baranhan.viltrumiteflight.util.FlightState;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({EntityRenderDispatcher.class})
public abstract class GrabbedEntityPositionMixin {
   @Inject(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
      )}
   )
   private <E extends Entity> void onRenderAbsolutePositionFix(
      E entity,
      double x,
      double y,
      double z,
      float entityYaw,
      float partialTicks,
      PoseStack poseStack,
      MultiBufferSource buffer,
      int packedLight,
      CallbackInfo ci
   ) {
      if (entity.level() != null && entity.level().isClientSide) {
         Player attached = dev.baranhan.viltrumitecore.client.hero.WorldPauseClient.active() ? null
            : dev.baranhan.viltrumitecore.hero.HeroHeldTargets.holder(entity);
         if (attached != null) {
            Vec3 at = dev.baranhan.viltrumitecore.hero.HeroRegistry.get(attached).heldTargetPosition(attached, entity, partialTicks);
            if (at != null) {
               Vec3 interpolated = entity.getPosition(partialTicks);
               poseStack.translate(at.x - interpolated.x, at.y - interpolated.y, at.z - interpolated.z);
            }
            return;
         }
         Player holdingPlayer = this.getHoldingPlayer(entity);
         if (holdingPlayer != null) {
            Vec3 handPos = null;
            Minecraft client = Minecraft.getInstance();
            boolean isFirstPerson = holdingPlayer == client.player && client.options.getCameraType().isFirstPerson();
            if (isFirstPerson) {
               Vec3 localHandPos = ((ViltrumiteCorePlayer)holdingPlayer).getFirstPersonLocalHandPos();
               if (localHandPos != null) {
                  Camera camera = client.gameRenderer.getMainCamera();
                  float baseFov = ((Integer)client.options.fov().get()).floatValue();
                  float realWorldFovMultiplier = 1.0F;
                  if (client.gameRenderer instanceof GameRendererAccessor accessor) {
                     float lastFov = accessor.getLastFovMultiplier();
                     float currentFov = accessor.getFovMultiplier();
                     realWorldFovMultiplier = Mth.lerp(partialTicks, lastFov, currentFov);
                  }

                  float totalFov = baseFov * realWorldFovMultiplier;
                  float fovDifference = totalFov - 70.0F;
                  float zFovOffset = fovDifference * 0.015F;
                  float xyScale = 1.0F + fovDifference * 0.0035F;
                  float adjustedLocalX = (float)localHandPos.x * xyScale;
                  float adjustedLocalY = (float)localHandPos.y * xyScale;
                  float adjustedLocalZ = (float)localHandPos.z + zFovOffset;
                  if (client.player instanceof ViltrumiteFlightPlayer fp && fp.getFlightState() != FlightState.NONE) {
                     adjustedLocalZ += 0.15F;
                  }

                  Vector3f localRotator = new Vector3f(adjustedLocalX, adjustedLocalY, adjustedLocalZ);
                  float invRoll = (float)Math.toRadians((double)(-ViltrumiteFlightClient.currentCameraRoll));
                  localRotator.rotateZ(invRoll);
                  localRotator.rotateX((float)Math.toRadians((double)(-camera.getXRot())));
                  localRotator.rotateY((float)Math.toRadians((double)(-(camera.getYRot() + 180.0F))));
                  handPos = camera.getPosition().add((double)localRotator.x(), (double)localRotator.y(), (double)localRotator.z());
               }
            } else {
               Vec3 handOffset = ((ViltrumiteCorePlayer)holdingPlayer).getCalculatedHandOffset();
               if (handOffset != null) {
                  double pLerpX = Mth.lerp((double)partialTicks, holdingPlayer.xo, holdingPlayer.getX());
                  double pLerpY = Mth.lerp((double)partialTicks, holdingPlayer.yo, holdingPlayer.getY());
                  double pLerpZ = Mth.lerp((double)partialTicks, holdingPlayer.zo, holdingPlayer.getZ());
                  Vec3 currentBodyPos = new Vec3(pLerpX, pLerpY, pLerpZ);
                  handPos = currentBodyPos.add(handOffset);
               } else {
                  handPos = ((ViltrumiteCorePlayer)holdingPlayer).getCalculatedHandPos();
               }
            }

            if (handPos != null) {
               Vec3 lookDir = holdingPlayer.getViewVector(partialTicks).normalize();
               float pushForce = isFirstPerson ? 0.0F : entity.getBbWidth() * 0.6F;
               Vec3 adjustedHandPos = handPos.add(lookDir.scale((double)pushForce));
               double renderX = Mth.lerp((double)partialTicks, entity.xo, entity.getX());
               double renderY = Mth.lerp((double)partialTicks, entity.yo, entity.getY());
               double renderZ = Mth.lerp((double)partialTicks, entity.zo, entity.getZ());
               poseStack.translate(adjustedHandPos.x - renderX, adjustedHandPos.y - renderY, adjustedHandPos.z - renderZ);
               float playerYaw = Mth.lerp(partialTicks, holdingPlayer.yRotO, holdingPlayer.getYRot());
               poseStack.mulPose(Axis.YN.rotationDegrees(playerYaw));
               float playerPitch = Mth.lerp(partialTicks, holdingPlayer.xRotO, holdingPlayer.getXRot());
               poseStack.mulPose(Axis.XP.rotationDegrees(playerPitch));
               if (holdingPlayer instanceof ViltrumiteFlightPlayer flightPlayer && !isFirstPerson) {
                  float throttle = flightPlayer.getLerpedFlightThrottle(partialTicks);
                  poseStack.mulPose(Axis.XP.rotationDegrees(throttle * 40.0F));
               }

               if (isFirstPerson) {
                  poseStack.scale(1.6F, 1.6F, 1.6F);
               }

               float grabHeight = entity.getEyeHeight() * 0.9F;
               poseStack.translate(0.0, (double)(-grabHeight), 0.0);
               entity.setYRot(180.0F);
               entity.yRotO = 180.0F;
               entity.setXRot(0.0F);
               entity.xRotO = 0.0F;
               if (entity instanceof LivingEntity living) {
                  living.yBodyRot = 180.0F;
                  living.yBodyRotO = 180.0F;
                  living.yHeadRot = 180.0F;
                  living.yHeadRotO = 180.0F;
                  if (living.walkAnimation != null) {
                     living.walkAnimation.setSpeed(0.0F);
                  }

                  if (living instanceof Warden warden) {
                     warden.sonicBoomAnimationState.stop();
                     warden.roarAnimationState.stop();
                     warden.attackAnimationState.stop();
                     warden.sniffAnimationState.stop();
                     warden.emergeAnimationState.stop();
                     warden.diggingAnimationState.stop();
                  }
               }
            }
         }
      }
   }

   private <E extends Entity> Player getHoldingPlayer(E entity) {
      for (Player player : entity.level().players()) {
         if (player instanceof ViltrumiteCorePlayer corePlayer && corePlayer.getGrabbedTarget() == entity) {
            return player;
         }
      }

      return null;
   }
}
