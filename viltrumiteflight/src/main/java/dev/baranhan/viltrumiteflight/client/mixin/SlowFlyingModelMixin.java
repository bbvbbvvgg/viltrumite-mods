package dev.baranhan.viltrumiteflight.client.mixin;

import dev.baranhan.viltrumiteflight.client.PoseDataManager;
import dev.baranhan.viltrumiteflight.client.render.FlightAnimManager;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import dev.baranhan.viltrumiteflight.util.FlightState;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({PlayerModel.class})
public abstract class SlowFlyingModelMixin<T extends LivingEntity> extends HumanoidModel<T> {
   public SlowFlyingModelMixin(ModelPart root) {
      super(root);
   }

   @Inject(
      method = {"setupAnim"},
      at = {@At("TAIL")}
   )
   private void onSetupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
      boolean isLocalFirstPerson = entity == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson();
      if (!isLocalFirstPerson || ShaderCompat.isShadowPass()) {
         PlayerModel<?> playerModel = (PlayerModel<?>)(Object)this;
         FlightAnimManager.AnimState state = FlightAnimManager.getState(entity.getUUID());
         boolean isFlying = false;
         float throttle = 0.0F;
         float tickDelta = Minecraft.getInstance().getPartialTick();
         float hoverForward = 0.0F;
         float hoverSideways = 0.0F;
         if (entity instanceof ViltrumiteFlightPlayer omniPlayer) {
            isFlying = omniPlayer.getFlightState() != FlightState.NONE;
            throttle = omniPlayer.getLerpedFlightThrottle(tickDelta);
            hoverForward = omniPlayer.getHoverForward();
            hoverSideways = omniPlayer.getHoverSideways();
         }

         float transitionSpeed = 2.0F * state.deltaSeconds;
         if (isFlying) {
            state.transition = Math.min(1.0F, state.transition + transitionSpeed);
         } else {
            state.transition = Math.max(0.0F, state.transition - transitionSpeed);
         }

         if (state.transition > 0.0F) {
            float cruiseFactor = Math.max(0.0F, Math.min(1.0F, throttle / 0.35F));
            float hoverHeadPitch = headPitch * (float) (Math.PI / 180.0);
            float cruiseHeadPitch = (float)Math.toRadians(-90.0);
            float adjustedLookPitch = Mth.lerp(cruiseFactor, hoverHeadPitch, cruiseHeadPitch);
            // Neck fix: the look yaw is wrapped and limited like a human neck, and fades out in cruise.
            // Lying prone in cruise the model's head yaw axis is the spine, so the look yaw rolled the
            // head around the neck instead of turning it (wrong axis); the whole body already faces the
            // flight direction there.
            float lookYaw = (float)Math.toRadians(Mth.clamp(Mth.wrapDegrees(netHeadYaw), -75.0F, 75.0F)) * (1.0F - cruiseFactor);
            this.blendGuiBasePose(state.transition, lookYaw, adjustedLookPitch);
            float hoverPower = 1.0F - cruiseFactor;
            float hoverTransition = hoverPower * state.transition;
            float centrifugalRoll = (float)Math.toRadians((double)(state.smoothedTurnSpeed * -0.06F)) * hoverPower;
            float centrifugalPitch = (float)Math.toRadians((double)(Math.abs(state.smoothedTurnSpeed) * 0.035F)) * hoverPower;
            if (hoverTransition > 0.0F) {
               this.rightArm.xRot = Mth.lerp(hoverTransition, this.rightArm.xRot, (float)Math.toRadians(3.91F));
               this.rightArm.yRot = Mth.lerp(hoverTransition, this.rightArm.yRot, (float)Math.toRadians(12.7F));
               this.rightArm.zRot = Mth.lerp(hoverTransition, this.rightArm.zRot, (float)Math.toRadians(9.78F));
               this.leftArm.xRot = Mth.lerp(hoverTransition, this.leftArm.xRot, (float)Math.toRadians(3.91F));
               this.leftArm.yRot = Mth.lerp(hoverTransition, this.leftArm.yRot, (float)Math.toRadians(-12.7F));
               this.leftArm.zRot = Mth.lerp(hoverTransition, this.leftArm.zRot, (float)Math.toRadians(-9.78F));
               this.rightLeg.xRot = Mth.lerp(hoverTransition, this.rightLeg.xRot, (float)Math.toRadians(0.0));
               this.rightLeg.yRot = Mth.lerp(hoverTransition, this.rightLeg.yRot, (float)Math.toRadians(6.72F));
               this.rightLeg.zRot = Mth.lerp(hoverTransition, this.rightLeg.zRot, (float)Math.toRadians(3.91F));
               this.rightLeg.y = Mth.lerp(hoverTransition, this.rightLeg.y, 12.0F);
               this.leftLeg.xRot = Mth.lerp(hoverTransition, this.leftLeg.xRot, (float)Math.toRadians(0.0));
               this.leftLeg.yRot = Mth.lerp(hoverTransition, this.leftLeg.yRot, (float)Math.toRadians(-6.72F));
               this.leftLeg.zRot = Mth.lerp(hoverTransition, this.leftLeg.zRot, (float)Math.toRadians(-3.91F));
               this.leftLeg.y = Mth.lerp(hoverTransition, this.leftLeg.y, 12.0F);
            }

            float time = state.hoverTime * 1.66F;
            float outwardSin = ((float)Math.sin((double)time) + 1.0F) * 0.5F;
            float armBreathRoll = outwardSin * 0.15F * hoverPower;
            float bodyBreathPitch = (float)Math.cos((double)time) * 0.0163F * hoverPower;
            float rightLegBreath = (float)Math.sin((double)time * 1.1) * 0.105F * hoverPower;
            float leftLegBreath = (float)Math.cos((double)time * 0.9) * 0.105F * hoverPower;
            float windDragPitch = (float)Math.toRadians((double)(state.currentPitch * -0.8F)) * hoverPower;
            float legDragPitch = (float)Math.toRadians((double)(state.currentPitch * -0.4F)) * hoverPower;
            float windDragRoll = (float)Math.toRadians((double)(state.currentRoll * -0.5F)) * hoverPower;
            float tension = 90.0F;
            float dampening = 10.0F;
            float targetMovePitch = hoverForward * (float)Math.toRadians(15.0);
            float displacementPitch = targetMovePitch - state.smoothedMovePitch;
            state.movePitchVelocity = state.movePitchVelocity + (tension * displacementPitch - dampening * state.movePitchVelocity) * state.deltaSeconds;
            state.smoothedMovePitch = state.smoothedMovePitch + state.movePitchVelocity * state.deltaSeconds;
            float moveDirectionPitch = state.smoothedMovePitch * hoverPower;
            float targetMoveRoll = hoverSideways * (float)Math.toRadians(6.0);
            float displacementRoll = targetMoveRoll - state.smoothedMoveRoll;
            state.moveRollVelocity = state.moveRollVelocity + (tension * displacementRoll - dampening * state.moveRollVelocity) * state.deltaSeconds;
            state.smoothedMoveRoll = state.smoothedMoveRoll + state.moveRollVelocity * state.deltaSeconds;
            float moveDirectionRoll = state.smoothedMoveRoll * hoverPower;
            this.body.xRot = this.body.xRot + bodyBreathPitch * state.transition;
            this.head.xRot = this.head.xRot + bodyBreathPitch * state.transition;
            this.rightArm.xRot = this.rightArm.xRot + (windDragPitch + moveDirectionPitch + centrifugalPitch) * state.transition;
            this.leftArm.xRot = this.leftArm.xRot + (windDragPitch + moveDirectionPitch + centrifugalPitch) * state.transition;
            this.rightArm.zRot = this.rightArm.zRot + (armBreathRoll + windDragRoll + centrifugalRoll - moveDirectionRoll) * state.transition;
            this.leftArm.zRot = this.leftArm.zRot + (-armBreathRoll + windDragRoll + centrifugalRoll - moveDirectionRoll) * state.transition;
            this.rightLeg.xRot = this.rightLeg.xRot + (rightLegBreath + legDragPitch + moveDirectionPitch + centrifugalPitch) * state.transition;
            this.leftLeg.xRot = this.leftLeg.xRot + (leftLegBreath + legDragPitch + moveDirectionPitch + centrifugalPitch) * state.transition;
            this.rightLeg.zRot = this.rightLeg.zRot + (windDragRoll + centrifugalRoll - moveDirectionRoll) * state.transition;
            this.leftLeg.zRot = this.leftLeg.zRot + (windDragRoll + centrifugalRoll - moveDirectionRoll) * state.transition;
            boolean isHovering = isFlying && throttle < 0.1F;
            boolean isDescending = isHovering && entity.isCrouching() && entity.getDeltaMovement().y <= 0.0;
            float descendAnimSpeed = 3.0F * state.deltaSeconds;
            if (isDescending) {
               state.currentDescendFactor = Math.min(1.0F, state.currentDescendFactor + descendAnimSpeed);
            } else {
               state.currentDescendFactor = Math.max(0.0F, state.currentDescendFactor - descendAnimSpeed);
            }

            if (state.currentDescendFactor > 0.0F) {
               float descendLerp = state.currentDescendFactor * state.currentDescendFactor * (3.0F - 2.0F * state.currentDescendFactor);
               float rightArmDescendRoll = (float)Math.toRadians(28.0);
               float leftArmDescendRoll = (float)Math.toRadians(-28.0);
               float armDescendPitch = (float)Math.toRadians(5.0);
               this.rightArm.zRot = Mth.lerp(descendLerp, this.rightArm.zRot, rightArmDescendRoll);
               this.rightArm.xRot = Mth.lerp(descendLerp, this.rightArm.xRot, armDescendPitch);
               this.leftArm.zRot = Mth.lerp(descendLerp, this.leftArm.zRot, leftArmDescendRoll);
               this.leftArm.xRot = Mth.lerp(descendLerp, this.leftArm.xRot, armDescendPitch);
            }

            boolean wantsSuperman = throttle >= 0.8F;
            float supermanAnimSpeed = 3.0F * state.deltaSeconds;
            if (wantsSuperman) {
               state.currentSupermanFactor = Math.min(1.0F, state.currentSupermanFactor + supermanAnimSpeed);
            } else {
               state.currentSupermanFactor = Math.max(0.0F, state.currentSupermanFactor - supermanAnimSpeed);
            }

            if (state.currentSupermanFactor > 0.0F) {
               float supermanFactor = state.currentSupermanFactor * state.currentSupermanFactor * (3.0F - 2.0F * state.currentSupermanFactor);
               float superPitch = (float)Math.toRadians(-180.0);
               float superRollR = (float)Math.toRadians(-8.8F);
               float superRollL = (float)Math.toRadians(8.8F);
               this.rightArm.xRot = Mth.lerp(supermanFactor, this.rightArm.xRot, superPitch);
               this.rightArm.zRot = Mth.lerp(supermanFactor, this.rightArm.zRot, superRollR);
               this.rightArm.yRot = Mth.lerp(supermanFactor, this.rightArm.yRot, 0.0F);
               this.leftArm.xRot = Mth.lerp(supermanFactor, this.leftArm.xRot, superPitch);
               this.leftArm.zRot = Mth.lerp(supermanFactor, this.leftArm.zRot, superRollL);
               this.leftArm.yRot = Mth.lerp(supermanFactor, this.leftArm.yRot, 0.0F);
            }

            if (this.attackTime > 0.0F) {
               float swing = this.attackTime;
               float swingIntensity = Mth.sin(swing * (float) Math.PI);
               float swingTwist = Mth.sin(Mth.sqrt(swing) * (float) (Math.PI * 2));
               boolean isRightHand = entity.getMainArm() == HumanoidArm.RIGHT;
               if (isRightHand) {
                  this.rightArm.xRot -= swingIntensity * 1.2F;
                  this.rightArm.yRot += swingTwist * 0.3F;
                  this.body.yRot += swingTwist * 0.2F;
               } else {
                  this.leftArm.xRot -= swingIntensity * 1.2F;
                  this.leftArm.yRot -= swingTwist * 0.3F;
                  this.body.yRot += swingTwist * 0.2F;
               }
            }

            this.syncLayers(playerModel);
         }
      }
   }

   @Unique
   private void blendGuiBasePose(float t, float lookYaw, float lookPitch) {
      this.head.xRot = Mth.lerp(t, this.head.xRot, lookPitch + (float)Math.toRadians((double)PoseDataManager.TP.head.pitch));
      this.head.yRot = Mth.lerp(t, this.head.yRot, lookYaw + (float)Math.toRadians((double)PoseDataManager.TP.head.yaw));
      this.head.zRot = Mth.lerp(t, this.head.zRot, (float)Math.toRadians((double)PoseDataManager.TP.head.roll));
      this.body.xRot = Mth.lerp(t, this.body.xRot, (float)Math.toRadians((double)PoseDataManager.TP.body.pitch));
      this.body.yRot = Mth.lerp(t, this.body.yRot, (float)Math.toRadians((double)PoseDataManager.TP.body.yaw));
      this.body.zRot = Mth.lerp(t, this.body.zRot, (float)Math.toRadians((double)PoseDataManager.TP.body.roll));
      this.rightArm.xRot = Mth.lerp(t, this.rightArm.xRot, (float)Math.toRadians((double)PoseDataManager.TP.rightArm.pitch));
      this.rightArm.yRot = Mth.lerp(t, this.rightArm.yRot, (float)Math.toRadians((double)PoseDataManager.TP.rightArm.yaw));
      this.rightArm.zRot = Mth.lerp(t, this.rightArm.zRot, (float)Math.toRadians((double)PoseDataManager.TP.rightArm.roll));
      this.leftArm.xRot = Mth.lerp(t, this.leftArm.xRot, (float)Math.toRadians((double)PoseDataManager.TP.leftArm.pitch));
      this.leftArm.yRot = Mth.lerp(t, this.leftArm.yRot, (float)Math.toRadians((double)PoseDataManager.TP.leftArm.yaw));
      this.leftArm.zRot = Mth.lerp(t, this.leftArm.zRot, (float)Math.toRadians((double)PoseDataManager.TP.leftArm.roll));
      this.rightLeg.xRot = Mth.lerp(t, this.rightLeg.xRot, (float)Math.toRadians((double)PoseDataManager.TP.rightLeg.pitch));
      this.rightLeg.yRot = Mth.lerp(t, this.rightLeg.yRot, (float)Math.toRadians((double)PoseDataManager.TP.rightLeg.yaw));
      this.rightLeg.zRot = Mth.lerp(t, this.rightLeg.zRot, (float)Math.toRadians((double)PoseDataManager.TP.rightLeg.roll));
      this.leftLeg.xRot = Mth.lerp(t, this.leftLeg.xRot, (float)Math.toRadians((double)PoseDataManager.TP.leftLeg.pitch));
      this.leftLeg.yRot = Mth.lerp(t, this.leftLeg.yRot, (float)Math.toRadians((double)PoseDataManager.TP.leftLeg.yaw));
      this.leftLeg.zRot = Mth.lerp(t, this.leftLeg.zRot, (float)Math.toRadians((double)PoseDataManager.TP.leftLeg.roll));
      this.head.x = Mth.lerp(t, this.head.x, 0.0F + PoseDataManager.TP.head.x);
      this.head.y = Mth.lerp(t, this.head.y, 0.0F + PoseDataManager.TP.head.y);
      this.head.z = Mth.lerp(t, this.head.z, 0.0F + PoseDataManager.TP.head.z);
      this.body.x = Mth.lerp(t, this.body.x, 0.0F + PoseDataManager.TP.body.x);
      this.body.y = Mth.lerp(t, this.body.y, 0.0F + PoseDataManager.TP.body.y);
      this.body.z = Mth.lerp(t, this.body.z, 0.0F + PoseDataManager.TP.body.z);
      this.rightArm.x = Mth.lerp(t, this.rightArm.x, -5.0F + PoseDataManager.TP.rightArm.x);
      this.rightArm.y = Mth.lerp(t, this.rightArm.y, 2.0F + PoseDataManager.TP.rightArm.y);
      this.rightArm.z = Mth.lerp(t, this.rightArm.z, 0.0F + PoseDataManager.TP.rightArm.z);
      this.leftArm.x = Mth.lerp(t, this.leftArm.x, 5.0F + PoseDataManager.TP.leftArm.x);
      this.leftArm.y = Mth.lerp(t, this.leftArm.y, 2.0F + PoseDataManager.TP.leftArm.y);
      this.leftArm.z = Mth.lerp(t, this.leftArm.z, 0.0F + PoseDataManager.TP.leftArm.z);
      this.rightLeg.x = Mth.lerp(t, this.rightLeg.x, -1.9F + PoseDataManager.TP.rightLeg.x);
      this.rightLeg.y = Mth.lerp(t, this.rightLeg.y, 12.0F + PoseDataManager.TP.rightLeg.y);
      this.rightLeg.z = Mth.lerp(t, this.rightLeg.z, 0.0F + PoseDataManager.TP.rightLeg.z);
      this.leftLeg.x = Mth.lerp(t, this.leftLeg.x, 1.9F + PoseDataManager.TP.leftLeg.x);
      this.leftLeg.y = Mth.lerp(t, this.leftLeg.y, 12.0F + PoseDataManager.TP.leftLeg.y);
      this.leftLeg.z = Mth.lerp(t, this.leftLeg.z, 0.0F + PoseDataManager.TP.leftLeg.z);
   }

   @Unique
   private void syncLayers(PlayerModel<?> playerModel) {
      this.hat.copyFrom(this.head);
      playerModel.rightSleeve.copyFrom(this.rightArm);
      playerModel.leftSleeve.copyFrom(this.leftArm);
      playerModel.rightPants.copyFrom(this.rightLeg);
      playerModel.leftPants.copyFrom(this.leftLeg);
      playerModel.jacket.copyFrom(this.body);
   }
}
