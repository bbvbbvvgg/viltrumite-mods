package dev.baranhan.viltrumitecore.client.thanos;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.hero.speech.HeroPortraits;
import dev.baranhan.viltrumitecore.client.reinhard.ReinhardAssets;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSceneTimeline;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Od Lagna in the universe scene: Reinhard's rig ({@link ReinhardAssets#BODY}, his files untouched)
 * in Od Lagna's own white-gold skin ({@link ThanosView#OD_LAGNA}), posed by his own clips
 * ({@code animations/thanos/thanos_od_lagna.animation.json}: forming, idle, one gesture per line,
 * the restore). The rig is shared with Reinhard's renderer, so every pose here is saved and put
 * back afterwards.
 */
final class ThanosOdLagna {
   /** Clip names in the Od Lagna animation file. */
   static final String FORM = "od_lagna_form";
   static final String IDLE = "od_lagna_idle";
   static final String RESTORE = "od_lagna_restore";
   /** Model px from the hair top to the chest cut of the subtitle bust; the neck's share of the box. */
   private static final float BUST_SPAN_PX = 12.5F;
   private static final float BUST_NECK = 0.66F;
   private static final float NECK_PX = 24.0F;

   private ThanosOdLagna() {
   }

   @Nullable
   static BakedGeoModel model() {
      return AnimCache.model(ReinhardAssets.BODY);
   }

   static List<float[]> save(BakedGeoModel model) {
      List<float[]> saved = new ArrayList<>();
      for (GeoBone bone : model.allBones()) {
         saved.add(new float[]{bone.rotX, bone.rotY, bone.rotZ, bone.posX, bone.posY, bone.posZ, bone.scaleX, bone.scaleY, bone.scaleZ,
            bone.hidden ? 1.0F : 0.0F});
      }

      return saved;
   }

   static void restore(BakedGeoModel model, List<float[]> saved) {
      int i = 0;
      for (GeoBone bone : model.allBones()) {
         if (i >= saved.size()) {
            break;
         }

         float[] s = saved.get(i++);
         bone.rotX = s[0];
         bone.rotY = s[1];
         bone.rotZ = s[2];
         bone.posX = s[3];
         bone.posY = s[4];
         bone.posZ = s[5];
         bone.scaleX = s[6];
         bone.scaleY = s[7];
         bone.scaleZ = s[8];
         bone.hidden = s[9] > 0.5F;
      }
   }

   static String lineClip(int line) {
      return "od_lagna_line_" + (line + 1);
   }

   private static void play(BakedGeoModel model, String name, float ticks, float weight, boolean loop) {
      Animation clip = ThanosView.clip(name);
      if (clip == null || weight <= 0.001F) {
         return;
      }

      double seconds = Math.max(0.0, ticks / 20.0);
      double length = Math.max(0.05, clip.lengthSeconds());
      seconds = loop ? seconds % length : Math.min(seconds, length - 0.001);
      AnimationController.overlay(clip, model, seconds, Math.min(1.0F, weight));
   }

   /** Poses the rig at scene time t: forming, the idle, each line's gesture, the restore. */
   static void pose(BakedGeoModel model, float t) {
      model.resetBones();
      float since = t - ThanosSceneTimeline.VOID_END;
      play(model, IDLE, since, 1.0F, true);
      if (t < ThanosSceneTimeline.ARRIVAL_END + 10) {
         float out = 1.0F - ThanosSceneTimeline.smooth((t - (ThanosSceneTimeline.ARRIVAL_END - 10)) / 20.0F);
         play(model, FORM, since, out, false);
      }

      int line = ThanosSceneTimeline.line(t);
      if (line >= 0) {
         float lt = ThanosSceneTimeline.lineTime(t);
         float w = Math.min(ThanosSceneTimeline.smooth(lt / 10.0F), 1.0F - ThanosSceneTimeline.smooth((lt - (ThanosSceneTimeline.LINE_TICKS - 12)) / 12.0F));
         play(model, lineClip(line), lt, w, false);
      }

      if (t >= ThanosSceneTimeline.SPEECH_END - 6) {
         play(model, RESTORE, t - (ThanosSceneTimeline.SPEECH_END - 6), ThanosSceneTimeline.smooth((t - (ThanosSceneTimeline.SPEECH_END - 6)) / 12.0F), false);
      }
   }

   /** Gentle hover of the feet over the ground (blocks). */
   static float hover(float t) {
      return 0.18F + 0.07F * (float)Math.sin(t * 0.055F);
   }

   /** The body and its glow at full presence (the pose already applied). */
   static void draw(BakedGeoModel model, PoseStack stack, MultiBufferSource buffers, float alpha, float glow) {
      RenderType type = alpha < 0.999F ? RenderType.entityTranslucent(ThanosView.OD_LAGNA) : RenderType.entityCutoutNoCull(ThanosView.OD_LAGNA);
      AnimRenderer.render(model, stack, null, buffers.getBuffer(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha, null);
      float g = glow * alpha;
      if (g > 0.01F) {
         VertexConsumer glowBuffer = buffers.getBuffer(RenderType.eyes(ThanosView.OD_LAGNA_GLOW));
         AnimRenderer.render(model, stack, null, glowBuffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, g, g, g, 1.0F, null);
      }
   }

   /** Subtitle portrait: his bust in the shared three-quarter bust camera (the rig's pose saved and restored). */
   static void bust(GuiGraphics graphics, HeroPortraits.Frame frame) {
      BakedGeoModel model = model();
      GeoBone body = model == null ? null : model.getBone(ReinhardAssets.BODY_BONE);
      GeoBone head = model == null ? null : model.getBone(ReinhardAssets.HEAD);
      if (body == null || head == null) {
         return;
      }

      List<float[]> saved = save(model);
      try {
         model.resetBones();
         head.rotY += HeroPortraits.swayYaw(frame.seconds());
         head.rotX += HeroPortraits.swayPitch(frame.seconds());
         float pixel = frame.height() / BUST_SPAN_PX;
         PoseStack pose = graphics.pose();
         HeroPortraits.renderClipped(graphics, frame, (buffers, alpha) -> {
            pose.pushPose();
            HeroPortraits.camera(pose, frame.x() + frame.width() / 2.0F + 1.0F, frame.y() + frame.height() * BUST_NECK, pixel);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.translate(0.0F, -NECK_PX / 16.0F, 0.0F);
            AnimRenderer.renderBone(body, pose, buffers.getBuffer(RenderType.entityTranslucent(ThanosView.OD_LAGNA)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);
            AnimRenderer.renderBone(body, pose, buffers.getBuffer(RenderType.eyes(ThanosView.OD_LAGNA_GLOW)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, alpha, alpha, alpha, 1.0F);
            pose.popPose();
         });
      } finally {
         restore(model, saved);
      }
   }
}
