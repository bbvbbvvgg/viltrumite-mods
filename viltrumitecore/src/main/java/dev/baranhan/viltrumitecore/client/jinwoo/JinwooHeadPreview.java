package dev.baranhan.viltrumitecore.client.jinwoo;

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

/** Race screen: Jinwoo's 3D head (skin + hair + glowing eyes, slow sway) and the dagger badge. */
public final class JinwooHeadPreview implements HeroHeadPreviews.Preview {
   public static final JinwooHeadPreview INSTANCE = new JinwooHeadPreview();
   private static final ResourceLocation BADGE = JinwooView.rl("textures/gui/jinwoo/hero.png");
   private static final float HEAD_SPAN_PX = 11.5F;
   private static final float HEAD_CENTRE_PX = 28.5F;
   private static final int BADGE_SIZE = 12;

   private JinwooHeadPreview() {
   }

   @Override
   public void draw(GuiGraphics graphics, int x, int y, int size, float partialTick) {
      BakedGeoModel body = AnimCache.model(JinwooView.BODY);
      BakedGeoModel gear = AnimCache.model(JinwooView.GEAR);
      GeoBone head = body == null ? null : body.getBone("head");
      if (head != null) {
         var saved = JinwooPortrait.save(body);
         var savedGear = gear == null ? null : JinwooPortrait.save(gear);
         body.resetBones();
         double t = Util.getMillis() / 1000.0;
         head.rotY = (float)Math.toRadians(15.0 * Math.sin(t * 2.0 * Math.PI / 4.0));
         GeoBone gearHead = null;
         if (gear != null) {
            JinwooPose.copyPose(body, gear);
            gearHead = gear.getBone("head");
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
         AnimRenderer.renderBone(head, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(JinwooView.SKIN)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
         if (gearHead != null) {
            AnimRenderer.renderBone(gearHead, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(JinwooView.GEAR_TEX)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            AnimRenderer.renderBone(gearHead, pose, buffers.getBuffer(RenderType.eyes(JinwooView.GEAR_GLOW)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
         }
         graphics.flush();
         Lighting.setupFor3DItems();
         pose.popPose();
         JinwooPortrait.restore(body, saved);
         if (gear != null) {
            JinwooPortrait.restore(gear, savedGear);
         }
      }
      graphics.blit(BADGE, x - BADGE_SIZE - 2, y + (size - BADGE_SIZE) / 2, BADGE_SIZE, BADGE_SIZE, 0.0F, 0.0F, 32, 32, 32, 32);
   }
}
