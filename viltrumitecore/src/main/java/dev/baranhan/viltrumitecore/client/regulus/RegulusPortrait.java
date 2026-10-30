package dev.baranhan.viltrumitecore.client.regulus;

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
 * Subtitle portrait: the bust of Regulus's own model in the idle stance (white suit, the calm
 * face), in the shared three-quarter bust camera. The shared model's pose is saved and restored
 * so the world render keeps its animation.
 */
public final class RegulusPortrait implements HeroPortraits.Portrait {
   public static final RegulusPortrait INSTANCE = new RegulusPortrait();
   /** Model px from the hair's crown to the cut at the upper chest. */
   private static final float SPAN_PX = 16.5F;
   /** Neck height in the box (fraction of the height from the top). */
   private static final float NECK = 0.68F;
   /** Neck pivot of the head bone, model px above the ground. */
   private static final float NECK_PX = 23.9F;

   private RegulusPortrait() {
   }

   @Override
   public void render(GuiGraphics graphics, HeroPortraits.Frame frame) {
      BakedGeoModel model = AnimCache.model(RegulusAssets.BODY);
      GeoBone root = model == null ? null : model.getBone("root");
      GeoBone head = model == null ? null : model.getBone("head");
      if (root == null || head == null) {
         PlayerBustPortrait.INSTANCE.render(graphics, frame);
         return;
      }

      List<float[]> saved = save(model);
      try {
         RegulusPoser.poseIdle(model);
         head.rotY += HeroPortraits.swayYaw(frame.seconds());
         head.rotX += HeroPortraits.swayPitch(frame.seconds());
         float pixel = frame.height() / SPAN_PX;
         PoseStack pose = graphics.pose();
         HeroPortraits.renderClipped(graphics, frame, (buffers, alpha) -> {
            pose.pushPose();
            HeroPortraits.camera(pose, frame.x() + frame.width() / 2.0F + 1.0F, frame.y() + frame.height() * NECK, pixel);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.translate(0.0F, -NECK_PX / 16.0F, 0.0F);
            AnimRenderer.renderBone(root, pose, buffers.getBuffer(RenderType.entityTranslucent(RegulusAssets.TEXTURE)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);
            AnimRenderer.renderBone(root, pose, buffers.getBuffer(RenderType.eyes(RegulusAssets.GLOW)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, alpha, alpha, alpha, 1.0F);
            pose.popPose();
         });
      } finally {
         restore(model, saved);
      }
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
            return;
         }

         float[] v = saved.get(i++);
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
}
