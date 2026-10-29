package dev.baranhan.viltrumitecore.client.dio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.RealTime;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.hero.dio.DioKnifeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * DIO's throwing knife: its own 3D model aimed along the flight, a pixel
 * ribbon trail while it flies and, hanging in stopped time, a cold glint that
 * runs down the blade (the knife itself stays perfectly still).
 */
public final class DioKnifeRenderer extends EntityRenderer<DioKnifeEntity> {
   public static final ResourceLocation GEO = new ResourceLocation("viltrumitecore", "geo/dio/dio_knife.geo.json");
   public static final ResourceLocation TEXTURE = new ResourceLocation("viltrumitecore", "textures/entity/dio/dio_knife.png");
   /** 16.6 model units → about 0.55 blocks. */
   static final float SCALE = 0.53F;
   static final float BLADE = 16.6F / 16.0F;

   public DioKnifeRenderer(Context context) {
      super(context);
      this.shadowRadius = 0.0F;
   }

   @Override
   public void render(DioKnifeEntity knife, float entityYaw, float partialTick, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel model = AnimCache.model(GEO);
      boolean frozen = knife.stopped();
      float p = frozen || knife.stuck() ? 1.0F : partialTick;
      float yaw = Mth.rotLerp(p, knife.yRotO, knife.getYRot());
      float pitch = Mth.lerp(p, knife.xRotO, knife.getXRot());
      stack.pushPose();
      stack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0F));
      stack.mulPose(Axis.XP.rotationDegrees(pitch));
      stack.scale(SCALE, SCALE, SCALE);
      if (model != null) {
         model.resetBones();
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F,
            1.0F, 1.0F, null);
      }

      VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
      Matrix4f m = stack.last().pose();
      if (frozen) {
         // A glint slides from the guard to the tip every 0.9 s.
         float u = (RealTime.seconds() + knife.getId() * 0.37F) % 0.9F / 0.9F;
         float z = -BLADE * (0.25F + 0.75F * u);
         float a = (float)Math.sin(u * Math.PI);
         cross(glow, m, z, 0.09F, 0.06F, 210, 235, 255, (int)(200 * a));
         cross(glow, m, -BLADE * 0.6F, BLADE * 0.5F, 0.02F, 150, 190, 255, 40);
      } else if (!knife.stuck()) {
         double speed = knife.getDeltaMovement().length();
         float length = (float)Math.min(2.4, 0.4 + speed * 0.9) / SCALE;
         ribbon(glow, m, 0.0F, length, 0.05F / SCALE, 235, 240, 255, 150);
      }

      stack.popPose();
      super.render(knife, entityYaw, partialTick, stack, buffers, light);
   }

   /** Two crossed quads along local Z from {@code z0} back to {@code z0 + length}, fading to the tail. */
   private static void ribbon(VertexConsumer c, Matrix4f m, float z0, float length, float w, int r, int g, int b, int a) {
      float z1 = z0 + length;
      c.vertex(m, -w, 0, z0).color(r, g, b, a).endVertex();
      c.vertex(m, w, 0, z0).color(r, g, b, a).endVertex();
      c.vertex(m, w * 0.2F, 0, z1).color(r, g, b, 0).endVertex();
      c.vertex(m, -w * 0.2F, 0, z1).color(r, g, b, 0).endVertex();
      c.vertex(m, 0, -w, z0).color(r, g, b, a).endVertex();
      c.vertex(m, 0, w, z0).color(r, g, b, a).endVertex();
      c.vertex(m, 0, w * 0.2F, z1).color(r, g, b, 0).endVertex();
      c.vertex(m, 0, -w * 0.2F, z1).color(r, g, b, 0).endVertex();
   }

   /** Small glow cross centered at local z. */
   private static void cross(VertexConsumer c, Matrix4f m, float z, float half, float w, int r, int g, int b, int a) {
      if (a <= 0) {
         return;
      }

      c.vertex(m, -w, 0, z - half).color(r, g, b, 0).endVertex();
      c.vertex(m, w, 0, z - half).color(r, g, b, 0).endVertex();
      c.vertex(m, w, 0, z + half).color(r, g, b, a).endVertex();
      c.vertex(m, -w, 0, z + half).color(r, g, b, a).endVertex();
      c.vertex(m, -half, -w, z).color(r, g, b, a).endVertex();
      c.vertex(m, half, -w, z).color(r, g, b, a).endVertex();
      c.vertex(m, half, w, z).color(r, g, b, a).endVertex();
      c.vertex(m, -half, w, z).color(r, g, b, a).endVertex();
   }

   @Override
   public ResourceLocation getTextureLocation(DioKnifeEntity knife) {
      return TEXTURE;
   }
}
