package dev.baranhan.viltrumitecore.client.venom;

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

/** Race screen: Venom's 3D head (the maw half open, the tongue lashing, the white eyes) and the spider badge. */
public final class VenomHeadPreview implements HeroHeadPreviews.Preview {
   public static final VenomHeadPreview INSTANCE = new VenomHeadPreview();
   private static final ResourceLocation BADGE = VenomView.rl("textures/gui/venom/hero.png");
   /** Head subtree of venom.geo.json (tools/assets/venom_model.py HEAD_*): span, centre height and depth in px. */
   private static final float HEAD_SPAN_PX = 11.0F;
   private static final float HEAD_CENTRE_PX = 28.6F;
   private static final float HEAD_DEPTH_PX = 4.2F;
   private static final int BADGE_SIZE = 12;

   private VenomHeadPreview() {
   }

   @Override
   public void draw(GuiGraphics graphics, int x, int y, int size, float partialTick) {
      BakedGeoModel body = AnimCache.model(VenomView.BODY);
      GeoBone head = body == null ? null : body.getBone("head");
      if (head != null) {
         var saved = VenomPortrait.save(body);
         body.resetBones();
         double t = Util.getMillis() / 1000.0;
         Animation face = AnimCache.animation(VenomView.FACE, "face_snarl");
         if (face != null) {
            AnimationController.overlay(face, body, t % face.lengthSeconds(), 1.0F);
         }
         VenomPose.tongue(body, t, 1.0F);
         head.rotY = (float)Math.toRadians(20.0 * Math.sin(t * 2.0 * Math.PI / 4.5));
         float scale = size * 16.0F / HEAD_SPAN_PX;
         PoseStack pose = graphics.pose();
         pose.pushPose();
         pose.translate(x + size / 2.0F, y + size / 2.0F, 100.0F);
         pose.scale(scale, scale, scale);
         pose.mulPose(Axis.XP.rotationDegrees(180.0F));
         pose.translate(0.0F, -HEAD_CENTRE_PX / 16.0F, HEAD_DEPTH_PX / 16.0F);
         Lighting.setupForEntityInInventory();
         MultiBufferSource.BufferSource buffers = graphics.bufferSource();
         AnimRenderer.renderBone(head, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(VenomView.SKIN)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
         AnimRenderer.renderBone(head, pose, buffers.getBuffer(RenderType.entityTranslucent(VenomView.MARKS)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
         graphics.flush();
         Lighting.setupFor3DItems();
         pose.popPose();
         VenomPortrait.restore(body, saved);
      }
      graphics.blit(BADGE, x - BADGE_SIZE - 2, y + (size - BADGE_SIZE) / 2, BADGE_SIZE, BADGE_SIZE, 0.0F, 0.0F, 32, 32, 32, 32);
   }
}
