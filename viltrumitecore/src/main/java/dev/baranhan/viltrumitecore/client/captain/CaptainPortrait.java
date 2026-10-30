package dev.baranhan.viltrumitecore.client.captain;

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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Subtitle portrait: the bust of Captain America (skin body + helmet/suit gear + star glow). The speaker
 * himself sees the shared front three-quarter view; observers see a low heroic angle from below.
 */
public final class CaptainPortrait implements HeroPortraits.Portrait {
   public static final CaptainPortrait INSTANCE = new CaptainPortrait();
   private static final float SPAN_PX = 13.0F;
   private static final float NECK = 0.64F;
   private static final float NECK_PX = 24.0F;
   /** Observer camera: from below and to the side. */
   static final float LOW_PITCH = -16.0F;
   static final float LOW_YAW = 26.0F;

   private CaptainPortrait() {
   }

   @Override
   public void render(GuiGraphics graphics, HeroPortraits.Frame frame) {
      BakedGeoModel body = AnimCache.model(CaptainView.BODY);
      BakedGeoModel gear = AnimCache.model(CaptainView.GEAR);
      GeoBone torso = body == null ? null : body.getBone("torso");
      GeoBone head = body == null ? null : body.getBone("head");
      if (torso == null || head == null || gear == null) {
         PlayerBustPortrait.INSTANCE.render(graphics, frame);
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      boolean own = mc.player != null && mc.player.getUUID().equals(frame.speaker());
      List<float[]> savedBody = save(body);
      List<float[]> savedGear = save(gear);
      try {
         body.resetBones();
         head.rotY += HeroPortraits.swayYaw(frame.seconds());
         head.rotX += HeroPortraits.swayPitch(frame.seconds()) + (own ? 0.0F : (float)Math.toRadians(8.0));
         CaptainPose.copyPose(body, gear);
         GeoBone gearTorso = gear.getBone("torso");
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
            AnimRenderer.renderBone(torso, pose, buffers.getBuffer(RenderType.entityTranslucent(CaptainView.SKIN)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, 1, 1, 1, alpha);
            if (gearTorso != null) {
               AnimRenderer.renderBone(gearTorso, pose, buffers.getBuffer(RenderType.entityTranslucent(CaptainView.GEAR_TEX)), LightTexture.FULL_BRIGHT,
                  OverlayTexture.NO_OVERLAY, 1, 1, 1, alpha);
               AnimRenderer.renderBone(gearTorso, pose, buffers.getBuffer(RenderType.eyes(CaptainView.GEAR_GLOW)), LightTexture.FULL_BRIGHT,
                  OverlayTexture.NO_OVERLAY, alpha, alpha, alpha, 1.0F);
            }
            pose.popPose();
         });
      } finally {
         restore(body, savedBody);
         restore(gear, savedGear);
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
