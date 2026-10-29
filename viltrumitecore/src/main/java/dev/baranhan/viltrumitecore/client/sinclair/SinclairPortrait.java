package dev.baranhan.viltrumitecore.client.sinclair;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.RealTime;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.hero.speech.HeroPortraits;
import dev.baranhan.viltrumitecore.client.hero.speech.PlayerBustPortrait;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Subtitle portrait (spec §5.6, PR #65): the bust of Sinclair's own 3D model in his top hat, the
 * wand raised and the medallion glowing, in the shared three-quarter bust camera. The shared
 * model's pose is restored afterwards so the world render keeps its animation.
 */
public final class SinclairPortrait implements HeroPortraits.Portrait {
   public static final SinclairPortrait INSTANCE = new SinclairPortrait();
   /** Model px from the hat's crown to the cut at the upper chest. */
   private static final float SPAN_PX = 17.5F;
   /** Neck height in the box (fraction of the height from the top). */
   private static final float NECK = 0.74F;
   /** Neck pivot of the head bone, model px above the ground. */
   private static final float NECK_PX = 23.4F;

   private SinclairPortrait() {
   }

   @Override
   public void render(GuiGraphics graphics, HeroPortraits.Frame frame) {
      BakedGeoModel model = AnimCache.model(SinclairAssets.BODY);
      GeoBone body = model == null ? null : model.getBone("body");
      GeoBone head = model == null ? null : model.getBone("head");
      if (body == null || head == null) {
         PlayerBustPortrait.INSTANCE.render(graphics, frame);
         return;
      }

      List<float[]> saved = new ArrayList<>();
      for (GeoBone bone : model.allBones()) {
         saved.add(save(bone));
      }

      try {
         Animation idle = SinclairAssets.clip("idle");
         if (idle != null) {
            AnimationController.seek(idle, model, SinclairPoser.loop(idle, RealTime.seconds()));
         } else {
            model.resetBones();
         }

         SinclairPoser.props(model, 0, null, 0.0);
         head.rotY += HeroPortraits.swayYaw(frame.seconds());
         head.rotX += HeroPortraits.swayPitch(frame.seconds());
         float pixel = frame.height() / SPAN_PX;
         PoseStack pose = graphics.pose();
         HeroPortraits.renderClipped(graphics, frame, (buffers, alpha) -> {
            pose.pushPose();
            HeroPortraits.camera(pose, frame.x() + frame.width() / 2.0F + 1.0F, frame.y() + frame.height() * NECK, pixel);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.translate(0.0F, -NECK_PX / 16.0F, 0.0F);
            AnimRenderer.renderBone(body, pose, buffers.getBuffer(RenderType.entityTranslucent(SinclairAssets.TEXTURE)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);
            AnimRenderer.renderBone(body, pose, buffers.getBuffer(RenderType.eyes(SinclairAssets.GLOW)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, alpha, alpha, alpha, 1.0F);
            pose.popPose();
         });
      } finally {
         int i = 0;
         for (GeoBone bone : model.allBones()) {
            restore(bone, saved.get(i++));
         }
      }
   }

   private static float[] save(GeoBone bone) {
      return new float[]{bone.rotX, bone.rotY, bone.rotZ, bone.posX, bone.posY, bone.posZ, bone.scaleX, bone.scaleY, bone.scaleZ, bone.hidden ? 1.0F : 0.0F};
   }

   private static void restore(GeoBone bone, float[] v) {
      bone.rotX = v[0];
      bone.rotY = v[1];
      bone.rotZ = v[2];
      bone.posX = v[3];
      bone.posY = v[4];
      bone.posZ = v[5];
      bone.scaleX = v[6];
      bone.scaleY = v[7];
      bone.scaleZ = v[8];
      bone.hidden = v[9] != 0.0F;
   }
}
