package dev.baranhan.viltrumitecore.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemInHandRenderer.class})
public abstract class FirstPersonChopMixin {
   @Unique
   private static final float S1_X = 1.34F;
   @Unique
   private static final float S1_Y = -0.59F;
   @Unique
   private static final float S1_Z = 0.11F;
   @Unique
   private static final float S1_RX = 70.79F;
   @Unique
   private static final float S1_RY = 9.75F;
   @Unique
   private static final float S1_RZ = -81.93F;
   @Unique
   private static final float S2_X = 1.2F;
   @Unique
   private static final float S2_Y = -0.35F;
   @Unique
   private static final float S2_Z = -0.98F;
   @Unique
   private static final float S2_RX = 47.28F;
   @Unique
   private static final float S2_RY = 48.34F;
   @Unique
   private static final float S2_RZ = -131.75F;

   @Inject(
      method = {"renderArmWithItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
         shift = Shift.AFTER
      )}
   )
   private void onRenderFirstPersonChop(
      AbstractClientPlayer player,
      float partialTicks,
      float pitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack stack,
      float equipProgress,
      PoseStack poseStack,
      MultiBufferSource buffer,
      int combinedLight,
      CallbackInfo ci
   ) {
      if (player instanceof ViltrumiteCorePlayer corePlayer) {
         int chopTicks = corePlayer.getChopTicks();
         if (chopTicks > 0 && corePlayer.getChopType() == 0) {
            boolean isLeftChop = corePlayer.isLeftChop();
            HumanoidArm currentArm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
            boolean isRenderingLeftArm = currentArm == HumanoidArm.LEFT;
            if (isLeftChop == isRenderingLeftArm) {
               // Reinhard: the continuous chop on the third-person timing.
               if (dev.baranhan.viltrumitecore.client.reinhard.ReinhardChopFirstPerson.applyIfReinhard(player, chopTicks, 0, isLeftChop, partialTicks, poseStack)) {
                  return;
               }

               float time = (20.0F - ((float)chopTicks - partialTicks)) / 20.0F;
               time = Mth.clamp(time, 0.0F, 1.0F);
               float m = isLeftChop ? -1.0F : 1.0F;
               float targetX = 0.0F;
               float targetY = 0.0F;
               float targetZ = 0.0F;
               float targetRX = 0.0F;
               float targetRY = 0.0F;
               float targetRZ = 0.0F;
               float overMultiplier = 1.15F;
               if (time < 0.1F) {
                  float localT = time / 0.1F;
                  targetX = Mth.lerp(localT, 0.0F, 1.34F);
                  targetY = Mth.lerp(localT, 0.0F, -0.59F);
                  targetZ = Mth.lerp(localT, 0.0F, 0.11F);
                  targetRX = Mth.lerp(localT, 0.0F, 70.79F);
                  targetRY = Mth.lerp(localT, 0.0F, 9.75F);
                  targetRZ = Mth.lerp(localT, 0.0F, -81.93F);
               } else if (time < 0.25F) {
                  targetX = 1.34F;
                  targetY = -0.59F;
                  targetZ = 0.11F;
                  targetRX = 70.79F;
                  targetRY = 9.75F;
                  targetRZ = -81.93F;
               } else if (time < 0.35F) {
                  float localT = (time - 0.25F) / 0.1F;
                  targetX = Mth.lerp(localT, 1.34F, 1.2F * overMultiplier);
                  targetY = Mth.lerp(localT, -0.59F, -0.35F * overMultiplier);
                  targetZ = Mth.lerp(localT, 0.11F, -0.98F * overMultiplier);
                  targetRX = Mth.lerp(localT, 70.79F, 47.28F * overMultiplier);
                  targetRY = Mth.lerp(localT, 9.75F, 48.34F * overMultiplier);
                  targetRZ = Mth.lerp(localT, -81.93F, -131.75F * overMultiplier);
               } else if (time < 0.45F) {
                  float localT = (time - 0.35F) / 0.1F;
                  targetX = Mth.lerp(localT, 1.2F * overMultiplier, 1.2F);
                  targetY = Mth.lerp(localT, -0.35F * overMultiplier, -0.35F);
                  targetZ = Mth.lerp(localT, -0.98F * overMultiplier, -0.98F);
                  targetRX = Mth.lerp(localT, 47.28F * overMultiplier, 47.28F);
                  targetRY = Mth.lerp(localT, 48.34F * overMultiplier, 48.34F);
                  targetRZ = Mth.lerp(localT, -131.75F * overMultiplier, -131.75F);
               } else if (time < 0.75F) {
                  targetX = 1.2F;
                  targetY = -0.35F;
                  targetZ = -0.98F;
                  targetRX = 47.28F;
                  targetRY = 48.34F;
                  targetRZ = -131.75F;
               } else if (time < 0.95F) {
                  float localT = (time - 0.75F) / 0.2F;
                  targetX = Mth.lerp(localT, 1.2F, 0.0F);
                  targetY = Mth.lerp(localT, -0.35F, 0.0F);
                  targetZ = Mth.lerp(localT, -0.98F, 0.0F);
                  targetRX = Mth.lerp(localT, 47.28F, 0.0F);
                  targetRY = Mth.lerp(localT, 48.34F, 0.0F);
                  targetRZ = Mth.lerp(localT, -131.75F, 0.0F);
               }

               poseStack.translate(targetX * m, targetY, targetZ);
               poseStack.mulPose(new Quaternionf().rotateX((float)Math.toRadians((double)targetRX)));
               poseStack.mulPose(new Quaternionf().rotateY((float)Math.toRadians((double)(targetRY * m))));
               poseStack.mulPose(new Quaternionf().rotateZ((float)Math.toRadians((double)(targetRZ * m))));
            }
         }
      }
   }
}
