package dev.baranhan.viltrumitecore.client.thanos.war;

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
import dev.baranhan.viltrumitecore.client.thanos.ThanosView;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Subtitle portraits of the war's voices (Thanos, Ebony Maw, Cull Obsidian): the torso and head of the
 * speaker's own rig in its idle pose, arms out of the frame, with the eye glow. The shared model is
 * restored after drawing (the world renderer poses it every frame anyway).
 */
public final class ThanosWarPortrait implements HeroPortraits.Portrait {
   public static final ThanosWarPortrait THANOS = new ThanosWarPortrait(ThanosView.GEO, ThanosView.TEXTURE, ThanosView.GLOW,
      () -> ThanosView.clip("idle"), 48.5F, 15.5F);
   public static final ThanosWarPortrait MAW = new ThanosWarPortrait(BlackOrderRenderer.MAW.geo(), BlackOrderRenderer.MAW.texture(),
      BlackOrderRenderer.MAW.glow(), () -> BlackOrderRenderer.MAW.clip("idle"), 25.5F, 15.0F);
   public static final ThanosWarPortrait CULL = new ThanosWarPortrait(BlackOrderRenderer.CULL.geo(), BlackOrderRenderer.CULL.texture(),
      BlackOrderRenderer.CULL.glow(), () -> BlackOrderRenderer.CULL.clip("idle"), 42.5F, 14.0F);
   private static final String[] HIDDEN = {"right_arm", "left_arm"};
   private static final float NECK = 0.66F;
   private final ResourceLocation geo;
   private final ResourceLocation texture;
   private final ResourceLocation glow;
   private final Supplier<Animation> idle;
   /** Model px of the neck pivot. */
   private final float neckPx;
   /** Model px from the neck to the top of the head, plus margin. */
   private final float spanPx;

   private ThanosWarPortrait(ResourceLocation geo, ResourceLocation texture, ResourceLocation glow, Supplier<Animation> idle, float neckPx,
      float spanPx) {
      this.geo = geo;
      this.texture = texture;
      this.glow = glow;
      this.idle = idle;
      this.neckPx = neckPx;
      this.spanPx = spanPx;
   }

   @Override
   public void render(GuiGraphics graphics, HeroPortraits.Frame frame) {
      BakedGeoModel model = AnimCache.model(this.geo);
      GeoBone torso = model == null ? null : model.getBone("torso");
      if (torso == null) {
         PlayerBustPortrait.INSTANCE.render(graphics, frame);
         return;
      }

      List<float[]> saved = CaptainPortrait.save(model);
      GeoBone[] hidden = new GeoBone[HIDDEN.length];
      try {
         model.resetBones();
         Animation clip = this.idle.get();
         if (clip != null) {
            AnimationController.overlay(clip, model, frame.seconds() % Math.max(0.05, clip.lengthSeconds()), 1.0F);
         }

         for (int i = 0; i < HIDDEN.length; i++) {
            hidden[i] = model.getBone(HIDDEN[i]);
            if (hidden[i] != null) {
               hidden[i].hidden = true;
            }
         }

         torso.rotX = 0.0F;
         torso.rotY = 0.0F;
         torso.rotZ = 0.0F;
         GeoBone head = model.getBone("head");
         if (head != null) {
            head.rotY += HeroPortraits.swayYaw(frame.seconds());
            head.rotX += HeroPortraits.swayPitch(frame.seconds());
         }

         float pixel = frame.height() / this.spanPx;
         PoseStack pose = graphics.pose();
         float shine = 0.85F + 0.15F * (float)Math.sin(frame.seconds() * 2.4);
         HeroPortraits.renderClipped(graphics, frame, (buffers, alpha) -> {
            pose.pushPose();
            HeroPortraits.camera(pose, frame.x() + frame.width() / 2.0F + 1.0F, frame.y() + frame.height() * NECK, pixel);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.translate(0.0F, -this.neckPx / 16.0F, 0.0F);
            AnimRenderer.renderBone(torso, pose, buffers.getBuffer(RenderType.entityTranslucent(this.texture)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, 1, 1, 1, alpha);
            AnimRenderer.renderBone(torso, pose, buffers.getBuffer(RenderType.eyes(this.glow)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
               alpha * shine, alpha * shine, alpha * shine, 1.0F);
            pose.popPose();
         });
      } finally {
         for (GeoBone b : hidden) {
            if (b != null) {
               b.hidden = false;
            }
         }

         CaptainPortrait.restore(model, saved);
      }
   }
}
