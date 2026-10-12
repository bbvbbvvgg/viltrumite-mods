package dev.baranhan.viltrumitecore.client.reinhard;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.hero.HeroHeadPreviews;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Race screen: Reinhard's 3D model head (slow yaw sway) and the archive hero icon as a badge. */
public final class ReinhardHeadPreview implements HeroHeadPreviews.Preview {
   public static final ReinhardHeadPreview INSTANCE = new ReinhardHeadPreview();
   private static final ResourceLocation BADGE = new ResourceLocation("viltrumitecore", "textures/gui/reinhard/hero.png");
   private static final float SWAY_DEGREES = 15.0F;
   private static final double SWAY_PERIOD_SECONDS = 4.0;
   /** Model px across the head with the hair: the preview square shows this much. */
   private static final float HEAD_SPAN_PX = 11.0F;
   /** Model px height of the head's centre (neck at 24, top at 32). */
   private static final float HEAD_CENTRE_PX = 28.0F;
   private static final int BADGE_SIZE = 12;

   private ReinhardHeadPreview() {
   }

   @Override
   public void draw(GuiGraphics graphics, int x, int y, int size, float partialTick) {
      BakedGeoModel body = AnimCache.model(ReinhardAssets.BODY);
      GeoBone head = body == null ? null : body.getBone(ReinhardAssets.HEAD);
      if (head != null) {
         body.resetBones();
         double t = Util.getMillis() / 1000.0;
         head.rotY = (float) Math.toRadians(SWAY_DEGREES * Math.sin(t * 2.0 * Math.PI / SWAY_PERIOD_SECONDS));
         float scale = size * 16.0F / HEAD_SPAN_PX;
         PoseStack pose = graphics.pose();
         pose.pushPose();
         pose.translate(x + size / 2.0F, y + size / 2.0F, 100.0F);
         pose.scale(scale, scale, scale);
         // GUI: y down, +z toward the viewer; the model has y up and faces -z.
         pose.mulPose(Axis.XP.rotationDegrees(180.0F));
         pose.translate(0.0F, -HEAD_CENTRE_PX / 16.0F, 0.0F);
         Lighting.setupForEntityInInventory();
         MultiBufferSource.BufferSource buffers = graphics.bufferSource();
         AnimRenderer.renderBone(head, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(ReinhardAssets.TEXTURE)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
         AnimRenderer.renderBone(head, pose, buffers.getBuffer(RenderType.eyes(ReinhardAssets.GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            1.0F, 1.0F, 1.0F, 1.0F);
         graphics.flush();
         Lighting.setupFor3DItems();
         pose.popPose();
      }

      graphics.blit(BADGE, x - BADGE_SIZE - 2, y + (size - BADGE_SIZE) / 2, BADGE_SIZE, BADGE_SIZE, 0.0F, 0.0F, 32, 32, 32, 32);
   }
}
