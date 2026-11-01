package dev.baranhan.viltrumitecore.client.chitauri;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.captain.CaptainPortrait;
import dev.baranhan.viltrumitecore.client.hero.speech.HeroPortraits;
import dev.baranhan.viltrumitecore.client.hero.speech.PlayerBustPortrait;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Subtitle portrait of the Chitauri voice (hive orders and the commanders' calls): the gold-crowned commander's bust
 * in its idle breathing pose, with the eye / antenna glow. The shared model is restored after drawing.
 */
public final class ChitauriPortrait implements HeroPortraits.Portrait {
   public static final ChitauriPortrait INSTANCE = new ChitauriPortrait();
   private static final ResourceLocation TEX = ChitauriAssets.unitTex("commander", false);
   private static final ResourceLocation GLOW = ChitauriAssets.unitTex("commander", true);
   private static final ResourceLocation ANIM = ChitauriAssets.unitAnim("commander", "");
   /** Model px from the neck to the top of the crown, plus margin. */
   private static final float SPAN_PX = 14.0F;
   private static final float NECK = 0.66F;
   private static final float NECK_PX = 25.8F;

   private ChitauriPortrait() {
   }

   @Override
   public void render(GuiGraphics graphics, HeroPortraits.Frame frame) {
      BakedGeoModel model = AnimCache.model(ChitauriAssets.COMMANDER_GEO);
      GeoBone chest = model == null ? null : model.getBone("chest");
      if (chest == null) {
         PlayerBustPortrait.INSTANCE.render(graphics, frame);
         return;
      }
      List<float[]> saved = CaptainPortrait.save(model);
      try {
         model.resetBones();
         Animation idle = AnimCache.animation(ANIM, "idle");
         if (idle != null) {
            AnimationController.overlay(idle, model, frame.seconds() % idle.lengthSeconds(), 1.0F);
         }
         // the bust: arms and weapon stay out of the frame
         for (String b : new String[]{"arm_r", "arm_l"}) {
            GeoBone bone = model.getBone(b);
            if (bone != null) {
               bone.hidden = true;
            }
         }
         GeoBone head = model.getBone("head");
         if (head != null) {
            head.rotY += HeroPortraits.swayYaw(frame.seconds());
            head.rotX += HeroPortraits.swayPitch(frame.seconds());
         }
         float pixel = frame.height() / SPAN_PX;
         PoseStack pose = graphics.pose();
         float glow = 0.85F + 0.15F * (float)Math.sin(frame.seconds() * 2.4);
         HeroPortraits.renderClipped(graphics, frame, (buffers, alpha) -> {
            pose.pushPose();
            HeroPortraits.camera(pose, frame.x() + frame.width() / 2.0F + 1.0F, frame.y() + frame.height() * NECK, pixel);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.translate(0.0F, -NECK_PX / 16.0F, 0.0F);
            AnimRenderer.renderBone(chest, pose, buffers.getBuffer(RenderType.entityTranslucent(TEX)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
               1, 1, 1, alpha);
            AnimRenderer.renderBone(chest, pose, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
               alpha * glow, alpha * glow, alpha * glow, 1.0F);
            pose.popPose();
         });
      } finally {
         CaptainPortrait.restore(model, saved);
      }
   }
}
