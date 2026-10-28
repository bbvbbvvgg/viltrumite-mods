package dev.baranhan.viltrumitecore.client.sinclair;

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

/** Race screen: Sinclair's 3D head in the top hat (slow yaw sway, a tip of the hat) and the hero icon as a badge. */
public final class SinclairHeadPreview implements HeroHeadPreviews.Preview {
   public static final SinclairHeadPreview INSTANCE = new SinclairHeadPreview();
   private static final float SWAY_DEGREES = 15.0F;
   private static final double SWAY_PERIOD_SECONDS = 4.0;
   /** Model px across the head with the hat brim: the preview square shows this much. */
   private static final float HEAD_SPAN_PX = 14.5F;
   /** Model px height of the centre of head and hat (neck at 23.4, crown at 36.5). */
   private static final float HEAD_CENTRE_PX = 29.6F;
   private static final int BADGE_SIZE = 12;

   private SinclairHeadPreview() {
   }

   @Override
   public void draw(GuiGraphics graphics, int x, int y, int size, float partialTick) {
      BakedGeoModel body = AnimCache.model(SinclairAssets.BODY);
      GeoBone head = body == null ? null : body.getBone("head");
      if (head != null) {
         body.resetBones();
         SinclairPoser.props(body, 0, null, 0.0);
         double t = Util.getMillis() / 1000.0;
         head.rotY = (float) Math.toRadians(SWAY_DEGREES * Math.sin(t * 2.0 * Math.PI / SWAY_PERIOD_SECONDS));
         GeoBone hat = body.getBone("hat");
         if (hat != null) {
            double tip = Math.max(0.0, Math.sin(t * 2.0 * Math.PI / 6.0) - 0.85) / 0.15;
            hat.posY += (float) (tip * 1.2);
            hat.rotX += (float) Math.toRadians(-tip * 12.0);
         }

         float scale = size * 16.0F / HEAD_SPAN_PX;
         PoseStack pose = graphics.pose();
         pose.pushPose();
         pose.translate(x + size / 2.0F, y + size / 2.0F, 100.0F);
         pose.scale(scale, scale, scale);
         pose.mulPose(Axis.XP.rotationDegrees(180.0F));
         pose.translate(0.0F, -HEAD_CENTRE_PX / 16.0F, 0.0F);
         Lighting.setupForEntityInInventory();
         MultiBufferSource.BufferSource buffers = graphics.bufferSource();
         AnimRenderer.renderBone(head, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(SinclairAssets.TEXTURE)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
         AnimRenderer.renderBone(head, pose, buffers.getBuffer(RenderType.eyes(SinclairAssets.GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            1.0F, 1.0F, 1.0F, 1.0F);
         graphics.flush();
         Lighting.setupFor3DItems();
         pose.popPose();
      }

      graphics.blit(SinclairAssets.HERO_ICON, x - BADGE_SIZE - 2, y + (size - BADGE_SIZE) / 2, BADGE_SIZE, BADGE_SIZE, 0.0F, 0.0F, 32, 32, 32, 32);
   }
}
