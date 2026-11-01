package dev.baranhan.viltrumitecore.client.redhulk;

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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Subtitle portrait: the Red Hulk's bust (torso, arms, head) with the fury face, the jaw working on a growl rhythm
 * while the line shows (non-verbal: the words are only subtitles), glowing eyes and heat veins. The speaker sees the
 * shared three-quarter view; observers see him from below.
 */
public final class RedHulkPortrait implements HeroPortraits.Portrait {
   public static final RedHulkPortrait INSTANCE = new RedHulkPortrait();
   private static final float SPAN_PX = 16.0F;
   private static final float NECK = 0.66F;
   private static final float NECK_PX = 25.0F;
   static final float LOW_PITCH = -14.0F;
   static final float LOW_YAW = 24.0F;

   private RedHulkPortrait() {
   }

   /** Pure: jaw opening (degrees) of the growl: two uneven beats a second, never fully shut. */
   static float growlJaw(double seconds) {
      double a = Math.sin(seconds * Math.PI * 2.0 * 2.1);
      double b = Math.sin(seconds * Math.PI * 2.0 * 3.3 + 1.1);
      return (float)(6.0 + 9.0 * Math.max(0.0, a) + 4.0 * Math.max(0.0, b));
   }

   @Override
   public void render(GuiGraphics graphics, HeroPortraits.Frame frame) {
      BakedGeoModel body = AnimCache.model(RedHulkView.BODY);
      GeoBone torso = body == null ? null : body.getBone("torso");
      GeoBone head = body == null ? null : body.getBone("head");
      if (torso == null || head == null) {
         PlayerBustPortrait.INSTANCE.render(graphics, frame);
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      boolean own = mc.player != null && mc.player.getUUID().equals(frame.speaker());
      List<float[]> saved = save(body);
      try {
         body.resetBones();
         Animation face = AnimCache.animation(RedHulkView.FACE, "face_fury");
         if (face != null) {
            AnimationController.overlay(face, body, frame.seconds() % face.lengthSeconds(), 1.0F);
         }
         GeoBone jaw = body.getBone("jaw");
         if (jaw != null) {
            jaw.rotX = (float)Math.toRadians(-growlJaw(frame.seconds()));
         }
         head.rotY += HeroPortraits.swayYaw(frame.seconds());
         head.rotX += HeroPortraits.swayPitch(frame.seconds()) + (own ? 0.0F : (float)Math.toRadians(6.0));
         float pixel = frame.height() / SPAN_PX;
         PoseStack pose = graphics.pose();
         HeroPortraits.renderClipped(graphics, frame, (buffers, alpha) -> {
            pose.pushPose();
            float cx = frame.x() + frame.width() / 2.0F + 1.0F;
            float cy = frame.y() + frame.height() * (own ? NECK : NECK + 0.06F);
            if (own) {
               HeroPortraits.camera(pose, cx, cy, pixel);
            } else {
               float k = pixel * 16.0F;
               pose.translate(cx, cy, HeroPortraits.DEPTH);
               pose.scale(k, -k, k);
               pose.mulPose(Axis.XP.rotationDegrees(LOW_PITCH));
               pose.mulPose(Axis.YP.rotationDegrees(LOW_YAW));
            }
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.translate(0.0F, -NECK_PX / 16.0F, 0.0F);
            AnimRenderer.renderBone(torso, pose, buffers.getBuffer(RenderType.entityTranslucent(RedHulkView.SKIN)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, 1, 1, 1, alpha);
            AnimRenderer.renderBone(torso, pose, buffers.getBuffer(RenderType.eyes(RedHulkView.EYES)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, alpha, alpha, alpha, 1.0F);
            float glow = 0.55F * alpha;
            AnimRenderer.renderBone(torso, pose, buffers.getBuffer(RenderType.eyes(RedHulkView.HEAT)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, glow, glow * 0.6F, glow * 0.35F, 1.0F);
            pose.popPose();
         });
      } finally {
         restore(body, saved);
      }
   }

   public static List<float[]> save(BakedGeoModel model) {
      List<float[]> out = new ArrayList<>();
      for (GeoBone b : model.allBones()) {
         out.add(new float[]{b.rotX, b.rotY, b.rotZ, b.posX, b.posY, b.posZ, b.scaleX, b.scaleY, b.scaleZ, b.hidden ? 1 : 0});
      }
      return out;
   }

   public static void restore(BakedGeoModel model, List<float[]> saved) {
      int i = 0;
      for (GeoBone b : model.allBones()) {
         float[] v = saved.get(i++);
         b.rotX = v[0];
         b.rotY = v[1];
         b.rotZ = v[2];
         b.posX = v[3];
         b.posY = v[4];
         b.posZ = v[5];
         b.scaleX = v[6];
         b.scaleY = v[7];
         b.scaleZ = v[8];
         b.hidden = v[9] != 0;
      }
   }
}
