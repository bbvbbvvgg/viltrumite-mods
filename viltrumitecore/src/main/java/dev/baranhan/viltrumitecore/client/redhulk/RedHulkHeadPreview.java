package dev.baranhan.viltrumitecore.client.redhulk;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
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

/** Race screen: the Red Hulk's 3D head (snarl, slow sway, glowing eyes) and the red fist badge. */
public final class RedHulkHeadPreview implements HeroHeadPreviews.Preview {
   public static final RedHulkHeadPreview INSTANCE = new RedHulkHeadPreview();
   private static final ResourceLocation BADGE = RedHulkView.rl("textures/gui/red_hulk/hero.png");
   /** Head subtree of red_hulk.geo.json: y 24.05-32.75, z -7.45-0.1 (the face juts forward of the neck). */
   private static final float HEAD_SPAN_PX = 10.0F;
   private static final float HEAD_CENTRE_PX = 28.4F;
   private static final float HEAD_DEPTH_PX = 3.7F;
   private static final int BADGE_SIZE = 12;

   private RedHulkHeadPreview() {
   }

   @Override
   public void draw(GuiGraphics graphics, int x, int y, int size, float partialTick) {
      BakedGeoModel body = AnimCache.model(RedHulkView.BODY);
      GeoBone head = body == null ? null : body.getBone("head");
      if (head != null) {
         var saved = RedHulkPortrait.save(body);
         body.resetBones();
         double t = Util.getMillis() / 1000.0;
         Animation face = AnimCache.animation(RedHulkView.FACE, "face_snarl");
         if (face != null) {
            AnimationController.overlay(face, body, t % face.lengthSeconds(), 1.0F);
         }
         head.rotY = (float)Math.toRadians(18.0 * Math.sin(t * 2.0 * Math.PI / 4.0));
         float scale = size * 16.0F / HEAD_SPAN_PX;
         PoseStack pose = graphics.pose();
         pose.pushPose();
         pose.translate(x + size / 2.0F, y + size / 2.0F, 100.0F);
         pose.scale(scale, scale, scale);
         pose.mulPose(Axis.XP.rotationDegrees(180.0F));
         pose.translate(0.0F, -HEAD_CENTRE_PX / 16.0F, HEAD_DEPTH_PX / 16.0F);
         Lighting.setupForEntityInInventory();
         MultiBufferSource.BufferSource buffers = graphics.bufferSource();
         AnimRenderer.renderBone(head, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(RedHulkView.SKIN)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
         AnimRenderer.renderBone(head, pose, buffers.getBuffer(RenderType.eyes(RedHulkView.EYES)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
         graphics.flush();
         Lighting.setupFor3DItems();
         pose.popPose();
         RedHulkPortrait.restore(body, saved);
      }
      graphics.blit(BADGE, x - BADGE_SIZE - 2, y + (size - BADGE_SIZE) / 2, BADGE_SIZE, BADGE_SIZE, 0.0F, 0.0F, 32, 32, 32, 32);
   }
}
