package dev.baranhan.viltrumitecore.client.reinhard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * The Dragon Sword sheathed on Reinhard's left hip (spec §8): sheath + blade in its sheathed place,
 * attached to the body bone. Stage 4: the blade slides out along the sheath axis while Reid is drawn.
 */
public final class ReinhardSword {
   /** Sheath mouth on the body, Bedrock px (+x = Reinhard's left), model space. */
   public static final float HIP_X = 4.5F;
   public static final float HIP_Y = 11.0F;
   public static final float HIP_Z = 0.0F;
   /**
    * Tilt in ModelPart degrees: the hilt leans in across the belly (Z) and forward, the tip back (X).
    * X is 50 (plan: 10): hanging nearly upright from the 11 px hip, the sheath tip went into the ground.
    */
   public static final float TILT_Z = -20.0F;
   public static final float TILT_X = 50.0F;
   /**
    * The archive sword is ~46 px long in block units ×16 (sized as a big item) against the
    * 32 px body; 0.35 makes the sheath ~13 px: with the tilt its tip stays ~2 px above the ground.
    */
   public static final float SWORD_SCALE = 0.35F;
   /** Stage 4: how far (sword px, along the sheath axis toward the hilt) the blade travels out at full draw. */
   public static final float DRAW_OUT_PX = 40.0F;

   private ReinhardSword() {
   }

   /** {@code stack} is the model frame (after the body yaw and player scale); the body bone is already posed. */
   public static void drawSheathed(PoseStack stack, BakedGeoModel body, MultiBufferSource buffers, int light, int overlay) {
      drawSheathed(stack, body, buffers, light, overlay, 0.0F);
   }

   /** {@code out} 0..1: the blade slides out of the sheath along its axis (Reid draw / sheathe, {@link ReinhardPoses#bladeOut}). */
   public static void drawSheathed(PoseStack stack, BakedGeoModel body, MultiBufferSource buffers, int light, int overlay, float out) {
      GeoBone bodyBone = body.getBone(ReinhardAssets.BODY_BONE);
      BakedGeoModel sheath = AnimCache.model(ReinhardAssets.SHEATH);
      BakedGeoModel blade = AnimCache.model(ReinhardAssets.BLADE);
      if (bodyBone == null || sheath == null || blade == null) {
         return;
      }

      sheath.resetBones();
      blade.resetBones();
      stack.pushPose();
      ReinhardRenderer.boneFrame(stack, bodyBone);
      // Renderer space mirrors Bedrock x (the parser negates it) like the bone pivots.
      stack.translate((-HIP_X - bodyBone.pivotX) / 16.0F, (HIP_Y - bodyBone.pivotY) / 16.0F, (HIP_Z - bodyBone.pivotZ) / 16.0F);
      // ModelPart angles → BakedGeoModel space: x negated, z kept (same rule as the clips).
      stack.mulPose(Axis.ZP.rotationDegrees(TILT_Z));
      stack.mulPose(Axis.XP.rotationDegrees(-TILT_X));
      stack.scale(SWORD_SCALE, SWORD_SCALE, SWORD_SCALE);
      // The archive stores the sheathed sword hilt down: turn it hilt up, sheath mouth (the sheathed guard) at the hip.
      stack.mulPose(Axis.ZP.rotationDegrees(180.0F));
      // Sword space: the sheathed guard sits at SHEATHED_OFFSET (Bedrock px; renderer x negated). Put it at the hip.
      float gx = -ReinhardAssets.SHEATHED_OFFSET.x / 16.0F;
      float gy = ReinhardAssets.SHEATHED_OFFSET.y / 16.0F;
      float gz = ReinhardAssets.SHEATHED_OFFSET.z / 16.0F;
      stack.translate(-gx, -gy, -gz);
      draw(sheath, stack, buffers, light, overlay);
      // The blade in its sheathed place: blade geo + SHEATHED_OFFSET.
      stack.translate(gx, gy + out * DRAW_OUT_PX / 16.0F, gz);
      draw(blade, stack, buffers, light, overlay);
      stack.popPose();
   }

   private static void draw(BakedGeoModel model, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
      AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(ReinhardAssets.SWORD_TEXTURE)), light, overlay, 1.0F, 1.0F, 1.0F,
         1.0F, null);
      if (!ShaderCompat.isShadowPass()) {
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(ReinhardAssets.SWORD_GLOW)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F, null);
      }
   }
}
