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
public abstract class FirstPersonChop2Mixin {
   @Unique
   private static final float S1_X = -0.06F;
   @Unique
   private static final float S1_Y = 0.0F;
   @Unique
   private static final float S1_Z = 0.17F;
   @Unique
   private static final float S1_RX = -11.48F;
   @Unique
   private static final float S1_RY = 48.13F;
   @Unique
   private static final float S1_RZ = 34.62F;
   @Unique
   private static final float S2_X = -0.07F;
   @Unique
   private static final float S2_Y = 0.1F;
   @Unique
   private static final float S2_Z = -0.85F;
   @Unique
   private static final float S2_RX = -34.11F;
   @Unique
   private static final float S2_RY = -27.78F;
   @Unique
   private static final float S2_RZ = 0.0F;

   @Inject(
      method = {"renderArmWithItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
         shift = Shift.AFTER
      )},
      cancellable = true
   )
   private void onRenderFirstPersonChop2(
      AbstractClientPlayer player,
      float tickDelta,
      float pitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack item,
      float equipProgress,
      PoseStack matrices,
      MultiBufferSource vertexConsumers,
      int light,
      CallbackInfo ci
   ) {
      HumanoidArm currentArm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
      boolean isRenderingLeftArm = currentArm == HumanoidArm.LEFT;
      if (hand == InteractionHand.OFF_HAND && item.isEmpty()) {
         boolean var32 = true;
      } else {
         boolean var10000 = false;
      }

      if (player instanceof ViltrumiteCorePlayer corePlayer) {
         int chopTicks = corePlayer.getChopTicks();
         if (chopTicks > 0 && corePlayer.getChopType() == 1) {
            boolean isLeftChop = corePlayer.isLeftChop();
            if (isLeftChop == isRenderingLeftArm) {
               // Reinhard: the continuous chop on the third-person timing.
               if (dev.baranhan.viltrumitecore.client.reinhard.ReinhardChopFirstPerson.applyIfReinhard(player, chopTicks, 1, isLeftChop, tickDelta, matrices)) {
                  return;
               }

               float time = (20.0F - ((float)chopTicks - tickDelta)) / 20.0F;
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
                  targetX = Mth.lerp(localT, 0.0F, -0.06F);
                  targetY = Mth.lerp(localT, 0.0F, 0.0F);
                  targetZ = Mth.lerp(localT, 0.0F, 0.17F);
                  targetRX = Mth.lerp(localT, 0.0F, -11.48F);
                  targetRY = Mth.lerp(localT, 0.0F, 48.13F);
                  targetRZ = Mth.lerp(localT, 0.0F, 34.62F);
               } else if (time < 0.25F) {
                  targetX = -0.06F;
                  targetY = 0.0F;
                  targetZ = 0.17F;
                  targetRX = -11.48F;
                  targetRY = 48.13F;
                  targetRZ = 34.62F;
               } else if (time < 0.35F) {
                  float localT = (time - 0.25F) / 0.1F;
                  targetX = Mth.lerp(localT, -0.06F, -0.07F * overMultiplier);
                  targetY = Mth.lerp(localT, 0.0F, 0.1F * overMultiplier);
                  targetZ = Mth.lerp(localT, 0.17F, -0.85F * overMultiplier);
                  targetRX = Mth.lerp(localT, -11.48F, -34.11F * overMultiplier);
                  targetRY = Mth.lerp(localT, 48.13F, -27.78F * overMultiplier);
                  targetRZ = Mth.lerp(localT, 34.62F, 0.0F * overMultiplier);
               } else if (time < 0.45F) {
                  float localT = (time - 0.35F) / 0.1F;
                  targetX = Mth.lerp(localT, -0.07F * overMultiplier, -0.07F);
                  targetY = Mth.lerp(localT, 0.1F * overMultiplier, 0.1F);
                  targetZ = Mth.lerp(localT, -0.85F * overMultiplier, -0.85F);
                  targetRX = Mth.lerp(localT, -34.11F * overMultiplier, -34.11F);
                  targetRY = Mth.lerp(localT, -27.78F * overMultiplier, -27.78F);
                  targetRZ = Mth.lerp(localT, 0.0F * overMultiplier, 0.0F);
               } else if (time < 0.75F) {
                  targetX = -0.07F;
                  targetY = 0.1F;
                  targetZ = -0.85F;
                  targetRX = -34.11F;
                  targetRY = -27.78F;
                  targetRZ = 0.0F;
               } else if (time < 0.95F) {
                  float localT = (time - 0.75F) / 0.2F;
                  targetX = Mth.lerp(localT, -0.07F, 0.0F);
                  targetY = Mth.lerp(localT, 0.1F, 0.0F);
                  targetZ = Mth.lerp(localT, -0.85F, 0.0F);
                  targetRX = Mth.lerp(localT, -34.11F, 0.0F);
                  targetRY = Mth.lerp(localT, -27.78F, 0.0F);
                  targetRZ = Mth.lerp(localT, 0.0F, 0.0F);
               }

               matrices.translate(targetX * m, targetY, targetZ);
               matrices.mulPose(new Quaternionf().rotateX((float)Math.toRadians((double)targetRX)));
               matrices.mulPose(new Quaternionf().rotateY((float)Math.toRadians((double)(targetRY * m))));
               matrices.mulPose(new Quaternionf().rotateZ((float)Math.toRadians((double)(targetRZ * m))));
            }
         }
      }
   }
}
