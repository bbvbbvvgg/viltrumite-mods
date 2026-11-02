package dev.baranhan.viltrumitecore.client.ghostrider;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.hero.HeroHeadPreviews;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Race screen: the Rider's 3D skull (slow sway, the jaw working, burning sockets) under a crown of hellfire and the badge. */
public final class GhostRiderHeadPreview implements HeroHeadPreviews.Preview {
   public static final GhostRiderHeadPreview INSTANCE = new GhostRiderHeadPreview();
   private static final ResourceLocation BADGE = GhostRiderAssets.rl("textures/gui/ghost_rider/hero.png");
   /** Skull subtree of rider_gear.geo.json: y 22.8-33, z -4.05-4.4; the frame leaves room for the flames above it. */
   private static final float SPAN_PX = 15.0F;
   private static final float CENTRE_PX = 29.6F;
   private static final float DEPTH_PX = -0.2F;
   private static final float SKULL_TOP = 33.0F / 16.0F;
   private static final int BADGE_SIZE = 12;
   private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);

   private GhostRiderHeadPreview() {
   }

   @Override
   public void draw(GuiGraphics graphics, int x, int y, int size, float partialTick) {
      BakedGeoModel gear = AnimCache.model(GhostRiderView.GEAR_GEO);
      GeoBone skull = gear == null ? null : gear.getBone("skull");
      if (skull != null) {
         List<float[]> saved = save(gear);
         gear.resetBones();
         double t = Util.getMillis() / 1000.0;
         skull.rotY = (float)Math.toRadians(20.0 * Math.sin(t * 2.0 * Math.PI / 5.0));
         GeoBone jaw = gear.getBone("jaw");
         if (jaw != null) {
            jaw.rotX = (float)Math.toRadians(-6.0 - 6.0 * Math.max(0.0, Math.sin(t * 2.0 * Math.PI / 1.6)));
         }
         float scale = size * 16.0F / SPAN_PX;
         PoseStack pose = graphics.pose();
         pose.pushPose();
         pose.translate(x + size / 2.0F, y + size / 2.0F, 100.0F);
         pose.scale(scale, scale, scale);
         pose.mulPose(Axis.XP.rotationDegrees(180.0F));
         pose.translate(0.0F, -CENTRE_PX / 16.0F, DEPTH_PX / 16.0F);
         Lighting.setupForEntityInInventory();
         MultiBufferSource.BufferSource buffers = graphics.bufferSource();
         AnimRenderer.renderBone(skull, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(GhostRiderView.GEAR_TEX)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
         AnimRenderer.renderBone(skull, pose, buffers.getBuffer(RenderType.eyes(GhostRiderView.GEAR_GLOW)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
         crown(buffers.getBuffer(HellfireDraw.type()), pose.last().pose(), (float)(t * 20.0));
         graphics.flush();
         Lighting.setupFor3DItems();
         pose.popPose();
         restore(gear, saved);
      }
      graphics.blit(BADGE, x - BADGE_SIZE - 2, y + (size - BADGE_SIZE) / 2, BADGE_SIZE, BADGE_SIZE, 0.0F, 0.0F, 32, 32, 32, 32);
   }

   /** Five tongues of hellfire along the crown of the skull, the middle ones tallest. */
   private static void crown(VertexConsumer vc, Matrix4f m, float age) {
      for (int i = 0; i < 5; i++) {
         float off = (i - 2) * 0.085F;
         float h = (0.42F - Math.abs(i - 2) * 0.07F) * HellfireDraw.flicker(age, i * 3);
         int frame = HellfireDraw.frame(age, i * 2);
         HellfireDraw.flame(vc, m, off, SKULL_TOP - 0.06F, 0.02F * (i & 1), UP, 0.0F, 0.0F, -1.0F, 0.26F, h, frame,
            i == 2 ? HellfireDraw.BLAZE : HellfireDraw.TONGUE, 255, 235, 210, 235);
      }
   }

   private static List<float[]> save(BakedGeoModel model) {
      List<float[]> out = new ArrayList<>();
      for (GeoBone b : model.allBones()) {
         out.add(new float[]{b.rotX, b.rotY, b.rotZ, b.posX, b.posY, b.posZ, b.scaleX, b.scaleY, b.scaleZ, b.hidden ? 1 : 0});
      }
      return out;
   }

   private static void restore(BakedGeoModel model, List<float[]> saved) {
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
