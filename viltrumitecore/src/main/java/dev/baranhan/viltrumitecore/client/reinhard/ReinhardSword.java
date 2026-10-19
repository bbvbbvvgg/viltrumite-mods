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
   public static final float HIP_X = 6.2F;
   public static final float HIP_Y = 16.0F;
   public static final float HIP_Z = 1.0F;
   /** Hilt slightly inward; long sheath trails behind the thigh, outside the walking legs. */
   public static final float TILT_Z = -12.0F;
   public static final float TILT_X = 60.0F;
   /** Twice the Stage 7 visual size; the raised mouth preserves ground clearance at rest. */
   public static final float SWORD_SCALE = 0.70F;
   /** Stage 4: how far (sword px, along the sheath axis toward the hilt) the blade travels out at full draw. */
   public static final float DRAW_OUT_PX = 40.0F;

   private ReinhardSword() {
   }

   /** Guard ahead of the palm in the vanilla held-item frame (px). */
   public static final float HAND_GUARD_PX = 2;

   /** Smooth, rigid transfer: never linearly interpolate matrices (that shears the sword). */
   public static org.joml.Matrix4f interpolate(org.joml.Matrix4f from, org.joml.Matrix4f to, float weight) {
      float w = Math.max(0, Math.min(1, weight));
      if (w == 0) return new org.joml.Matrix4f(from);
      if (w == 1) return new org.joml.Matrix4f(to);
      org.joml.Vector3f p = from.getTranslation(new org.joml.Vector3f()).lerp(to.getTranslation(new org.joml.Vector3f()), w);
      org.joml.Vector3f scale = from.getScale(new org.joml.Vector3f()).lerp(to.getScale(new org.joml.Vector3f()), w);
      org.joml.Quaternionf q = from.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize()
         .slerp(to.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize(), w);
      return new org.joml.Matrix4f().translationRotateScale(p, q, scale);
   }

   public static float handWeight(ReinhardPoses.ReidPhase phase, double ticks) {
      return switch (phase) {
         case DRAWN -> 1;
         case DRAWING -> smooth((ticks - 96) / 24);
         case SHEATHING -> 1 - smooth(ticks / 8);
         default -> 0;
      };
   }

   public static float strikeWeight(double ticks) {
      return smooth(ticks / 4) * smooth((12 - ticks) / 4);
   }

   private static float smooth(double value) {
      double t = Math.max(0, Math.min(1, value));
      return (float)(t*t*(3-2*t));
   }

   /** Blade guard at the hip, before extraction. */
   static org.joml.Matrix4f hip(BakedGeoModel body) {
      PoseStack frame = new PoseStack();
      GeoBone bone = body.getBone(ReinhardAssets.BODY_BONE);
      if (bone != null) {
         ReinhardRenderer.boneFrame(frame, bone);
         frame.translate((-HIP_X-bone.pivotX)/16, (HIP_Y-bone.pivotY)/16, (HIP_Z-bone.pivotZ)/16);
      }
      frame.mulPose(Axis.ZP.rotationDegrees(TILT_Z));
      frame.mulPose(Axis.XP.rotationDegrees(-TILT_X));
      frame.scale(SWORD_SCALE,SWORD_SCALE,SWORD_SCALE);
      frame.mulPose(Axis.ZP.rotationDegrees(180));
      return new org.joml.Matrix4f(frame.last().pose());
   }

   /** Both meshes have a frame every render, so interruptions can recover without duplication. */
   static void drawAttached(Object player, PoseStack stack, org.joml.Matrix4f hip, org.joml.Matrix4f hand,
      ReinhardPoses.ReidPhase phase, double ticks, boolean strike, double actionTicks, int key,
      MultiBufferSource buffers, int light, int overlay) {
      org.joml.Matrix4f[] target=frames(hip,hand,phase,ticks,strike,actionTicks);
      org.joml.Matrix4f[] frames = ReinhardRecovery.weapons(player,key,target[0],target[1],net.minecraft.Util.getNanos());
      drawFrame(ReinhardAssets.SHEATH, stack,frames[0],buffers,light,overlay);
      drawFrame(ReinhardAssets.BLADE, stack,frames[1],buffers,light,overlay);
   }

   static org.joml.Matrix4f[] frames(org.joml.Matrix4f hip,org.joml.Matrix4f hand,
      ReinhardPoses.ReidPhase phase,double ticks,boolean strike,double actionTicks) {
      float sw = strike ? strikeWeight(actionTicks) : 0;
      org.joml.Matrix4f sheath = interpolate(hip,hand,sw)
         .translate(ReinhardAssets.SHEATHED_OFFSET.x/16, -ReinhardAssets.SHEATHED_OFFSET.y/16, -ReinhardAssets.SHEATHED_OFFSET.z/16);
      org.joml.Matrix4f blade = strike ? interpolate(hip,hand,sw)
         : interpolate(new org.joml.Matrix4f(hip).translate(0,-ReinhardPoses.bladeOut(phase,ticks)*DRAW_OUT_PX/16,0), hand, handWeight(phase,ticks));
      return new org.joml.Matrix4f[]{sheath,blade};
   }

   private static void drawFrame(net.minecraft.resources.ResourceLocation resource, PoseStack stack, org.joml.Matrix4f frame,
      MultiBufferSource buffers, int light, int overlay) {
      BakedGeoModel model = AnimCache.model(resource);
      if (model == null) return;
      model.resetBones();
      stack.pushPose();
      stack.mulPoseMatrix(frame);
      stack.last().normal().mul(new org.joml.Matrix3f(frame).invert().transpose());
      draw(model,stack,buffers,light,overlay);
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
