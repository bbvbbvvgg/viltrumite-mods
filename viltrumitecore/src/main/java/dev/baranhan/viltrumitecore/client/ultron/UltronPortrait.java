package dev.baranhan.viltrumitecore.client.ultron;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
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
 * Subtitle portrait of Ultron's world voice: the bust of Ultron Prime's own model (layered faceplates, the
 * red eyes and mouth seam lit), idling with the face clip talking, in the shared three-quarter bust camera.
 * The same face speaks from the first glitch on, long before the body is built. The shared model's pose is
 * saved and restored so the world render keeps its animation.
 */
public final class UltronPortrait implements HeroPortraits.Portrait {
   public static final UltronPortrait INSTANCE = new UltronPortrait();
   /** Model px from the crown to the cut at the upper chest. */
   private static final float SPAN_PX = 13.8F;
   /** Neck height in the box (fraction of the height from the top). */
   private static final float NECK = 0.52F;
   /** Pivot of the head bone, model px above the ground. */
   private static final float NECK_PX = 40.6F;

   private UltronPortrait() {
   }

   @Override
   public void render(GuiGraphics graphics, HeroPortraits.Frame frame) {
      BakedGeoModel model = AnimCache.model(UltronAssets.PRIME.geo());
      GeoBone root = model == null ? null : model.getBone("root");
      GeoBone head = model == null ? null : model.getBone("head");
      if (root == null || head == null) {
         PlayerBustPortrait.INSTANCE.render(graphics, frame);
         return;
      }
      List<float[]> saved = save(model);
      try {
         model.resetBones();
         double t = frame.seconds();
         Animation idle = AnimCache.animation(UltronAssets.PRIME.anim(), "idle");
         if (idle != null) {
            AnimationController.overlay(idle, model, t % Math.max(0.05, idle.lengthSeconds()));
         }
         Animation talk = AnimCache.animation(UltronAssets.PRIME.anim(), "talk");
         if (talk != null) {
            AnimationController.additive(talk, model, t % Math.max(0.05, talk.lengthSeconds()), 1.0F);
         }
         head.rotY += HeroPortraits.swayYaw(t);
         head.rotX += HeroPortraits.swayPitch(t);
         float pixel = frame.height() / SPAN_PX;
         PoseStack pose = graphics.pose();
         float glow = 0.85F + 0.15F * (float)Math.sin(t * 5.0);
         HeroPortraits.renderClipped(graphics, frame, (buffers, alpha) -> {
            pose.pushPose();
            HeroPortraits.camera(pose, frame.x() + frame.width() / 2.0F + 1.0F, frame.y() + frame.height() * NECK, pixel);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.translate(0.0F, -NECK_PX / 16.0F, 0.0F);
            AnimRenderer.renderBone(root, pose, buffers.getBuffer(RenderType.entityTranslucent(UltronAssets.PRIME.texture())), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);
            float g = alpha * glow;
            AnimRenderer.renderBone(root, pose, buffers.getBuffer(RenderType.eyes(UltronAssets.PRIME.glow())), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, g, g, g, 1.0F);
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
