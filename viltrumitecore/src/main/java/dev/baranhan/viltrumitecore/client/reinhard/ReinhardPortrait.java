package dev.baranhan.viltrumitecore.client.reinhard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
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
 * Subtitle portrait of Reinhard: the bust of his own 3D model (he is always
 * drawn as this geo model, never as the vanilla skin) with hair, collar and
 * the glow layer, in the shared three-quarter bust camera. The shared model's
 * bone pose is restored afterwards, so the world render keeps its animation.
 */
public final class ReinhardPortrait implements HeroPortraits.Portrait {
   public static final ReinhardPortrait INSTANCE = new ReinhardPortrait();
   /** Model px from the hair top to the cut at the upper chest. */
   private static final float SPAN_PX = 12.5F;
   /** Neck height in the box (fraction of the height from the top). */
   private static final float NECK = 0.66F;
   /** Neck pivot of the head bone, model px above the ground. */
   private static final float NECK_PX = 24.0F;

   private ReinhardPortrait() {
   }

   @Override
   public void render(GuiGraphics graphics, HeroPortraits.Frame frame) {
      BakedGeoModel model = AnimCache.model(ReinhardAssets.BODY);
      GeoBone body = model == null ? null : model.getBone(ReinhardAssets.BODY_BONE);
      GeoBone head = model == null ? null : model.getBone(ReinhardAssets.HEAD);
      if (body == null || head == null) {
         PlayerBustPortrait.INSTANCE.render(graphics, frame);
         return;
      }

      List<float[]> saved = new ArrayList<>();
      for (GeoBone bone : model.allBones()) {
         saved.add(save(bone));
      }

      try {
         model.resetBones();
         head.rotY += HeroPortraits.swayYaw(frame.seconds());
         head.rotX += HeroPortraits.swayPitch(frame.seconds());
         float pixel = frame.height() / SPAN_PX;
         PoseStack pose = graphics.pose();
         HeroPortraits.renderClipped(graphics, frame, (buffers, alpha) -> {
            pose.pushPose();
            HeroPortraits.camera(pose, frame.x() + frame.width() / 2.0F + 1.0F, frame.y() + frame.height() * NECK, pixel);
            // The geo model is y-up and faces -z: half a turn about y faces it to the viewer.
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.translate(0.0F, -NECK_PX / 16.0F, 0.0F);
            AnimRenderer.renderBone(body, pose, buffers.getBuffer(RenderType.entityTranslucent(ReinhardAssets.TEXTURE)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);
            AnimRenderer.renderBone(body, pose, buffers.getBuffer(RenderType.eyes(ReinhardAssets.GLOW)), LightTexture.FULL_BRIGHT,
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
